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
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.wind.integration.core.enums.DataIsolationDimensions.TENANT;

class SelectIsolationRewriterTests {

    @Test
    void testRewriteAddsTenantWhereClause() {
        Configuration configuration = configuration();
        String sql = "SELECT id FROM protected_records";
        MappedStatement statement = statement(configuration, sql);
        BoundSql original = statement.getBoundSql(null);

        BoundSql rewritten = rewriter(configuration).rewrite(statement, original);

        assertRewrittenSql(rewritten, "SELECT id FROM protected_records WHERE (protected_records.tenant_id = ?)", List.of(10L));
        Assertions.assertEquals(sql, original.getSql());
        Assertions.assertTrue(original.getParameterMappings().isEmpty());
    }

    /**
     * 原 OR 条件作为整体与租户条件取 AND；新增租户参数位于业务参数之后、分页参数之前。
     */
    @Test
    void testRewriteGroupsOriginalOrBeforeTenantAndPagination() {
        Configuration configuration = configuration();
        String sql = "SELECT r.id FROM protected_records r WHERE r.id = ? OR r.id = ? ORDER BY r.id LIMIT ? OFFSET ?";
        MappedStatement statement = statement(configuration, sql);
        List<ParameterMapping> mappings = List.of(
                new ParameterMapping.Builder(configuration, "firstId", Long.class).build(),
                new ParameterMapping.Builder(configuration, "secondId", Long.class).build(),
                new ParameterMapping.Builder(configuration, "limit", Integer.class).build(),
                new ParameterMapping.Builder(configuration, "offset", Integer.class).build());
        Map<String, Object> input = Map.of("firstId", 1L, "secondId", 3L, "limit", 5, "offset", 0);
        BoundSql original = new BoundSql(configuration, sql, mappings, input);

        BoundSql rewritten = rewriter(configuration).rewrite(statement, original);

        assertRewrittenSql(rewritten, """
                SELECT r.id FROM protected_records r
                WHERE (r.id = ? OR r.id = ?) AND ((r.tenant_id = ?))
                ORDER BY r.id LIMIT ? OFFSET ?
                """, List.of(1L, 3L, 10L, 5, 0));
        Assertions.assertSame(input, rewritten.getParameterObject());
        Assertions.assertEquals(sql, original.getSql());
        Assertions.assertEquals(mappings, original.getParameterMappings());
    }

    @Test
    void testRewriteUsesInForMultipleTenants() {
        Configuration configuration = configuration();
        MappedStatement statement = statement(configuration, "SELECT r.id FROM protected_records r");
        SelectIsolationRewriter rewriter = new SelectIsolationRewriter(configuration,
                new FieldDataIsolationStrategy(List.of(FieldRule.forDimension(TENANT, "tenantId",
                        () -> DataIsolationScope.ofValues(List.of(10L, 20L))))));

        BoundSql rewritten = rewriter.rewrite(statement, statement.getBoundSql(null));

        assertRewrittenSql(rewritten, "SELECT r.id FROM protected_records r WHERE (r.tenant_id IN (?, ?))", List.of(10L, 20L));
    }

    @Test
    void testRewriteDeniesEmptyTenantScope() {
        Configuration configuration = configuration();
        MappedStatement statement = statement(configuration, "SELECT r.id FROM protected_records r");
        SelectIsolationRewriter rewriter = new SelectIsolationRewriter(configuration,
                new FieldDataIsolationStrategy(List.of(FieldRule.forDimension(TENANT, "tenantId",
                        () -> DataIsolationScope.ofValues(List.of())))));

        BoundSql rewritten = rewriter.rewrite(statement, statement.getBoundSql(null));

        assertRewrittenSql(rewritten, "SELECT r.id FROM protected_records r WHERE (1 = 0)", List.of());
    }

