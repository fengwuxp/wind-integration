package com.wind.mybatis.plugin;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.BaseMapper;
import com.mybatisflex.core.datasource.FlexDataSource;
import com.mybatisflex.core.mybatis.FlexConfiguration;
import com.mybatisflex.core.mybatis.FlexSqlSessionFactoryBuilder;
import com.mybatisflex.core.query.QueryTable;
import com.mybatisflex.core.query.QueryWrapper;
import com.wind.integration.core.context.DataIsolationContextHolder;
import com.wind.integration.core.context.DataIsolationQueryIgnoreHolder;
import com.wind.integration.core.model.TenantIsolationObject;
import com.wind.mybatis.isolation.DataIsolationScope;
import com.wind.mybatis.isolation.DataIsolationStrategy.FieldRule;
import com.wind.mybatis.isolation.DataIsolationStrategy;
import com.wind.mybatis.isolation.FieldDataIsolationStrategy;
import lombok.Getter;
import lombok.Setter;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.exceptions.PersistenceException;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static com.wind.integration.core.enums.DataIsolationDimensions.TENANT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 使用真实 Mapper 验证普通字段规则、默认隔离与有序策略的组合行为。
 */
class FieldDataIsolationStrategyIntegrationTests {

    private static final AtomicInteger DATABASE_SEQUENCE = new AtomicInteger();

    private DataSource dataSource;

