package com.wind.mybatis.plugin;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.BaseMapper;
import com.wind.integration.core.model.TenantIsolationObject;
import com.wind.mybatis.isolation.DataIsolationScope;
import com.wind.mybatis.isolation.DataIsolationStrategy.FieldRule;
import com.wind.mybatis.isolation.FieldDataIsolationStrategy;
import org.apache.ibatis.builder.StaticSqlSource;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.wind.integration.core.enums.DataIsolationDimensions.TENANT;

class SelectIsolationRewriterTests {

    @Test
    void testRepeatedPublicEntityQuerySkipsSqlParsing() {
        Configuration configuration = configuration();
        SelectIsolationRewriter rewriter = rewriter(configuration);
        String sql = "SELECT id FROM public_records WHERE id = ?";
        MappedStatement statement = statement(configuration, sql);
        BoundSql boundSql = statement.getBoundSql(1L);

        Assertions.assertSame(boundSql, rewriter.rewrite(statement, boundSql));
        Assertions.assertSame(boundSql, rewriter.rewrite(statement, boundSql));
    }

    @Test
    void testChangedSqlOnSameStatementStillChecksProtectedTable() {
        Configuration configuration = configuration();
        SelectIsolationRewriter rewriter = rewriter(configuration);
        MappedStatement statement = statement(configuration, "SELECT id FROM public_records");
        BoundSql publicSql = statement.getBoundSql(null);
        Assertions.assertSame(publicSql, rewriter.rewrite(statement, publicSql));

        BoundSql protectedSql = new BoundSql(configuration, "SELECT id FROM protected_records", List.of(), null);
        BoundSql isolated = rewriter.rewrite(statement, protectedSql);
        Assertions.assertNotSame(protectedSql, isolated);
        Assertions.assertTrue(isolated.getSql().contains("tenant_id"));

        BoundSql joinedSql = new BoundSql(configuration,
                "SELECT p.id FROM public_records p JOIN protected_records r ON p.id = r.id", List.of(), null);
        Assertions.assertThrows(IllegalStateException.class, () -> rewriter.rewrite(statement, joinedSql));
    }

    @Test
    void testUnregisteredOrComplexQueriesDoNotEnterFastPath() {
        Configuration configuration = configuration();
        SelectIsolationRewriter rewriter = rewriter(configuration);
        String unknownSql = "SELECT id FROM another_public_table";
        String complexSql = "SELECT p.id FROM public_records p JOIN public_records q ON p.id = q.id";
        MappedStatement statement = statement(configuration, unknownSql);

        BoundSql unknown = statement.getBoundSql(null);
        Assertions.assertSame(unknown, rewriter.rewrite(statement, unknown));
        Assertions.assertSame(unknown, rewriter.rewrite(statement, unknown));
        BoundSql complex = new BoundSql(configuration, complexSql, List.of(), null);
        Assertions.assertSame(complex, rewriter.rewrite(statement, complex));
        Assertions.assertSame(complex, rewriter.rewrite(statement, complex));
    }

    @Test
    void testWhereExistsOnRegisteredPublicTablePreservesParameterOrder() {
        Configuration configuration = configuration();
        SelectIsolationRewriter rewriter = rewriter(configuration);
        String sql = "SELECT r.id FROM protected_records r WHERE r.id > ? AND EXISTS "
                + "(SELECT 1 FROM public_records p WHERE p.id = r.id AND p.id < ?) ORDER BY r.id LIMIT ?";
        MappedStatement statement = statement(configuration, sql);
        List<ParameterMapping> mappings = List.of(
                new ParameterMapping.Builder(configuration, "minimum", Long.class).build(),
                new ParameterMapping.Builder(configuration, "maximum", Long.class).build(),
                new ParameterMapping.Builder(configuration, "limit", Integer.class).build());
        BoundSql original = new BoundSql(configuration, sql, mappings, null);

        BoundSql result = rewriter.rewrite(statement, original);

        Assertions.assertTrue(result.getSql().contains("EXISTS"));
        Assertions.assertTrue(result.getSql().contains("tenant_id"));
        Assertions.assertEquals(List.of("minimum", "maximum", "windIsolation3", "limit"),
                result.getParameterMappings().stream().map(ParameterMapping::getProperty).toList());
        Assertions.assertEquals(10L, result.getAdditionalParameter("windIsolation3"));
    }