    @Test
    void testUnrestrictedScopeKeepsOriginalSqlAndBindings() {
        Configuration configuration = configuration();
        String sql = "SELECT r.id FROM protected_records r WHERE r.id = ?";
        MappedStatement statement = statement(configuration, sql);
        BoundSql original = new BoundSql(configuration, sql,
                List.of(new ParameterMapping.Builder(configuration, "id", Long.class).build()), Map.of("id", 1L));
        SelectIsolationRewriter rewriter = new SelectIsolationRewriter(configuration,
                new FieldDataIsolationStrategy(List.of(FieldRule.forDimension(TENANT, "tenantId", DataIsolationScope::unrestrictedScope))));

        BoundSql rewritten = rewriter.rewrite(statement, original);

        assertRewrittenSql(rewritten, sql, List.of(1L));
        Assertions.assertSame(original, rewritten);
    }

    /**
     * 两个 UNION 分支分别追加各自别名限定的条件，不能只过滤第一个分支。
     */
    @Test
    void testRewriteIsolatesEveryUnionBranch() {
        Configuration configuration = configuration();
        String sql = "SELECT a.id FROM protected_records a UNION ALL SELECT b.id FROM protected_records b ORDER BY id";
        MappedStatement statement = statement(configuration, sql);

        BoundSql rewritten = rewriter(configuration).rewrite(statement, statement.getBoundSql(null));

        assertRewrittenSql(rewritten, """
                SELECT a.id FROM protected_records a WHERE (a.tenant_id = ?)
                UNION ALL
                SELECT b.id FROM protected_records b WHERE (b.tenant_id = ?)
                ORDER BY id
                """, List.of(10L, 10L));
    }

    /**
     * 计数包装在读取底表时隔离，外层无需投影或引用 tenant_id。
     */
    @Test
    void testRewritePlacesIsolationInsideDerivedCount() {
        Configuration configuration = configuration();
        String sql = "SELECT COUNT(*) FROM (SELECT r.id FROM protected_records r WHERE r.id > ?) records";
        MappedStatement statement = statement(configuration, sql);
        BoundSql original = new BoundSql(configuration, sql,
                List.of(new ParameterMapping.Builder(configuration, "minimum", Long.class).build()), Map.of("minimum", 3L));

        BoundSql rewritten = rewriter(configuration).rewrite(statement, original);

        assertRewrittenSql(rewritten, """
                SELECT COUNT(*) FROM
                (SELECT r.id FROM protected_records r WHERE (r.id > ?) AND ((r.tenant_id = ?))) records
                """, List.of(3L, 10L));
    }

    @Test
    void testParameterNamesAvoidNestedPropertiesAndAdditionalValues() {
        Configuration configuration = configuration();
        SelectIsolationRewriter rewriter = rewriter(configuration);
        String sql = "SELECT id FROM protected_records WHERE id > ? AND id < ? AND id <> ? ORDER BY id LIMIT ?";
        MappedStatement statement = statement(configuration, sql);
        List<ParameterMapping> mappings = List.of(
                new ParameterMapping.Builder(configuration, "windIsolation4.value", Long.class).build(),
                new ParameterMapping.Builder(configuration, "windIsolation4Value[0]", Long.class).build(),
                new ParameterMapping.Builder(configuration, "windIsolation4ValueValue.deep[0]", Long.class).build(),
                new ParameterMapping.Builder(configuration, "limit", Integer.class).build());
        Map<String, Object> input = Map.of("windIsolation4", Map.of("value", 0L),
                "windIsolation4Value", List.of(100L), "windIsolation4ValueValue", Map.of("deep", List.of(5L)), "limit", 20);
        BoundSql original = new BoundSql(configuration, sql, mappings, input);
        original.setAdditionalParameter("windIsolation4ValueValueValue", 999L);

        BoundSql rewritten = rewriter.rewrite(statement, original);

        Assertions.assertEquals(List.of("windIsolation4.value", "windIsolation4Value[0]", "windIsolation4ValueValue.deep[0]",
                        "windIsolation4ValueValueValueValue", "limit"),
                rewritten.getParameterMappings().stream().map(ParameterMapping::getProperty).toList());
        Assertions.assertSame(input, rewritten.getParameterObject());
        Assertions.assertSame(mappings.getFirst(), rewritten.getParameterMappings().getFirst());
        Assertions.assertSame(mappings.getLast(), rewritten.getParameterMappings().getLast());
        Assertions.assertEquals(999L, rewritten.getAdditionalParameter("windIsolation4ValueValueValue"));
        Assertions.assertEquals(10L, rewritten.getAdditionalParameter("windIsolation4ValueValueValueValue"));
        Assertions.assertFalse(original.hasAdditionalParameter("windIsolation4ValueValueValueValue"));
    }

