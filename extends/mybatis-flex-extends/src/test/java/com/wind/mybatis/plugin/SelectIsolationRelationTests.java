package com.wind.mybatis.plugin;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.BaseMapper;
import com.wind.mybatis.isolation.DataIsolationScope;
import com.wind.mybatis.isolation.DataIsolationStrategy;
import com.wind.mybatis.isolation.FieldDataIsolationStrategy;
import org.apache.ibatis.builder.StaticSqlSource;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SelectIsolationRelationTests {

    @Test
    void testRelationParametersFollowFinalSqlOrder() {
        Configuration configuration = configuration();
        String sql = "SELECT r.id FROM relation_root r WHERE r.id > ? ORDER BY r.id LIMIT ?";
        MappedStatement statement = statement(configuration, sql);
        List<ParameterMapping> mappings = List.of(
                new ParameterMapping.Builder(configuration, "minimum", Long.class).build(),
                new ParameterMapping.Builder(configuration, "limit", Integer.class).build());
        Map<String, Object> input = Map.of("minimum", 0L, "limit", 5);
        BoundSql original = new BoundSql(configuration, sql, mappings, input);
        DataIsolationStrategy root = rootCondition("(r.id = ? OR EXISTS (SELECT 1 FROM relation_link l "
                + "INNER JOIN relation_department d ON d.id = l.department_id AND d.id > ? "
                + "WHERE l.root_id = r.id AND d.id < ?)) AND r.id < ?", List.of(1L, 2L, 90L, 100L));

        BoundSql rewritten = new SelectIsolationRewriter(configuration, fields(), root).rewrite(statement, original);

        List<Object> values = rewritten.getParameterMappings().stream().map(mapping -> {
            String name = mapping.getProperty();
            return rewritten.hasAdditionalParameter(name) ? rewritten.getAdditionalParameter(name) : input.get(name);
        }).toList();
        assertEquals(List.of(0L, 10L, 1L, 2L, 90L, 10L, 10L, 100L, 5), values);
        assertTrue(rewritten.getSql().contains("l.tenant_id"));
        assertTrue(rewritten.getSql().contains("d.tenant_id"));
    }

    @Test
    void testRelationMustAppearInPositiveBooleanPosition() {
        String exists = "EXISTS (SELECT 1 FROM relation_link l WHERE l.root_id = r.id)";
        for (String expression : List.of("NOT " + exists, "NOT (" + exists + ")", exists + " = FALSE",
                "CASE WHEN " + exists + " THEN 0 ELSE 1 END = 1", "COALESCE(" + exists + ", FALSE)")) {
            assertRejected(expression);
        }
    }

    @Test
    void testRelationShapeAliasesColumnsAndCorrelationAreChecked() {
        for (String expression : List.of(
                "EXISTS (SELECT 1 FROM relation_link l WHERE l.root_id = r.id OR l.id = 1)",
                "EXISTS (SELECT 1 FROM relation_link l WHERE l.id = 1)",
                "EXISTS (SELECT 1 FROM relation_link r WHERE r.id = r.id)",
                "EXISTS (SELECT 1 FROM relation_link l WHERE l.missing_id = r.id)",
                "EXISTS (SELECT 1 FROM relation_link l WHERE l.root_id = r.missing_id)",
                "EXISTS (SELECT 1 FROM relation_link l WHERE l.root_id = other.id)",
                "EXISTS (SELECT 1 FROM unknown_link l WHERE l.root_id = r.id)",
                "EXISTS (SELECT 1 FROM other_schema.relation_link l WHERE l.root_id = r.id)",
                "EXISTS (SELECT 1 FROM relation_link l WHERE l.root_id = r.id LIMIT 1)",
                "EXISTS (SELECT 1 FROM relation_link l WHERE l.root_id = r.id LIMIT 1 BY l.id)",
                "EXISTS (SELECT 1 FROM relation_link l PREWHERE l.id = 1 WHERE l.root_id = r.id)",
                "EXISTS (SELECT 1 INTO OUTFILE 'dump.csv' FROM relation_link l WHERE l.root_id = r.id)",
                "EXISTS (SELECT 1 FROM relation_link l WHERE l.root_id = r.id FOR UPDATE)",
                "EXISTS (SELECT l.id FROM relation_link l WHERE l.root_id = r.id)",
                "EXISTS (SELECT 1 FROM relation_link l WHERE l.root_id = r.id AND EXISTS (SELECT 1 FROM relation_department d))",
                "EXISTS (SELECT 1 FROM relation_link l LEFT JOIN relation_department d ON d.id=l.department_id WHERE l.root_id=r.id)",
                "EXISTS (SELECT 1 FROM relation_link l JOIN relation_department d ON d.id=l.department_id JOIN relation_department x ON x.id=d.id WHERE l.root_id=r.id)",
                "r.id IN (SELECT l.root_id FROM relation_link l)",
                "EXISTS (SELECT 1 FROM relation_link l WHERE l.root_id = r.id) OR r.id = (SELECT id FROM relation_department)")) {
            assertRejected(expression);
        }
    }

    @Test
    void testUndeclaredRelationStrategyIsRejected() {
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
        assertThrows(IllegalStateException.class, () -> rewrite(exists(), undeclared));
    }

    @Test
    void testRelationHookCannotReturnRecursiveConditions() {
        DataIsolationStrategy recursive = new DataIsolationStrategy() {
            @Override
            public boolean supports(EntityMetadata entity) {
                return entity.type() == Link.class;
            }

            @Override
            public List<Condition> resolve(QueryContext context) {
                return List.of();
            }

            @Override
            public List<Condition> resolveRelation(QueryContext context) {
                return List.of(Condition.of("EXISTS (SELECT 1 FROM relation_department d WHERE d.id = l.department_id)"));
            }
        };
        assertThrows(IllegalArgumentException.class, () -> rewrite(exists(), recursive));
    }

    @Test
    void testRelationUsesExplicitHookInsteadOfRootPermissionResolver() {
        AtomicInteger permissionQueries = new AtomicInteger();
        DataIsolationStrategy permission = new DataIsolationStrategy() {
            @Override
            public boolean supports(EntityMetadata entity) {
                return entity.type() == Link.class;
            }

            @Override
            public List<Condition> resolve(QueryContext context) {
                permissionQueries.incrementAndGet();
                throw new IllegalStateException("关系事实不得请求独立资源权限");
            }

            @Override
            public List<Condition> resolveRelation(QueryContext context) {
                return List.of();
            }
        };
        assertTrue(rewrite(exists(), permission).getSql().contains("l.tenant_id"));
        assertEquals(0, permissionQueries.get());
    }

    @Test
    void testOriginalSqlStillRejectsProtectedRelationTables() {
        Configuration configuration = configuration();
        SelectIsolationRewriter rewriter = new SelectIsolationRewriter(configuration, fields());
        MappedStatement statement = statement(configuration, "SELECT r.id FROM relation_root r WHERE " + exists());
        assertThrows(IllegalStateException.class, () -> rewriter.rewrite(statement, statement.getBoundSql(null)));
    }

    @Test
    void testRelationPlaceholderCountAndNamedParametersAreRejected() {
        assertRejected("EXISTS (SELECT 1 FROM relation_link l WHERE l.root_id = r.id AND l.id = ?)");
        assertRejected("EXISTS (SELECT 1 FROM relation_link l WHERE l.root_id = r.id AND l.id = :value)");
        assertRejected("EXISTS (SELECT 1 FROM relation_link l WHERE l.root_id = r.id AND l.id = ?1)");
    }

    private void assertRejected(String expression) {
        assertThrows(IllegalArgumentException.class, () -> rewrite(expression), expression);
    }

    private BoundSql rewrite(String expression, DataIsolationStrategy... extra) {
        Configuration configuration = configuration();
        List<DataIsolationStrategy> strategies = new ArrayList<>(List.of(fields(), rootCondition(expression, List.of())));
        strategies.addAll(List.of(extra));
        SelectIsolationRewriter rewriter = new SelectIsolationRewriter(configuration, strategies.toArray(DataIsolationStrategy[]::new));
        MappedStatement statement = statement(configuration, "SELECT r.id FROM relation_root r");
        return rewriter.rewrite(statement, statement.getBoundSql(null));
    }

    private static String exists() {
        return "EXISTS (SELECT 1 FROM relation_link l WHERE (l.root_id = r.id) AND l.id > 0)";
    }

    private static DataIsolationStrategy rootCondition(String sql, List<?> values) {
        return new DataIsolationStrategy() {
            @Override
            public boolean supports(EntityMetadata entity) {
                return entity.type() == Root.class;
            }

            @Override
            public List<Condition> resolve(QueryContext context) {
                return List.of(new Condition(sql, values));
            }
        };
    }

    private static FieldDataIsolationStrategy fields() {
        return new FieldDataIsolationStrategy(List.of(
                new DataIsolationStrategy.FieldRule(Root.class, "tenantId", () -> DataIsolationScope.of(10L)),
                new DataIsolationStrategy.FieldRule(Link.class, "tenantId", () -> DataIsolationScope.of(10L)),
                new DataIsolationStrategy.FieldRule(Department.class, "tenantId", () -> DataIsolationScope.of(10L))));
    }

    private static Configuration configuration() {
        Configuration configuration = new Configuration();
        configuration.addMapper(RootMapper.class);
        configuration.addMapper(LinkMapper.class);
        configuration.addMapper(DepartmentMapper.class);
        return configuration;
    }

    private static MappedStatement statement(Configuration configuration, String sql) {
        return new MappedStatement.Builder(configuration, "test.relation", new StaticSqlSource(configuration, sql), SqlCommandType.SELECT).build();
    }

    @Table("relation_root")
    static class Root {
        @Id
        private Long id;
        private Long tenantId;
    }

    @Table("relation_link")
    static class Link {
        @Id
        private Long id;
        private Long tenantId;
        private Long rootId;
        private Long departmentId;
    }

    @Table("relation_department")
    static class Department {
        @Id
        private Long id;
        private Long tenantId;
    }

    interface RootMapper extends BaseMapper<Root> {
    }

    interface LinkMapper extends BaseMapper<Link> {
    }

    interface DepartmentMapper extends BaseMapper<Department> {
    }
}
