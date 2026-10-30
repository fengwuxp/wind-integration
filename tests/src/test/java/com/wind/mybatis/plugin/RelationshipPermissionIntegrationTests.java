package com.wind.mybatis.plugin;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.BaseMapper;
import com.mybatisflex.core.datasource.FlexDataSource;
import com.mybatisflex.core.mybatis.FlexConfiguration;
import com.mybatisflex.core.mybatis.FlexSqlSessionFactoryBuilder;
import com.mybatisflex.core.query.CPI;
import com.mybatisflex.core.query.QueryWrapper;
import com.wind.integration.core.context.DataIsolationContextHolder;
import com.wind.integration.core.model.TenantIsolationObject;
import com.wind.integration.core.model.WorkspaceIsolationObject;
import lombok.Getter;
import lombok.Setter;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.exceptions.PersistenceException;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.LocalCacheScope;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static com.wind.integration.core.enums.DataIsolationDimensions.TENANT;
import static com.wind.integration.core.enums.DataIsolationDimensions.WORKSPACE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RelationshipPermissionIntegrationTests {

    private static final AtomicInteger DATABASE_SEQUENCE = new AtomicInteger();

    private SqlSessionFactory sessionFactory;

    private final AtomicInteger queries = new AtomicInteger();

    @BeforeEach
    void setUp() throws Exception {
        UnpooledDataSource source = new UnpooledDataSource("org.h2.Driver",
                "jdbc:h2:mem:relationship_permission_" + DATABASE_SEQUENCE.incrementAndGet() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        try (var connection = source.getConnection(); var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE scope_member (id BIGINT PRIMARY KEY, tenant_id BIGINT, workspace_id VARCHAR(32))");
            statement.execute("INSERT INTO scope_member VALUES (1,10,'a'),(2,10,'a'),(3,10,'b'),(4,20,'a'),(5,10,'a')");
            statement.execute("CREATE TABLE scope_relation (id BIGINT PRIMARY KEY, member_id BIGINT, department VARCHAR(32), workspace_id VARCHAR(32), tenant_id BIGINT)");
            statement.execute("INSERT INTO scope_relation VALUES (1,2,'alpha','a',10),(2,3,'alpha','b',10),(3,4,'alpha','a',20),(4,5,'beta','a',10),(5,5,'alpha','b',10),(6,5,'alpha','a',20)");
            statement.execute("CREATE TABLE scope_department (id BIGINT PRIMARY KEY, code VARCHAR(32), tenant_id BIGINT, workspace_id VARCHAR(32), node_path VARCHAR(128))");
            statement.execute("INSERT INTO scope_department VALUES (1,'alpha',10,'a','/root/2/'),(2,'beta',10,'a','/root/2/leaf/'),(3,'alpha',10,'b','/root/2/'),(4,'alpha',20,'a','/root/2/'),(5,'beta',10,'b','/root/2/leaf/')");
        }
        FlexConfiguration configuration = new FlexConfiguration(new Environment("scope", new JdbcTransactionFactory(),
                new FlexDataSource("default", source, false)));
        configuration.setLocalCacheScope(LocalCacheScope.STATEMENT);
        configuration.setCacheEnabled(false);
        configuration.addInterceptor(new DataIsolationQueryInterceptor());
        configuration.addInterceptor(new QueryCounter(queries));
        configuration.addMapper(MemberMapper.class);
        configuration.addMapper(RelationMapper.class);
        configuration.addMapper(DepartmentMapper.class);
        sessionFactory = new FlexSqlSessionFactoryBuilder().build(configuration);
    }

    @Test
    void testScopeUnionAndBaseIsolationApplyToListCountPageAndDetail() {
        inContext(10L, "a", () -> {
            try (SqlSession session = sessionFactory.openSession()) {
                MemberMapper mapper = session.getMapper(MemberMapper.class);
                assertEquals(List.of(1L, 2L), ids(mapper, scope(1L, "alpha")));
                assertEquals(2L, mapper.selectCountByQuery(scope(1L, "alpha")));
                assertEquals(List.of(2L), ids(mapper, scope(1L, "alpha").limit(1, 1)));
                assertEquals(2L, mapper.selectOneByQuery(scope(1L, "alpha").and("scope_member.id = ?", 2L)).getId());
                assertNull(mapper.selectOneByQuery(scope(1L, "alpha").and("scope_member.id = ?", 3L)));
                assertNull(mapper.selectOneByQuery(scope(1L, "alpha").and("scope_member.id = ?", 4L)));
            }
        });
    }

    @Test
    void testUnrestrictedAndEmptyBusinessScopeRetainBaseIsolation() {
        inContext(10L, "a", () -> {
            try (SqlSession session = sessionFactory.openSession()) {
                MemberMapper mapper = session.getMapper(MemberMapper.class);
                assertEquals(List.of(1L, 2L, 5L), ids(mapper, QueryWrapper.create()));
                assertEquals(List.of(), ids(mapper, QueryWrapper.create().where("1 = 0")));
            }
        });
    }

    @Test
    void testPermissionParametersAndContextDoNotShareResultsWithinSession() {
        try (SqlSession session = sessionFactory.openSession()) {
            MemberMapper mapper = session.getMapper(MemberMapper.class);
            inContext(10L, "a", () -> {
                assertEquals(List.of(1L, 2L), ids(mapper, scope(1L, "alpha")));
                assertEquals(List.of(1L, 5L), ids(mapper, scope(1L, "beta")));
            });
            inContext(10L, "b", () -> assertEquals(List.of(3L), ids(mapper, scope(1L, "alpha"))));
            inContext(20L, "a", () -> assertEquals(List.of(4L), ids(mapper, scope(1L, "alpha"))));
        }
    }

    @Test
    void testPageConditionsWithOuterOrCannotBypassBaseIsolation() {
        inContext(10L, "a", () -> {
            try (SqlSession session = sessionFactory.openSession()) {
                QueryWrapper query = QueryWrapper.create().where("scope_member.id = ? OR scope_member.id = ? "
                        + "OR EXISTS (SELECT 1 FROM scope_relation r WHERE r.member_id = scope_member.id "
                        + "AND r.department = ? AND r.workspace_id = ? AND r.tenant_id = ?)", 1L, 3L, "alpha", "a", 10L);
                assertEquals(List.of(1L, 2L), ids(session.getMapper(MemberMapper.class), query));
            }
        });
    }

    @Test
    void testFailedBusinessResolutionDoesNotExecuteQueryOrReturnEmpty() {
        inContext(10L, "a", () -> {
            try (SqlSession session = sessionFactory.openSession()) {
                MemberMapper mapper = session.getMapper(MemberMapper.class);
                IllegalStateException error = assertThrows(IllegalStateException.class, () -> queryWithScope(mapper, () -> {
                    throw new IllegalStateException("业务权限解析失败");
                }));
                assertEquals("业务权限解析失败", error.getMessage());
                assertEquals(0, queries.get());
            }
        });
    }

    @Test
    void testRelationChangesAreVisibleWithStatementCache() throws Exception {
        try (SqlSession session = sessionFactory.openSession()) {
            MemberMapper mapper = session.getMapper(MemberMapper.class);
            inContext(10L, "a", () -> assertEquals(List.of(1L, 2L), ids(mapper, scope(1L, "alpha"))));
            try (var connection = sessionFactory.getConfiguration().getEnvironment().getDataSource().getConnection();
                 var statement = connection.createStatement()) {
                statement.execute("UPDATE scope_relation SET department='beta' WHERE member_id=2");
            }
            inContext(10L, "a", () -> assertEquals(List.of(1L), ids(mapper, scope(1L, "alpha"))));
        }
    }

    @Test
    void testDepartmentNodeAndSubtreeRemainWithinTenantAndWorkspace() {
        try (SqlSession session = sessionFactory.openSession()) {
            MemberMapper mapper = session.getMapper(MemberMapper.class);
            inContext(10L, "a", () -> {
                assertEquals(List.of(2L), ids(mapper, departmentNode(10L, "a", "alpha")));
                assertEquals(List.of(2L, 5L), ids(mapper, departmentSubtree(10L, "a", "/root/2/")));
                assertEquals(2L, mapper.selectCountByQuery(departmentSubtree(10L, "a", "/root/2/")));
                assertEquals(List.of(5L), ids(mapper, departmentSubtree(10L, "a", "/root/2/").limit(1, 1)));
                assertNull(mapper.selectOneByQuery(departmentSubtree(10L, "a", "/root/2/").and("scope_member.id = ?", 3L)));
                assertNull(mapper.selectOneByQuery(departmentSubtree(10L, "a", "/root/2/").and("scope_member.id = ?", 4L)));
            });
            inContext(10L, "b", () -> assertEquals(List.of(3L), ids(mapper, departmentNode(10L, "b", "alpha"))));
            inContext(20L, "a", () -> assertEquals(List.of(4L), ids(mapper, departmentNode(20L, "a", "alpha"))));
        }
    }

    @Test
    void testDepartmentSubtreeUsesCurrentPathAndExcludesSibling() throws Exception {
        try (SqlSession session = sessionFactory.openSession()) {
            MemberMapper mapper = session.getMapper(MemberMapper.class);
            inContext(10L, "a", () -> assertEquals(List.of(2L, 5L), ids(mapper, departmentSubtree(10L, "a", "/root/2/"))));
            try (var connection = sessionFactory.getConfiguration().getEnvironment().getDataSource().getConnection();
                 var statement = connection.createStatement()) {
                statement.execute("UPDATE scope_department SET node_path='/root/20/leaf/' WHERE id=2");
            }
            inContext(10L, "a", () -> assertEquals(List.of(2L), ids(mapper, departmentSubtree(10L, "a", "/root/2/"))));
        }
    }

    @Test
    void testSubtreeResolvesCurrentRootPathAfterDepartmentMove() throws Exception {
        try (SqlSession session = sessionFactory.openSession()) {
            MemberMapper members = session.getMapper(MemberMapper.class);
            DepartmentMapper departments = session.getMapper(DepartmentMapper.class);
            inContext(10L, "a", () -> assertEquals(List.of(2L, 5L), ids(members, departmentSubtree(10L, "a", currentPath(departments)))));
            try (var connection = sessionFactory.getConfiguration().getEnvironment().getDataSource().getConnection();
                 var statement = connection.createStatement()) {
                statement.execute("UPDATE scope_department SET node_path='/new/2/' WHERE id=1");
                statement.execute("UPDATE scope_department SET node_path='/new/2/leaf/' WHERE id=2");
            }
            inContext(10L, "a", () -> assertEquals(List.of(2L, 5L), ids(members, departmentSubtree(10L, "a", currentPath(departments)))));
        }
    }

    private static String currentPath(DepartmentMapper departments) {
        return departments.selectOneByQuery(QueryWrapper.create().where("id = ? AND tenant_id = ? AND workspace_id = ?", 1L, 10L, "a")).getNodePath();
    }

    @Test
    void testSubtreePathEscapesLikeWildcards() throws Exception {
        try (var connection = sessionFactory.getConfiguration().getEnvironment().getDataSource().getConnection();
             var statement = connection.createStatement()) {
            statement.execute("UPDATE scope_department SET node_path='/root/a_b/' WHERE id=1");
            statement.execute("UPDATE scope_department SET node_path='/root/axb/leaf/' WHERE id=2");
        }
        inContext(10L, "a", () -> {
            try (SqlSession session = sessionFactory.openSession()) {
                assertEquals(List.of(2L), ids(session.getMapper(MemberMapper.class), departmentSubtree(10L, "a", "/root/a_b/")));
            }
        });
    }

    @Test
    void testJoinOnAndWhereParametersKeepTheirOriginalOrder() {
        inContext(10L, "a", () -> {
            try (SqlSession session = sessionFactory.openSession()) {
                QueryWrapper query = departmentNode(10L, "a", "alpha").and("scope_member.id > ?", 1L);
                assertEquals(List.of(2L), ids(session.getMapper(MemberMapper.class), query));
                assertThrows(IllegalStateException.class, () -> departmentNode(20L, "a", "alpha"));
            }
        });
    }

    @Test
    void testOtherNestedQueriesRemainRejected() {
        inContext(10L, "a", () -> {
            try (SqlSession session = sessionFactory.openSession()) {
                MemberMapper mapper = session.getMapper(MemberMapper.class);
                assertThrows(PersistenceException.class, mapper::nestedProtectedTable);
                assertThrows(PersistenceException.class, mapper::unregisteredRelation);
            }
        });
    }

    @Test
    void testUnifiedResourceQueriesApplyEveryScopeToListCountDetailAndExport() {
        inContext(10L, "a", () -> {
            try (SqlSession session = sessionFactory.openSession()) {
                MemberResource resource = new MemberResource(session.getMapper(MemberMapper.class));
                assertResourceResults(resource, new ResourceScope(ScopeKind.UNBOUND, 10L, "a", null, null), List.of(1L, 2L, 5L));
                assertResourceResults(resource, new ResourceScope(ScopeKind.ALL, 10L, "a", null, null), List.of(1L, 2L, 5L));
                assertResourceResults(resource, new ResourceScope(ScopeKind.ID, 10L, "a", 1L, null), List.of(1L));
                assertResourceResults(resource, new ResourceScope(ScopeKind.NODE, 10L, "a", null, "alpha"), List.of(2L));
                assertResourceResults(resource, new ResourceScope(ScopeKind.SUBTREE, 10L, "a", null, "/root/2/"), List.of(2L, 5L));
                assertResourceResults(resource, new ResourceScope(ScopeKind.NODE, 10L, "a", 1L, "alpha"), List.of(1L, 2L));
                assertResourceResults(resource, new ResourceScope(ScopeKind.SUBTREE, 10L, "a", 1L, "/root/2/"), List.of(1L, 2L, 5L));
                assertResourceResults(resource, new ResourceScope(ScopeKind.EMPTY, 10L, "a", null, null), List.of());
            }
        });
    }

    @Test
    void testMultipleScopesUnionAndAllAbsorbsNarrowScopes() {
        inContext(10L, "a", () -> {
            try (SqlSession session = sessionFactory.openSession()) {
                MemberResource resource = new MemberResource(session.getMapper(MemberMapper.class));
                ResourceScope first = new ResourceScope(ScopeKind.ID, 10L, "a", 1L, null);
                ResourceScope second = new ResourceScope(ScopeKind.ID, 10L, "a", 5L, null);
                ResourceScope department = new ResourceScope(ScopeKind.NODE, 10L, "a", null, "alpha");
                ResourceScope otherDepartment = new ResourceScope(ScopeKind.NODE, 10L, "a", null, "beta");
                assertEquals(List.of(1L, 2L, 5L), resource.listUnion(List.of(first, second, department, otherDepartment)));
                assertEquals(List.of(1L, 2L, 5L), resource.listUnion(List.of(department, new ResourceScope(ScopeKind.ALL, 10L, "a", null, null))));
                assertThrows(IllegalStateException.class, () -> resource.listUnion(List.of(
                        new ResourceScope(ScopeKind.ALL, 10L, "a", null, null), new ResourceScope(ScopeKind.DENY, 10L, "a", null, null))));
                assertThrows(IllegalStateException.class, () -> resource.listUnion(List.of(
                        new ResourceScope(ScopeKind.UNBOUND, 10L, "a", null, null), first)));
                assertThrows(IllegalStateException.class, () -> resource.list(new ResourceScope(ScopeKind.ALL, 10L, "a", 1L, null)));
                assertThrows(IllegalStateException.class, () -> resource.list(new ResourceScope(ScopeKind.ALL, 10L, "a", null, "alpha")));
                assertEquals(List.of(1L, 2L, 5L), resource.listUnion(List.of(new ResourceScope(ScopeKind.UNBOUND, 10L, "a", null, null))));
                assertThrows(IllegalStateException.class, () -> resource.list(new ResourceScope(ScopeKind.ID, 10L, "a", null, null)));
                assertThrows(IllegalStateException.class, () -> resource.list(new ResourceScope(ScopeKind.NODE, 10L, "a", null, null)));
            }
        });
    }

    @Test
    void testUnifiedResourceQueriesRejectDeniedMissingOrWrongContext() {
        try (SqlSession session = sessionFactory.openSession()) {
            MemberResource resource = new MemberResource(session.getMapper(MemberMapper.class));
            inContext(10L, "a", () -> {
                assertThrows(IllegalStateException.class, () -> resource.list(null));
                assertThrows(IllegalStateException.class, () -> resource.count(new ResourceScope(ScopeKind.DENY, 10L, "a", null, null)));
                assertThrows(IllegalStateException.class, () -> resource.detail(new ResourceScope(ScopeKind.DENY, 10L, "a", null, null), 1L));
                assertThrows(IllegalStateException.class, () -> resource.export(new ResourceScope(ScopeKind.DENY, 10L, "a", null, null)));
                assertEquals(0, queries.get());
            });
            ResourceScope previousTeam = new ResourceScope(ScopeKind.ALL, 10L, "a", null, null);
            assertThrows(IllegalStateException.class, () -> resource.list(previousTeam));
            DataIsolationContextHolder.runWith(Map.of(TENANT, 10L),
                    () -> assertThrows(IllegalStateException.class, () -> resource.list(previousTeam)));
            DataIsolationContextHolder.runWith(Map.of(WORKSPACE, "a"),
                    () -> assertThrows(IllegalStateException.class, () -> resource.list(previousTeam)));
            inContext(10L, "b", () -> {
                assertThrows(IllegalStateException.class, () -> resource.list(previousTeam));
                assertResourceResults(resource, new ResourceScope(ScopeKind.ALL, 10L, "b", null, null), List.of(3L));
            });
            inContext(20L, "a", () -> assertThrows(IllegalStateException.class, () -> resource.list(previousTeam)));
            inContext(10L, "a", () -> {
                MemberResource anotherActor = new MemberResource(session.getMapper(MemberMapper.class), "other");
                assertThrows(IllegalStateException.class, () -> anotherActor.list(previousTeam));
                assertEquals(List.of(2L), anotherActor.list(new ResourceScope(ScopeKind.NODE, 10L, "a", null, "alpha", "other")));
            });
        }
    }

    private static void assertResourceResults(MemberResource resource, ResourceScope scope, List<Long> expected) {
        assertEquals(expected, resource.list(scope));
        assertEquals((long) expected.size(), resource.count(scope));
        assertEquals(expected, resource.export(scope));
        for (long id = 1; id <= 5; id++) {
            Member member = resource.detail(scope, id);
            assertEquals(expected.contains(id) ? id : null, member == null ? null : member.getId());
        }
    }

    private enum ScopeKind {
        UNBOUND, ALL, ID, NODE, SUBTREE, EMPTY, DENY
    }

    private record ResourceScope(ScopeKind kind, long tenantId, String workspaceId, Long memberId, String department, String actor) {
        private ResourceScope(ScopeKind kind, long tenantId, String workspaceId, Long memberId, String department) {
            this(kind, tenantId, workspaceId, memberId, department, "actor");
        }
    }

    private static final class MemberResource {
        private final MemberMapper mapper;

        private final String actor;

        private MemberResource(MemberMapper mapper) {
            this(mapper, "actor");
        }

        private MemberResource(MemberMapper mapper, String actor) {
            this.mapper = mapper;
            this.actor = actor;
        }

        private List<Long> list(ResourceScope scope) {
            return ids(mapper, query(scope));
        }

        private List<Long> listUnion(List<ResourceScope> scopes) {
            if (scopes.isEmpty()) {
                throw new IllegalStateException("数据范围不可用");
            }
            List<QueryWrapper> conditions = scopes.stream().map(this::query).toList();
            if (scopes.stream().anyMatch(scope -> scope.kind() == ScopeKind.UNBOUND)) {
                if (scopes.size() != 1) {
                    throw new IllegalStateException("无绑定不能与其他范围合并");
                }
                return ids(mapper, conditions.getFirst());
            }
            if (scopes.stream().anyMatch(scope -> scope.kind() == ScopeKind.ALL)) {
                return ids(mapper, QueryWrapper.create());
            }
            QueryWrapper combined = QueryWrapper.create().where(group -> {
                conditions.forEach(condition -> group.or(CPI.getWhereQueryCondition(condition)));
            });
            return ids(mapper, combined);
        }

        private long count(ResourceScope scope) {
            return mapper.selectCountByQuery(query(scope));
        }

        private Member detail(ResourceScope scope, long id) {
            return mapper.selectOneByQuery(query(scope).and("scope_member.id = ?", id));
        }

        private List<Long> export(ResourceScope scope) {
            return ids(mapper, query(scope));
        }

        private QueryWrapper query(ResourceScope scope) {
            if (scope == null || scope.kind() == ScopeKind.DENY) {
                throw new IllegalStateException("数据范围不可用");
            }
            if (!actor.equals(scope.actor()) || scope.kind() == ScopeKind.ID && scope.memberId() == null
                    || scope.kind() == ScopeKind.ALL && (scope.memberId() != null || scope.department() != null)
                    || (scope.kind() == ScopeKind.NODE || scope.kind() == ScopeKind.SUBTREE) && scope.department() == null) {
                throw new IllegalStateException("数据范围无效");
            }
            if (!Long.valueOf(scope.tenantId()).equals(DataIsolationContextHolder.getTenantId())
                    || !scope.workspaceId().equals(DataIsolationContextHolder.getWorkspaceId())) {
                throw new IllegalStateException("数据范围与当前上下文不一致");
            }
            return switch (scope.kind()) {
                case UNBOUND, ALL -> QueryWrapper.create();
                case EMPTY -> QueryWrapper.create().where("1 = 0");
                case ID -> QueryWrapper.create().where("scope_member.id = ?", scope.memberId());
                case NODE, SUBTREE -> {
                    QueryWrapper department = scope.kind() == ScopeKind.NODE
                            ? departmentNode(scope.tenantId(), scope.workspaceId(), scope.department())
                            : departmentSubtree(scope.tenantId(), scope.workspaceId(), scope.department());
                    if (scope.memberId() == null) {
                        yield department;
                    }
                    yield QueryWrapper.create().where(group -> {
                        group.where(CPI.getWhereQueryCondition(department)).or("scope_member.id = ?", scope.memberId());
                    });
                }
                case DENY -> throw new IllegalStateException("数据范围不可用");
            };
        }
    }

    private static void inContext(long tenantId, String workspaceId, Runnable action) {
        DataIsolationContextHolder.runWith(Map.of(TENANT, tenantId, WORKSPACE, workspaceId), action);
    }

    private static QueryWrapper scope(long memberId, String department) {
        return QueryWrapper.create().where("(scope_member.id = ? OR EXISTS (SELECT 1 FROM scope_relation r "
                + "WHERE r.member_id = scope_member.id AND r.department = ? AND r.workspace_id = ? AND r.tenant_id = ?))",
                memberId, department, DataIsolationContextHolder.requireWorkspaceId(), DataIsolationContextHolder.getTenantId());
    }

    private static QueryWrapper departmentNode(long tenantId, String workspaceId, String department) {
        requireMatchingContext(tenantId, workspaceId);
        return QueryWrapper.create().where("EXISTS (SELECT 1 FROM scope_relation r INNER JOIN scope_department d "
                + "ON d.code = r.department AND d.tenant_id = ? AND d.workspace_id = ? "
                + "WHERE r.member_id = scope_member.id AND r.tenant_id = ? AND r.workspace_id = ? AND d.code = ?)",
                tenantId, workspaceId, tenantId, workspaceId, department);
    }

    private static QueryWrapper departmentSubtree(long tenantId, String workspaceId, String path) {
        requireMatchingContext(tenantId, workspaceId);
        return QueryWrapper.create().where("EXISTS (SELECT 1 FROM scope_relation r INNER JOIN scope_department d "
                + "ON d.code = r.department AND d.tenant_id = ? AND d.workspace_id = ? "
                + "WHERE r.member_id = scope_member.id AND r.tenant_id = ? AND r.workspace_id = ? "
                + "AND (d.node_path = ? OR d.node_path LIKE ? ESCAPE '!'))", tenantId, workspaceId, tenantId, workspaceId,
                path, path.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%");
    }

    private static void requireMatchingContext(long tenantId, String workspaceId) {
        if (!Long.valueOf(tenantId).equals(DataIsolationContextHolder.getTenantId())
                || !workspaceId.equals(DataIsolationContextHolder.getWorkspaceId())) {
            throw new IllegalStateException("数据范围与当前上下文不一致");
        }
    }

    private static List<Long> queryWithScope(MemberMapper mapper, Supplier<QueryWrapper> resolver) {
        return ids(mapper, resolver.get());
    }

    private static List<Long> ids(MemberMapper mapper, QueryWrapper query) {
        return mapper.selectListByQuery(query.orderBy("id")).stream().map(Member::getId).toList();
    }

    @Getter
    @Setter
    @Table("scope_member")
    static class Member implements TenantIsolationObject<Long>, WorkspaceIsolationObject<String> {
        @Id
        private Long id;
        private Long tenantId;
        private String workspaceId;
    }

    @Getter
    @Setter
    @Table("scope_relation")
    static class Relation {
        @Id
        private Long id;
        private Long memberId;
        private String department;
        private String workspaceId;
        private Long tenantId;
    }

    interface MemberMapper extends BaseMapper<Member> {
        @Select("SELECT id FROM scope_member WHERE EXISTS (SELECT 1 FROM scope_member nested WHERE nested.id = scope_member.id)")
        List<Long> nestedProtectedTable();

        @Select("SELECT id FROM scope_member WHERE EXISTS (SELECT 1 FROM unknown_relation r WHERE r.member_id = scope_member.id)")
        List<Long> unregisteredRelation();
    }

    interface RelationMapper extends BaseMapper<Relation> {
    }

    @Getter
    @Setter
    @Table("scope_department")
    static class Department {
        @Id
        private Long id;
        private String code;
        private Long tenantId;
        private String workspaceId;
        private String nodePath;
    }

    interface DepartmentMapper extends BaseMapper<Department> {
    }

    @Intercepts(@Signature(type = Executor.class, method = "query", args = {
            MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class
    }))
    static class QueryCounter implements Interceptor {
        private final AtomicInteger queries;

        QueryCounter(AtomicInteger queries) {
            this.queries = queries;
        }

        @Override
        public Object intercept(Invocation invocation) throws Throwable {
            queries.incrementAndGet();
            return invocation.proceed();
        }
    }
}
