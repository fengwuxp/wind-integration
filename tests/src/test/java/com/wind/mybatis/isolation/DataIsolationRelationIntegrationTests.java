package com.wind.mybatis.isolation;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.BaseMapper;
import com.mybatisflex.core.datasource.FlexDataSource;
import com.mybatisflex.core.mybatis.FlexConfiguration;
import com.mybatisflex.core.mybatis.FlexSqlSessionFactoryBuilder;
import com.mybatisflex.core.query.QueryWrapper;
import com.wind.mybatis.plugin.DataIsolationQueryInterceptor;
import lombok.Getter;
import lombok.Setter;
import org.apache.ibatis.annotations.Param;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 通过真实 JDBC 验证策略关系谓词、内表约束和原参数的共同执行。
 * 范围值是可信测试输入，不模拟业务授权或部门树解析。
 */
class DataIsolationRelationIntegrationTests {

    private static final AtomicInteger DATABASE_SEQUENCE = new AtomicInteger();

    private DataSource dataSource;

    private long tenantId = 10L;

    private long selectedId = 1L;

    private long departmentId = 100L;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new UnpooledDataSource("org.h2.Driver", "jdbc:h2:mem:strategy_relation_"
                + DATABASE_SEQUENCE.incrementAndGet() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE strategy_root (id BIGINT PRIMARY KEY, tenant_id BIGINT)");
            statement.execute("INSERT INTO strategy_root VALUES (1,10),(2,10),(3,10),(4,20),(5,10),(6,10)");
            statement.execute("CREATE TABLE strategy_link (id BIGINT PRIMARY KEY, root_id BIGINT, department_id BIGINT, tenant_id BIGINT, workspace_id BIGINT)");
            statement.execute("INSERT INTO strategy_link VALUES (1,2,100,10,1000),(2,3,100,20,1000),(3,4,200,20,1000),(4,5,200,10,1000),(5,6,100,10,2000)");
            statement.execute("CREATE TABLE strategy_department (id BIGINT PRIMARY KEY, tenant_id BIGINT)");
            statement.execute("INSERT INTO strategy_department VALUES (100,10),(200,20)");
        }
    }

    @Test
    void testFieldAndRelationUnionConstrainGeneratedAndAnnotatedQueries() {
        SqlSessionFactory factory = factory();
        try (SqlSession session = factory.openSession()) {
            RootMapper mapper = session.getMapper(RootMapper.class);
            assertEquals(List.of(1L, 2L), mapper.ids(0L, 10));
            assertEquals(List.of(1L, 2L), mapper.selectListByQuery(QueryWrapper.create().orderBy("id"))
                    .stream().map(Root::getId).toList());
            assertEquals(2L, mapper.countRows());
            assertEquals(3L, mapper.sumIds());
            assertEquals(List.of(2L), mapper.ids(1L, 1));
        }
    }

    @Test
    void testRelationParametersAndHardBoundaryChangesDoNotReusePreviousRows() {
        selectedId = 0L;
        SqlSessionFactory factory = factory();
        try (SqlSession session = factory.openSession()) {
            RootMapper mapper = session.getMapper(RootMapper.class);
            assertEquals(List.of(2L), mapper.ids(0L, 10));
            departmentId = 200L;
            assertEquals(List.of(), mapper.ids(0L, 10));
            tenantId = 20L;
            assertEquals(List.of(4L), mapper.ids(0L, 10));
            tenantId = 10L;
            departmentId = 100L;
            assertEquals(List.of(2L), mapper.ids(0L, 10));
        }
    }

    @Test
    void testIndependentInnerPermissionIsNotResolvedForRelationFacts() {
        AtomicInteger resourceReads = new AtomicInteger();
        DataIsolationStrategy innerPermission = new DataIsolationStrategy() {
            @Override
            public boolean supports(EntityMetadata entity) {
                return entity.type() == Link.class || entity.type() == Department.class;
            }

            @Override
            public List<Condition> resolve(QueryContext context) {
                resourceReads.incrementAndGet();
                throw new IllegalStateException("独立资源范围供应者不应被关系查询调用");
            }

            @Override
            public List<Condition> resolveRelation(QueryContext context) {
                return List.of();
            }
        };
        try (SqlSession session = factory(innerPermission).openSession()) {
            assertEquals(List.of(1L, 2L), session.getMapper(RootMapper.class).ids(0L, 10));
            assertEquals(0, resourceReads.get());
        }
    }

    @Test
    void testUnknownInnerStrategyRejectsQuery() {
        DataIsolationStrategy undeclared = new DataIsolationStrategy() {
            @Override
            public boolean supports(EntityMetadata entity) {
                return entity.type() == Link.class;
            }

            @Override
            public List<Condition> resolve(QueryContext context) {
                return List.of();
            }
        };
        try (SqlSession session = factory(undeclared).openSession()) {
            assertRejected(() -> session.getMapper(RootMapper.class).ids(0L, 10), "does not support relation queries");
        }
    }

    @Test
    void testOriginalSqlDoesNotGainAccessToProtectedInnerTable() {
        try (SqlSession session = factory().openSession()) {
            assertRejected(() -> session.getMapper(RootMapper.class).originalExists(), "single-table SELECT only");
        }
    }

    private static void assertRejected(Runnable query, String diagnostic) {
        PersistenceException failure = assertThrows(PersistenceException.class, query::run);
        Throwable cause = failure;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        assertInstanceOf(IllegalStateException.class, cause);
        assertTrue(cause.getMessage().contains(diagnostic), cause.getMessage());
    }

    private SqlSessionFactory factory(DataIsolationStrategy... extra) {
        FieldDataIsolationStrategy fields = new FieldDataIsolationStrategy(List.of(
                new DataIsolationStrategy.FieldRule(Root.class, "tenantId", () -> DataIsolationScope.of(tenantId)),
                new DataIsolationStrategy.FieldRule(Link.class, "tenantId", () -> DataIsolationScope.of(tenantId)),
                new DataIsolationStrategy.FieldRule(Link.class, "workspaceId", () -> DataIsolationScope.of(1000L)),
                new DataIsolationStrategy.FieldRule(Department.class, "tenantId", () -> DataIsolationScope.of(tenantId))));
        DataIsolationStrategy rootPermission = new DataIsolationStrategy() {
            @Override
            public boolean supports(EntityMetadata entity) {
                return entity.type() == Root.class;
            }

            @Override
            public List<Condition> resolve(QueryContext context) {
                return List.of(Condition.of("(" + context.column("id") + " = ? OR EXISTS "
                        + "(SELECT 1 FROM strategy_link l INNER JOIN strategy_department d ON d.id = l.department_id "
                        + "WHERE l.root_id = " + context.column("id") + " AND d.id = ?))", selectedId, departmentId));
            }
        };
        List<DataIsolationStrategy> strategies = new ArrayList<>(List.of(fields, rootPermission));
        strategies.addAll(List.of(extra));
        FlexConfiguration configuration = new FlexConfiguration(new Environment("relation", new JdbcTransactionFactory(),
                new FlexDataSource("default", dataSource, false)));
        configuration.addInterceptor(new DataIsolationQueryInterceptor(strategies));
        configuration.addMapper(RootMapper.class);
        configuration.addMapper(LinkMapper.class);
        configuration.addMapper(DepartmentMapper.class);
        return new FlexSqlSessionFactoryBuilder().build(configuration);
    }

    @Getter
    @Setter
    @Table("strategy_root")
    public static class Root {
        @Id
        private Long id;
        private Long tenantId;
    }

    @Getter
    @Setter
    @Table("strategy_link")
    public static class Link {
        @Id
        private Long id;
        private Long rootId;
        private Long departmentId;
        private Long tenantId;
        private Long workspaceId;
    }

    @Getter
    @Setter
    @Table("strategy_department")
    public static class Department {
        @Id
        private Long id;
        private Long tenantId;
    }

    interface RootMapper extends BaseMapper<Root> {
        @Select("SELECT r.id FROM strategy_root r WHERE r.id > #{minimum} ORDER BY r.id LIMIT #{limit}")
        List<Long> ids(@Param("minimum") long minimum, @Param("limit") int limit);

        @Select("SELECT COUNT(*) FROM strategy_root r")
        long countRows();

        @Select("SELECT COALESCE(SUM(r.id), 0) FROM strategy_root r")
        long sumIds();

        @Select("SELECT r.id FROM strategy_root r WHERE EXISTS (SELECT 1 FROM strategy_link l WHERE l.root_id=r.id)")
        List<Long> originalExists();
    }

    interface LinkMapper extends BaseMapper<Link> {
    }

    interface DepartmentMapper extends BaseMapper<Department> {
    }
}
