package com.wind.mybatis.isolation;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.core.read.ListAppender;
import ch.qos.logback.core.spi.FilterReply;
import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.BaseMapper;
import com.mybatisflex.core.datasource.FlexDataSource;
import com.mybatisflex.core.mybatis.FlexConfiguration;
import com.mybatisflex.core.mybatis.FlexSqlSessionFactoryBuilder;
import com.mybatisflex.core.mybatis.TypeHandlerObject;
import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.query.RawQueryCondition;
import com.mybatisflex.core.tenant.TenantFactory;
import com.mybatisflex.core.tenant.TenantManager;
import com.wind.integration.core.context.DataIsolationQueryIgnoreHolder;
import com.wind.integration.core.context.TenantContextHolder;
import com.wind.integration.core.model.EnvIsolationObject;
import com.wind.integration.core.model.TenantIsolationObject;
import com.wind.integration.core.model.TestDataIsolationObject;
import com.wind.mybatis.convert.LocaleTypeHandler;
import com.wind.mybatis.isolation.DataIsolationStrategy.FieldRule;
import com.wind.mybatis.isolation.FieldDataIsolationStrategy;
import com.wind.mybatis.plugin.DataIsolationQueryInterceptor;
import lombok.Getter;
import lombok.Setter;
import org.apache.ibatis.annotations.CacheNamespace;
import org.apache.ibatis.annotations.Case;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Many;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.TypeDiscriminator;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.builder.StaticSqlSource;
import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.exceptions.PersistenceException;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ResultMap;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.session.TransactionIsolationLevel;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.apache.ibatis.type.JdbcType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;

import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import static com.wind.integration.core.enums.DataIsolationDimensions.ENVIRONMENT;
import static com.wind.integration.core.enums.DataIsolationDimensions.TENANT;
import static com.wind.integration.core.enums.DataIsolationDimensions.TEST_DATA;

class DataIsolationQueryInterceptorIntegrationTests {

    private static final AtomicInteger DATABASE_SEQUENCE = new AtomicInteger();

    private SqlSessionFactory sessionFactory;

    private DataIsolationScope tenantScope;

    private DataIsolationScope environmentScope;

    private DataIsolationScope testDataScope;

    @BeforeEach
    void setUp() throws Exception {
        String url = "jdbc:h2:mem:read_isolation_" + DATABASE_SEQUENCE.incrementAndGet() + ";MODE=MySQL;DB_CLOSE_DELAY=-1";
        UnpooledDataSource dataSource = new UnpooledDataSource("org.h2.Driver", url, "sa", "");
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE t_read_isolation (id BIGINT PRIMARY KEY, tenant_id BIGINT, env VARCHAR(20), is_test BOOLEAN, name VARCHAR(50))");
            statement.execute("INSERT INTO t_read_isolation VALUES (1,10,'sit',TRUE,'one'),(2,10,'sit',FALSE,'two'),(3,20,'sit',TRUE,'three'),(4,10,'prod',TRUE,'four'),(5,10,'sit',TRUE,'five')");
            statement.execute("CREATE TABLE t_public (id BIGINT PRIMARY KEY)");
            statement.execute("INSERT INTO t_public VALUES (1),(2)");
        }
        Configuration configuration = new Configuration(new Environment("test", new JdbcTransactionFactory(), dataSource));
        configuration.addInterceptor(interceptor());
        configuration.addMapper(FlexReadMapper.class);
        configuration.addMapper(ReadMapper.class);
        sessionFactory = new SqlSessionFactoryBuilder().build(configuration);
        TenantContextHolder.setTenantId(10L);
        environmentScope = DataIsolationScope.of("sit");
        testDataScope = DataIsolationScope.of(true);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void testInheritedInterfacesConstrainListCountDetailAndAggregate() {
        try (SqlSession session = sessionFactory.openSession()) {
            ReadMapper mapper = session.getMapper(ReadMapper.class);
            Assertions.assertEquals(List.of(1L, 5L), mapper.ids());
            Assertions.assertEquals(2L, mapper.count());
            Assertions.assertEquals(6L, mapper.sum());
            Assertions.assertNull(mapper.byId(3L));
            Assertions.assertEquals(5L, mapper.byId(5L));
            Assertions.assertEquals(2L, mapper.publicCount());
        }
    }

    @Test
    void testPaginationAndDynamicParametersKeepTheirBindings() {
        try (SqlSession session = sessionFactory.openSession()) {
            ReadMapper mapper = session.getMapper(ReadMapper.class);
            Assertions.assertEquals(List.of(5L), mapper.page(0L, 1, 1));
            Assertions.assertEquals(List.of(5L), mapper.commaPage(1, 1));
            Assertions.assertEquals(List.of(1L, 5L), mapper.inIds(List.of(1L, 2L, 3L, 5L)));
            Assertions.assertEquals(List.of("row-5"), mapper.projection("row-", 0L, 6L, true, 1, 0));
            Assertions.assertEquals(List.of(1L, 5L), mapper.parameterNameCollision(0L));
        }
    }

    @Test
    void testOriginalOrConditionsRemainGrouped() {
        try (SqlSession session = sessionFactory.openSession()) {
            Assertions.assertEquals(List.of(1L), session.getMapper(ReadMapper.class).byIdOrName(1L, "three"));
        }
    }

