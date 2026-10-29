package com.wind.mybatis.plugin;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.BaseMapper;
import com.mybatisflex.core.datasource.FlexDataSource;
import com.mybatisflex.core.mybatis.FlexConfiguration;
import com.mybatisflex.core.mybatis.FlexSqlSessionFactoryBuilder;
import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryWrapper;
import com.wind.integration.core.context.DataIsolationContextHolder;
import com.wind.integration.core.model.TenantIsolationObject;
import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.ResultMap;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.session.TransactionIsolationLevel;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

class DynamicSqlIsolationIntegrationTests {

    private static final AtomicInteger DATABASE_SEQUENCE = new AtomicInteger();

    private UnpooledDataSource dataSource;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new UnpooledDataSource("org.h2.Driver", "jdbc:h2:mem:dynamic_sql_"
                + DATABASE_SEQUENCE.incrementAndGet() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        try (var connection = dataSource.getConnection(); var sql = connection.createStatement()) {
            sql.execute("CREATE TABLE public_records(id BIGINT)");
            sql.execute("INSERT INTO public_records VALUES(99)");
            sql.execute("CREATE TABLE private_orders(id BIGINT, tenant_id BIGINT)");
            sql.execute("INSERT INTO private_orders VALUES(1,10),(2,20)");
        }
    }

    /** 四参数查询必须执行首次检查的 SQL，且在两种插件顺序下都经过审计插件。 */
    @Test
    void testDynamicSqlQueryUsesCheckedBoundSqlWithBothPluginOrders() {
        for (boolean isolationFirst : List.of(false, true)) {
            AtomicInteger evaluations = new AtomicInteger();
            QueryAuditInterceptor audit = new QueryAuditInterceptor();
            SqlSessionFactory factory = factory(evaluations, audit, isolationFirst);

            DataIsolationContextHolder.runWithTenant(10L, () -> {
                try (var session = factory.openSession()) {
                    Assertions.assertEquals(List.of(99L), session.selectList("dynamic.switch"));
                    Assertions.assertEquals(1, evaluations.get());
                    Assertions.assertEquals(List.of("dynamic.switch"), audit.events);
                }
            });
        }
    }

    /** 游标必须消费首次检查的 SQL，不能因再次求值读出另一租户的数据。 */
    @Test
    void testDynamicSqlCursorUsesCheckedBoundSql() {
        AtomicInteger evaluations = new AtomicInteger();
        SqlSessionFactory factory = factory(evaluations, new QueryAuditInterceptor(), false);

        DataIsolationContextHolder.runWithTenant(10L, () -> {
            try (var session = factory.openSession(); var cursor = session.<Long>selectCursor("dynamic.switch")) {
                List<Long> ids = new ArrayList<>();
                cursor.forEach(ids::add);
                Assertions.assertEquals(List.of(99L), ids);
                Assertions.assertEquals(1, evaluations.get());
            } catch (java.io.IOException exception) {
                throw new IllegalStateException(exception);
            }
        });
    }

    /** 其他插件读取动态 SQL 时，两种插件顺序均不能执行未经隔离检查的查询。 */
    @Test
    void testSqlReadingPluginWithBothOrders() {
        for (boolean isolationFirst : List.of(false, true)) {
            AtomicInteger evaluations = new AtomicInteger();
            QueryAuditInterceptor audit = new QueryAuditInterceptor(true);
            SqlSessionFactory factory = factory(evaluations, audit, isolationFirst);

            DataIsolationContextHolder.runWithTenant(10L, () -> {
                try (var session = factory.openSession()) {
                    List<Long> expected = isolationFirst ? List.of(1L) : List.of(99L);
                    Assertions.assertEquals(expected, session.selectList("dynamic.switch"));
                    Assertions.assertEquals(1, audit.sql.size());
                    Assertions.assertEquals(isolationFirst ? 2 : 1, evaluations.get());
                }
            });
        }
    }

