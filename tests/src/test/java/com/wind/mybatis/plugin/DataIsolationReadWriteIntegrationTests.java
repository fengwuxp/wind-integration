package com.wind.mybatis.plugin;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.BaseMapper;
import com.mybatisflex.core.datasource.FlexDataSource;
import com.mybatisflex.core.mybatis.FlexConfiguration;
import com.mybatisflex.core.mybatis.FlexSqlSessionFactoryBuilder;
import com.mybatisflex.core.tenant.TenantFactory;
import com.mybatisflex.core.tenant.TenantManager;
import com.wind.common.util.ServiceInfoUtils;
import com.wind.integration.core.context.DataIsolationContextHolder;
import com.wind.integration.core.context.DataIsolationQueryIgnoreHolder;
import com.wind.integration.core.context.TenantContextHolder;
import com.wind.integration.core.enums.DataIsolationDimensions;
import com.wind.integration.core.model.EnvIsolationObject;
import com.wind.integration.core.model.SettableTenantIsolationObject;
import com.wind.integration.core.model.SettableTestDataIsolationObject;
import com.wind.integration.core.model.SettableWorkspaceIsolationObject;
import com.wind.integration.core.model.TenantIsolationObject;
import com.wind.integration.core.model.TestDataIsolationObject;
import com.wind.integration.core.model.WorkspaceIsolationObject;
import com.wind.mybatis.flex.EnvIsolationFieldInsertListener;
import com.wind.mybatis.flex.TenantIsolationFieldInsertListener;
import com.wind.mybatis.flex.TestDataIsolationFieldInsertListener;
import com.wind.mybatis.flex.WorkspaceIsolationFieldInsertListener;
import lombok.Getter;
import lombok.Setter;
import com.wind.mybatis.isolation.FieldDataIsolationStrategy;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.wind.common.WindConstants.SPRING_PROFILES_ACTIVE;
import static com.wind.integration.core.enums.DataIsolationDimensions.ENVIRONMENT;
import static com.wind.integration.core.enums.DataIsolationDimensions.TENANT;
import static com.wind.integration.core.enums.DataIsolationDimensions.TEST_DATA;
import static com.wind.integration.core.enums.DataIsolationDimensions.WORKSPACE;
import static org.mockito.Mockito.mockStatic;

@ResourceLock(Resources.SYSTEM_PROPERTIES)
class DataIsolationReadWriteIntegrationTests {

    private static final AtomicInteger DATABASE_SEQUENCE = new AtomicInteger();

    private String previousProfile;

    private SqlSessionFactory sessionFactory;

    private TenantFactory previousTenantFactory;