    @Test
    void testDebugLogsSummarizeQueryWithoutSqlOrParameterValues() {
        try (IsolationLogs logs = new IsolationLogs(); SqlSession session = sessionFactory.openSession()) {
            Assertions.assertEquals(List.of(1L), session.getMapper(ReadMapper.class)
                    .byIdOrName(1L, "private-query-value-marker"));

            List<String> messages = logs.appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
            String statementId = ReadMapper.class.getName() + ".byIdOrName";
            List<String> summaries = messages.stream().filter(message -> message.startsWith("Read isolation prepared:")).toList();
            Assertions.assertEquals(1, summaries.size());
            String summary = summaries.getFirst();
            Assertions.assertTrue(summary.contains(statementId));
            Assertions.assertTrue(summary.contains("sqlChanged=true"));
            Assertions.assertTrue(summary.contains("parameterCount=5"));
            Assertions.assertTrue(summary.contains("elapsedMicros="));
            Assertions.assertFalse(messages.stream().anyMatch(message -> message.contains("private-query-value-marker")
                    || message.contains("SELECT ")));
            Assertions.assertFalse(messages.stream().anyMatch(message -> message.startsWith("Resolving read isolation:")));
            Assertions.assertTrue(logs.appender.list.stream().allMatch(event -> event.getLevel() == Level.DEBUG));
            Assertions.assertTrue(logs.appender.list.stream().allMatch(event -> event.getThrowableProxy() == null));
        }
    }

    /**
     * 前后 SQL 在同一 TRACE 事件中对照；查询照常隔离，值、字面量和注释均不进入诊断日志。
     */
    @Test
    void testTraceLogsBeforeAndAfterSqlWithoutSensitiveValues() {
        try (IsolationLogs logs = new IsolationLogs(Level.TRACE); SqlSession session = sessionFactory.openSession()) {
            Assertions.assertEquals(List.of(1L), session.getMapper(ReadMapper.class)
                    .diagnosticQuery(1L, "private-query-value-marker"));

            String statementId = ReadMapper.class.getName() + ".diagnosticQuery";
            List<ILoggingEvent> sqlEvents = logs.appender.list.stream()
                    .filter(event -> event.getMessage().startsWith("Read isolation SQL:")).toList();
            Assertions.assertEquals(1, sqlEvents.size());
            ILoggingEvent event = sqlEvents.getFirst();
            Assertions.assertEquals(Level.TRACE, event.getLevel());
            Assertions.assertTrue(event.getMessage().contains("before={}"));
            Assertions.assertTrue(event.getMessage().contains("after={}"));
            Object[] arguments = event.getArgumentArray();
            Assertions.assertEquals(4, arguments.length);
            Assertions.assertEquals(statementId, arguments[0]);
            Assertions.assertEquals(true, arguments[1]);
            String before = (String) arguments[2];
            String after = (String) arguments[3];
            Assertions.assertTrue(before.contains("SELECT id FROM t_read_isolation"));
            Assertions.assertFalse(before.contains("tenant_id"));
            Assertions.assertTrue(after.contains("tenant_id = ?"));
            Assertions.assertTrue(after.contains("env = ?"));
            Assertions.assertTrue(after.contains("is_test = ?"));
            List<String> messages = logs.appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
            Assertions.assertTrue(messages.stream().anyMatch(message -> message.contains(statementId)
                    && message.contains(IsolatedEntity.class.getName())
                    && message.contains(FieldDataIsolationStrategy.class.getName())));
            for (String marker : List.of("private-query-value-marker", "private-query-literal-marker", "private-query-comment-marker")) {
                Assertions.assertFalse(messages.stream().anyMatch(message -> message.contains(marker)), marker);
            }
            Assertions.assertTrue(logs.appender.list.stream().allMatch(logEvent -> logEvent.getThrowableProxy() == null));
        }
    }

    /**
     * Flex 自定义参数仍需适配，但全维忽略不改 SQL 文本，不能把 BoundSql 换对象记成 SQL 改写。
     */
    @Test
    void testTraceReportsUnchangedSqlAfterFlexParameterAdaptation() {
        Configuration original = sessionFactory.getConfiguration();
        FlexDataSource dataSource = new FlexDataSource("default", original.getEnvironment().getDataSource(), false);
        FlexConfiguration configuration = new FlexConfiguration(new Environment("flex", new JdbcTransactionFactory(), dataSource));
        configuration.addInterceptor(interceptor());
        configuration.addMapper(FlexReadMapper.class);
        SqlSessionFactory factory = new FlexSqlSessionFactoryBuilder().build(configuration);
        TenantFactory previous = TenantManager.getTenantFactory();
        TenantManager.setTenantFactory(() -> new Object[]{TenantContextHolder.requireTenantId()});
        try (IsolationLogs logs = new IsolationLogs(Level.TRACE); SqlSession session = factory.openSession()) {
            QueryWrapper query = QueryWrapper.create().where(new QueryColumn("name")
                    .eq(new TypeHandlerObject(new LocaleTypeHandler(), Locale.forLanguageTag("one"), JdbcType.VARCHAR)));
            DataIsolationQueryIgnoreHolder.runIgnoring(() -> {
                IsolatedEntity entity = session.getMapper(FlexReadMapper.class).selectOneByQuery(query);
                Assertions.assertEquals(1L, entity.getId());
            }, TENANT, ENVIRONMENT, TEST_DATA);

            List<ILoggingEvent> sqlEvents = logs.appender.list.stream()
                    .filter(event -> event.getMessage().startsWith("Read isolation SQL:")).toList();
            Assertions.assertEquals(1, sqlEvents.size());
            Object[] arguments = sqlEvents.getFirst().getArgumentArray();
            Assertions.assertEquals(false, arguments[1]);
            Assertions.assertEquals(arguments[2], arguments[3]);
            Assertions.assertTrue(arguments[2].toString().contains("t_read_isolation"));
            Assertions.assertTrue(logs.appender.list.stream().map(ILoggingEvent::getFormattedMessage)
                    .anyMatch(message -> message.startsWith("Read isolation prepared:") && message.contains("sqlChanged=false")));
        } finally {
            TenantManager.setTenantFactory(previous);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"OFF", "DEBUG", "TRACE"})
    void testRejectedQueryLogsFailureStageWithoutParserSqlDetails(String levelName) {
        Level level = Level.valueOf(levelName);
        try (IsolationLogs logs = new IsolationLogs(level)) {
            PersistenceException failure = Assertions.assertThrows(PersistenceException.class,
                    () -> selectBoundarySql("SELECT 'private-query-literal-marker' FROM"));

            Assertions.assertTrue(failure.getMessage().contains("Cannot parse query for read isolation"));
            if (level == Level.OFF) {
                Assertions.assertTrue(logs.appender.list.isEmpty());
                return;
            }
            String statementId = ReadMapper.class.getName() + ".boundarySql";
            Assertions.assertTrue(logs.appender.list.stream().anyMatch(event -> event.getLevel() == Level.WARN
                    && event.getFormattedMessage().contains(statementId)
                    && event.getFormattedMessage().contains("stage=REWRITE")
                    && event.getFormattedMessage().contains(IllegalStateException.class.getName())));
            Assertions.assertFalse(logs.appender.list.stream().map(ILoggingEvent::getFormattedMessage)
                    .anyMatch(message -> message.contains("private-query-literal-marker") || message.contains("SELECT ")));
            Assertions.assertTrue(logs.appender.list.stream().allMatch(event -> event.getThrowableProxy() == null));
        }
    }