    @Test
    void testSimilarPropertyPrefixDoesNotReserveAnUnrelatedName() {
        Configuration configuration = configuration();
        SelectIsolationRewriter rewriter = rewriter(configuration);
        String sql = "SELECT id FROM protected_records WHERE id > ?";
        MappedStatement statement = statement(configuration, sql);
        List<ParameterMapping> mappings = List.of(
                new ParameterMapping.Builder(configuration, "windIsolation1Other.value", Long.class).build());
        BoundSql result = rewriter.rewrite(statement, new BoundSql(configuration, sql, mappings,
                Map.of("windIsolation1Other", Map.of("value", 0L))));

        Assertions.assertEquals(List.of("windIsolation1Other.value", "windIsolation1"),
                result.getParameterMappings().stream().map(ParameterMapping::getProperty).toList());
        Assertions.assertEquals(10L, result.getAdditionalParameter("windIsolation1"));
    }

    @Test
    void testNewParserQueryFormsDoNotBypassIsolation() {
        Configuration configuration = configuration();
        SelectIsolationRewriter rewriter = rewriter(configuration);
        for (String sql : List.of(
                "FROM protected_records |> SELECT id",
                "TABLE protected_records",
                "(SELECT id FROM protected_records) ORDER BY id",
                "(SELECT id FROM protected_records) LIMIT 1",
                "SELECT id INTO OUTFILE 'dump.csv' FROM protected_records",
                "WITH changed AS (DELETE FROM protected_records RETURNING id) SELECT id FROM public_records",
                "WITH changed AS (UPDATE protected_records SET tenant_id = 20 RETURNING id) SELECT id FROM public_records",
                "WITH changed AS (INSERT INTO protected_records (id) VALUES (1) RETURNING id) SELECT id FROM public_records",
                "WITH x AS (FROM protected_records |> SELECT id) SELECT id FROM x",
                "SELECT id FROM protected_records PREWHERE id = 1",
                "SELECT id FROM protected_records QUALIFY id = 1",
                "SELECT id FROM protected_records LIMIT 1 BY id")) {
            MappedStatement statement = statement(configuration, sql);
            Assertions.assertThrows(IllegalStateException.class,
                    () -> rewriter.rewrite(statement, statement.getBoundSql(null)), sql);
        }
    }

    @Test
    void testLimitAndOffsetUseOneParameterTraversal() {
        Configuration configuration = configuration();
        SelectIsolationRewriter rewriter = rewriter(configuration);
        for (String sql : List.of("SELECT id FROM protected_records WHERE id > ? LIMIT ? OFFSET ?",
                "SELECT id FROM protected_records WHERE id > ? LIMIT ?, ?")) {
            MappedStatement statement = statement(configuration, sql);
            List<ParameterMapping> mappings = List.of(
                    new ParameterMapping.Builder(configuration, "minimum", Long.class).build(),
                    new ParameterMapping.Builder(configuration, "firstLimitArgument", Integer.class).build(),
                    new ParameterMapping.Builder(configuration, "secondLimitArgument", Integer.class).build());
            BoundSql result = rewriter.rewrite(statement, new BoundSql(configuration, sql, mappings, null));
            Assertions.assertEquals(List.of("minimum", "windIsolation3", "firstLimitArgument", "secondLimitArgument"),
                    result.getParameterMappings().stream().map(ParameterMapping::getProperty).toList());
            Assertions.assertEquals(10L, result.getAdditionalParameter("windIsolation3"));
        }
    }

