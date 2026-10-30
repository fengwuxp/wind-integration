package com.wind.mybatis.spring;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.BaseMapper;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.boot.v4.MybatisFlexAutoConfiguration;
import com.wind.integration.core.context.DataIsolationContextHolder;
import com.wind.integration.core.context.DataIsolationQueryIgnoreHolder;
import com.wind.integration.core.model.TenantIsolationObject;
import com.wind.integration.core.model.WorkspaceIsolationObject;
import com.wind.mybatis.isolation.DataIsolationScope;
import com.wind.mybatis.isolation.DataIsolationStrategy;
import com.wind.mybatis.isolation.DataIsolationStrategy.FieldRule;
import com.wind.mybatis.isolation.FieldDataIsolationStrategy;
import com.wind.mybatis.plugin.DataIsolationQueryInterceptor;
import lombok.Getter;
import lombok.Setter;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static com.wind.integration.core.enums.DataIsolationDimensions.TENANT;
import static com.wind.integration.core.enums.DataIsolationDimensions.WORKSPACE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证 Flex Boot 自动装配、策略集合注入和普通 Mapper 的真实隔离结果。
 */
class DataIsolationSpringIntegrationTests {

    private static final AtomicInteger DATABASE_SEQUENCE = new AtomicInteger();

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MybatisFlexAutoConfiguration.class))
            .withUserConfiguration(IsolationConfiguration.class);

    @Test
    void testAutoConfiguredMapperAppliesEveryStrategy() {
        runner.run(context -> {
            assertNull(context.getStartupFailure());
            SqlSessionFactory factory = context.getBean(SqlSessionFactory.class);
            assertEquals(1, factory.getConfiguration().getInterceptors().size());
            assertEquals(2, context.getBeansOfType(DataIsolationStrategy.class).size());
            OrderMapper mapper = context.getBean(OrderMapper.class);

            DataIsolationContextHolder.runWith(Map.of(TENANT, 10L, WORKSPACE, 100L), () -> {
                assertEquals(List.of(1L, 5L), ids(mapper));
                assertEquals(2L, mapper.selectCountByQuery(QueryWrapper.create()));
                assertEquals(1L, mapper.selectOneById(1L).getId());
                assertNull(mapper.selectOneById(2L));
                assertNull(mapper.selectOneById(3L));
                assertEquals(List.of(1L, 5L), mapper.annotatedIds());
                Page<OrderRecord> page = mapper.paginate(2, 1, QueryWrapper.create().orderBy("id"));
                assertEquals(2L, page.getTotalRow());
                assertEquals(List.of(5L), page.getRecords().stream().map(OrderRecord::getId).toList());
            });
        });
    }

    @Test
    void testContextSwitchAndIgnoreRestoreWithSpringMapper() {
        runner.run(context -> {
            assertNull(context.getStartupFailure());
            OrderMapper mapper = context.getBean(OrderMapper.class);
            DataIsolationContextHolder.runWith(Map.of(TENANT, 10L, WORKSPACE, 100L), () -> {
                assertEquals(List.of(1L, 5L), ids(mapper));
                DataIsolationContextHolder.runWithWorkspace(200L, () -> assertEquals(List.of(4L), ids(mapper)));
                DataIsolationContextHolder.runWithTenant(20L, () -> assertEquals(List.of(3L), ids(mapper)));
                DataIsolationQueryIgnoreHolder.runIgnoring(
                        () -> assertEquals(List.of(1L, 4L, 5L), ids(mapper)), WORKSPACE);
                assertEquals(List.of(1L, 5L), ids(mapper));
            });
            assertEquals(List.of(), ids(mapper));
        });
    }

    @Test
    void testMissingContextReturnsNoRowsAndUnsupportedJoinFails() {
        runner.run(context -> {
            assertNull(context.getStartupFailure());
            OrderMapper mapper = context.getBean(OrderMapper.class);
            assertEquals(List.of(), ids(mapper));
            DataIsolationContextHolder.runWith(Map.of(TENANT, 10L, WORKSPACE, 100L), () -> {
                RuntimeException error = assertThrows(RuntimeException.class, mapper::joinedIds);
                assertTrue(error.getMessage().contains("single-table SELECT only"));
                assertEquals(List.of(1L, 5L), ids(mapper));
            });
        });
    }

    @Test
    void testConcurrentContextsDoNotShareRows() {
        runner.run(context -> {
            assertNull(context.getStartupFailure());
            OrderMapper mapper = context.getBean(OrderMapper.class);
            try (var executor = Executors.newFixedThreadPool(2)) {
                var first = executor.submit(() -> DataIsolationContextHolder.callWith(
                        Map.of(TENANT, 10L, WORKSPACE, 100L), () -> ids(mapper)));
                var second = executor.submit(() -> DataIsolationContextHolder.callWith(
                        Map.of(TENANT, 20L, WORKSPACE, 100L), () -> ids(mapper)));
                assertEquals(List.of(1L, 5L), first.get());
                assertEquals(List.of(3L), second.get());
            }
            assertEquals(List.of(), ids(mapper));
        });
    }

    private static List<Long> ids(OrderMapper mapper) {
        return mapper.selectListByQuery(QueryWrapper.create().orderBy("id"))
                .stream().map(OrderRecord::getId).toList();
    }

    @Configuration(proxyBeanMethods = false)
    @MapperScan(basePackageClasses = DataIsolationSpringIntegrationTests.class, annotationClass = Mapper.class)
    static class IsolationConfiguration {

        @Bean
        DataSource dataSource() throws Exception {
            DataSource dataSource = new UnpooledDataSource("org.h2.Driver", "jdbc:h2:mem:isolation_spring_"
                    + DATABASE_SEQUENCE.incrementAndGet() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
            try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
                statement.execute("CREATE TABLE spring_isolation_order (id BIGINT PRIMARY KEY, tenant_id BIGINT, organization_id BIGINT, owner_id BIGINT)");
                statement.execute("INSERT INTO spring_isolation_order VALUES (1,10,100,7),(2,10,100,8),(3,20,100,7),(4,10,200,7),(5,10,100,7)");
            }
            return dataSource;
        }

        @Bean
        DataIsolationStrategy fieldIsolation() {
            return FieldDataIsolationStrategy.withDefaults(List.of(
                    new FieldRule(OrderRecord.class, "organizationId",
                            () -> DataIsolationScope.of(DataIsolationContextHolder.getWorkspaceId()), WORKSPACE)));
        }

        @Bean
        DataIsolationStrategy ownerIsolation() {
            return new FieldDataIsolationStrategy(List.of(
                    new FieldRule(OrderRecord.class, "ownerId", () -> DataIsolationScope.of(7L))), 100);
        }

        @Bean
        DataIsolationQueryInterceptor isolationInterceptor(List<DataIsolationStrategy> strategies) {
            return new DataIsolationQueryInterceptor(strategies);
        }
    }

    @Mapper
    interface OrderMapper extends BaseMapper<OrderRecord> {

        @Select("SELECT o.id FROM spring_isolation_order o ORDER BY o.id")
        List<Long> annotatedIds();

        @Select("SELECT a.id FROM spring_isolation_order a JOIN spring_isolation_order b ON a.id = b.id")
        List<Long> joinedIds();
    }

    @Getter
    @Setter
    @Table("spring_isolation_order")
    static class OrderRecord implements TenantIsolationObject<Long>, WorkspaceIsolationObject<Long> {
        @Id
        private Long id;
        private Long tenantId;
        @Column("organization_id")
        private Long organizationId;
        private Long ownerId;

        @Override
        public Long getWorkspaceId() {
            return organizationId;
        }

        @Override
        public void setWorkspaceId(Long workspaceId) {
            organizationId = workspaceId;
        }
    }
}
