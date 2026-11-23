package com.wind.mybatis.plugin;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.BaseMapper;
import com.mybatisflex.core.datasource.FlexDataSource;
import com.mybatisflex.core.mybatis.FlexConfiguration;
import com.mybatisflex.core.mybatis.FlexSqlSessionFactoryBuilder;
import com.mybatisflex.core.query.QueryWrapper;
import com.wind.integration.core.context.DataIsolationQueryIgnoreHolder;
import com.wind.integration.core.model.TenantIsolationObject;
import com.wind.mybatis.isolation.DataIsolationScope;
import com.wind.mybatis.isolation.DataIsolationStrategy.Condition;
import com.wind.mybatis.isolation.DataIsolationStrategy.FieldRule;
import com.wind.mybatis.isolation.FieldDataIsolationStrategy;
import com.wind.mybatis.isolation.FieldDataIsolationStrategy.ConditionGroup;
import com.wind.mybatis.isolation.FieldDataIsolationStrategy.EntityRules;
import com.wind.mybatis.isolation.FieldDataIsolationStrategy.FieldCondition;
import lombok.Getter;
import lombok.Setter;
import org.apache.ibatis.annotations.CacheNamespace;
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
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static com.wind.integration.core.enums.DataIsolationDimensions.TENANT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 通过真实 H2 和 Flex 查询验证字段分组、独立边界、关系与缓存，不模拟权限政策。
 *
 * @author wuxp
 * @since 2026-11-20
 */
class FieldDataIsolationStrategyGroupedIntegrationTests {

    private static final AtomicInteger DATABASE_SEQUENCE = new AtomicInteger();

    private static final Set<String> PROPERTIES = Set.of("id", "tenantId", "workspaceId", "owner", "groupId", "code");

    private DataSource source;

    private List<ConditionGroup> groups;