    @Test
    void testXmlForeachAndBindParametersRemainAvailable() {
        String script = "<script>SELECT id FROM public_records WHERE id IN "
                + "<foreach collection='ids' item='item' open='(' separator=',' close=')'>#{item}</foreach> "
                + "<bind name='maximum' value='ceiling'/> AND id &lt; #{maximum}</script>";
        SqlSessionFactory factory = factory(configuration ->
                new XMLLanguageDriver().createSqlSource(configuration, script, Map.class), new QueryAuditInterceptor(), false);

        DataIsolationContextHolder.runWithTenant(10L, () -> {
            Map<String, Object> parameter = Map.of("ids", List.of(99L, 100L), "ceiling", 100L);
            try (var session = factory.openSession()) {
                Assertions.assertEquals(List.of(99L), session.selectList("dynamic.switch", parameter));
            }
            try (var session = factory.openSession(); var cursor = session.<Long>selectCursor("dynamic.switch", parameter)) {
                List<Long> ids = new ArrayList<>();
                cursor.forEach(ids::add);
                Assertions.assertEquals(List.of(99L), ids);
            } catch (java.io.IOException exception) {
                throw new IllegalStateException(exception);
            }
        });
    }

    @Test
    void testUnmanagedFlexQueriesKeepNativeParameterBinding() {
        FlexDataSource flexSource = new FlexDataSource("default", dataSource, false);
        FlexConfiguration configuration = new FlexConfiguration(new Environment("flex", new JdbcTransactionFactory(), flexSource));
        configuration.addInterceptor(new DataIsolationQueryInterceptor());
        configuration.addMapper(PublicMapper.class);
        SqlSessionFactory factory = new FlexSqlSessionFactoryBuilder().build(configuration);

        try (var session = factory.openSession()) {
            PublicMapper mapper = session.getMapper(PublicMapper.class);
            Assertions.assertEquals(99L, mapper.selectOneById(99L).getId());
            Assertions.assertNull(mapper.selectOneById(100L));
            Assertions.assertEquals(List.of(99L), mapper.selectListByQuery(
                    QueryWrapper.create().where(new QueryColumn("id").eq(99L))).stream().map(PublicRecord::getId).toList());
        }
    }

    /** 未受管查询固定 SQL 时，同时保留普通参数和动态 SQL 的附加参数。 */
    @Test
    void testUnmanagedDynamicSqlKeepsParameterMappingsAndAdditionalParameters() {
        AtomicInteger evaluations = new AtomicInteger();
        SqlSessionFactory factory = factory(configuration -> parameter -> {
            evaluations.incrementAndGet();
            List<ParameterMapping> mappings = List.of(
                    new ParameterMapping.Builder(configuration, "minimum", Long.class).build(),
                    new ParameterMapping.Builder(configuration, "__ceiling", Long.class).build());
            BoundSql sql = new BoundSql(configuration, "SELECT id FROM public_records WHERE id >= ? AND id < ?",
                    mappings, parameter);
            sql.setAdditionalParameter("__ceiling", 100L);
            return sql;
        }, new QueryAuditInterceptor(), false);

        DataIsolationContextHolder.runWithTenant(10L, () -> {
            try (var session = factory.openSession()) {
                Assertions.assertEquals(List.of(99L), session.selectList("dynamic.switch", Map.of("minimum", 90L)));
            }
            try (var session = factory.openSession();
                 var cursor = session.<Long>selectCursor("dynamic.switch", Map.of("minimum", 90L))) {
                List<Long> ids = new ArrayList<>();
                cursor.forEach(ids::add);
                Assertions.assertEquals(List.of(99L), ids);
            } catch (java.io.IOException exception) {
                throw new IllegalStateException(exception);
            }
        });
        Assertions.assertEquals(2, evaluations.get());
    }

    /** 首次生成受管 SQL 时应保持租户过滤，即使后续求值可能产生另一条 SQL。 */
    @Test
    void testFirstProtectedSqlIsFilteredWithoutReevaluatingSource() {
        AtomicInteger evaluations = new AtomicInteger();
        SqlSessionFactory factory = factory(configuration -> parameter -> new BoundSql(configuration,
                evaluations.getAndIncrement() == 0 ? "SELECT id FROM private_orders ORDER BY id" : "SELECT id FROM public_records",
                List.of(), parameter), new QueryAuditInterceptor(), false);

        DataIsolationContextHolder.runWithTenant(10L, () -> {
            try (var session = factory.openSession()) {
                Assertions.assertEquals(List.of(1L), session.selectList("dynamic.switch"));
                Assertions.assertEquals(1, evaluations.get());
            }
        });
    }