    /**
     * 每次求值得到不同记录；任何日志引起的额外求值都会跳过第二条应返回的记录。
     */
    @ParameterizedTest
    @ValueSource(strings = {"OFF", "DEBUG", "TRACE"})
    void testDiagnosticLevelsDoNotReevaluateDynamicSql(String levelName) {
        Configuration configuration = sessionFactory.getConfiguration();
        String statementId = ReadMapper.class.getName() + ".changingSql";
        AtomicInteger evaluations = new AtomicInteger();
        SqlSource source = parameter -> {
            long id = switch (evaluations.incrementAndGet()) {
                case 1 -> 1L;
                case 2 -> 5L;
                default -> 3L;
            };
            return new BoundSql(configuration, "SELECT id FROM t_read_isolation WHERE id = " + id, List.of(), parameter);
        };
        ResultMap result = new ResultMap.Builder(configuration, statementId + "Result", Long.class, List.of()).build();
        configuration.addMappedStatement(new MappedStatement.Builder(configuration, statementId, source, SqlCommandType.SELECT)
                .resultMaps(List.of(result)).build());

        try (IsolationLogs logs = new IsolationLogs(Level.valueOf(levelName)); SqlSession session = sessionFactory.openSession()) {
            Assertions.assertEquals(List.of(1L), session.selectList(statementId));
            Assertions.assertEquals(List.of(5L), session.selectList(statementId));
            Assertions.assertEquals(2, evaluations.get());
        }
    }

    /**
     * 原 SQL 超过诊断长度上限时仅省略日志内容，合法长注释不能影响实际查询与隔离。
     */
    @Test
    void testTraceOmitsOversizedSqlWithoutChangingQueryResult() {
        String sql = "SELECT id FROM t_read_isolation /* private-long-comment-marker " + "x".repeat(20000) + " */ ORDER BY id";
        try (IsolationLogs logs = new IsolationLogs(Level.TRACE)) {
            Assertions.assertEquals(List.of(1L, 5L), selectBoundarySql(sql));

            List<ILoggingEvent> sqlEvents = logs.appender.list.stream()
                    .filter(event -> event.getMessage().startsWith("Read isolation SQL:")).toList();
            Assertions.assertEquals(1, sqlEvents.size());
            Object[] arguments = sqlEvents.getFirst().getArgumentArray();
            String before = (String) arguments[2];
            String after = (String) arguments[3];
            Assertions.assertEquals("<SQL omitted; length=" + sql.length() + "; reason=input-too-long>", before);
            Assertions.assertTrue(after.contains("tenant_id = ?"));
            Assertions.assertFalse(logs.appender.list.stream().map(ILoggingEvent::getFormattedMessage)
                    .anyMatch(message -> message.contains("private-long-comment-marker")));
        }
    }

    /**
     * 日志后端直接抛出异常时，准备摘要或 SQL 对照失败均不能中止已通过隔离校验的查询。
     */
    @ParameterizedTest
    @ValueSource(strings = {"DEBUG", "TRACE"})
    void testDiagnosticAppenderFailureDoesNotChangeQueryResult(String levelName) {
        AtomicInteger failures = new AtomicInteger();
        Logger logger = (Logger) LoggerFactory.getLogger(DataIsolationQueryInterceptor.class);
        ListAppender<ILoggingEvent> appender = failingLogAppender(Level.valueOf(levelName), failures);
        try (IsolationLogs logs = new IsolationLogs(logger, Level.TRACE, appender); SqlSession session = sessionFactory.openSession()) {
            Assertions.assertEquals(List.of(1L, 5L), session.getMapper(ReadMapper.class).ids());
            Assertions.assertEquals(1, failures.get());
        }
    }

    @Test
    void testRejectedQueryKeepsOriginalFailureWhenWarnAppenderThrows() {
        AtomicInteger failures = new AtomicInteger();
        Logger logger = (Logger) LoggerFactory.getLogger(DataIsolationQueryInterceptor.class);
        try (IsolationLogs logs = new IsolationLogs(logger, Level.TRACE, failingLogAppender(Level.WARN, failures))) {
            PersistenceException failure = Assertions.assertThrows(PersistenceException.class,
                    () -> selectBoundarySql("SELECT 'private-query-literal-marker' FROM"));

            Assertions.assertTrue(failure.getMessage().contains("Cannot parse query for read isolation"));
            Assertions.assertFalse(failure.getMessage().contains("diagnostic-appender-failure"));
            Assertions.assertEquals(1, failures.get());
        }
    }