    private DataIsolationScope ownerScope;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new UnpooledDataSource("org.h2.Driver", "jdbc:h2:mem:field_strategy_"
                + DATABASE_SEQUENCE.incrementAndGet() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE field_scoped_item (id BIGINT PRIMARY KEY, tenant_id BIGINT, owner_key VARCHAR(64), department_id BIGINT)");
            statement.execute("INSERT INTO field_scoped_item VALUES (1,10,'alice',100),(2,10,'bob',100),(3,20,'alice',100),(4,10,'alice',200)");
            statement.execute("CREATE TABLE field_plain_item (id BIGINT PRIMARY KEY, owner_key VARCHAR(64), department_id BIGINT)");
            statement.execute("INSERT INTO field_plain_item VALUES (1,'alice',100),(2,'bob',100),(3,'alice',200)");
        }
        ownerScope = DataIsolationScope.of("alice");
    }

    @Test
    void testDefaultTenantAndMappedOrdinaryFieldApplyTogether() {
        FieldDataIsolationStrategy fields = fieldsFor(ScopedItem.class);
        SqlSessionFactory factory = factory(new DataIsolationQueryInterceptor(List.of(new FieldDataIsolationStrategy(), fields)), ScopedMapper.class);
        DataIsolationContextHolder.runWith(Map.of(TENANT, 10L), () -> {
            try (SqlSession session = factory.openSession()) {
                ScopedMapper mapper = session.getMapper(ScopedMapper.class);
                assertEquals(List.of(1L, 4L), scopedIds(mapper));
                assertEquals(2L, mapper.selectCountByQuery(QueryWrapper.create()));
                assertEquals(1L, mapper.selectOneById(1L).getId());
                assertNull(mapper.selectOneById(2L));
                assertNull(mapper.selectOneById(3L));
                assertEquals(List.of(1L, 4L), mapper.annotatedIds());
            }
        });
    }

    @Test
    void testDimensionOverrideCombinesWithAdditionalFieldStrategy() {
        FieldDataIsolationStrategy defaults = FieldDataIsolationStrategy.withDefaults(List.of(
                FieldRule.forDimension(TENANT, "tenantId", () -> DataIsolationScope.of(20L))));
        DataIsolationQueryInterceptor interceptor = new DataIsolationQueryInterceptor(List.of(defaults, fieldsFor(ScopedItem.class)));
        SqlSessionFactory factory = factory(interceptor, ScopedMapper.class);

        DataIsolationContextHolder.runWith(Map.of(TENANT, 10L), () -> {
            try (SqlSession session = factory.openSession()) {
                ScopedMapper mapper = session.getMapper(ScopedMapper.class);
                assertEquals(List.of(3L), scopedIds(mapper));
                ownerScope = DataIsolationScope.of("bob");
                assertEquals(List.of(), scopedIds(mapper));
            }
        });
    }

    @Test
    void testOrdinaryFieldRuleDoesNotRequireIsolationInterface() {
        SqlSessionFactory factory = factory(new DataIsolationQueryInterceptor(List.of(new FieldDataIsolationStrategy(), fieldsFor(PlainItem.class))),
                PlainMapper.class);
        try (SqlSession session = factory.openSession()) {
            PlainMapper mapper = session.getMapper(PlainMapper.class);
            assertEquals(List.of(1L, 3L), plainIds(mapper));
            assertNull(mapper.selectOneById(2L));
            assertEquals(List.of(1L, 3L), mapper.selectListByQuery(QueryWrapper.create()
                            .from(new QueryTable("field_plain_item").as("p")).where("p.id > ?", 0L).orderBy("p.id"))
                    .stream().map(PlainItem::getId).toList());
        }
    }

    @Test
    void testExplicitStrategiesDoNotAddDefaultFieldIsolation() {
        FieldDataIsolationStrategy fields = fieldsFor(ScopedItem.class);
        SqlSessionFactory factory = factory(new DataIsolationQueryInterceptor(List.of(fields)), ScopedMapper.class);
        DataIsolationContextHolder.runWith(Map.of(TENANT, 10L), () -> {
            try (SqlSession session = factory.openSession()) {
                assertEquals(List.of(1L, 3L, 4L), scopedIds(session.getMapper(ScopedMapper.class)));
            }
        });
    }

    @Test
    void testEmptyStrategyConfigurationIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new DataIsolationQueryInterceptor(List.of()));
    }

    @Test
    void testOrdinaryFieldRuleDoesNotRestrictOtherEntities() {
        SqlSessionFactory factory = factory(new DataIsolationQueryInterceptor(List.of(new FieldDataIsolationStrategy(), fieldsFor(ScopedItem.class))),
                PlainMapper.class);
        try (SqlSession session = factory.openSession()) {
            assertEquals(List.of(1L, 2L, 3L), plainIds(session.getMapper(PlainMapper.class)));
        }
    }

    @Test
    void testSingleMultipleEmptyUnrestrictedAndMissingScopesRemainDistinct() {
        SqlSessionFactory factory = factory(new DataIsolationQueryInterceptor(List.of(new FieldDataIsolationStrategy(), fieldsFor(PlainItem.class))),
                PlainMapper.class);
        try (SqlSession session = factory.openSession()) {
            PlainMapper mapper = session.getMapper(PlainMapper.class);
            assertEquals(List.of(1L, 3L), plainIds(mapper));
            ownerScope = DataIsolationScope.of("bob");
            assertEquals(List.of(2L), plainIds(mapper));
            ownerScope = DataIsolationScope.ofValues(List.of("alice", "bob"));
            assertEquals(List.of(1L, 2L, 3L), plainIds(mapper));
            ownerScope = DataIsolationScope.ofValues(List.of());
            assertEquals(List.of(), plainIds(mapper));
            assertEquals(0L, mapper.selectCountByQuery(QueryWrapper.create()));
            ownerScope = DataIsolationScope.unrestrictedScope();
            assertEquals(List.of(1L, 2L, 3L), plainIds(mapper));
            ownerScope = DataIsolationScope.of("alice");
            assertEquals(List.of(1L, 3L), plainIds(mapper));
            ownerScope = null;
            assertThrows(PersistenceException.class, () -> plainIds(mapper));
        }
    }

    @Test
    void testIgnoringBuiltinDimensionKeepsOrdinaryFieldRestriction() {
        SqlSessionFactory factory = factory(new DataIsolationQueryInterceptor(List.of(new FieldDataIsolationStrategy(), fieldsFor(ScopedItem.class))),
                ScopedMapper.class);
        DataIsolationContextHolder.runWith(Map.of(TENANT, 10L), () -> {
            try (SqlSession session = factory.openSession()) {
                ScopedMapper mapper = session.getMapper(ScopedMapper.class);
                assertEquals(List.of(1L, 4L), scopedIds(mapper));
                DataIsolationQueryIgnoreHolder.runIgnoring(
                        () -> assertEquals(List.of(1L, 3L, 4L), scopedIds(mapper)), TENANT);
                assertEquals(List.of(1L, 4L), scopedIds(mapper));
                assertEquals(Long.valueOf(10L), DataIsolationContextHolder.<Long>getTenantId());
            }
        });
    }

    @Test
    void testMultipleFieldRulesProduceIntersectionAndUseBoundValues() {
        FieldDataIsolationStrategy fields = new FieldDataIsolationStrategy(List.of(
                new FieldRule(PlainItem.class, "ownerId", () -> ownerScope),
                new FieldRule(PlainItem.class, "departmentId", () -> DataIsolationScope.of(100L))));
        SqlSessionFactory factory = factory(new DataIsolationQueryInterceptor(List.of(new FieldDataIsolationStrategy(), fields)), PlainMapper.class);
        try (SqlSession session = factory.openSession()) {
            PlainMapper mapper = session.getMapper(PlainMapper.class);
            assertEquals(List.of(1L), plainIds(mapper));
            ownerScope = DataIsolationScope.of("alice' OR 1=1 --");
            assertEquals(List.of(), plainIds(mapper));
        }
    }

    @Test
    void testOrderedStrategiesAllContributeAndUnrestrictedCannotOverride() {
        List<String> executionOrder = new ArrayList<>();
        DataIsolationStrategy last = new FieldDataIsolationStrategy(List.of(new FieldRule(PlainItem.class, "departmentId", () -> {
            executionOrder.add("department");
            return DataIsolationScope.of(100L);
        })), 100);
        DataIsolationStrategy first = strategy(-100, "unrestricted", executionOrder, context -> List.of());
        DataIsolationStrategy middle = new FieldDataIsolationStrategy(List.of(new FieldRule(PlainItem.class, "ownerId", () -> {
            executionOrder.add("owner");
            return DataIsolationScope.of("alice");
        })), 0);
        SqlSessionFactory factory = factory(new DataIsolationQueryInterceptor(List.of(new FieldDataIsolationStrategy(), last, first, middle)), PlainMapper.class);

        try (SqlSession session = factory.openSession()) {
            assertEquals(List.of(1L), plainIds(session.getMapper(PlainMapper.class)));
            assertEquals(List.of("unrestricted", "owner", "department"), executionOrder);
        }
    }

    @Test
    void testEqualPriorityPreservesInputOrder() {
        List<String> executionOrder = new ArrayList<>();
        DataIsolationStrategy owner = strategy(10, "owner", executionOrder,
                context -> List.of(DataIsolationStrategy.Condition.of(context.column("ownerId") + " = ?", "alice")));
        DataIsolationStrategy department = strategy(10, "department", executionOrder,
                context -> List.of(DataIsolationStrategy.Condition.of(context.column("departmentId") + " = ?", 100L)));
        SqlSessionFactory factory = factory(new DataIsolationQueryInterceptor(List.of(department, owner)), PlainMapper.class);
        try (SqlSession session = factory.openSession()) {
            assertEquals(List.of(1L), plainIds(session.getMapper(PlainMapper.class)));
            assertEquals(List.of("department", "owner"), executionOrder);
        }
    }

    @Test
    void testEmptyScopeDoesNotSkipLaterFailure() {
        List<String> executionOrder = new ArrayList<>();
        DataIsolationStrategy empty = strategy(-10, "empty", executionOrder,
                context -> List.of(DataIsolationStrategy.Condition.of("1 = 0")));
        DataIsolationStrategy denied = strategy(10, "denied", executionOrder, context -> {
            throw new IllegalStateException("后续隔离拒绝");
        });
        SqlSessionFactory factory = factory(new DataIsolationQueryInterceptor(List.of(new FieldDataIsolationStrategy(), denied, empty)), PlainMapper.class);

        try (SqlSession session = factory.openSession()) {
            PersistenceException error = assertThrows(PersistenceException.class,
                    () -> plainIds(session.getMapper(PlainMapper.class)));
            assertTrue(error.getMessage().contains("后续隔离拒绝"));
            assertEquals(List.of("empty", "denied"), executionOrder);
        }
    }

    @Test
    void testRegisteringMapperAfterFirstReadActivatesOrdinaryFieldRule() {
        SqlSessionFactory factory = factory(new DataIsolationQueryInterceptor(List.of(new FieldDataIsolationStrategy(), fieldsFor(PlainItem.class))),
                SummaryMapper.class);
        try (SqlSession session = factory.openSession()) {
            SummaryMapper mapper = session.getMapper(SummaryMapper.class);
            assertEquals(List.of(1L, 2L, 3L), mapper.ids());
            factory.getConfiguration().addMapper(PlainMapper.class);
            assertEquals(List.of(1L, 3L), mapper.ids());
        }
    }

    @Test
    void testMissingConfiguredPropertyFailsBeforeReturningUnscopedRows() {
        FieldDataIsolationStrategy fields = new FieldDataIsolationStrategy(List.of(
                new FieldRule(PlainItem.class, "unknownProperty", () -> ownerScope)));
        SqlSessionFactory factory = factory(new DataIsolationQueryInterceptor(List.of(new FieldDataIsolationStrategy(), fields)), PlainMapper.class);
        try (SqlSession session = factory.openSession()) {
            assertThrows(PersistenceException.class, () -> plainIds(session.getMapper(PlainMapper.class)));
        }
    }

    @Test
    void testLikeEscapeParametersKeepOriginalWhereAndPagination() throws Exception {
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute("INSERT INTO field_plain_item VALUES (4,'alice_1',100),(5,'aliceX1',100),(6,'alice_1',200)");
        }
        DataIsolationStrategy escapedOwner = strategy(0, "escaped-owner", new ArrayList<>(), context -> List.of(
                DataIsolationStrategy.Condition.of(context.column("ownerId") + " LIKE ? ESCAPE ?", "alice!_%", "!")));
        SqlSessionFactory factory = factory(new DataIsolationQueryInterceptor(List.of(escapedOwner)), PlainMapper.class);
        try (SqlSession session = factory.openSession()) {
            QueryWrapper query = QueryWrapper.create().from(new QueryTable("field_plain_item").as("p"))
                    .where("p.department_id = ?", 100L).orderBy("p.id").limit(0, 1);
            assertEquals(List.of(4L), session.getMapper(PlainMapper.class).selectListByQuery(query)
                    .stream().map(PlainItem::getId).toList());
        }
    }

    @Test
    void testFunctionKeywordParametersKeepBindingOrder() {
        List<DataIsolationStrategy.Condition> conditions = List.of(
                DataIsolationStrategy.Condition.of("TRIM(BOTH ? FROM field_plain_item.owner_key) = ?", "a", "lice"),
                DataIsolationStrategy.Condition.of("SUBSTRING(field_plain_item.owner_key FROM ? FOR ?) = ?", 2, 3, "lic"),
                DataIsolationStrategy.Condition.of("POSITION(? IN field_plain_item.owner_key) > ?", "lic", 1));
        for (DataIsolationStrategy.Condition condition : conditions) {
            DataIsolationStrategy scalarScope = strategy(0, "scalar-scope", new ArrayList<>(), context -> List.of(condition));
            SqlSessionFactory factory = factory(new DataIsolationQueryInterceptor(List.of(scalarScope)), PlainMapper.class);
            try (SqlSession session = factory.openSession()) {
                QueryWrapper query = QueryWrapper.create().where("id > ? AND department_id = ?", 0L, 100L)
                        .orderBy("id").limit(0, 1);
                assertEquals(List.of(1L), session.getMapper(PlainMapper.class).selectListByQuery(query)
                        .stream().map(PlainItem::getId).toList());
            }
        }
    }

    @Test
    void testStrategyConditionsRejectInvalidBindingsAndSubqueries() {
        Map<DataIsolationStrategy.Condition, String> unsupported = Map.of(
                DataIsolationStrategy.Condition.of("field_plain_item.owner_key = ?"), "parameter count does not match",
                DataIsolationStrategy.Condition.of("1 = 0", "unused"), "parameter count does not match",
                DataIsolationStrategy.Condition.of("field_plain_item.id IN (SELECT id FROM field_plain_item)"), "do not support subqueries",
                DataIsolationStrategy.Condition.of("1 = (TABLE field_plain_item)"), "single-table SELECT only",
                DataIsolationStrategy.Condition.of("field_plain_item.owner_key = :owner", "alice"), "positional placeholders",
                DataIsolationStrategy.Condition.of("field_plain_item.owner_key LIKE ? ESCAPE :escape", "alice%"), "Cannot parse isolation condition",
                DataIsolationStrategy.Condition.of("SUBSTRING(field_plain_item.owner_key FROM :start FOR ?) = ?", 3, "lic"), "positional placeholders",
                DataIsolationStrategy.Condition.of("field_plain_item.owner_key = ?; SELECT 1", "alice"), "Cannot parse isolation condition",
                DataIsolationStrategy.Condition.of("field_plain_item.owner_key = ? ORDER BY id", "alice"), "Cannot parse isolation condition");
        for (Map.Entry<DataIsolationStrategy.Condition, String> rejected : unsupported.entrySet()) {
            DataIsolationStrategy strategy = strategy(0, "invalid", new ArrayList<>(), context -> List.of(rejected.getKey()));
            SqlSessionFactory factory = factory(new DataIsolationQueryInterceptor(List.of(new FieldDataIsolationStrategy(), strategy)), PlainMapper.class);
            try (SqlSession session = factory.openSession()) {
                PersistenceException error = assertThrows(PersistenceException.class,
                        () -> plainIds(session.getMapper(PlainMapper.class)));
                assertTrue(error.getMessage().contains(rejected.getValue()),
                        () -> rejected.getKey().sql() + ": " + error.getMessage());
            }
        }
    }

    private FieldDataIsolationStrategy fieldsFor(Class<?> entityType) {
        return new FieldDataIsolationStrategy(List.of(new FieldRule(entityType, "ownerId", () -> ownerScope)));
    }

    private SqlSessionFactory factory(DataIsolationQueryInterceptor interceptor, Class<?>... mappers) {
        FlexConfiguration configuration = new FlexConfiguration(new Environment("field-strategy", new JdbcTransactionFactory(),
                new FlexDataSource("default", dataSource, false)));
        configuration.addInterceptor(interceptor);
        for (Class<?> mapper : mappers) {
            configuration.addMapper(mapper);
        }
        return new FlexSqlSessionFactoryBuilder().build(configuration);
    }

    private static List<Long> scopedIds(ScopedMapper mapper) {
        return mapper.selectListByQuery(QueryWrapper.create().orderBy("id")).stream().map(ScopedItem::getId).toList();
    }

    private static List<Long> plainIds(PlainMapper mapper) {
        return mapper.selectListByQuery(QueryWrapper.create().orderBy("id")).stream().map(PlainItem::getId).toList();
    }

    private static DataIsolationStrategy strategy(int order, String name, List<String> executionOrder,
                                                   Function<DataIsolationStrategy.QueryContext,
                                                           List<DataIsolationStrategy.Condition>> resolver) {
        return new DataIsolationStrategy() {
            @Override
            public boolean supports(EntityMetadata entity) {
                return entity.type() == PlainItem.class;
            }

            @Override
            public List<Condition> resolve(QueryContext context) {
                executionOrder.add(name);
                return resolver.apply(context);
            }

            @Override
            public int getOrder() {
                return order;
            }
        };
    }

    @Getter
    @Setter
    @Table("field_scoped_item")
    static class ScopedItem implements TenantIsolationObject<Long> {
        @Id
        private Long id;
        private Long tenantId;
        @Column("owner_key")
        private String ownerId;
        private Long departmentId;
    }

    @Getter
    @Setter
    @Table("field_plain_item")
    static class PlainItem {
        @Id
        private Long id;
        @Column("owner_key")
        private String ownerId;
        private Long departmentId;
    }

    interface ScopedMapper extends BaseMapper<ScopedItem> {
        @Select("SELECT i.id FROM field_scoped_item i ORDER BY i.id")
        List<Long> annotatedIds();
    }

    interface PlainMapper extends BaseMapper<PlainItem> {
    }

    interface SummaryMapper {
        @Select("SELECT id FROM field_plain_item ORDER BY id")
        List<Long> ids();
    }
}