    @Test
    void testWhereExistsWithInnerJoinPreservesParameterOrder() {
        Configuration configuration = configuration();
        SelectIsolationRewriter rewriter = rewriter(configuration);
        String sql = "SELECT r.id FROM protected_records r WHERE r.id > ? AND EXISTS "
                + "(SELECT 1 FROM public_records p INNER JOIN public_records d ON d.id = p.id AND d.id > ? "
                + "WHERE p.id = r.id AND p.id < ?) ORDER BY r.id LIMIT ?";
        MappedStatement statement = statement(configuration, sql);
        List<ParameterMapping> mappings = List.of(
                new ParameterMapping.Builder(configuration, "minimum", Long.class).build(),
                new ParameterMapping.Builder(configuration, "joinMinimum", Long.class).build(),
                new ParameterMapping.Builder(configuration, "maximum", Long.class).build(),
                new ParameterMapping.Builder(configuration, "limit", Integer.class).build());
        for (String variant : List.of(sql, sql.replace("INNER JOIN", "JOIN"))) {
            BoundSql result = rewriter.rewrite(statement, new BoundSql(configuration, variant, mappings, null));

            Assertions.assertTrue(result.getSql().contains("JOIN"));
            Assertions.assertEquals(List.of("minimum", "joinMinimum", "maximum", "windIsolation4", "limit"),
                    result.getParameterMappings().stream().map(ParameterMapping::getProperty).toList());
            Assertions.assertEquals(10L, result.getAdditionalParameter("windIsolation4"));
        }
    }

    @Test
    void testOnlyRegisteredPublicWhereExistsIsSupported() {
        Configuration configuration = configuration();
        SelectIsolationRewriter rewriter = rewriter(configuration);
        List<String> unsupported = List.of(
                "SELECT r.id FROM protected_records r WHERE EXISTS (SELECT 1 FROM protected_records p WHERE p.id = r.id)",
                "SELECT r.id FROM protected_records r WHERE EXISTS (SELECT 1 FROM unknown_records p WHERE p.id = r.id)",
                "SELECT r.id FROM protected_records r WHERE EXISTS (SELECT 1 FROM other_schema.public_records p WHERE p.id = r.id)",
                "SELECT r.id FROM protected_records r WHERE EXISTS (SELECT 1 FROM other_catalog.other_schema.public_records p WHERE p.id = r.id)",
                "SELECT r.id FROM protected_records r WHERE EXISTS (SELECT 1 FROM schema_records p WHERE p.id = r.id)",
                "SELECT r.id FROM protected_records r WHERE EXISTS (SELECT 1 FROM \"other.public_records\" p WHERE p.id = r.id)",
                "SELECT r.id FROM protected_records r WHERE EXISTS (SELECT 1 FROM public_records p WHERE p.id = r.id FOR UPDATE)",
                "SELECT r.id FROM protected_records r WHERE NOT EXISTS (SELECT 1 FROM public_records p WHERE p.id = r.id)",
                "SELECT r.id FROM protected_records r WHERE NOT (EXISTS (SELECT 1 FROM public_records p WHERE p.id = r.id))",
                "SELECT r.id FROM protected_records r WHERE EXISTS (SELECT 1 FROM public_records p WHERE EXISTS (SELECT 1 FROM public_records q))",
                "SELECT r.id FROM protected_records r WHERE EXISTS (SELECT 1 FROM public_records p) OR r.id = (SELECT id FROM public_records)",
                "SELECT r.id FROM protected_records r WHERE EXISTS (SELECT 1 FROM public_records r WHERE r.id = 1)",
                "SELECT r.id FROM protected_records r WHERE r.id IN (SELECT id FROM public_records)",
                "SELECT (SELECT COUNT(*) FROM public_records) FROM protected_records",
                "SELECT r.id FROM protected_records r ORDER BY (SELECT COUNT(*) FROM public_records)",
                "SELECT r.id FROM protected_records r WHERE EXISTS (SELECT 1 FROM public_records p LEFT JOIN public_records q ON p.id=q.id)",
                "SELECT r.id FROM protected_records r WHERE EXISTS (SELECT 1 FROM public_records p RIGHT JOIN public_records q ON p.id=q.id)",
                "SELECT r.id FROM protected_records r WHERE EXISTS (SELECT 1 FROM public_records p CROSS JOIN public_records q)",
                "SELECT r.id FROM protected_records r WHERE EXISTS (SELECT 1 FROM public_records p NATURAL JOIN public_records q)",
                "SELECT r.id FROM protected_records r WHERE EXISTS (SELECT 1 FROM public_records p JOIN public_records q USING (id))",
                "SELECT r.id FROM protected_records r WHERE EXISTS (SELECT 1 FROM public_records p, public_records q WHERE p.id=q.id)",
                "SELECT r.id FROM protected_records r WHERE EXISTS (SELECT 1 FROM public_records p JOIN protected_records q ON p.id=q.id)",
                "SELECT r.id FROM protected_records r WHERE EXISTS (SELECT 1 FROM public_records p JOIN unknown_records q ON p.id=q.id)",
                "SELECT r.id FROM protected_records r WHERE EXISTS (SELECT 1 FROM public_records p JOIN other_schema.public_records q ON p.id=q.id)",
                "SELECT r.id FROM protected_records r WHERE EXISTS (SELECT 1 FROM public_records p JOIN public_records p ON p.id=p.id)",
                "SELECT r.id FROM protected_records r WHERE EXISTS (SELECT 1 FROM public_records p JOIN public_records r ON p.id=r.id)",
                "SELECT r.id FROM protected_records r WHERE EXISTS (SELECT 1 FROM public_records p JOIN public_records q ON p.id=q.id JOIN public_records x ON x.id=q.id)",
                "SELECT r.id FROM protected_records r WHERE EXISTS (SELECT 1 FROM public_records p JOIN public_records q ON p.id=(SELECT id FROM public_records))",
                "SELECT r.id FROM protected_records r WHERE EXISTS (SELECT id FROM public_records UNION SELECT id FROM public_records)",
                "WITH p AS (SELECT id FROM public_records) SELECT id FROM protected_records WHERE EXISTS (SELECT 1 FROM p)");
        for (String sql : unsupported) {
            MappedStatement statement = statement(configuration, sql);
            BoundSql original = statement.getBoundSql(null);
            Assertions.assertThrows(IllegalStateException.class, () -> rewriter.rewrite(statement, original), sql);
        }
    }