    /**
     * 重写器日志提交失败不影响隔离、公共表快速路径及全维忽略，真实不支持的查询仍须拒绝。
     */
    @ParameterizedTest
    @ValueSource(strings = {"DEBUG", "TRACE"})
    void testRewriterAppenderFailurePreservesQueryBehavior(String levelName) {
        sessionFactory.getConfiguration().addMapper(DiagnosticPublicMapper.class);
        AtomicInteger failures = new AtomicInteger();
        Logger logger = (Logger) LoggerFactory.getLogger("com.wind.mybatis.plugin.SelectIsolationRewriter");
        ListAppender<ILoggingEvent> appender = failingLogAppender(Level.valueOf(levelName), failures);
        try (IsolationLogs logs = new IsolationLogs(logger, Level.TRACE, appender); SqlSession session = sessionFactory.openSession()) {
            ReadMapper mapper = session.getMapper(ReadMapper.class);
            Assertions.assertEquals(List.of(1L, 5L), mapper.ids());
            Assertions.assertEquals(2L, mapper.publicCount());
            Assertions.assertEquals(2L, mapper.publicCount());
            DataIsolationQueryIgnoreHolder.runIgnoring(() ->
                    Assertions.assertEquals(List.of(1L, 2L, 3L, 4L, 5L), mapper.ids()), TENANT, ENVIRONMENT, TEST_DATA);
            Assertions.assertEquals(List.of(1L, 5L), mapper.ids());
            Assertions.assertTrue(failures.get() > 0);

            PersistenceException failure = Assertions.assertThrows(PersistenceException.class, mapper::joined);
            Assertions.assertTrue(failure.getMessage().contains("Read isolation supports single-table SELECT only"));
            Assertions.assertFalse(failure.getMessage().contains("diagnostic-appender-failure"));
        }
    }

    /**
     * 级别过滤器在两个隔离日志入口抛异常时，不能中止正常读取，也不能掩盖真实的 SQL 拒绝。
     */
    @ParameterizedTest
    @ValueSource(strings = {"DEBUG", "TRACE"})
    void testLoggingLevelFilterFailurePreservesQueryBehavior(String levelName) {
        sessionFactory.getConfiguration().addMapper(DiagnosticPublicMapper.class);
        Level failedLevel = Level.valueOf(levelName);
        String interceptorLogger = DataIsolationQueryInterceptor.class.getName();
        String rewriterLogger = "com.wind.mybatis.plugin.SelectIsolationRewriter";
        AtomicInteger interceptorFailures = new AtomicInteger();
        AtomicInteger rewriterFailures = new AtomicInteger();
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        TurboFilter filter = new TurboFilter() {
            @Override
            public FilterReply decide(Marker marker, Logger logger, Level level, String format, Object[] arguments, Throwable throwable) {
                if (level != failedLevel) {
                    return FilterReply.NEUTRAL;
                }
                if (interceptorLogger.equals(logger.getName())) {
                    interceptorFailures.incrementAndGet();
                } else if (rewriterLogger.equals(logger.getName())) {
                    rewriterFailures.incrementAndGet();
                } else {
                    return FilterReply.NEUTRAL;
                }
                throw new IllegalStateException("diagnostic-level-filter-failure");
            }
        };
        filter.setContext(context);
        filter.start();
        context.addTurboFilter(filter);
        try (IsolationLogs logs = new IsolationLogs(Level.TRACE); SqlSession session = sessionFactory.openSession()) {
            ReadMapper mapper = session.getMapper(ReadMapper.class);
            Assertions.assertEquals(List.of(1L, 5L), mapper.ids());
            Assertions.assertEquals(2L, mapper.publicCount());
            Assertions.assertEquals(2L, mapper.publicCount());
            DataIsolationQueryIgnoreHolder.runIgnoring(() ->
                    Assertions.assertEquals(List.of(1L, 2L, 3L, 4L, 5L), mapper.ids()), TENANT, ENVIRONMENT, TEST_DATA);
            Assertions.assertEquals(List.of(1L, 5L), mapper.ids());
            Assertions.assertTrue(interceptorFailures.get() > 0);
            Assertions.assertTrue(rewriterFailures.get() > 0);

            PersistenceException failure = Assertions.assertThrows(PersistenceException.class, mapper::joined);
            Assertions.assertTrue(failure.getMessage().contains("Read isolation supports single-table SELECT only"));
            Assertions.assertFalse(failure.getMessage().contains("diagnostic-level-filter-failure"));
        } finally {
            context.getTurboFilterList().remove(filter);
            filter.stop();
        }
    }

    @Test
    void testCursorUsesTheSameScope() throws Exception {
        try (SqlSession session = sessionFactory.openSession(); Cursor<Long> cursor = session.getMapper(ReadMapper.class).cursor()) {
            List<Long> ids = new ArrayList<>();
            cursor.forEach(ids::add);
            Assertions.assertEquals(List.of(1L, 5L), ids);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"OFF", "DEBUG", "TRACE"})
    void testScopeValuesSeparateLocalAndSecondLevelCaches(String levelName) {
        try (IsolationLogs logs = new IsolationLogs(Level.valueOf(levelName)); SqlSession session = sessionFactory.openSession()) {
            ReadMapper mapper = session.getMapper(ReadMapper.class);
            Assertions.assertEquals(List.of(1L, 5L), mapper.ids());
            TenantContextHolder.runWithTenantId(20L, () -> Assertions.assertEquals(List.of(3L), mapper.ids()));
            Assertions.assertEquals(List.of(1L, 5L), mapper.ids());
            session.commit();
        }
        try (IsolationLogs logs = new IsolationLogs(Level.valueOf(levelName)); SqlSession session = sessionFactory.openSession()) {
            TenantContextHolder.runWithTenantId(20L,
                    () -> Assertions.assertEquals(List.of(3L), session.getMapper(ReadMapper.class).ids()));
        }
    }

