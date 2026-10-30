package com.wind.mybatis.plugin;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.BaseMapper;
import com.mybatisflex.core.datasource.FlexDataSource;
import com.mybatisflex.core.mybatis.FlexConfiguration;
import com.mybatisflex.core.mybatis.FlexSqlSessionFactoryBuilder;
import com.mybatisflex.core.query.QueryWrapper;
import com.wind.integration.core.context.TenantContextHolder;
import com.wind.integration.core.model.EnvIsolationObject;
import com.wind.integration.core.model.TenantIsolationObject;
import com.wind.integration.core.model.TestDataIsolationObject;
import com.wind.integration.core.model.WorkspaceIsolationObject;
import com.wind.integration.core.context.DataIsolationQueryIgnoreHolder;
import com.wind.integration.core.context.DataIsolationContextHolder;
import com.wind.mybatis.isolation.DataIsolationScope;
import com.wind.mybatis.isolation.DataIsolationStrategy.FieldRule;
import com.wind.mybatis.isolation.FieldDataIsolationStrategy;
import lombok.Getter;
import lombok.Setter;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.exceptions.PersistenceException;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static com.wind.integration.core.enums.DataIsolationDimensions.ENVIRONMENT;
import static com.wind.integration.core.enums.DataIsolationDimensions.TEST_DATA;
import static com.wind.integration.core.enums.DataIsolationDimensions.TENANT;
import static com.wind.integration.core.enums.DataIsolationDimensions.WORKSPACE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkspaceIsolationIntegrationTests {

    private static final AtomicInteger DATABASE_SEQUENCE = new AtomicInteger();

    private DataSource dataSource;

    private DataIsolationScope workspaceScope;

    @BeforeEach
    void setUp() throws Exception {
        String url = "jdbc:h2:mem:workspace_isolation_" + DATABASE_SEQUENCE.incrementAndGet() + ";MODE=MySQL;DB_CLOSE_DELAY=-1";
        dataSource = new UnpooledDataSource("org.h2.Driver", url, "sa", "");
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE t_workspace_order (id BIGINT PRIMARY KEY, tenant_id BIGINT, organization_id VARCHAR(32), env VARCHAR(16), is_test BOOLEAN)");
            statement.execute("INSERT INTO t_workspace_order VALUES (1,10,'ws-a','sit',TRUE),(2,10,'ws-b','sit',TRUE),(3,20,'ws-a','sit',TRUE),(4,10,'ws-a','prod',TRUE),(5,10,'ws-a','sit',FALSE),(6,10,'ws-c','sit',TRUE)");
        }
        workspaceScope = DataIsolationScope.of("ws-a");
    }

    @AfterEach
    void tearDown() {
        DataIsolationQueryIgnoreHolder.clear();
        TenantContextHolder.clear();
    }

    @Test
    void testWorkspaceAndTenantUseIndependentValuesAndColumnMapping() {
        SqlSessionFactory factory = factory(interceptor(), OrderMapper.class);
        TenantContextHolder.runWithTenantId(10L, () -> {
            try (SqlSession session = factory.openSession()) {
                OrderMapper mapper = session.getMapper(OrderMapper.class);
                assertEquals(List.of(1L), ids(mapper));
                assertEquals(1L, mapper.selectCountByQuery(QueryWrapper.create()));
                assertEquals("ws-a", mapper.selectOneById(1L).getWorkspaceId());
                assertNull(mapper.selectOneById(2L));
                assertNull(mapper.selectOneById(3L));
            }
        });
    }

    @Test
    void testWorkspaceScopeChangesApplyToEachQueryAndCacheKey() {
        SqlSessionFactory factory = factory(interceptor(), OrderMapper.class);
        TenantContextHolder.runWithTenantId(10L, () -> {
            try (SqlSession session = factory.openSession()) {
                OrderMapper mapper = session.getMapper(OrderMapper.class);
                assertEquals(List.of(1L), ids(mapper));
                workspaceScope = DataIsolationScope.of("ws-b");
                assertEquals(List.of(2L), ids(mapper));
                workspaceScope = DataIsolationScope.ofValues(List.of("ws-a", "ws-b"));
                assertEquals(List.of(1L, 2L), ids(mapper));
                workspaceScope = DataIsolationScope.ofValues(List.of());
                assertEquals(List.of(), ids(mapper));
                assertEquals(0L, mapper.selectCountByQuery(QueryWrapper.create()));
                workspaceScope = DataIsolationScope.unrestrictedScope();
                assertEquals(List.of(1L, 2L, 6L), ids(mapper));
            }
        });
    }

    @Test
    void testMissingWorkspaceReturnsNoRowsAndNullCustomSupplierFails() {
        SqlSessionFactory defaults = factory(new DataIsolationQueryInterceptor(), OrderMapper.class);
        SqlSessionFactory configured = factory(interceptor(), OrderMapper.class);
        DataIsolationContextHolder.runWith(Map.of(TENANT, 10L, ENVIRONMENT, "sit", TEST_DATA, true), () -> {
            try (SqlSession session = defaults.openSession()) {
                OrderMapper mapper = session.getMapper(OrderMapper.class);
                assertEquals(List.of(), ids(mapper));
                DataIsolationContextHolder.runWith(Map.of(TENANT, 10L, WORKSPACE, "ws-a", ENVIRONMENT, "sit", TEST_DATA, true),
                        () -> assertEquals(List.of(1L), ids(mapper)));
                assertEquals(List.of(), ids(mapper));
            }
            workspaceScope = null;
            try (SqlSession session = configured.openSession()) {
                PersistenceException error = assertThrows(PersistenceException.class,
                        () -> ids(session.getMapper(OrderMapper.class)));
                assertTrue(error.getMessage().contains(WorkspaceIsolationObject.class.getName()));
            }
        });
    }

    @Test
    void testIgnoringWorkspacePreservesOtherDimensionsAndRestoresState() {
        SqlSessionFactory factory = factory(interceptor(), OrderMapper.class);
        TenantContextHolder.runWithTenantId(10L, () -> {
            try (SqlSession session = factory.openSession()) {
                OrderMapper mapper = session.getMapper(OrderMapper.class);
                DataIsolationQueryIgnoreHolder.runIgnoring(
                        () -> assertEquals(List.of(1L, 2L, 6L), ids(mapper)), WORKSPACE);
                DataIsolationQueryIgnoreHolder.runIgnoring(
                        () -> assertEquals(List.of(1L, 2L, 4L, 6L), ids(mapper)), WORKSPACE, ENVIRONMENT);
                assertEquals(List.of(1L), ids(mapper));
                assertEquals(10L, TenantContextHolder.requireTenantId());
            }
        });
    }

    @Test
    void testWorkspaceOnlyEntityDoesNotRequireTenantContext() {
        SqlSessionFactory factory = factory(interceptor(), WorkspaceOnlyMapper.class);

        assertNull(TenantContextHolder.getTenantId());
        try (SqlSession session = factory.openSession()) {
            List<Long> ids = session.getMapper(WorkspaceOnlyMapper.class).selectListByQuery(QueryWrapper.create())
                    .stream().map(WorkspaceFields::getId).sorted().toList();
            assertEquals(List.of(1L, 3L, 4L, 5L), ids);
        }
    }

    private DataIsolationQueryInterceptor interceptor() {
        FieldDataIsolationStrategy fields = FieldDataIsolationStrategy.withDefaults(List.of(
                FieldRule.forDimension(WORKSPACE, "workspaceId", () -> workspaceScope),
                FieldRule.forDimension(ENVIRONMENT, "env", () -> DataIsolationScope.of("sit")),
                FieldRule.forDimension(TEST_DATA, "testData", () -> DataIsolationScope.of(true))));
        return new DataIsolationQueryInterceptor(List.of(fields));
    }

    private SqlSessionFactory factory(DataIsolationQueryInterceptor interceptor, Class<?> mapperType) {
        FlexDataSource flexDataSource = new FlexDataSource("default", dataSource, false);
        FlexConfiguration configuration = new FlexConfiguration(new Environment(
                "workspace", new JdbcTransactionFactory(), flexDataSource));
        configuration.addInterceptor(interceptor);
        configuration.addMapper(mapperType);
        return new FlexSqlSessionFactoryBuilder().build(configuration);
    }

    private List<Long> ids(OrderMapper mapper) {
        return mapper.selectListByQuery(QueryWrapper.create()).stream().map(WorkspaceFields::getId).sorted().toList();
    }

    @Getter
    @Setter
    static class WorkspaceFields implements WorkspaceIsolationObject<String> {
        @Id
        private Long id;
        private Long tenantId;
        @Column("organization_id")
        private String workspaceId;
        private String env;
        @Column("is_test")
        private boolean testData;
    }

    @Table("t_workspace_order")
    static class WorkspaceOrder extends WorkspaceFields implements TenantIsolationObject<Long>, EnvIsolationObject, TestDataIsolationObject {
    }

    @Table("t_workspace_order")
    static class WorkspaceOnlyOrder extends WorkspaceFields {
    }

    interface OrderMapper extends BaseMapper<WorkspaceOrder> {
    }

    interface WorkspaceOnlyMapper extends BaseMapper<WorkspaceOnlyOrder> {
    }
}