    @BeforeEach
    void setUp() throws SQLException {
        previousProfile = System.getProperty(SPRING_PROFILES_ACTIVE);
        System.setProperty(SPRING_PROFILES_ACTIVE, "sit");
        UnpooledDataSource source = new UnpooledDataSource("org.h2.Driver",
                "jdbc:h2:mem:holder_write_" + DATABASE_SEQUENCE.incrementAndGet() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        try (var connection = source.getConnection(); var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE t_holder_order (id BIGINT PRIMARY KEY, tenant_id BIGINT NOT NULL, workspace_id VARCHAR(32), env VARCHAR(16) NOT NULL, is_test BOOLEAN NOT NULL, name VARCHAR(32))");
            statement.execute("INSERT INTO t_holder_order VALUES (1,10,'ws-a','sit',FALSE,'one'),(2,10,'ws-b','sit',FALSE,'two'),(3,20,'ws-a','sit',FALSE,'three'),(4,10,'ws-a','sit',TRUE,'four'),(5,10,'ws-a','prod',FALSE,'five')");
            statement.execute("CREATE TABLE t_tenant_listener(id BIGINT PRIMARY KEY, account_key BIGINT)");
        }
        FlexConfiguration configuration = new FlexConfiguration(new Environment("holder", new JdbcTransactionFactory(),
                new FlexDataSource("default", source, false)));
        configuration.addInterceptor(new DataIsolationQueryInterceptor(List.of(FieldDataIsolationStrategy.withDefaults(List.of()))));
        configuration.addMapper(OrderMapper.class);
        configuration.addMapper(TenantMapper.class);
        sessionFactory = new FlexSqlSessionFactoryBuilder().build(configuration);
        previousTenantFactory = TenantManager.getTenantFactory();
        TenantManager.setTenantFactory(null);
    }

    @AfterEach
    void tearDown() {
        if (previousProfile == null) {
            System.clearProperty(SPRING_PROFILES_ACTIVE);
        } else {
            System.setProperty(SPRING_PROFILES_ACTIVE, previousProfile);
        }
        TenantManager.setTenantFactory(previousTenantFactory);
        TenantContextHolder.clear();
    }

    /** 插入与读取使用应用 profile，trace 中不同的环境值不改变字段填充。 */
    @Test
    void testInsertAndReadUseTheSameContext() throws Exception {
        DataIsolationContextHolder.callWith(Map.of(TENANT, 10L, WORKSPACE, "ws-a", ENVIRONMENT, "client-env", TEST_DATA, false), () -> {
            try (SqlSession session = sessionFactory.openSession()) {
                OrderMapper mapper = session.getMapper(OrderMapper.class);
                OrderEntity entity = order(10L);
                Assertions.assertEquals(1, mapper.insert(entity));
                Assertions.assertEquals(10L, entity.getId());
                Assertions.assertEquals(10L, entity.getTenantId());
                assertStoredContext(session, 10L, "ws-a");
                Assertions.assertEquals(List.of(1L, 10L), mapper.ids());
                Assertions.assertEquals("ws-a", mapper.selectOneById(10L).getWorkspaceId());
                session.rollback();
            }
            return null;
        });
    }

    /** 缺少租户时清空实体预填值，租户必填约束由数据库拒绝，不能保留客户端值。 */
    @Test
    void testMissingTenantClearsPresetValueAndDatabaseRejectsNull() throws SQLException {
        TenantContextHolder.clear();
        try (SqlSession session = sessionFactory.openSession()) {
            OrderEntity entity = order(10L);
            RuntimeException error = Assertions.assertThrows(RuntimeException.class,
                    () -> session.getMapper(OrderMapper.class).insert(entity));
            Throwable cause = error;
            while (cause.getCause() != null) {
                cause = cause.getCause();
            }
            Assertions.assertInstanceOf(SQLException.class, cause);
            Assertions.assertNull(entity.getTenantId());
            try (var statement = session.getConnection().createStatement();
                 var result = statement.executeQuery("SELECT COUNT(*) FROM t_holder_order WHERE id=10")) {
                Assertions.assertTrue(result.next());
                Assertions.assertEquals(0L, result.getLong(1));
            }
        }
    }

    /** 可空租户列允许落库，但缺少上下文的默认读取仍没有可见记录。 */
    @Test
    void testMissingTenantWritesNullWhenColumnAllowsIt() throws SQLException {
        TenantContextHolder.clear();
        try (SqlSession session = sessionFactory.openSession()) {
            TenantRecord record = new TenantRecord(31L, 99L);
            Assertions.assertEquals(1, session.getMapper(TenantMapper.class).insert(record));
            Assertions.assertNull(record.getTenantId());
            try (var statement = session.getConnection().createStatement();
                 var result = statement.executeQuery("SELECT account_key FROM t_tenant_listener WHERE id=31")) {
                Assertions.assertTrue(result.next());
                Assertions.assertNull(result.getObject(1));
            }
            Assertions.assertNull(session.getMapper(TenantMapper.class).selectOneById(31L));
            session.rollback();
        }
    }

    /** 通过接口设置租户，物理列重命名不改变填充行为，也不覆盖实体主键。 */
    @Test
    void testTenantListenerSupportsMappedColumn() throws SQLException {
        try (SqlSession session = sessionFactory.openSession()) {
            TenantRecord record = new TenantRecord(30L, 99L);
            DataIsolationContextHolder.runWithTenant(20L, () -> {
                Assertions.assertEquals(1, session.getMapper(TenantMapper.class).insert(record));
                Assertions.assertEquals(30L, record.getId());
                Assertions.assertEquals(20L, record.getTenantId());
            });
            try (var statement = session.getConnection().createStatement();
                 var result = statement.executeQuery("SELECT account_key FROM t_tenant_listener WHERE id=30")) {
                Assertions.assertTrue(result.next());
                Assertions.assertEquals(20L, result.getLong(1));
            }
            session.rollback();
        }
    }

    /** 监听器只作用于租户接口实体，不能为普通对象要求租户上下文。 */
    @Test
    void testTenantListenerIgnoresObjectsWithoutTenantInterface() {
        TenantContextHolder.clear();
        Assertions.assertDoesNotThrow(() -> new TenantIsolationFieldInsertListener().onInsert(new Object()));
    }

    /** 只声明租户读取能力的对象无需 setter，也不会被插入监听器改变归属。 */
    @Test
    void testTenantListenerIgnoresReadOnlyTenantObject() {
        TenantIsolationObject<Long> readOnly = new TenantIsolationObject<>() {
            @Override
            public Long getId() {
                return 30L;
            }

            @Override
            public Long getTenantId() {
                return 99L;
            }
        };
        DataIsolationContextHolder.runWithTenant(10L, () -> {
            new TenantIsolationFieldInsertListener().onInsert(readOnly);
            Assertions.assertEquals(30L, readOnly.getId());
            Assertions.assertEquals(99L, readOnly.getTenantId());
        });
    }

    /** 存在 setter 不等于声明自动填充能力，只有实现子接口的实体参与租户填充。 */
    @Test
    void testTenantListenerRequiresSettableInterfaceEvenWhenSetterExists() {
        CoverageTenantRecord record = new CoverageTenantRecord();
        record.setId(31L);
        record.setTenantId(99L);
        DataIsolationContextHolder.runWithTenant(10L, () -> new TenantIsolationFieldInsertListener().onInsert(record));

        Assertions.assertEquals(99L, record.getTenantId());
    }

    /** 只读对象无需实现 setter，工作空间归属与默认正式数据标记不受插入监听器影响。 */
    @Test
    void testListenersIgnoreReadOnlyClassificationObjects() {
        WorkspaceIsolationObject<String> workspace = () -> "ws-original";
        TestDataIsolationObject testData = new TestDataIsolationObject() {
        };

        DataIsolationContextHolder.runWith(Map.of(WORKSPACE, "ws-current", TEST_DATA, true), () -> {
            new WorkspaceIsolationFieldInsertListener().onInsert(workspace);
            new TestDataIsolationFieldInsertListener().onInsert(testData);
        });

        Assertions.assertEquals("ws-original", workspace.getWorkspaceId());
        Assertions.assertFalse(testData.isTestData());
    }

    /** 仅有 setter 不会参与填充，实体必须声明对应的可赋值子接口。 */
    @Test
    void testClassificationListenersRequireSettableInterfacesEvenWhenSettersExist() {
        QueryOnlyRecord record = new QueryOnlyRecord();
        record.setWorkspaceId("ws-original");
        record.setTestData(true);

        DataIsolationContextHolder.runWith(Map.of(WORKSPACE, "ws-current", TEST_DATA, false), () -> {
            new WorkspaceIsolationFieldInsertListener().onInsert(record);
            new TestDataIsolationFieldInsertListener().onInsert(record);
        });

        Assertions.assertEquals("ws-original", record.getWorkspaceId());
        Assertions.assertTrue(record.isTestData());
    }

    /** 可赋值子接口同时参与查询隔离，测试数据插入后只在匹配的上下文中可见。 */
    @Test
    void testInsertedTestDataIsVisibleOnlyInMatchingContext() {
        DataIsolationContextHolder.runWith(context(10L, "ws-a", true), () -> {
            try (SqlSession session = sessionFactory.openSession()) {
                OrderMapper mapper = session.getMapper(OrderMapper.class);
                OrderEntity entity = order(10L);
                entity.setTestData(false);

                Assertions.assertEquals(1, mapper.insert(entity));
                Assertions.assertEquals("ws-a", entity.getWorkspaceId());
                Assertions.assertTrue(entity.isTestData());
                Assertions.assertEquals(List.of(4L, 10L), mapper.ids());
                Assertions.assertTrue(mapper.selectOneById(10L).isTestData());
                DataIsolationContextHolder.runWith(context(10L, "ws-a", false),
                        () -> Assertions.assertNull(mapper.selectOneById(10L)));
                DataIsolationContextHolder.runWith(context(10L, "ws-b", true),
                        () -> Assertions.assertNull(mapper.selectOneById(10L)));
                session.rollback();
            }
        });
    }

    @Test
    void testDefaultReadsAndCachesFollowAllFourDimensions() {
        try (SqlSession session = sessionFactory.openSession()) {
            OrderMapper mapper = session.getMapper(OrderMapper.class);
            DataIsolationContextHolder.runWith(context(10L, "ws-a", false),
                    () -> Assertions.assertEquals(List.of(1L), mapper.ids()));
            DataIsolationContextHolder.runWith(context(20L, "ws-a", false),
                    () -> Assertions.assertEquals(List.of(3L), mapper.ids()));
            DataIsolationContextHolder.runWith(context(10L, "ws-b", false),
                    () -> Assertions.assertEquals(List.of(2L), mapper.ids()));
            System.setProperty(SPRING_PROFILES_ACTIVE, "prod");
            DataIsolationContextHolder.runWith(context(10L, "ws-a", false),
                    () -> Assertions.assertEquals(List.of(5L), mapper.ids()));
            System.setProperty(SPRING_PROFILES_ACTIVE, "sit");
            DataIsolationContextHolder.runWith(context(10L, "ws-a", true),
                    () -> Assertions.assertEquals(List.of(4L), mapper.ids()));
        }
    }

    @Test
    void testInvalidTestDataTypeRejectsReadsAndInserts() {
        DataIsolationContextHolder.runWith(Map.of(TENANT, 10L, WORKSPACE, "ws-a", TEST_DATA, "true"), () -> {
            try (SqlSession session = sessionFactory.openSession()) {
                OrderMapper mapper = session.getMapper(OrderMapper.class);
                Assertions.assertThrows(RuntimeException.class, mapper::ids);
                Assertions.assertThrows(RuntimeException.class, () -> mapper.insert(order(10L)));
                try (var statement = session.getConnection().createStatement();
                     var result = statement.executeQuery("SELECT COUNT(*) FROM t_holder_order WHERE id=10")) {
                    result.next();
                    Assertions.assertEquals(0L, result.getLong(1));
                } catch (SQLException exception) {
                    throw new IllegalStateException(exception);
                }
                session.rollback();
            }
        });
    }

    /** 同一工作线程顺序处理不同请求，缺失租户的请求不能读取上一请求的数据。 */
    @Test
    void testReusedWorkerDoesNotReadPreviousTenant() throws Exception {
        try (var worker = Executors.newSingleThreadExecutor()) {
            List<Long> tenantRows = worker.submit(() -> DataIsolationContextHolder.callWith(
                    context(10L, "ws-a", false), () -> {
                        try (SqlSession session = sessionFactory.openSession()) {
                            return session.getMapper(OrderMapper.class).ids();
                        }
                    })).get(5, TimeUnit.SECONDS);
            List<Long> missingTenantRows = worker.submit(() -> DataIsolationContextHolder.callWith(
                    Map.of(WORKSPACE, "ws-a"), () -> {
                        try (SqlSession session = sessionFactory.openSession()) {
                            return session.getMapper(OrderMapper.class).ids();
                        }
                    })).get(5, TimeUnit.SECONDS);

            Assertions.assertEquals(List.of(1L), tenantRows);
            Assertions.assertEquals(List.of(), missingTenantRows);
        }
    }

    /** 忽略只控制查询条件；插入监听器继续使用原归属值，退出后查询恢复隔离。 */
    @Test
    void testIgnoringReadsDoesNotChangeInsertValues() throws Exception {
        DataIsolationContextHolder.callWith(context(10L, "ws-a", false), () -> {
            try (SqlSession session = sessionFactory.openSession()) {
                OrderMapper mapper = session.getMapper(OrderMapper.class);
                DataIsolationQueryIgnoreHolder.runIgnoring(
                        () -> Assertions.assertEquals(List.of(1L, 2L, 4L), mapper.ids()), WORKSPACE, TEST_DATA);
                DataIsolationQueryIgnoreHolder.runIgnoring(() -> Assertions.assertEquals(1, mapper.insert(order(10L))),
                        TENANT, WORKSPACE, ENVIRONMENT, TEST_DATA);
                assertStoredContext(session, 10L, "ws-a");
                Assertions.assertEquals(List.of(1L, 10L), mapper.ids());
                session.rollback();
            }
            return null;
        });
    }

    @Test
    void testMissingEnvironmentRejectsInsert() throws Exception {
        try (var environmentSource = mockStatic(ServiceInfoUtils.class)) {
            environmentSource.when(ServiceInfoUtils::getSpringProfilesActive).thenReturn(null);
            DataIsolationContextHolder.callWith(Map.of(TENANT, 10L, WORKSPACE, "ws-a"), () -> {
                try (SqlSession session = sessionFactory.openSession()) {
                    RuntimeException error = Assertions.assertThrows(RuntimeException.class,
                            () -> session.getMapper(OrderMapper.class).insert(order(10L)));
                    Assertions.assertTrue(error.getMessage().contains("context env"));
                    try (var statement = session.getConnection().createStatement();
                         var result = statement.executeQuery("SELECT COUNT(*) FROM t_holder_order WHERE id=10")) {
                        result.next();
                        Assertions.assertEquals(0L, result.getLong(1));
                    }
                    session.rollback();
                }
                return null;
            });
        }
    }

    /** 工作空间监听器按当前契约允许填 null，该记录随后不在缺失工作空间的默认读取范围内。 */
    @Test
    void testMissingWorkspaceWritesNullAndReadsNoRows() throws Exception {
        DataIsolationContextHolder.callWith(Map.of(TENANT, 10L), () -> {
            try (SqlSession session = sessionFactory.openSession()) {
                OrderMapper mapper = session.getMapper(OrderMapper.class);
                Assertions.assertEquals(1, mapper.insert(order(10L)));
                assertStoredContext(session, 10L, null);
                Assertions.assertEquals(List.of(), mapper.ids());
                session.rollback();
            }
            return null;
        });
    }

    /** 手写写入语句不经过实体插入监听器，读隔离插件也不会为 UPDATE/DELETE 追加权限条件。 */
    @Test
    void testAnnotatedWritesRemainOutsideReadIsolation() {
        DataIsolationContextHolder.runWith(context(10L, "ws-a", false), () -> {
            try (SqlSession session = sessionFactory.openSession()) {
                OrderMapper mapper = session.getMapper(OrderMapper.class);
                Assertions.assertEquals(1, mapper.insertOutsideContext());
                Assertions.assertEquals(1, mapper.updateOutsideContext());
                Assertions.assertEquals(List.of(1L), mapper.ids());
                Assertions.assertEquals(1, mapper.deleteOutsideContext());
                session.rollback();
            }
        });
    }

    private Map<DataIsolationDimensions, Object> context(long tenantId, String workspaceId, boolean testData) {
        return Map.of(TENANT, tenantId, WORKSPACE, workspaceId, TEST_DATA, testData);
    }

    private OrderEntity order(long id) {
        OrderEntity entity = new OrderEntity();
        entity.setId(id);
        entity.setTenantId(99L);
        entity.setWorkspaceId("client-value");
        entity.setEnv("client-value");
        entity.setTestData(true);
        entity.setName("synthetic");
        return entity;
    }

    private void assertStoredContext(SqlSession session, long id, String workspaceId) throws SQLException {
        try (var statement = session.getConnection().prepareStatement("SELECT tenant_id,workspace_id,env,is_test FROM t_holder_order WHERE id=?")) {
            statement.setLong(1, id);
            try (var result = statement.executeQuery()) {
                Assertions.assertTrue(result.next());
                Assertions.assertEquals(10L, result.getLong("tenant_id"));
                Assertions.assertEquals(workspaceId, result.getString("workspace_id"));
                Assertions.assertEquals("sit", result.getString("env"));
                Assertions.assertFalse(result.getBoolean("is_test"));
            }
        }
    }

    @Getter
    @Setter
    @Table(value = "t_holder_order", onInsert = {
            TenantIsolationFieldInsertListener.class, EnvIsolationFieldInsertListener.class,
            TestDataIsolationFieldInsertListener.class, WorkspaceIsolationFieldInsertListener.class
    })
    static class OrderEntity implements SettableTenantIsolationObject<Long>, SettableWorkspaceIsolationObject<String>, EnvIsolationObject, SettableTestDataIsolationObject {
        @Id
        private Long id;
        private Long tenantId;
        private String workspaceId;
        private String env;
        @Column("is_test")
        private boolean testData;
        private String name;
    }

    @Getter
    @Setter
    static class QueryOnlyRecord implements WorkspaceIsolationObject<String>, TestDataIsolationObject {
        private String workspaceId;
        private boolean testData;
    }

    @Getter
    @Setter
    @Table(value = "t_tenant_listener", onInsert = TenantIsolationFieldInsertListener.class, mapperGenerateEnable = false)
    static class TenantRecord implements SettableTenantIsolationObject<Long> {
        @Id
        private Long id;
        @Column("account_key")
        private Long tenantId;

        TenantRecord(Long id, Long tenantId) {
            this.id = id;
            this.tenantId = tenantId;
        }
    }

    interface TenantMapper extends BaseMapper<TenantRecord> {
    }

    interface OrderMapper extends BaseMapper<OrderEntity> {
        @Select("SELECT id FROM t_holder_order ORDER BY id")
        List<Long> ids();

        @Insert("INSERT INTO t_holder_order VALUES (20,20,'ws-other','prod',TRUE,'outside')")
        int insertOutsideContext();

        @Update("UPDATE t_holder_order SET name='updated' WHERE id=20")
        int updateOutsideContext();

        @Delete("DELETE FROM t_holder_order WHERE id=20")
        int deleteOutsideContext();
    }
}