    @Test
    void testOtherQueryPluginsRunInEitherRegistrationOrder() {
        for (boolean isolationFirst : List.of(false, true)) {
            Configuration configuration = new Configuration(sessionFactory.getConfiguration().getEnvironment());
            QueryAuditInterceptor audit = new QueryAuditInterceptor();
            if (isolationFirst) {
                configuration.addInterceptor(interceptor());
                configuration.addInterceptor(audit);
            } else {
                configuration.addInterceptor(audit);
                configuration.addInterceptor(interceptor());
            }
            configuration.addMapper(FlexReadMapper.class);
            configuration.addMapper(ReadMapper.class);
            SqlSessionFactory factory = new SqlSessionFactoryBuilder().build(configuration);

            try (SqlSession session = factory.openSession()) {
                Assertions.assertEquals(List.of(1L, 5L), session.getMapper(ReadMapper.class).ids());
                Assertions.assertEquals(List.of(ReadMapper.class.getName() + ".ids"), audit.events);
            }
        }
    }

    @Test
    void testSixArgumentQueryRebuildsExistingCacheKey() throws Exception {
        Configuration configuration = sessionFactory.getConfiguration();
        Environment environment = configuration.getEnvironment();
        Executor executor = configuration.newExecutor(environment.getTransactionFactory()
                .newTransaction(environment.getDataSource(), TransactionIsolationLevel.READ_COMMITTED, false));
        try {
            MappedStatement statement = configuration.getMappedStatement(ReadMapper.class.getName() + ".ids");
            BoundSql sql = statement.getBoundSql(null);
            CacheKey unscopedKey = executor.createCacheKey(statement, null, RowBounds.DEFAULT, sql);
            Assertions.assertEquals(List.of(1L, 5L), executor.query(statement, null, RowBounds.DEFAULT, null, unscopedKey, sql));
            List<Long> otherTenant = TenantContextHolder.callWithTenantId(20L,
                    () -> executor.query(statement, null, RowBounds.DEFAULT, null, unscopedKey, sql));
            Assertions.assertEquals(List.of(3L), otherTenant);
        } finally {
            executor.close(true);
        }
    }

    @Test
    void testIgnoringOneDimensionPreservesOthersAndOriginalSql() {
        try (SqlSession session = sessionFactory.openSession()) {
            ReadMapper mapper = session.getMapper(ReadMapper.class);
            DataIsolationQueryIgnoreHolder.runIgnoring(() -> Assertions.assertEquals(List.of(1L, 2L, 5L), mapper.ids()), TEST_DATA);
            Assertions.assertEquals(List.of(1L, 5L), mapper.ids());
            DataIsolationQueryIgnoreHolder.runIgnoring(() -> Assertions.assertEquals(List.of(3L), mapper.explicitTenant(20L)), TENANT);
        }
    }

    @Test
    void testIgnoringMultipleDimensionsPreservesRemainingFilterAndRestoresContext() throws Exception {
        try (SqlSession session = sessionFactory.openSession()) {
            ReadMapper mapper = session.getMapper(ReadMapper.class);
            List<Long> ids = DataIsolationQueryIgnoreHolder.callIgnoring(mapper::ids, TENANT, ENVIRONMENT);

            Assertions.assertEquals(List.of(1L, 3L, 4L, 5L), ids);
            Assertions.assertEquals(List.of(1L, 5L), mapper.ids());
            Assertions.assertEquals(10L, TenantContextHolder.requireTenantId());
        }
    }

    @Test
    void testEmptyScopeReturnsNoRowsAndMissingScopeFails() {
        try (SqlSession session = sessionFactory.openSession()) {
            ReadMapper mapper = session.getMapper(ReadMapper.class);
            tenantScope = DataIsolationScope.ofValues(List.of());
            Assertions.assertEquals(List.of(), mapper.ids());
            Assertions.assertEquals(0L, mapper.count());
            tenantScope = null;
            TenantContextHolder.clear();
            Assertions.assertThrows(PersistenceException.class, mapper::ids);
        }
    }

    @Test
    void testMultipleValuesAndUnrestrictedTestScope() {
        tenantScope = DataIsolationScope.ofValues(List.of(10L, 20L));
        testDataScope = DataIsolationScope.unrestrictedScope();
        try (SqlSession session = sessionFactory.openSession()) {
            Assertions.assertEquals(List.of(1L, 2L, 3L, 5L), session.getMapper(ReadMapper.class).ids());
            Assertions.assertEquals(10L, TenantContextHolder.requireTenantId());
        }
    }

    @Test
    void testScopeSupplierChangesReadRangeWithoutChangingTenantIdentity() {
        try (SqlSession session = sessionFactory.openSession()) {
            ReadMapper mapper = session.getMapper(ReadMapper.class);
            Assertions.assertEquals(List.of(1L, 5L), mapper.ids());
            tenantScope = DataIsolationScope.of(20L);
            Assertions.assertEquals(List.of(3L), mapper.ids());
            Assertions.assertEquals(10L, TenantContextHolder.requireTenantId());
            tenantScope = null;
            Assertions.assertEquals(List.of(1L, 5L), mapper.ids());
            Assertions.assertEquals(10L, TenantContextHolder.requireTenantId());
        }
    }

    @Test
    void testScopeValuesUseParameterBinding() {
        environmentScope = DataIsolationScope.of("sit' OR 1=1 --");
        try (SqlSession session = sessionFactory.openSession()) {
            Assertions.assertEquals(List.of(), session.getMapper(ReadMapper.class).ids());
        }
    }

