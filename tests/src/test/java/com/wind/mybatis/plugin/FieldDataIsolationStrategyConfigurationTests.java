package com.wind.mybatis.plugin;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.BaseMapper;
import com.mybatisflex.core.datasource.FlexDataSource;
import com.mybatisflex.core.mybatis.FlexConfiguration;
import com.mybatisflex.core.mybatis.FlexSqlSessionFactoryBuilder;
import com.mybatisflex.core.query.QueryWrapper;
import com.wind.integration.core.context.DataIsolationContextHolder;
import com.wind.integration.core.context.DataIsolationQueryIgnoreHolder;
import com.wind.integration.core.model.WorkspaceIsolationObject;
import com.wind.mybatis.flex.WorkspaceIsolationFieldInsertListener;
import com.wind.mybatis.isolation.DataIsolationScope;
import com.wind.mybatis.isolation.DataIsolationStrategy.FieldRule;
import com.wind.mybatis.isolation.FieldDataIsolationStrategy;
import lombok.Getter;
import lombok.Setter;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.exceptions.PersistenceException;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static com.wind.integration.core.enums.DataIsolationDimensions.WORKSPACE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证实体级配置、默认约定和继承规则对真实查询的影响。
 */
class FieldDataIsolationStrategyConfigurationTests {

    private static final AtomicInteger DATABASE_SEQUENCE = new AtomicInteger();

    private DataSource dataSource;

