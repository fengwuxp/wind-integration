package com.wind.mybatis.plugin;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.BaseMapper;
import com.wind.integration.core.context.TenantContextHolder;
import com.wind.integration.core.model.WorkspaceIsolationObject;
import com.wind.integration.core.model.TenantIsolationObject;
import com.wind.integration.core.context.DataIsolationQueryIgnoreHolder;
import com.wind.mybatis.isolation.DataIsolationScope;
import com.wind.mybatis.isolation.DataIsolationStrategy.FieldRule;
import com.wind.mybatis.isolation.FieldDataIsolationStrategy;
import lombok.Getter;
import lombok.Setter;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.exceptions.PersistenceException;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static com.wind.integration.core.enums.DataIsolationDimensions.TENANT;
import static com.wind.integration.core.enums.DataIsolationDimensions.WORKSPACE;

class DataIsolationMetadataIntegrationTests {

    private static final AtomicInteger DATABASE_SEQUENCE = new AtomicInteger();

    private UnpooledDataSource dataSource;

    private DataIsolationScope workspaceScope;

    @BeforeEach
    void setUp() throws Exception {
        String url = "jdbc:h2:mem:auto_isolation_" + DATABASE_SEQUENCE.incrementAndGet() + ";MODE=MySQL;DB_CLOSE_DELAY=-1";
        dataSource = new UnpooledDataSource("org.h2.Driver", url, "sa", "");
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE auto_orders (id BIGINT PRIMARY KEY, account_id BIGINT, tenant_key BIGINT, workspace_key BIGINT)");
            statement.execute("INSERT INTO auto_orders VALUES (1,10,20,100),(2,20,10,100),(3,10,10,200)");
        }
        workspaceScope = DataIsolationScope.of(100L);
    }

    @AfterEach
    void tearDown() {
        DataIsolationQueryIgnoreHolder.clear();
        TenantContextHolder.clear();
    }

    @Test
    void testWorkspaceUsesEntityColumnMappingAndKeepsTenantRule() throws Exception {
        SqlSessionFactory factory = factory(workspaceInterceptor(), WorkspaceMapper.class, AnotherWorkspaceMapper.class);

        assertEquals(List.of(1L), ids(factory));
        long count = TenantContextHolder.callWithTenantId(10L, () -> {
            try (SqlSession session = factory.openSession()) {
                return session.getMapper(SummaryMapper.class).count();
            }
        });
        assertEquals(1L, count);
        workspaceScope = null;
        assertThrows(PersistenceException.class, () -> ids(factory));
    }

    @Test
    void testSharedInterceptorKeepsFactoryMappingsSeparate() throws Exception {
        DataIsolationQueryInterceptor interceptor = workspaceInterceptor();
        SqlSessionFactory workspaceFactory = factory(interceptor, WorkspaceMapper.class);
        SqlSessionFactory tenantFactory = factory(interceptor, TenantMapper.class);

        assertEquals(List.of(1L), ids(workspaceFactory));
        assertEquals(List.of(2L, 3L), ids(tenantFactory));
        assertEquals(List.of(1L), ids(workspaceFactory));
    }

    @Test
    void testRegisteringMapperAfterFirstQueryRefreshesIsolationMetadata() throws Exception {
        SqlSessionFactory factory = factory(workspaceInterceptor());

        assertEquals(List.of(1L, 2L, 3L), ids(factory));
        factory.getConfiguration().addMapper(WorkspaceMapper.class);
        assertEquals(List.of(1L), ids(factory));
    }

    @Test
    void testRegisteringConflictingMapperAfterPublicQueryDoesNotReuseFastPath() throws Exception {
        SqlSessionFactory factory = factory(workspaceInterceptor(), PublicMapper.class);

        assertEquals(List.of(1L, 2L, 3L), ids(factory));
        assertEquals(List.of(1L, 2L, 3L), ids(factory));
        factory.getConfiguration().addMapper(WorkspaceMapper.class);

        assertThrows(PersistenceException.class, () -> ids(factory));
    }

    @Test
    void testConflictingMappingsForTheSameTableFailInsteadOfChoosingOne() {
        SqlSessionFactory factory = factory(workspaceInterceptor(), WorkspaceMapper.class, TenantMapper.class);

        assertThrows(PersistenceException.class, () -> ids(factory));
    }

    @Test
    void testWorkspaceAndTenantCanBeIgnoredTogether() throws Exception {
        SqlSessionFactory factory = factory(workspaceInterceptor(), WorkspaceMapper.class);

        assertEquals(List.of(1L), ids(factory));
        List<Long> workspaceIgnored = DataIsolationQueryIgnoreHolder.callIgnoring(() -> ids(factory), WORKSPACE);
        assertEquals(List.of(1L, 3L), workspaceIgnored);
        List<Long> allIgnored = DataIsolationQueryIgnoreHolder.callIgnoring(() -> ids(factory), WORKSPACE, TENANT);
        assertEquals(List.of(1L, 2L, 3L), allIgnored);
        assertEquals(List.of(1L), ids(factory));
    }

    @Test
    void testIgnoredDimensionDoesNotReadItsScopeSupplier() throws Exception {
        FieldDataIsolationStrategy fields = FieldDataIsolationStrategy.withDefaults(List.of(
                FieldRule.forDimension(WORKSPACE, "workspaceId", () -> {
                    throw new IllegalStateException("当前操作未绑定归属范围");
                })));
        DataIsolationQueryInterceptor interceptor = new DataIsolationQueryInterceptor(List.of(fields));
        SqlSessionFactory factory = factory(interceptor, WorkspaceMapper.class);

        assertThrows(PersistenceException.class, () -> ids(factory));
        List<Long> records = DataIsolationQueryIgnoreHolder.callIgnoring(() -> ids(factory), WORKSPACE);
        assertEquals(List.of(1L, 3L), records);
        assertThrows(PersistenceException.class, () -> ids(factory));
    }

    @Test
    void testCustomRuleOverridesOneBuiltinDimension() throws Exception {
        FieldDataIsolationStrategy fields = FieldDataIsolationStrategy.withDefaults(List.of(
                FieldRule.forDimension(WORKSPACE, "workspaceId", () -> workspaceScope),
                FieldRule.forDimension(TENANT, "tenantId", () -> DataIsolationScope.of(20L))));
        DataIsolationQueryInterceptor interceptor = new DataIsolationQueryInterceptor(List.of(fields));

        assertEquals(List.of(2L), ids(factory(interceptor, WorkspaceMapper.class)));
    }

    @Test
    void testDuplicateCustomDimensionIsRejectedAtConstruction() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () ->
                FieldDataIsolationStrategy.withDefaults(List.of(
                        FieldRule.forDimension(TENANT, "tenantId", () -> DataIsolationScope.of(10L)),
                        FieldRule.forDimension(TENANT, "tenantId", DataIsolationScope::unrestrictedScope))));

        assertTrue(error.getMessage().contains("TENANT"));
    }

    private DataIsolationQueryInterceptor workspaceInterceptor() {
        FieldDataIsolationStrategy fields = FieldDataIsolationStrategy.withDefaults(List.of(
                FieldRule.forDimension(WORKSPACE, "workspaceId", () -> workspaceScope)));
        return new DataIsolationQueryInterceptor(List.of(fields));
    }

    private SqlSessionFactory factory(DataIsolationQueryInterceptor interceptor, Class<?>... mapperTypes) {
        Configuration configuration = new Configuration(new Environment("metadata", new JdbcTransactionFactory(), dataSource));
        configuration.addInterceptor(interceptor);
        configuration.addMapper(SummaryMapper.class);
        for (Class<?> mapperType : mapperTypes) {
            configuration.addMapper(mapperType);
        }
        return new SqlSessionFactoryBuilder().build(configuration);
    }

    private List<Long> ids(SqlSessionFactory factory) throws Exception {
        return TenantContextHolder.callWithTenantId(10L, () -> {
            try (SqlSession session = factory.openSession()) {
                return session.getMapper(SummaryMapper.class).ids();
            }
        });
    }

    @Getter
    @Setter
    static class Identity {
        @Id
        private Long id;
    }

    @Table("auto_orders")
    static class PublicOrder extends Identity {
    }

    @Getter
    @Setter
    @Table("auto_orders")
    static class WorkspaceOrder extends Identity implements TenantIsolationObject<Long>, WorkspaceIsolationObject<Long> {
        @Column("account_id")
        private Long tenantId;
        @Column("workspace_key")
        private Long workspaceId;
    }

    @Getter
    @Setter
    @Table("auto_orders")
    static class TenantOrder extends Identity implements TenantIsolationObject<Long> {
        @Column("tenant_key")
        private Long tenantId;
    }

    interface GenericMapper<T> extends BaseMapper<T> {
    }

    interface WorkspaceMapper extends GenericMapper<WorkspaceOrder> {
    }

    interface PublicMapper extends BaseMapper<PublicOrder> {
    }

    interface AnotherWorkspaceMapper extends GenericMapper<WorkspaceOrder> {
    }

    interface TenantMapper extends BaseMapper<TenantOrder> {
    }

    interface SummaryMapper {
        @Select("SELECT id FROM auto_orders ORDER BY id")
        List<Long> ids();

        @Select("SELECT COUNT(*) FROM auto_orders")
        long count();
    }
}