    @Test
    void testFlexGeneratedPrimaryKeyAndQuerySqlUseTheSameReader() {
        Configuration original = sessionFactory.getConfiguration();
        FlexDataSource dataSource = new FlexDataSource("default", original.getEnvironment().getDataSource(), false);
        FlexConfiguration configuration = new FlexConfiguration(new Environment("flex", new JdbcTransactionFactory(), dataSource));
        configuration.addInterceptor(interceptor());
        configuration.addMapper(FlexReadMapper.class);
        SqlSessionFactory flexFactory = new FlexSqlSessionFactoryBuilder().build(configuration);
        TenantFactory previous = TenantManager.getTenantFactory();
        TenantManager.setTenantFactory(() -> new Object[]{TenantContextHolder.requireTenantId()});
        try (SqlSession session = flexFactory.openSession()) {
            FlexReadMapper mapper = session.getMapper(FlexReadMapper.class);
            Assertions.assertNull(mapper.selectOneById(3L));
            Assertions.assertEquals(1L, mapper.selectOneById(1L).getId());
            Assertions.assertEquals(2L, mapper.selectCountByQuery(QueryWrapper.create()));
            QueryWrapper customTypeQuery = QueryWrapper.create().where(new QueryColumn("name")
                    .eq(new TypeHandlerObject(new LocaleTypeHandler(), Locale.forLanguageTag("one"), JdbcType.VARCHAR)));
            Assertions.assertEquals(1L, mapper.selectOneByQuery(customTypeQuery).getId());
            Assertions.assertEquals(2L, mapper.selectCountByQuery(QueryWrapper.create()
                    .where(new RawQueryCondition("? IS NULL", (Object) null))));
            DataIsolationQueryIgnoreHolder.runIgnoring(() -> {
                List<Long> ids = mapper.selectListByQuery(QueryWrapper.create()).stream().map(IsolatedEntity::getId).sorted().toList();
                Assertions.assertEquals(List.of(1L, 2L, 5L), ids);
            }, TEST_DATA);
            DataIsolationQueryIgnoreHolder.runIgnoring(() -> Assertions.assertNull(mapper.selectOneById(3L)), TENANT);
        } finally {
            TenantManager.setTenantFactory(previous);
        }
    }

    @Test
    void testUnsupportedQueriesFailBeforeReturningUnscopedData() {
        try (SqlSession session = sessionFactory.openSession()) {
            ReadMapper mapper = session.getMapper(ReadMapper.class);
            Assertions.assertThrows(PersistenceException.class, mapper::joined);
            Assertions.assertThrows(PersistenceException.class, mapper::subquery);
            Assertions.assertThrows(PersistenceException.class, mapper::nested);
            Assertions.assertThrows(PersistenceException.class, mapper::discriminatedNested);
        }
    }

    /** UNION ALL 的两个叶查询均应隔离，并保留重复记录。 */
    @Test
    void testUnionBranchesPreserveIsolationAndDuplicates() {
        try (SqlSession session = sessionFactory.openSession()) {
            List<Long> ids = session.getMapper(ReadMapper.class).union();
            Assertions.assertEquals(List.of(1L, 1L, 5L, 5L), ids.stream().sorted().toList());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "(SELECT id FROM t_read_isolation ORDER BY id LIMIT 1 OFFSET 1)",
            "((SELECT id FROM t_read_isolation ORDER BY id LIMIT 1 OFFSET 1))"
    })
    void testParenthesizedRootQueryKeepsVisibleRows(String sql) {
        Assertions.assertEquals(List.of(5L), selectBoundarySql(sql));
    }

    /** 复杂查询不能通过 CTE 名称遮蔽或表达式位置读取其他租户的数据。 */
    @ParameterizedTest
    @ValueSource(strings = {
            "WITH t_read_isolation AS (SELECT * FROM t_read_isolation) SELECT id FROM t_read_isolation",
            "WITH `t_read_isolation` AS (SELECT * FROM `t_read_isolation`) SELECT id FROM `t_read_isolation`",
            "WITH t_read_isolation AS (SELECT * FROM PUBLIC.t_read_isolation) SELECT id FROM t_read_isolation",
            "WITH orders AS (SELECT * FROM t_read_isolation), ids AS (SELECT id FROM orders) SELECT id FROM ids",
            "WITH t_read_isolation AS (SELECT * FROM t_public) SELECT id FROM t_read_isolation",
            "SELECT id FROM t_public ORDER BY (SELECT tenant_id FROM t_read_isolation WHERE id=3)",
            "SELECT COUNT(*) FROM t_public GROUP BY (SELECT tenant_id FROM t_read_isolation WHERE id=3)",
            "SELECT p.id FROM t_public p JOIN t_public q ON p.id=q.id AND (SELECT tenant_id FROM t_read_isolation WHERE id=3)=20",
            "SELECT id FROM t_read_isolation ORDER BY (SELECT id FROM t_public WHERE id=1)",
            "SELECT id FROM t_public WHERE (SELECT tenant_id FROM t_read_isolation WHERE id=3) IS NOT NULL",
            "SELECT id FROM t_read_isolation WHERE id=ANY(SELECT id FROM t_public)",
            "SELECT FIRST_VALUE((SELECT tenant_id FROM t_read_isolation WHERE id=3)) OVER () FROM t_public",
            "SELECT MAX(id) OVER (PARTITION BY (SELECT tenant_id FROM t_read_isolation WHERE id=3)) FROM t_public",
            "SELECT EXTRACT(YEAR FROM (SELECT CURRENT_DATE FROM t_read_isolation WHERE id=3)) FROM t_public",
            "SELECT id FROM t_public LIMIT (SELECT COUNT(*) FROM t_read_isolation)",
            "SELECT p.id FROM (t_public p JOIN t_read_isolation r ON p.id=r.id)",
            "SELECT id FROM (t_read_isolation)"
    })
    void testNestedSqlCannotBypassReadIsolation(String sql) {
        PersistenceException error = Assertions.assertThrows(PersistenceException.class, () -> selectBoundarySql(sql));

        Assertions.assertTrue(error.getMessage().contains("Read isolation supports single-table SELECT only"));
    }