    @BeforeEach
    void setUp() throws Exception {
        source = new UnpooledDataSource("org.h2.Driver", "jdbc:h2:mem:field_groups_" + DATABASE_SEQUENCE.incrementAndGet()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        try (var connection = source.getConnection(); var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE grouped_record(id BIGINT PRIMARY KEY,tenant_id BIGINT,workspace_id BIGINT,owner_key VARCHAR(40),group_id BIGINT,code VARCHAR(40),active BOOLEAN)");
            statement.execute("INSERT INTO grouped_record VALUES (1,10,100,'alice',100,'001',TRUE),(2,10,100,'bob',200,'1',TRUE),"
                    + "(3,20,200,'alice',300,'001',TRUE),(4,10,200,'alice',200,'001',TRUE),(5,20,100,'bob',100,'001',TRUE),"
                    + "(6,10,100,NULL,NULL,'001',TRUE),(7,10,100,'alice',100,'001',FALSE)");
            statement.execute("CREATE TABLE grouped_link(id BIGINT PRIMARY KEY,root_id BIGINT,tenant_id BIGINT,group_id BIGINT)");
            statement.execute("INSERT INTO grouped_link VALUES (1,1,10,100),(2,1,20,200),(3,2,10,200),(4,3,20,200)");
        }
        groups = List.of(group(values("owner", "alice")));
    }

    @Test
    void testCompleteGroupsPreservePairsAndIndependentHardBoundary() throws Exception {
        groups = List.of(group(values("tenantId", 10L), values("workspaceId", 100L), values("owner", "alice")),
                group(values("tenantId", 20L), values("workspaceId", 200L), values("owner", "alice")));
        try (SqlSession session = factory(strategy(() -> groups)).openSession()) {
            RecordMapper mapper = session.getMapper(RecordMapper.class);
            assertEquals(List.of(1L), mapper.ids());
            DataIsolationQueryIgnoreHolder.callIgnoring(() -> {
                assertEquals(List.of(1L, 3L), mapper.ids());
                assertEquals(List.of(1L, 1L, 3L, 3L), mapper.unionIds());
                return null;
            }, TENANT);
            assertEquals(List.of(1L), mapper.ids());
        }
    }

    @Test
    void testRepeatedPropertyWithinAGroupIsIntersection() {
        groups = List.of(group(values("id", 1L, 2L), values("id", 2L, 4L)));
        try (SqlSession session = factory(strategy(() -> groups)).openSession()) {
            RecordMapper mapper = session.getMapper(RecordMapper.class);
            assertEquals(List.of(2L), mapper.ids());
            groups = List.of(group(values("id", 1L), values("id", 2L)));
            assertEquals(List.of(), mapper.ids());
        }
    }

    @Test
    void testEmptyGroupsEmptyValuesAndExplicitUnrestrictedRemainDistinct() {
        try (SqlSession session = factory(strategy(() -> groups)).openSession()) {
            RecordMapper mapper = session.getMapper(RecordMapper.class);
            groups = List.of();
            assertEquals(List.of(), mapper.ids());
            assertEquals(0L, mapper.countRows());
            groups = List.of(group(values("id")));
            assertEquals(List.of(), mapper.ids());
            groups = List.of(ConditionGroup.unrestricted());
            assertEquals(List.of(1L, 2L, 4L, 6L), mapper.ids());
            groups = List.of(group(FieldCondition.isNotNull("owner")));
            assertEquals(List.of(1L, 2L, 4L), mapper.ids());
        }
    }

    @Test
    void testListCountAggregatePaginationAndUnionUseTheSameGroups() {
        groups = List.of(group(values("id", 1L)), group(values("groupId", "200")));
        try (SqlSession session = factory(strategy(() -> groups)).openSession()) {
            RecordMapper mapper = session.getMapper(RecordMapper.class);
            assertEquals(List.of(1L, 2L, 4L), mapper.ids());
            assertEquals(3L, mapper.countRows());
            assertEquals(7L, mapper.sumIds());
            var page = mapper.paginate(2, 1, QueryWrapper.create().orderBy("id"));
            assertEquals(3L, page.getTotalRow());
            assertEquals(List.of(2L), page.getRecords().stream().map(Record::getId).toList());
            assertEquals(List.of(1L, 1L, 2L, 2L, 4L, 4L), mapper.unionIds());
        }
    }

    @Test
    void testCanonicalStringConversionPreservesReferenceIdentity() {
        try (SqlSession session = factory(strategy(() -> groups)).openSession()) {
            RecordMapper mapper = session.getMapper(RecordMapper.class);
            groups = List.of(group(values("id", "001")));
            assertRejected(mapper::ids, "reference");
            groups = List.of(group(values("id", "1")));
            assertEquals(List.of(1L), mapper.ids());
            groups = List.of(group(values("id", 1L)));
            assertEquals(List.of(1L), mapper.ids());
            groups = List.of(group(values("id", 1)));
            assertRejected(mapper::ids, "type");
            groups = List.of(group(values("code", "001")));
            assertEquals(List.of(1L, 4L, 6L), mapper.ids());
            groups = List.of(group(values("code", "1")));
            assertEquals(List.of(2L), mapper.ids());
            groups = List.of(group(values("owner", "alice' OR 1=1 --")));
            assertEquals(List.of(), mapper.ids());
        }
    }

    @Test
    void testUnrestrictedOrEmptyBranchCannotHideUnknownFieldsAndSourceFailures() {
        try (SqlSession session = factory(strategy(() -> groups)).openSession()) {
            RecordMapper mapper = session.getMapper(RecordMapper.class);
            groups = List.of(ConditionGroup.unrestricted(), group(values("active", true)));
            assertRejected(mapper::ids, "registered");
            groups = List.of(group(values("id"), values("unknown", 1L)));
            assertRejected(mapper::ids, "registered");
            groups = null;
            assertRejected(mapper::ids, "groups");
        }
        try (SqlSession session = factory(strategy(() -> { throw new IllegalStateException("source unavailable"); })).openSession()) {
            assertRejected(session.getMapper(RecordMapper.class)::ids, "source unavailable");
        }
    }

    @Test
    void testRelationUsesOneWitnessAndKeepsInnerBaseRulesWithoutIndependentGroups() {
        groups = List.of(new ConditionGroup(List.of(), List.of(query -> {
            String alias = "r".equals(query.qualifier()) ? "relation_row" : "r";
            return Condition.of("EXISTS (SELECT 1 FROM grouped_link " + alias + " WHERE " + alias + ".root_id = "
                    + query.column("id") + " AND " + alias + ".tenant_id = ? AND " + alias + ".group_id = ?)", 10L, 200L);
        })));
        EntityRules linkRules = new EntityRules(Link.class, Set.of("id"), () -> {
            throw new IllegalStateException("inner resource groups must not be evaluated");
        });
        List<FieldRule> base = new ArrayList<>(baseRules());
        base.add(new FieldRule(Link.class, "tenantId", () -> DataIsolationScope.of(10L)));
        FieldDataIsolationStrategy strategy = FieldDataIsolationStrategy.grouped(base,
                List.of(new EntityRules(Record.class, PROPERTIES, () -> groups), linkRules));
        try (SqlSession session = factory(strategy, LinkMapper.class).openSession()) {
            RecordMapper mapper = session.getMapper(RecordMapper.class);
            assertEquals(List.of(2L), mapper.ids());
            assertEquals(1L, mapper.countRows());
            assertEquals(List.of(2L, 2L), mapper.unionIds());
            assertThrows(PersistenceException.class, () -> session.getMapper(LinkMapper.class).selectListByQuery(QueryWrapper.create()));
        }
    }

    @Test
    void testNullTrustedConditionRejectsRatherThanDroppingItsBranch() {
        groups = List.of(group(values("id", 1L)), new ConditionGroup(List.of(), List.of(query -> null)));
        try (SqlSession session = factory(strategy(() -> groups)).openSession()) {
            assertRejected(session.getMapper(RecordMapper.class)::ids, "condition");
        }
    }

    @Test
    void testChangedGroupsSeparateLocalAndSecondLevelCacheRows() {
        SqlSessionFactory factory = factory(strategy(() -> groups));
        try (SqlSession session = factory.openSession()) {
            RecordMapper mapper = session.getMapper(RecordMapper.class);
            assertEquals(List.of(1L, 4L), mapper.ids());
            groups = List.of(group(values("owner", "bob")));
            assertEquals(List.of(2L), mapper.ids());
            session.commit();
        }
        groups = List.of(group(values("owner", "alice")));
        try (SqlSession session = factory.openSession()) {
            assertEquals(List.of(1L, 4L), session.getMapper(RecordMapper.class).ids());
        }
    }

    @Test
    void testGroupedOnlyMappingsCannotHideSameTableEntityConflicts() {
        EntityRules first = new EntityRules(Record.class, Set.of("id"), () -> List.of(group(values("id", 1L))));
        EntityRules second = new EntityRules(OtherRecord.class, Set.of("id"), () -> List.of(group(values("id", 2L))));
        FieldDataIsolationStrategy grouped = FieldDataIsolationStrategy.grouped(List.of(), List.of(first, second));
        try (SqlSession session = factory(grouped, OtherMapper.class).openSession()) {
            assertRejected(session.getMapper(RecordMapper.class)::ids, "Conflicting isolation mappings");
        }
    }

    @Test
    void testStaticValidationDoesNotReadGroupsAndRejectsUnknownMappedProperty() {
        FieldDataIsolationStrategy valid = strategy(() -> { throw new AssertionError("metadata must not read groups"); });
        SqlSessionFactory validFactory = factory(valid);
        DataIsolationQueryInterceptor validInterceptor = (DataIsolationQueryInterceptor) validFactory.getConfiguration().getInterceptors().getFirst();
        validInterceptor.validate(validFactory.getConfiguration(), Record.class);
        FieldDataIsolationStrategy invalid = FieldDataIsolationStrategy.grouped(List.of(),
                List.of(new EntityRules(Record.class, Set.of("missing"), List::of)));
        SqlSessionFactory invalidFactory = factory(invalid);
        DataIsolationQueryInterceptor invalidInterceptor = (DataIsolationQueryInterceptor) invalidFactory.getConfiguration().getInterceptors().getFirst();
        assertThrows(IllegalArgumentException.class, () -> invalidInterceptor.validate(invalidFactory.getConfiguration(), Record.class));
    }

    private FieldDataIsolationStrategy strategy(Supplier<List<ConditionGroup>> supplier) {
        return FieldDataIsolationStrategy.grouped(baseRules(), List.of(new EntityRules(Record.class, PROPERTIES, supplier)));
    }

    private List<FieldRule> baseRules() {
        return List.of(new FieldRule(Record.class, "tenantId", () -> DataIsolationScope.of(10L), TENANT),
                new FieldRule(Record.class, "active", () -> DataIsolationScope.of(true)));
    }

    private SqlSessionFactory factory(FieldDataIsolationStrategy fields, Class<?>... extraMappers) {
        FlexConfiguration configuration = new FlexConfiguration(new Environment("field-groups", new JdbcTransactionFactory(),
                new FlexDataSource("default", source, false)));
        configuration.addInterceptor(new DataIsolationQueryInterceptor(List.of(fields)));
        configuration.addMapper(RecordMapper.class);
        for (Class<?> mapper : extraMappers) {
            configuration.addMapper(mapper);
        }
        return new FlexSqlSessionFactoryBuilder().build(configuration);
    }

    private static ConditionGroup group(FieldCondition... fields) {
        return new ConditionGroup(List.of(fields), List.of());
    }

    private static FieldCondition values(String property, Object... values) {
        return FieldCondition.ofValues(property, List.of(values));
    }

    private static void assertRejected(Runnable query, String diagnostic) {
        PersistenceException failure = assertThrows(PersistenceException.class, query::run);
        StringBuilder messages = new StringBuilder();
        for (Throwable current = failure; current != null; current = current.getCause()) {
            messages.append(current.getMessage());
        }
        assertTrue(messages.toString().contains(diagnostic), messages::toString);
    }

    @Getter
    @Setter
    @Table("grouped_record")
    static class Record implements TenantIsolationObject<Long> {
        @Id
        private Long id;
        private Long tenantId;
        private Long workspaceId;
        @Column("owner_key")
        private String owner;
        private Long groupId;
        private String code;
        private boolean active;
    }

    @Getter
    @Setter
    @Table("grouped_record")
    static class OtherRecord {
        @Id
        private Long id;
    }

    @Getter
    @Setter
    @Table("grouped_link")
    static class Link {
        @Id
        private Long id;
        private Long rootId;
        private Long tenantId;
        private Long groupId;
    }

    @CacheNamespace(readWrite = false)
    interface RecordMapper extends BaseMapper<Record> {
        @Select("SELECT d.id FROM grouped_record d ORDER BY d.id")
        List<Long> ids();

        @Select("SELECT d.id FROM grouped_record d UNION ALL SELECT r.id FROM grouped_record r ORDER BY id")
        List<Long> unionIds();

        @Select("SELECT COUNT(*) FROM grouped_record d")
        long countRows();

        @Select("SELECT COALESCE(SUM(d.id),0) FROM grouped_record d")
        long sumIds();
    }

    interface LinkMapper extends BaseMapper<Link> {
    }

    interface OtherMapper extends BaseMapper<OtherRecord> {
    }
}