    @Test
    void testParenthesizedRootKeepsIsolationAndParameterOrder() {
        Configuration configuration = configuration();
        SelectIsolationRewriter rewriter = rewriter(configuration);
        for (String sql : List.of("(SELECT id FROM protected_records WHERE id > ? ORDER BY id LIMIT ?)",
                "((SELECT id FROM protected_records WHERE id > ? ORDER BY id LIMIT ?))")) {
            MappedStatement statement = statement(configuration, sql);
            List<ParameterMapping> mappings = List.of(
                    new ParameterMapping.Builder(configuration, "minimum", Long.class).build(),
                    new ParameterMapping.Builder(configuration, "limit", Integer.class).build());
            BoundSql result = rewriter.rewrite(statement, new BoundSql(configuration, sql, mappings, null));
            Assertions.assertTrue(result.getSql().startsWith("("));
            Assertions.assertTrue(result.getSql().contains("tenant_id"));
            Assertions.assertEquals(List.of("minimum", "windIsolation2", "limit"),
                    result.getParameterMappings().stream().map(ParameterMapping::getProperty).toList());
        }
    }

    @Test
    void testRepeatedPublicEntityQuerySkipsSqlParsing() {
        Configuration configuration = configuration();
        String sql = "SELECT id FROM public_records WHERE id = ?";
        MappedStatement statement = statement(configuration, sql);
        BoundSql boundSql = statement.getBoundSql(1L);

        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            SelectIsolationRewriter rewriter = new SelectIsolationRewriter(configuration, executor,
                    new FieldDataIsolationStrategy(List.of(FieldRule.forDimension(TENANT, "tenantId", () -> DataIsolationScope.of(10L)))));
            Assertions.assertSame(boundSql, rewriter.rewrite(statement, boundSql));
            executor.shutdown();

            Assertions.assertSame(boundSql, rewriter.rewrite(statement, boundSql));
            BoundSql changed = new BoundSql(configuration, "SELECT id FROM protected_records", List.of(), null);
            IllegalStateException failure = Assertions.assertThrows(IllegalStateException.class, () -> rewriter.rewrite(statement, changed));
            Assertions.assertTrue(failure.getMessage().contains("Cannot parse query for read isolation"));
        }
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
        String unknownSql = "SELECT id FROM another_public_table";
        String complexSql = "SELECT p.id FROM public_records p JOIN public_records q ON p.id = q.id";
        MappedStatement statement = statement(configuration, unknownSql);

        BoundSql unknown = statement.getBoundSql(null);
        BoundSql complex = new BoundSql(configuration, complexSql, List.of(), null);
        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            SelectIsolationRewriter rewriter = new SelectIsolationRewriter(configuration, executor,
                    new FieldDataIsolationStrategy(List.of(FieldRule.forDimension(TENANT, "tenantId", () -> DataIsolationScope.of(10L)))));
            Assertions.assertSame(unknown, rewriter.rewrite(statement, unknown));
            Assertions.assertSame(complex, rewriter.rewrite(statement, complex));
            executor.shutdown();

            for (BoundSql query : List.of(unknown, complex)) {
                IllegalStateException failure = Assertions.assertThrows(IllegalStateException.class, () -> rewriter.rewrite(statement, query));
                Assertions.assertTrue(failure.getMessage().contains("Cannot parse query for read isolation"));
            }
        }
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

    private void assertRewrittenSql(BoundSql rewritten, String expectedSql, List<?> expectedValues) {
        Assertions.assertEquals(expectedSql.replaceAll("\\s+", " ").trim(), rewritten.getSql());
        MetaObject input = SystemMetaObject.forObject(rewritten.getParameterObject());
        List<Object> values = rewritten.getParameterMappings().stream().map(mapping -> {
            String property = mapping.getProperty();
            return rewritten.hasAdditionalParameter(property)
                    ? rewritten.getAdditionalParameter(property) : input.getValue(property);
        }).toList();
        Assertions.assertEquals(expectedValues, values);
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
