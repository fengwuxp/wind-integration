package com.wind.integration.metrics.jdbc;

import com.wind.integration.metrics.dsl.MetricDefinitionDslCodec;
import com.wind.integration.metrics.dsl.filter.ComparisonMetricFilterDsl;
import com.wind.integration.metrics.dsl.literal.StringMetricLiteralDsl;
import com.wind.integration.metrics.enums.MetricFilterOperator;
import com.wind.integration.metrics.query.MetricQuery;
import org.jooq.SQLDialect;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 在 H2 上执行三种方言生成的受限 SQL；不等于实际 MySQL/PostgreSQL 验收。
 *
 * @author wuxp
 * @since 2026-09-16
 */
class MetricJdbcDialectTests {
    private static final LocalDateTime START = LocalDateTime.of(2026, 9, 1, 0, 0);
    private static final LocalDateTime END = START.plusMonths(1);

    @Test
    void testCompiledLimitedSumExecutesAcrossDialects() throws Exception {
        for (SQLDialect dialect : List.of(SQLDialect.MYSQL, SQLDialect.POSTGRES, SQLDialect.H2)) {
            var definition = new MetricDefinitionDslCodec().parse("""
                    {"schemaVersion":1,"metric":{"code":"AMOUNT","valueShape":"SCALAR","fact":"MetricOrderFact",
                     "subject":{"type":"CUSTOMER","field":"customerId"},"time":{"field":"occurredAt"},
                     "dimensions":[],"rowSelection":{"filter":{"eq":{"status":"APPROVED"}},
                       "orderBy":[{"field":"occurredAt","direction":"ASC"},{"field":"id","direction":"ASC"}],
                       "limit":{"value":2}},"value":{"valueType":"LONG","measure":{"aggregation":"SUM",
                       "field":"amount"},"orElse":{"mode":"ZERO"}}}}
                    """).metric();
            CompiledMetricSql compiled = new MetricJdbcSqlCompiler(ZoneId.of("UTC"), 1000, dialect)
                    .compile(definition, new MetricQuery("customer-1", START, END, Map.of(), Map.of()),
                            MetricJdbcTestFixtures.binding(definition));
            Assertions.assertEquals(List.of("customer-1", START, END, "APPROVED", 2),
                    compiled.bindings().stream().map(MetricSqlBinding::value).toList());
            String mode = dialect == SQLDialect.MYSQL ? "MySQL" : "PostgreSQL";
            try (Connection connection = DriverManager.getConnection(
                    "jdbc:h2:mem:limited_" + dialect + ";MODE=" + mode + ";DATABASE_TO_LOWER=TRUE")) {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("CREATE TABLE t_metric_order_fact (id BIGINT PRIMARY KEY, customer_id VARCHAR, "
                            + "occurred_at TIMESTAMP, amount DECIMAL(20,4), status VARCHAR)");
                    statement.execute("INSERT INTO t_metric_order_fact VALUES "
                            + "(1,'customer-1','2026-09-01',999,'DECLINED'),"
                            + "(2,'customer-1','2026-09-02',100,'APPROVED'),"
                            + "(3,'customer-1','2026-09-02',200,'APPROVED'),"
                            + "(4,'customer-1','2026-09-03',400,'APPROVED'),"
                            + "(5,'customer-1','2026-10-01',800,'APPROVED')");
                }
                try (PreparedStatement statement = connection.prepareStatement(compiled.sql())) {
                    for (int index = 0; index < compiled.bindings().size(); index++) {
                        MetricSqlBinding value = compiled.bindings().get(index);
                        statement.setObject(index + 1, value.value(), value.jdbcType());
                    }
                    try (ResultSet result = statement.executeQuery()) {
                        Assertions.assertTrue(result.next());
                        Assertions.assertEquals(300, result.getBigDecimal(1).intValueExact());
                    }
                }
            }
        }
    }

    @Test
    void testDialectQuotesNamesAndRetainsJdbcTypes() {
        var definition = MetricJdbcTestFixtures.scalarCountDefinition().metric();
        var query = new MetricQuery("customer-1", START, END, Map.of("region", "EU"), Map.of());
        var mysql = new MetricJdbcSqlCompiler(ZoneId.of("UTC"), 1000, SQLDialect.MYSQL)
                .compile(definition, query, MetricJdbcTestFixtures.binding(definition));
        var postgres = new MetricJdbcSqlCompiler(ZoneId.of("UTC"), 1000, SQLDialect.POSTGRES)
                .compile(definition, query, MetricJdbcTestFixtures.binding(definition));
        Assertions.assertTrue(mysql.sql().contains("`p`.`customer_id`"));
        Assertions.assertTrue(postgres.sql().contains("\"p\".\"customer_id\""));
        Assertions.assertEquals(mysql.bindings(), postgres.bindings());
        Assertions.assertEquals(List.of(Types.VARCHAR, Types.TIMESTAMP, Types.TIMESTAMP, Types.VARCHAR),
                postgres.bindings().stream().map(MetricSqlBinding::jdbcType).toList());
    }

    @Test
    void testCodecNullRemainsTypedParameter() {
        var binding = new MetricJdbcBinding() {
            @Override
            public String tableName(String field) { throw new AssertionError(); }
            @Override
            public String columnName(String field) { throw new AssertionError(); }
            @Override
            public Class<?> javaType(String field) { return String.class; }
            @Override
            public int jdbcType(String field) { return Types.VARCHAR; }
            @Override
            public Object toJdbcValue(String field, Object value) { return null; }
        };
        for (SQLDialect dialect : List.of(SQLDialect.MYSQL, SQLDialect.POSTGRES, SQLDialect.H2)) {
            List<MetricSqlBinding> values = new ArrayList<>();
            String sql = new MetricJdbcSqlCompiler(ZoneId.of("UTC"), 1000, dialect).renderValidatedFilter(
                    binding, new ComparisonMetricFilterDsl(MetricFilterOperator.EQ, "name",
                            new StringMetricLiteralDsl("logical")), values, field -> "p.name");
            Assertions.assertEquals("p.name = ?", sql);
            Assertions.assertEquals(List.of(new MetricSqlBinding(null, Types.VARCHAR)), values);
        }
    }

    @Test
    void testUnknownDialectDoesNotSilentlyFallback() {
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> new MetricJdbcSqlCompiler(ZoneId.of("UTC"), 1000, SQLDialect.DEFAULT));
    }
}