    /** JSQLParser 5.4 在语法解析阶段拒绝括号内 TABLE 简写，仍不能执行未隔离查询。 */
    @ParameterizedTest
    @ValueSource(strings = {
            "SELECT id FROM t_public WHERE id=(TABLE t_read_isolation)",
            "SELECT id FROM t_public WHERE id=(TABLE /* 查询简写 */ t_public)"
    })
    void testTableShorthandIsRejectedDuringParsing(String sql) {
        PersistenceException error = Assertions.assertThrows(PersistenceException.class, () -> selectBoundarySql(sql));
        Assertions.assertTrue(error.getMessage().contains("Cannot parse query for read isolation"));
    }

    /** 无受管表的查询仍可使用复杂结构，投影别名和字符串内容不应触发隔离。 */
    @ParameterizedTest
    @ValueSource(strings = {
            "SELECT p.id FROM t_public p ORDER BY (SELECT q.id FROM t_public q WHERE q.id=p.id)",
            "SELECT p.id FROM t_public p JOIN t_public q ON p.id=q.id ORDER BY p.id",
            "SELECT id FROM t_public UNION SELECT id FROM t_public ORDER BY id",
            "WITH t_public AS (SELECT id FROM t_public) SELECT id FROM t_public ORDER BY id",
            "SELECT t_read_isolation.* FROM t_public t_read_isolation ORDER BY t_read_isolation.id",
            "SELECT id FROM (t_public) ORDER BY id",
            "(SELECT id FROM t_public) ORDER BY id",
            "(SELECT id FROM t_public ORDER BY id) LIMIT 2",
            "SELECT p.id FROM (t_public p JOIN t_public q ON p.id=q.id) ORDER BY p.id",
            "SELECT id FROM t_public WHERE 'SELECT * FROM t_read_isolation' IS NOT NULL ORDER BY id",
            "SELECT id FROM t_public WHERE '(TABLE t_read_isolation)' IS NOT NULL ORDER BY id",
            "SELECT id FROM t_public /* (TABLE t_read_isolation) */ ORDER BY id"
    })
    void testPublicQueriesKeepTheirOriginalBehavior(String sql) {
        Assertions.assertEquals(List.of(1L, 2L), selectBoundarySql(sql));
    }

    @Test
    void testIgnoringAllDimensionsDoesNotAllowNestedSql() {
        DataIsolationQueryIgnoreHolder.runIgnoring(() -> {
            PersistenceException error = Assertions.assertThrows(PersistenceException.class, () -> selectBoundarySql(
                    "SELECT id FROM t_public ORDER BY (SELECT tenant_id FROM t_read_isolation WHERE id=3)"));
            Assertions.assertTrue(error.getMessage().contains("Read isolation supports single-table SELECT only"));
        }, TENANT, ENVIRONMENT, TEST_DATA);
    }

    private List<Long> selectBoundarySql(String sql) {
        Configuration configuration = sessionFactory.getConfiguration();
        String statementId = ReadMapper.class.getName() + ".boundarySql";
        ResultMap result = new ResultMap.Builder(configuration, statementId + "Result", Long.class, List.of()).build();
        configuration.addMappedStatement(new MappedStatement.Builder(configuration, statementId,
                new StaticSqlSource(configuration, sql), SqlCommandType.SELECT)
                .resultMaps(List.of(result)).build());
        try (SqlSession session = sessionFactory.openSession()) {
            return session.selectList(statementId);
        }
    }

    @Test
    void testReadInterceptorDoesNotChangeWrites() {
        try (SqlSession session = sessionFactory.openSession()) {
            ReadMapper mapper = session.getMapper(ReadMapper.class);
            Assertions.assertEquals(1, mapper.insertOutsideReadScope());
            Assertions.assertEquals(1, mapper.updateOutsideReadScope());
            Assertions.assertNull(mapper.byId(8L));
            DataIsolationQueryIgnoreHolder.runIgnoring(() -> Assertions.assertEquals(8L, mapper.byId(8L)), TENANT, ENVIRONMENT);
            Assertions.assertEquals(1, mapper.deleteOutsideReadScope());
            session.rollback();
        }
    }

    private DataIsolationQueryInterceptor interceptor() {
        FieldDataIsolationStrategy fields = FieldDataIsolationStrategy.withDefaults(List.of(
                FieldRule.forDimension(TENANT, "tenantId", () -> tenantScope == null
                        ? DataIsolationScope.of(TenantContextHolder.requireTenantId()) : tenantScope),
                FieldRule.forDimension(ENVIRONMENT, "env", () -> environmentScope),
                FieldRule.forDimension(TEST_DATA, "testData", () -> testDataScope)));
        return new DataIsolationQueryInterceptor(List.of(fields));
    }

    private ListAppender<ILoggingEvent> failingLogAppender(Level failedLevel, AtomicInteger failures) {
        return new ListAppender<>() {
            @Override
            public void doAppend(ILoggingEvent event) {
                if (event.getLevel() == failedLevel) {
                    failures.incrementAndGet();
                    throw new IllegalStateException("diagnostic-appender-failure");
                }
            }
        };
    }

    private static final class IsolationLogs implements AutoCloseable {

        private final Logger logger;

        private final Level previousLevel;

        private final boolean previousAdditive;

        private final ListAppender<ILoggingEvent> appender;

