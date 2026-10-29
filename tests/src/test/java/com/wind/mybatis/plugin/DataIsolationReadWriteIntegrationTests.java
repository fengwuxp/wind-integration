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
import com.wind.integration.core.context.DataIsolationContextHolder;
import com.wind.integration.core.context.DataIsolationQueryIgnoreHolder;
import com.wind.integration.core.context.TenantContextHolder;
import com.wind.integration.core.enums.DataIsolationDimensions;
import com.wind.integration.core.model.EnvIsolationObject;
import com.wind.integration.core.model.TenantIsolationObject;
import com.wind.integration.core.model.TestDataIsolationObject;
import com.wind.integration.core.model.WorkspaceIsolationObject;
import com.wind.mybatis.flex.EnvIsolationFieldInsertListener;
import com.wind.mybatis.flex.TestDataIsolationFieldInsertListener;
import com.wind.mybatis.flex.WorkspaceIsolationFieldInsertListener;
import lombok.Getter;
import lombok.Setter;
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

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.wind.integration.core.enums.DataIsolationDimensions.ENVIRONMENT;
import static com.wind.integration.core.enums.DataIsolationDimensions.TENANT;
import static com.wind.integration.core.enums.DataIsolationDimensions.TEST_DATA;
import static com.wind.integration.core.enums.DataIsolationDimensions.WORKSPACE;

class DataIsolationReadWriteIntegrationTests {

    private static final AtomicInteger DATABASE_SEQUENCE = new AtomicInteger();

    private SqlSessionFactory sessionFactory;

    private TenantFactory previousTenantFactory;

    @BeforeEach
    void setUp() throws SQLException {
        UnpooledDataSource source = new UnpooledDataSource("org.h2.Driver",
                "jdbc:h2:mem:holder_write_" + DATABASE_SEQUENCE.incrementAndGet() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        try (var connection = source.getConnection(); var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE t_holder_order (id BIGINT PRIMARY KEY, tenant_id BIGINT NOT NULL, workspace_id VARCHAR(32), env VARCHAR(16) NOT NULL, is_test BOOLEAN NOT NULL, name VARCHAR(32))");
            statement.execute("INSERT INTO t_holder_order VALUES (1,10,'ws-a','sit',FALSE,'one'),(2,10,'ws-b','sit',FALSE,'two'),(3,20,'ws-a','sit',FALSE,'three'),(4,10,'ws-a','sit',TRUE,'four'),(5,10,'ws-a','prod',FALSE,'five')");
        }
        FlexConfiguration configuration = new FlexConfiguration(new Environment("holder", new JdbcTransactionFactory(),
                new FlexDataSource("default", source, false)));
        configuration.addInterceptor(new DataIsolationQueryInterceptor());
        configuration.addMapper(OrderMapper.class);
        sessionFactory = new FlexSqlSessionFactoryBuilder().build(configuration);
        previousTenantFactory = TenantManager.getTenantFactory();
        TenantManager.setTenantFactory(() -> new Object[]{DataIsolationContextHolder.requireTenantId()});
    }

    @AfterEach
    void tearDown() {
        TenantManager.setTenantFactory(previousTenantFactory);
        TenantContextHolder.clear();
        DataIsolationQueryIgnoreHolder.clear();
    }

    /** Flex 填充租户，三个监听器填充其余归属字段，插入后默认读取范围能看到该记录。 */
    @Test
    void testInsertAndReadUseTheSameContext() throws Exception {
        DataIsolationContextHolder.callWith(context(10L, "ws-a", "sit", false), () -> {
            try (SqlSession session = sessionFactory.openSession()) {
                OrderMapper mapper = session.getMapper(OrderMapper.class);
                OrderEntity entity = order(10L);
                Assertions.assertEquals(1, mapper.insert(entity));
                assertStoredContext(session, 10L, "ws-a");
                Assertions.assertEquals(List.of(1L, 10L), mapper.ids());
                Assertions.assertEquals("ws-a", mapper.selectOneById(10L).getWorkspaceId());
                session.rollback();
            }
            return null;
        });
    }

    @Test
    void testDefaultReadsAndCachesFollowAllFourDimensions() {
        try (SqlSession session = sessionFactory.openSession()) {
            OrderMapper mapper = session.getMapper(OrderMapper.class);
            DataIsolationContextHolder.runWith(context(10L, "ws-a", "sit", false),
                    () -> Assertions.assertEquals(List.of(1L), mapper.ids()));
            DataIsolationContextHolder.runWith(context(20L, "ws-a", "sit", false),
                    () -> Assertions.assertEquals(List.of(3L), mapper.ids()));
            DataIsolationContextHolder.runWith(context(10L, "ws-b", "sit", false),
                    () -> Assertions.assertEquals(List.of(2L), mapper.ids()));
            DataIsolationContextHolder.runWith(context(10L, "ws-a", "prod", false),
                    () -> Assertions.assertEquals(List.of(5L), mapper.ids()));
            DataIsolationContextHolder.runWith(context(10L, "ws-a", "sit", true),
                    () -> Assertions.assertEquals(List.of(4L), mapper.ids()));
        }
    }

    @Test
    void testInvalidTestDataTypeRejectsReadsAndInserts() {
        DataIsolationContextHolder.runWith(Map.of(TENANT, 10L, WORKSPACE, "ws-a", ENVIRONMENT, "sit", TEST_DATA, "true"), () -> {
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
                    context(10L, "ws-a", "sit", false), () -> {
                        try (SqlSession session = sessionFactory.openSession()) {
                            return session.getMapper(OrderMapper.class).ids();
                        }
                    })).get(5, TimeUnit.SECONDS);
            List<Long> missingTenantRows = worker.submit(() -> DataIsolationContextHolder.callWith(
                    Map.of(WORKSPACE, "ws-a", ENVIRONMENT, "sit"), () -> {
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
        DataIsolationContextHolder.callWith(context(10L, "ws-a", "sit", false), () -> {
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

    /** 工作空间监听器按当前契约允许填 null，该记录随后不在缺失工作空间的默认读取范围内。 */
    @Test
    void testMissingWorkspaceWritesNullAndReadsNoRows() throws Exception {
        DataIsolationContextHolder.callWith(Map.of(TENANT, 10L, ENVIRONMENT, "sit"), () -> {
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
        DataIsolationContextHolder.runWith(context(10L, "ws-a", "sit", false), () -> {
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

    private Map<DataIsolationDimensions, Object> context(long tenantId, String workspaceId, String env, boolean testData) {
        return Map.of(TENANT, tenantId, WORKSPACE, workspaceId, ENVIRONMENT, env, TEST_DATA, testData);
    }

    private OrderEntity order(long id) {
        OrderEntity entity = new OrderEntity();
        entity.setId(id);
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
            EnvIsolationFieldInsertListener.class, TestDataIsolationFieldInsertListener.class, WorkspaceIsolationFieldInsertListener.class
    })
    static class OrderEntity implements TenantIsolationObject<Long>, WorkspaceIsolationObject<String>, EnvIsolationObject, TestDataIsolationObject {
        @Id
        private Long id;
        @Column(tenantId = true)
        private Long tenantId;
        private String workspaceId;
        private String env;
        @Column("is_test")
        private boolean testData;
        private String name;
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