    private DataIsolationScope workspaceScope;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new UnpooledDataSource("org.h2.Driver", "jdbc:h2:mem:field_configuration_"
                + DATABASE_SEQUENCE.incrementAndGet() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE configured_workspace_item (id BIGINT PRIMARY KEY, organization_id BIGINT, owner_id BIGINT)");
            statement.execute("INSERT INTO configured_workspace_item VALUES (1,100,7),(2,100,8),(3,200,7)");
            statement.execute("CREATE TABLE standard_workspace_item (id BIGINT PRIMARY KEY, workspace_key BIGINT)");
            statement.execute("INSERT INTO standard_workspace_item VALUES (1,100),(2,200)");
            statement.execute("CREATE TABLE billing_workspace_item (id BIGINT PRIMARY KEY, organization_id BIGINT, owner_id BIGINT, billing_space_id BIGINT)");
            statement.execute("INSERT INTO billing_workspace_item VALUES (11,100,7,200),(12,200,8,100)");
            statement.execute("CREATE TABLE ambiguous_workspace_item (id BIGINT PRIMARY KEY, workspace_key BIGINT)");
            statement.execute("INSERT INTO ambiguous_workspace_item VALUES (1,100),(2,200)");
            statement.execute("CREATE TABLE specific_workspace_item (id BIGINT PRIMARY KEY, workspace_key BIGINT)");
            statement.execute("INSERT INTO specific_workspace_item VALUES (1,100),(2,200)");
        }
        workspaceScope = DataIsolationScope.of(100L);
    }

    @Test
    void testEntityPropertyOverridesConvention() {
        FieldDataIsolationStrategy fields = FieldDataIsolationStrategy.withDefaults(List.of(
                new FieldRule(ConfiguredItem.class, "organizationId", () -> workspaceScope, WORKSPACE)));
        SqlSessionFactory factory = factory(fields, ConfiguredMapper.class);

        DataIsolationContextHolder.runWithWorkspace(999L, () -> {
            try (var session = factory.openSession()) {
                ConfiguredMapper mapper = session.getMapper(ConfiguredMapper.class);
                assertEquals(List.of(1L, 2L), ids(mapper));
                workspaceScope = DataIsolationScope.of(200L);
                assertEquals(List.of(3L), ids(mapper));
            }
        });
    }

    @Test
    void testEntityOverrideCoexistsWithDefaultConvention() {
        FieldDataIsolationStrategy fields = FieldDataIsolationStrategy.withDefaults(List.of(
                new FieldRule(ConfiguredItem.class, "organizationId", () -> workspaceScope, WORKSPACE)));
        SqlSessionFactory factory = factory(fields, ConfiguredMapper.class, StandardMapper.class);

        DataIsolationContextHolder.runWithWorkspace(200L, () -> {
            try (var session = factory.openSession()) {
                assertEquals(List.of(1L, 2L), ids(session.getMapper(ConfiguredMapper.class)));
                assertEquals(List.of(2L), standardIds(session.getMapper(StandardMapper.class)));
            }
        });
    }

    @Test
    void testRenamedFieldKeepsSharedCustomScope() {
        Supplier<DataIsolationScope> sharedScope = () -> workspaceScope;
        FieldDataIsolationStrategy fields = FieldDataIsolationStrategy.withDefaults(List.of(
                FieldRule.forDimension(WORKSPACE, "workspaceId", sharedScope),
                new FieldRule(ConfiguredItem.class, "organizationId", sharedScope, WORKSPACE)));
        SqlSessionFactory factory = factory(fields, ConfiguredMapper.class, StandardMapper.class);

        DataIsolationContextHolder.runWithWorkspace(999L, () -> {
            try (var session = factory.openSession()) {
                ConfiguredMapper configured = session.getMapper(ConfiguredMapper.class);
                StandardMapper standard = session.getMapper(StandardMapper.class);
                assertEquals(List.of(1L, 2L), ids(configured));
                assertEquals(List.of(1L), standardIds(standard));
                assertEquals(2L, configured.selectCountByQuery(QueryWrapper.create()));
                assertNull(configured.selectOneById(3L));
                assertEquals(List.of(2L), configured.selectListByQuery(QueryWrapper.create()
                                .where("owner_id >= ?", 7L).orderBy("id").limit(1, 1))
                        .stream().map(ConfiguredItem::getId).toList());
                workspaceScope = DataIsolationScope.of(200L);
                assertEquals(List.of(3L), ids(configured));
                assertEquals(List.of(2L), standardIds(standard));
                workspaceScope = DataIsolationScope.unrestrictedScope();
                assertEquals(List.of(1L, 2L, 3L), ids(configured));
                workspaceScope = DataIsolationScope.of(100L);
                assertEquals(List.of(1L, 2L), ids(configured));
            }
        });
    }

    @Test
    void testMostSpecificClassWinsRegardlessOfConfigurationOrder() {
        FieldRule global = FieldRule.forDimension(WORKSPACE, "workspaceId", () -> {
            throw new IllegalStateException("覆盖后的全局规则不应参与查询");
        });
        FieldRule parent = new FieldRule(ConfiguredItem.class, "organizationId", () -> workspaceScope, WORKSPACE);
        FieldRule child = new FieldRule(BillingItem.class, "billingSpaceId", () -> DataIsolationScope.of(200L), WORKSPACE);

        for (List<FieldRule> rules : List.of(List.of(global, parent, child), List.of(child, parent, global))) {
            SqlSessionFactory factory = factory(FieldDataIsolationStrategy.withDefaults(rules),
                    ConfiguredMapper.class, BillingMapper.class);
            try (var session = factory.openSession()) {
                assertEquals(List.of(1L, 2L), ids(session.getMapper(ConfiguredMapper.class)));
                assertEquals(List.of(11L), session.getMapper(BillingMapper.class).selectListByQuery(QueryWrapper.create())
                        .stream().map(BillingItem::getId).toList());
            }
        }
    }

    @Test
    void testInheritedEntityUsesParentRule() {
        FieldDataIsolationStrategy fields = FieldDataIsolationStrategy.withDefaults(List.of(
                new FieldRule(ConfiguredItem.class, "organizationId", () -> workspaceScope, WORKSPACE)));
        SqlSessionFactory factory = factory(fields, BillingMapper.class);
        try (var session = factory.openSession()) {
            assertEquals(List.of(11L), session.getMapper(BillingMapper.class).selectListByQuery(QueryWrapper.create())
                    .stream().map(BillingItem::getId).toList());
        }
    }

    @Test
    void testMoreSpecificInterfaceOverridesParentInterface() {
        FieldRule parent = new FieldRule(PrimaryWorkspace.class, "workspaceId", () -> DataIsolationScope.of(100L), WORKSPACE);
        FieldRule child = new FieldRule(NarrowWorkspace.class, "workspaceId", () -> DataIsolationScope.of(200L), WORKSPACE);
        for (List<FieldRule> rules : List.of(List.of(parent, child), List.of(child, parent))) {
            SqlSessionFactory factory = factory(FieldDataIsolationStrategy.withDefaults(rules), SpecificInterfaceMapper.class);
            try (var session = factory.openSession()) {
                assertEquals(List.of(2L), session.getMapper(SpecificInterfaceMapper.class).selectListByQuery(QueryWrapper.create())
                        .stream().map(StandardFields::getId).toList());
            }
        }
    }

    @Test
    void testUnrelatedInterfacesAreRejected() {
        FieldDataIsolationStrategy fields = FieldDataIsolationStrategy.withDefaults(List.of(
                new FieldRule(PrimaryWorkspace.class, "workspaceId", () -> DataIsolationScope.of(100L), WORKSPACE),
                new FieldRule(SecondaryWorkspace.class, "workspaceId", () -> DataIsolationScope.of(200L), WORKSPACE)));
        SqlSessionFactory factory = factory(fields, AmbiguousMapper.class);
        try (var session = factory.openSession()) {
            PersistenceException error = assertThrows(PersistenceException.class,
                    () -> session.getMapper(AmbiguousMapper.class).selectListByQuery(QueryWrapper.create()));
            assertTrue(error.getMessage().contains("Ambiguous isolation rules"));
        }
    }

    @Test
    void testExactEntityResolvesOverlappingInterfaceRules() {
        FieldDataIsolationStrategy fields = FieldDataIsolationStrategy.withDefaults(List.of(
                new FieldRule(PrimaryWorkspace.class, "workspaceId", () -> DataIsolationScope.of(200L), WORKSPACE),
                new FieldRule(SecondaryWorkspace.class, "workspaceId", () -> DataIsolationScope.of(200L), WORKSPACE),
                new FieldRule(AmbiguousItem.class, "workspaceId", () -> workspaceScope, WORKSPACE)));
        SqlSessionFactory factory = factory(fields, AmbiguousMapper.class);
        try (var session = factory.openSession()) {
            assertEquals(List.of(1L), session.getMapper(AmbiguousMapper.class).selectListByQuery(QueryWrapper.create())
                    .stream().map(StandardFields::getId).toList());
        }
    }

    @Test
    void testColumnNameIsNotAcceptedAsJavaProperty() {
        FieldDataIsolationStrategy fields = FieldDataIsolationStrategy.withDefaults(List.of(
                new FieldRule(StandardItem.class, "workspace_key", () -> workspaceScope, WORKSPACE)));
        SqlSessionFactory factory = factory(fields, StandardMapper.class);
        DataIsolationContextHolder.runWithWorkspace(100L, () -> {
            try (var session = factory.openSession()) {
                PersistenceException error = assertThrows(PersistenceException.class,
                        () -> standardIds(session.getMapper(StandardMapper.class)));
                assertTrue(error.getMessage().contains("workspace_key"));
            }
        });
    }

    @Test
    void testMissingCustomScopeDoesNotFallBackToContext() {
        FieldDataIsolationStrategy fields = FieldDataIsolationStrategy.withDefaults(List.of(
                new FieldRule(StandardItem.class, "workspaceId", () -> workspaceScope, WORKSPACE)));
        SqlSessionFactory factory = factory(fields, StandardMapper.class);
        DataIsolationContextHolder.runWithWorkspace(100L, () -> {
            try (var session = factory.openSession()) {
                StandardMapper mapper = session.getMapper(StandardMapper.class);
                assertEquals(List.of(1L), standardIds(mapper));
                workspaceScope = null;
                assertThrows(PersistenceException.class, () -> standardIds(mapper));
                workspaceScope = DataIsolationScope.ofValues(List.of());
                assertEquals(List.of(), standardIds(mapper));
            }
        });
    }

    @Test
    void testCustomScopeFailureDoesNotFallBackToContext() {
        FieldDataIsolationStrategy fields = FieldDataIsolationStrategy.withDefaults(List.of(
                new FieldRule(StandardItem.class, "workspaceId", () -> {
                    throw new IllegalStateException("授权范围读取失败");
                }, WORKSPACE)));
        SqlSessionFactory factory = factory(fields, StandardMapper.class);
        DataIsolationContextHolder.runWithWorkspace(100L, () -> {
            try (var session = factory.openSession()) {
                PersistenceException error = assertThrows(PersistenceException.class,
                        () -> standardIds(session.getMapper(StandardMapper.class)));
                assertTrue(error.getMessage().contains("授权范围读取失败"));
            }
        });
    }

    @Test
    void testDuplicateAndInvalidFieldRulesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> FieldDataIsolationStrategy.withDefaults(List.of(
                new FieldRule(ConfiguredItem.class, "organizationId", () -> workspaceScope, WORKSPACE),
                new FieldRule(ConfiguredItem.class, "id", () -> workspaceScope, WORKSPACE))));
        assertThrows(IllegalArgumentException.class, () -> new FieldDataIsolationStrategy(List.of(
                new FieldRule(ConfiguredItem.class, "ownerId", () -> DataIsolationScope.of(7L)),
                new FieldRule(ConfiguredItem.class, "ownerId", () -> DataIsolationScope.of(8L)))));
        assertThrows(IllegalArgumentException.class,
                () -> new FieldRule(String.class, "id", () -> workspaceScope, WORKSPACE));
    }

    @Test
    void testIgnoringCustomizedDimensionKeepsOrdinaryFieldRule() {
        FieldDataIsolationStrategy fields = FieldDataIsolationStrategy.withDefaults(List.of(
                new FieldRule(ConfiguredItem.class, "organizationId", () -> workspaceScope, WORKSPACE),
                new FieldRule(ConfiguredItem.class, "ownerId", () -> DataIsolationScope.of(7L))));
        SqlSessionFactory factory = factory(fields, ConfiguredMapper.class);
        try (var session = factory.openSession()) {
            ConfiguredMapper mapper = session.getMapper(ConfiguredMapper.class);
            assertEquals(List.of(1L), ids(mapper));
            DataIsolationQueryIgnoreHolder.runIgnoring(() -> assertEquals(List.of(1L, 3L), ids(mapper)), WORKSPACE);
            assertEquals(List.of(1L), ids(mapper));
        }
    }

    @Test
    void testOrdinaryFieldsKeepIntersectionAcrossInheritance() {
        FieldDataIsolationStrategy fields = new FieldDataIsolationStrategy(List.of(
                new FieldRule(ConfiguredItem.class, "ownerId", () -> DataIsolationScope.of(7L)),
                new FieldRule(BillingItem.class, "ownerId", () -> DataIsolationScope.of(8L))));
        SqlSessionFactory factory = factory(fields, BillingMapper.class);
        try (var session = factory.openSession()) {
            assertEquals(List.of(), session.getMapper(BillingMapper.class).selectListByQuery(QueryWrapper.create()));
        }
    }

    @Test
    void testInterfaceSetterAndConfiguredPropertyUseSameColumn() {
        FieldDataIsolationStrategy fields = FieldDataIsolationStrategy.withDefaults(List.of(
                new FieldRule(ConfiguredItem.class, "organizationId",
                        () -> DataIsolationScope.of(DataIsolationContextHolder.getWorkspaceId()), WORKSPACE)));
        SqlSessionFactory factory = factory(fields, ConfiguredMapper.class);
        DataIsolationContextHolder.runWithWorkspace(100L, () -> {
            try (var session = factory.openSession()) {
                ConfiguredMapper mapper = session.getMapper(ConfiguredMapper.class);
                ConfiguredItem item = new ConfiguredItem();
                item.setId(9L);
                item.setOrganizationId(999L);
                item.setOwnerId(7L);
                assertEquals(1, mapper.insert(item));
                assertEquals(100L, item.getOrganizationId());
                assertEquals(100L, mapper.selectOneById(9L).getOrganizationId());
                DataIsolationContextHolder.runWithWorkspace(200L, () -> assertNull(mapper.selectOneById(9L)));
                assertEquals(100L, mapper.selectOneById(9L).getOrganizationId());
            }
        });
    }

    private SqlSessionFactory factory(FieldDataIsolationStrategy fields, Class<?>... mapperTypes) {
        FlexConfiguration configuration = new FlexConfiguration(new Environment("field-configuration",
                new JdbcTransactionFactory(), new FlexDataSource("default", dataSource, false)));
        configuration.addInterceptor(new DataIsolationQueryInterceptor(List.of(fields)));
        for (Class<?> mapperType : mapperTypes) {
            configuration.addMapper(mapperType);
        }
        return new FlexSqlSessionFactoryBuilder().build(configuration);
    }

    private static List<Long> ids(ConfiguredMapper mapper) {
        return mapper.selectListByQuery(QueryWrapper.create().orderBy("id"))
                .stream().map(ConfiguredItem::getId).toList();
    }

    private static List<Long> standardIds(StandardMapper mapper) {
        return mapper.selectListByQuery(QueryWrapper.create().orderBy("id"))
                .stream().map(StandardFields::getId).toList();
    }

    @Getter
    @Setter
    @Table(value = "configured_workspace_item", onInsert = WorkspaceIsolationFieldInsertListener.class)
    static class ConfiguredItem implements WorkspaceIsolationObject<Long> {
        @Id
        private Long id;
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

    interface ConfiguredMapper extends BaseMapper<ConfiguredItem> {
    }

    @Getter
    @Setter
    static class StandardFields implements WorkspaceIsolationObject<Long> {
        @Id
        private Long id;
        @Column("workspace_key")
        private Long workspaceId;
    }

    @Table("standard_workspace_item")
    static class StandardItem extends StandardFields {
    }

    @Getter
    @Setter
    @Table("billing_workspace_item")
    static class BillingItem extends ConfiguredItem {
        private Long billingSpaceId;

        @Override
        public Long getWorkspaceId() {
            return billingSpaceId;
        }

        @Override
        public void setWorkspaceId(Long workspaceId) {
            billingSpaceId = workspaceId;
        }
    }

    interface PrimaryWorkspace extends WorkspaceIsolationObject<Long> {
    }

    interface SecondaryWorkspace extends WorkspaceIsolationObject<Long> {
    }

    interface NarrowWorkspace extends PrimaryWorkspace {
    }

    @Table("ambiguous_workspace_item")
    static class AmbiguousItem extends StandardFields implements PrimaryWorkspace, SecondaryWorkspace {
    }

    @Table("specific_workspace_item")
    static class SpecificInterfaceItem extends StandardFields implements NarrowWorkspace {
    }

    interface StandardMapper extends BaseMapper<StandardItem> {
    }

    interface BillingMapper extends BaseMapper<BillingItem> {
    }

    interface AmbiguousMapper extends BaseMapper<AmbiguousItem> {
    }

    interface SpecificInterfaceMapper extends BaseMapper<SpecificInterfaceItem> {
    }
}