        private IsolationLogs() {
            this(Level.DEBUG);
        }

        private IsolationLogs(Level level) {
            this((Logger) LoggerFactory.getLogger("com.wind.mybatis.plugin"), level, new ListAppender<>());
        }

        private IsolationLogs(Logger logger, Level level, ListAppender<ILoggingEvent> appender) {
            this.logger = logger;
            this.appender = appender;
            previousLevel = logger.getLevel();
            previousAdditive = logger.isAdditive();
            appender.start();
            logger.addAppender(appender);
            logger.setLevel(level);
            logger.setAdditive(false);
        }

        @Override
        public void close() {
            logger.detachAppender(appender);
            appender.stop();
            logger.setLevel(previousLevel);
            logger.setAdditive(previousAdditive);
        }
    }

    @Intercepts(@Signature(type = Executor.class, method = "query", args = {
            MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class
    }))
    static class QueryAuditInterceptor implements Interceptor {

        private final List<String> events = new ArrayList<>();

        @Override
        public Object intercept(Invocation invocation) throws Throwable {
            events.add(((MappedStatement) invocation.getArgs()[0]).getId());
            return invocation.proceed();
        }
    }

    @Getter
    @Setter
    static class IsolatedBase implements TenantIsolationObject<Long>, EnvIsolationObject, TestDataIsolationObject {
        @Id
        private Long id;
        @Column(tenantId = true)
        private Long tenantId;
        private String env;
        @Column("is_test")
        private boolean testData;
    }

    @Table("t_read_isolation")
    static class IsolatedEntity extends IsolatedBase {
    }

    interface FlexReadMapper extends BaseMapper<IsolatedEntity> {
    }

    @Getter
    @Setter
    @Table("t_public")
    static class DiagnosticPublicRecord {
        @Id
        private Long id;
    }

    interface DiagnosticPublicMapper extends BaseMapper<DiagnosticPublicRecord> {
    }

    @Getter
    @Setter
    static class NestedView {
        private List<Long> related;
    }

    @CacheNamespace(readWrite = false)
    interface ReadMapper {
        @Select("SELECT id FROM t_read_isolation ORDER BY id")
        List<Long> ids();

        @Select("SELECT COUNT(*) FROM t_read_isolation")
        long count();

        @Select("SELECT SUM(id) FROM t_read_isolation")
        long sum();

        @Select("SELECT id FROM t_read_isolation WHERE id = #{id}")
        Long byId(Long id);

        @Select("SELECT COUNT(*) FROM t_public")
        long publicCount();

        @Select("SELECT r.id FROM t_read_isolation r WHERE r.id > #{min} ORDER BY r.id LIMIT #{size} OFFSET #{offset}")
        List<Long> page(@Param("min") long min, @Param("size") int size, @Param("offset") int offset);

        @Select("SELECT id FROM t_read_isolation ORDER BY id LIMIT #{offset}, #{size}")
        List<Long> commaPage(@Param("offset") int offset, @Param("size") int size);

        @Select("SELECT id FROM t_read_isolation WHERE id > #{windIsolation1} ORDER BY id")
        List<Long> parameterNameCollision(@Param("windIsolation1") long min);

        @Select("<script>SELECT id FROM t_read_isolation WHERE id IN <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach> ORDER BY id</script>")
        List<Long> inIds(@Param("ids") List<Long> ids);

        @Select("SELECT CONCAT(#{prefix}, id) FROM t_read_isolation WHERE id > #{min} GROUP BY id HAVING id < #{max} ORDER BY CASE WHEN #{descending} THEN -id ELSE id END LIMIT #{size} OFFSET #{offset}")
        List<String> projection(@Param("prefix") String prefix, @Param("min") long min, @Param("max") long max,
                                @Param("descending") boolean descending, @Param("size") int size, @Param("offset") int offset);

        @Select("SELECT id FROM t_read_isolation WHERE id = #{id} OR name = #{name} ORDER BY id")
        List<Long> byIdOrName(@Param("id") long id, @Param("name") String name);

        @Select("SELECT id FROM t_read_isolation WHERE id = #{id} OR name = #{name} "
                + "OR name = 'private-query-literal-marker' /* private-query-comment-marker */ ORDER BY id")
        List<Long> diagnosticQuery(@Param("id") long id, @Param("name") String name);

        @Select("SELECT id FROM t_read_isolation ORDER BY id")
        Cursor<Long> cursor();

        @Select("SELECT id FROM t_read_isolation WHERE tenant_id = #{tenant} ORDER BY id")
        List<Long> explicitTenant(long tenant);

        @Select("SELECT a.id FROM t_read_isolation a JOIN t_public b ON a.id=b.id")
        List<Long> joined();

        @Select("SELECT id FROM t_read_isolation UNION ALL SELECT id FROM t_read_isolation")
        List<Long> union();

        @Select("SELECT (SELECT COUNT(*) FROM t_read_isolation) FROM t_public")
        List<Long> subquery();

        @Select("SELECT id FROM t_public")
        @Results(@Result(column = "id", property = "related", many = @Many(select = "ids")))
        List<NestedView> nested();

        @Select("SELECT id FROM t_public")
        @TypeDiscriminator(column = "id", javaType = Long.class, cases = {
                @Case(value = "1", type = NestedView.class,
                        results = @Result(column = "id", property = "related", many = @Many(select = "ids")))
        })
        List<NestedView> discriminatedNested();

        @Insert("INSERT INTO t_read_isolation VALUES (8,20,'prod',TRUE,'eight')")
        int insertOutsideReadScope();

        @Update("UPDATE t_read_isolation SET name='changed' WHERE id=8")
        int updateOutsideReadScope();

        @Delete("DELETE FROM t_read_isolation WHERE id=8")
        int deleteOutsideReadScope();
    }
}