    @Test
    void testConflictingSchemaRegistrationRejectsUnqualifiedExists() {
        for (boolean schemaFirst : List.of(false, true)) {
            Configuration configuration = new Configuration();
            configuration.addMapper(ProtectedMapper.class);
            if (schemaFirst) {
                configuration.addMapper(SchemaPublicMapper.class);
                configuration.addMapper(PublicMapper.class);
            } else {
                configuration.addMapper(PublicMapper.class);
                configuration.addMapper(SchemaPublicMapper.class);
            }
            SelectIsolationRewriter rewriter = rewriter(configuration);
            String sql = "SELECT r.id FROM protected_records r WHERE EXISTS "
                    + "(SELECT 1 FROM public_records p WHERE p.id = r.id)";
            MappedStatement statement = statement(configuration, sql);

            Assertions.assertThrows(IllegalStateException.class,
                    () -> rewriter.rewrite(statement, statement.getBoundSql(null)));
        }
    }

    private Configuration configuration() {
        Configuration configuration = new Configuration();
        configuration.addMapper(PublicMapper.class);
        configuration.addMapper(ProtectedMapper.class);
        configuration.addMapper(SchemaMapper.class);
        return configuration;
    }

    private SelectIsolationRewriter rewriter(Configuration configuration) {
        return new SelectIsolationRewriter(configuration,
                new FieldDataIsolationStrategy(List.of(FieldRule.forDimension(TENANT, "tenantId", () -> DataIsolationScope.of(10L)))));
    }

    private MappedStatement statement(Configuration configuration, String sql) {
        return new MappedStatement.Builder(configuration, "test.query", new StaticSqlSource(configuration, sql),
                SqlCommandType.SELECT).build();
    }

    @Table("public_records")
    static class PublicRecord {
        @Id
        private Long id;
    }

    @Table(value = "schema_records", schema = "schema_a")
    static class SchemaRecord {
        @Id
        private Long id;
    }

    @Table(value = "public_records", schema = "schema_a")
    static class SchemaPublicRecord {
        @Id
        private Long id;
    }

    @Table("protected_records")
    static class ProtectedRecord implements TenantIsolationObject<Long> {
        @Id
        private Long id;
        private Long tenantId;

        @Override
        public Long getId() {
            return id;
        }

        @Override
        public Long getTenantId() {
            return tenantId;
        }
    }

    interface PublicMapper extends BaseMapper<PublicRecord> {
    }

    interface ProtectedMapper extends BaseMapper<ProtectedRecord> {
    }

    interface SchemaMapper extends BaseMapper<SchemaRecord> {
    }

    interface SchemaPublicMapper extends BaseMapper<SchemaPublicRecord> {
    }
}