    /** 六参数入口使用调用方提供的 SQL，并按隔离值重建缓存键。 */
    @Test
    void testSixArgumentQueryKeepsProvidedBoundSqlAndTenantCacheSeparation() throws Exception {
        AtomicInteger evaluations = new AtomicInteger();
        SqlSessionFactory factory = factory(evaluations, new QueryAuditInterceptor(), false);
        Configuration configuration = factory.getConfiguration();
        Environment environment = configuration.getEnvironment();
        Executor executor = configuration.newExecutor(environment.getTransactionFactory()
                .newTransaction(dataSource, TransactionIsolationLevel.READ_COMMITTED, false));
        try {
            MappedStatement statement = configuration.getMappedStatement("dynamic.switch");
            BoundSql protectedSql = new BoundSql(configuration, "SELECT id FROM private_orders ORDER BY id", List.of(), null);
            CacheKey unscoped = executor.createCacheKey(statement, null, RowBounds.DEFAULT, protectedSql);

            List<Long> firstTenant = DataIsolationContextHolder.callWithTenantId(10L,
                    () -> executor.query(statement, null, RowBounds.DEFAULT, null, unscoped, protectedSql));
            List<Long> secondTenant = DataIsolationContextHolder.callWithTenantId(20L,
                    () -> executor.query(statement, null, RowBounds.DEFAULT, null, unscoped, protectedSql));

            Assertions.assertEquals(List.of(1L), firstTenant);
            Assertions.assertEquals(List.of(2L), secondTenant);
            Assertions.assertEquals(0, evaluations.get());
        } finally {
            executor.close(true);
        }
    }

    private SqlSessionFactory factory(AtomicInteger evaluations, QueryAuditInterceptor audit, boolean isolationFirst) {
        return factory(configuration -> parameter -> new BoundSql(configuration,
                evaluations.getAndIncrement() == 0 ? "SELECT id FROM public_records" : "SELECT id FROM private_orders ORDER BY id",
                List.of(), parameter), audit, isolationFirst);
    }

    private SqlSessionFactory factory(Function<Configuration, SqlSource> sourceFactory,
                                      QueryAuditInterceptor audit, boolean isolationFirst) {
        Configuration configuration = new Configuration(new Environment("dynamic", new JdbcTransactionFactory(), dataSource));
        configuration.addMapper(OrderMapper.class);
        if (isolationFirst) {
            configuration.addInterceptor(new DataIsolationQueryInterceptor());
            configuration.addInterceptor(audit);
        } else {
            configuration.addInterceptor(audit);
            configuration.addInterceptor(new DataIsolationQueryInterceptor());
        }
        SqlSource switching = sourceFactory.apply(configuration);
        ResultMap result = new ResultMap.Builder(configuration, "dynamic.result", Long.class, List.of()).build();
        configuration.addMappedStatement(new MappedStatement.Builder(configuration, "dynamic.switch", switching,
                SqlCommandType.SELECT).resultMaps(List.of(result)).build());
        return new SqlSessionFactoryBuilder().build(configuration);
    }

    @Table("private_orders")
    static class Order implements TenantIsolationObject<Long> {
        @Id
        private Long id;
        private Long tenantId;

        @Override
        public Long getId() {
            return id;
        }

        @Override
        public Long getTenantId() {
            return tenantId;
        }
    }

    interface OrderMapper extends BaseMapper<Order> {
    }

    @Table("public_records")
    static class PublicRecord {
        @Id
        private Long id;

        public Long getId() {
            return id;
        }
    }

    interface PublicMapper extends BaseMapper<PublicRecord> {
    }

    @Intercepts(@Signature(type = Executor.class, method = "query", args = {
            MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class
    }))
    static class QueryAuditInterceptor implements Interceptor {

        private final List<String> events = new ArrayList<>();
        private final List<String> sql = new ArrayList<>();
        private final boolean readSql;

        QueryAuditInterceptor() {
            this(false);
        }

        QueryAuditInterceptor(boolean readSql) {
            this.readSql = readSql;
        }

        @Override
        public Object intercept(Invocation invocation) throws Throwable {
            MappedStatement statement = (MappedStatement) invocation.getArgs()[0];
            events.add(statement.getId());
            if (readSql) {
                sql.add(statement.getBoundSql(invocation.getArgs()[1]).getSql());
            }
            return invocation.proceed();
        }
    }
}
