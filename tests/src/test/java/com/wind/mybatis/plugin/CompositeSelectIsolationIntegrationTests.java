package com.wind.mybatis.plugin;

import com.mybatisflex.core.BaseMapper;
import com.wind.mybatis.isolation.DataIsolationScope;
import com.wind.mybatis.isolation.DataIsolationStrategy.FieldRule;
import com.wind.mybatis.isolation.FieldDataIsolationStrategy;
import org.apache.ibatis.annotations.CacheNamespace;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.exceptions.PersistenceException;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static com.wind.integration.core.enums.DataIsolationDimensions.TENANT;
import static com.wind.integration.core.enums.DataIsolationDimensions.WORKSPACE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CompositeSelectIsolationIntegrationTests {

    private static final AtomicInteger DATABASE_SEQUENCE = new AtomicInteger();

    private UnpooledDataSource dataSource;

    private SqlSessionFactory sessionFactory;

    private DataIsolationScope tenantScope;

    private DataIsolationScope workspaceScope;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new UnpooledDataSource("org.h2.Driver", "jdbc:h2:mem:composite_isolation_"
                + DATABASE_SEQUENCE.incrementAndGet() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        try (var connection = dataSource.getConnection(); var sql = connection.createStatement()) {
            sql.execute("CREATE TABLE coverage_tenant_record(id BIGINT PRIMARY KEY, tenant_id BIGINT)");
            sql.execute("INSERT INTO coverage_tenant_record VALUES(1,10),(2,20),(3,10),(4,10),(5,20)");
            sql.execute("CREATE TABLE composite_workspace_record(id BIGINT PRIMARY KEY, account_key BIGINT, workspace_key BIGINT)");
            sql.execute("INSERT INTO composite_workspace_record VALUES(101,10,100),(102,20,100),(103,10,200),(104,10,100)");
            sql.execute("CREATE TABLE coverage_plain_record(id BIGINT PRIMARY KEY, tenant_id BIGINT, owner_id BIGINT)");
            sql.execute("INSERT INTO coverage_plain_record VALUES(201,10,1),(202,20,2)");
        }
        tenantScope = DataIsolationScope.of(10L);
        workspaceScope = DataIsolationScope.of(100L);
        sessionFactory = factory(false);
    }

    /** 不同实体及别名的 UNION ALL 分支各自应用基础维度，不能把首分支的列映射复用给后分支。 */
    @Test
    void testUnionAllAppliesEachEntityStrategyIndependently() {
        try (var session = sessionFactory.openSession()) {
            ReadMapper mapper = session.getMapper(ReadMapper.class);
            assertEquals(List.of(1L, 3L, 4L, 101L, 104L), mapper.unionIds());
            tenantScope = DataIsolationScope.of(20L);
            assertEquals(List.of(2L, 5L, 102L), mapper.unionIds());
        }
    }

    /** UNION 保留去重，UNION ALL 保留重复行；两者都只能返回本租户数据。 */
    @Test
    void testUnionDistinctAndUnionAllKeepTheirDuplicateSemantics() {
        try (var session = sessionFactory.openSession()) {
            ReadMapper mapper = session.getMapper(ReadMapper.class);
            assertEquals(List.of(1L, 3L, 4L), mapper.distinctIds());
            assertEquals(List.of(1L, 3L, 3L, 4L, 4L), mapper.duplicateIds());
        }
    }

    /** 派生 COUNT 的内层不投出租户列，仍应在读取底表时隔离，外层只负责计数。 */
    @Test
    void testDerivedCountFiltersItsLeafBeforeAggregation() {
        try (var session = sessionFactory.openSession()) {
            ReadMapper mapper = session.getMapper(ReadMapper.class);
            assertEquals(3L, mapper.derivedCount());
            tenantScope = DataIsolationScope.of(20L);
            assertEquals(2L, mapper.derivedCount());
        }
    }

    /** 两层派生包装内的 UNION 先隔离和去重，再按外层业务条件计数。 */
    @Test
    void testNestedDerivedUnionCountKeepsFilteringAndDeduplication() {
        try (var session = sessionFactory.openSession()) {
            ReadMapper mapper = session.getMapper(ReadMapper.class);
            assertEquals(2L, mapper.nestedDistinctCount(2L));
            tenantScope = DataIsolationScope.of(20L);
            assertEquals(1L, mapper.nestedDistinctCount(2L));
        }
    }

    /** 原分支参数与外层过滤、排序、分页参数须按最终 SQL 顺序绑定，不能被新增隔离参数错位。 */
    @Test
    void testBranchAndOuterPageParametersKeepTheirPositions() {
        try (var session = sessionFactory.openSession()) {
            List<Long> ids = session.getMapper(ReadMapper.class).page(1L, 5L, 100L, 105L, 1L, 100L, 3, 1);

            assertEquals(List.of(104L, 1L, 3L), ids);
        }
    }

    /** foreach 与 bind 的附加参数必须跨 UNION 和分页包装保留。 */
    @Test
    void testCompositeDynamicParametersRemainBound() {
        try (var session = sessionFactory.openSession()) {
            List<Long> ids = session.getMapper(ReadMapper.class).dynamicPage(List.of(1L, 2L, 4L), 100L, 2, 1);

            assertEquals(List.of(4L, 101L), ids);
        }
    }

    /** 公共分支继续返回公共记录，受保护分支在旧模式和严格模式中均保持租户隔离。 */
    @Test
    void testMixedProtectedAndPublicBranches() {
        for (boolean strict : List.of(false, true)) {
            try (var session = factory(strict).openSession()) {
                ReadMapper mapper = session.getMapper(ReadMapper.class);
                tenantScope = DataIsolationScope.of(10L);
                assertEquals(List.of(1L, 3L, 4L, 201L, 202L), mapper.mixedIds());
                tenantScope = DataIsolationScope.of(20L);
                assertEquals(List.of(2L, 5L, 201L, 202L), mapper.mixedIds());
            }
        }
    }

    /** 同一会话及二级缓存中，组合查询必须按当前隔离范围区分结果。 */
    @Test
    void testCompositeQueryCachesFollowScopeChanges() {
        try (var session = sessionFactory.openSession()) {
            ReadMapper mapper = session.getMapper(ReadMapper.class);
            assertEquals(List.of(1L, 3L, 4L, 101L, 104L), mapper.unionIds());
            workspaceScope = DataIsolationScope.of(200L);
            assertEquals(List.of(1L, 3L, 4L, 103L), mapper.unionIds());
            tenantScope = DataIsolationScope.of(20L);
            assertEquals(List.of(2L, 5L), mapper.unionIds());
            tenantScope = DataIsolationScope.of(10L);
            workspaceScope = DataIsolationScope.of(100L);
            assertEquals(List.of(1L, 3L, 4L, 101L, 104L), mapper.unionIds());
            session.commit();
        }
        tenantScope = DataIsolationScope.of(20L);
        try (var session = sessionFactory.openSession()) {
            assertEquals(List.of(2L, 5L, 102L), session.getMapper(ReadMapper.class).unionIds());
        }
    }

    /** 空分支范围只清空该分支；范围缺失则拒绝整条查询，不能静默返回部分结果。 */
    @Test
    void testEmptyAndMissingBranchScopesKeepTheirDistinctSemantics() {
        try (var session = sessionFactory.openSession()) {
            ReadMapper mapper = session.getMapper(ReadMapper.class);
            workspaceScope = DataIsolationScope.ofValues(List.of());
            assertEquals(List.of(1L, 3L, 4L), mapper.unionIds());
            workspaceScope = null;
            PersistenceException failure = assertThrows(PersistenceException.class, mapper::unionIds);
            assertInstanceOf(IllegalStateException.class, rootCause(failure));
        }
    }

    /** 游标消费组合查询时仍执行每个叶查询的隔离。 */
    @Test
    void testDerivedUnionCursorUsesTheSameScope() throws Exception {
        try (var session = sessionFactory.openSession(); Cursor<Long> cursor = session.getMapper(ReadMapper.class).cursor()) {
            List<Long> ids = new ArrayList<>();
            cursor.forEach(ids::add);

            assertEquals(List.of(1L, 3L, 4L, 101L, 104L), ids);
        }
    }

    /** 已登记公共表的有限 EXISTS 可用于组合查询叶节点，根表仍保持自己的租户约束。 */
    @Test
    void testControlledExistsInsideDerivedUnionKeepsRelationScope() {
        try (var session = sessionFactory.openSession()) {
            ReadMapper mapper = session.getMapper(ReadMapper.class);
            assertEquals(List.of(1L, 201L, 202L), mapper.relatedIds());
            tenantScope = DataIsolationScope.of(20L);
            assertEquals(List.of(2L, 201L, 202L), mapper.relatedIds());
        }
    }

    /** 不支持的查询不能藏在包装或后续分支中；应由隔离器拒绝，而非靠数据库语法错误兜底。 */
    @ParameterizedTest
    @ValueSource(strings = {
            "SELECT q.id FROM (SELECT t.id FROM coverage_tenant_record t JOIN coverage_plain_record p ON t.id=p.id) q",
            "SELECT q.id FROM (SELECT id FROM coverage_tenant_record INTERSECT SELECT id FROM coverage_tenant_record) q",
            "SELECT q.id FROM (SELECT id FROM coverage_tenant_record EXCEPT SELECT id FROM coverage_tenant_record) q",
            "WITH hidden AS (SELECT id FROM coverage_tenant_record) SELECT q.id FROM (SELECT id FROM hidden) q",
            "SELECT (SELECT MAX(p.id) FROM coverage_plain_record p) FROM (SELECT id FROM coverage_tenant_record) q",
            "SELECT q.id FROM (SELECT id FROM coverage_tenant_record) q WHERE q.id IN (SELECT id FROM coverage_plain_record)",
            "SELECT id FROM coverage_tenant_record UNION ALL SELECT p.id FROM coverage_plain_record p WHERE p.id IN (SELECT id FROM coverage_tenant_record)",
            "SELECT q.id FROM (SELECT id FROM coverage_tenant_record UNION ALL SELECT (SELECT MAX(id) FROM coverage_plain_record) AS id FROM coverage_tenant_record) q"
    })
    void testUnsupportedChildQueriesAreRejected(String sql) {
        try (var session = sessionFactory.openSession()) {
            PersistenceException failure = assertThrows(PersistenceException.class,
                    () -> session.getMapper(ReadMapper.class).unsupported(sql));

            assertInstanceOf(IllegalStateException.class, rootCause(failure));
        }
    }

    private SqlSessionFactory factory(boolean strict) {
        FieldDataIsolationStrategy fields = new FieldDataIsolationStrategy(List.of(
                FieldRule.forDimension(TENANT, "tenantId", () -> tenantScope),
                FieldRule.forDimension(WORKSPACE, "workspaceId", () -> workspaceScope)));
        DataIsolationQueryInterceptor interceptor;
        if (strict) {
            interceptor = DataIsolationQueryInterceptor.strict(List.of(fields), Set.of(CoveragePlainRecord.class));
        } else {
            interceptor = new DataIsolationQueryInterceptor(List.of(fields));
        }
        Configuration configuration = new Configuration(new Environment("composite", new JdbcTransactionFactory(), dataSource));
        configuration.addInterceptor(interceptor);
        configuration.addMapper(TenantMapper.class);
        configuration.addMapper(WorkspaceMapper.class);
        configuration.addMapper(PublicMapper.class);
        configuration.addMapper(ReadMapper.class);
        return new SqlSessionFactoryBuilder().build(configuration);
    }

    private static Throwable rootCause(Throwable failure) {
        Throwable cause = failure;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }

    interface TenantMapper extends BaseMapper<CoverageTenantRecord> {
    }

    interface WorkspaceMapper extends BaseMapper<CompositeWorkspaceRecord> {
    }

    interface PublicMapper extends BaseMapper<CoveragePlainRecord> {
    }

    @CacheNamespace
    interface ReadMapper {

        @Select("SELECT t.id FROM coverage_tenant_record t UNION ALL SELECT w.id FROM composite_workspace_record w ORDER BY id")
        List<Long> unionIds();

        @Select("SELECT t.id FROM coverage_tenant_record t WHERE t.id <= 4 UNION SELECT r.id FROM coverage_tenant_record r WHERE r.id >= 3 ORDER BY id")
        List<Long> distinctIds();

        @Select("SELECT t.id FROM coverage_tenant_record t WHERE t.id <= 4 UNION ALL SELECT r.id FROM coverage_tenant_record r WHERE r.id >= 3 ORDER BY id")
        List<Long> duplicateIds();

        @Select("SELECT COUNT(*) FROM (SELECT id FROM coverage_tenant_record) counted")
        long derivedCount();

        @Select("SELECT COUNT(*) FROM (SELECT u.id FROM (SELECT id FROM coverage_tenant_record WHERE id <= 4 "
                + "UNION SELECT id FROM coverage_tenant_record WHERE id >= 3) u WHERE u.id > #{minimum}) counted")
        long nestedDistinctCount(@Param("minimum") long minimum);

        @Select("SELECT q.id FROM (SELECT t.id FROM coverage_tenant_record t WHERE t.id >= #{firstMinimum} AND t.id < #{firstMaximum} "
                + "UNION ALL SELECT w.id FROM composite_workspace_record w WHERE w.id >= #{secondMinimum} AND w.id < #{secondMaximum}) q "
                + "WHERE q.id >= #{outerMinimum} ORDER BY CASE WHEN q.id >= #{priority} THEN 0 ELSE 1 END, q.id "
                + "LIMIT #{pageSize} OFFSET #{offset}")
        List<Long> page(@Param("firstMinimum") long firstMinimum, @Param("firstMaximum") long firstMaximum,
                        @Param("secondMinimum") long secondMinimum, @Param("secondMaximum") long secondMaximum,
                        @Param("outerMinimum") long outerMinimum, @Param("priority") long priority,
                        @Param("pageSize") int pageSize, @Param("offset") int offset);

        @Select("<script><bind name='archiveFloor' value='minimumArchive'/> "
                + "SELECT q.id FROM (SELECT t.id FROM coverage_tenant_record t WHERE t.id IN "
                + "<foreach collection='ids' item='item' open='(' separator=',' close=')'>#{item}</foreach> "
                + "UNION ALL SELECT w.id FROM composite_workspace_record w WHERE w.id >= #{archiveFloor}) q "
                + "ORDER BY q.id LIMIT #{pageSize} OFFSET #{offset}</script>")
        List<Long> dynamicPage(@Param("ids") List<Long> ids, @Param("minimumArchive") long minimumArchive,
                               @Param("pageSize") int pageSize, @Param("offset") int offset);

        @Select("SELECT q.id FROM (SELECT t.id FROM coverage_tenant_record t UNION ALL SELECT p.id FROM coverage_plain_record p) q ORDER BY q.id")
        List<Long> mixedIds();

        @Select("SELECT q.id FROM (SELECT t.id FROM coverage_tenant_record t UNION ALL SELECT w.id FROM composite_workspace_record w) q ORDER BY q.id")
        Cursor<Long> cursor();

        @Select("SELECT q.id FROM (SELECT t.id FROM coverage_tenant_record t WHERE EXISTS "
                + "(SELECT 1 FROM coverage_plain_record r WHERE r.owner_id=t.id) "
                + "UNION ALL SELECT p.id FROM coverage_plain_record p) q ORDER BY q.id")
        List<Long> relatedIds();

        @Select("${sql}")
        List<Long> unsupported(@Param("sql") String sql);
    }
}
