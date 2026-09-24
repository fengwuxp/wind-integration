package com.wind.integration.metrics.jdbc;

import com.wind.integration.metrics.dsl.MetricValueCalculator;
import com.wind.integration.metrics.dsl.definition.MetricExpressionDsl;
import com.wind.integration.metrics.dsl.definition.MetricJoinDsl;
import com.wind.integration.metrics.dsl.definition.MetricJoinOnDsl;
import com.wind.integration.metrics.dsl.definition.MetricMeasureDsl;
import com.wind.integration.metrics.dsl.definition.MetricOrElseDsl;
import com.wind.integration.metrics.dsl.definition.MetricQueryParameterDsl;
import com.wind.integration.metrics.dsl.definition.MetricSubjectDsl;
import com.wind.integration.metrics.dsl.definition.MetricTimeDsl;
import com.wind.integration.metrics.dsl.definition.MetricValueDsl;
import com.wind.integration.metrics.dsl.definition.selection.MetricLimitDsl;
import com.wind.integration.metrics.dsl.definition.selection.MetricOrderByDsl;
import com.wind.integration.metrics.dsl.definition.selection.MetricRowSelectionDsl;
import com.wind.integration.metrics.dsl.filter.ComparisonMetricFilterDsl;
import com.wind.integration.metrics.dsl.filter.MetricFilterDsl;
import com.wind.integration.metrics.dsl.literal.StringMetricLiteralDsl;
import com.wind.integration.metrics.enums.MetricAggregation;
import com.wind.integration.metrics.enums.MetricExpressionType;
import com.wind.integration.metrics.enums.MetricFilterOperator;
import com.wind.integration.metrics.enums.MetricJoinCardinality;
import com.wind.integration.metrics.enums.MetricJoinType;
import com.wind.integration.metrics.enums.MetricOrElseMode;
import com.wind.integration.metrics.enums.MetricSortDirection;
import com.wind.integration.metrics.enums.MetricValueShape;
import com.wind.integration.metrics.enums.MetricValueType;
import com.wind.integration.metrics.expression.MetricExpression;
import com.wind.integration.metrics.expression.MetricExpressionCompiler;
import com.wind.integration.metrics.query.MetricQuery;
import com.wind.integration.metrics.spec.MetricDSLDefinition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 将真实编译结果及有序 JDBC 参数交给 H2 执行，验证统计结果与原始量合并。
 * 不启动 Spring，也不把 H2 场景当作宿主仓储、生产 MySQL 或物化调度验收。
 *
 * @author wuxp
 */
class MetricJdbcSqlCompilerExecutionTests {

    private static final LocalDateTime START = LocalDateTime.of(2026, 9, 1, 0, 0);

    private static final LocalDateTime MIDDLE = START.plusDays(1);

    private static final LocalDateTime END = START.plusDays(2);

    private final MetricJdbcSqlCompiler compiler = new MetricJdbcSqlCompiler(ZoneOffset.UTC);

    private final MetricJdbcMapping mapping = new PaymentMapping();

    private Connection connection;

    @BeforeEach
    void setUp() throws SQLException {
        connection = DriverManager.getConnection("jdbc:h2:mem:metric_" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE");
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE payment_fact (
                        id BIGINT PRIMARY KEY,
                        subject_id VARCHAR(64) NOT NULL,
                        currency VARCHAR(3) NOT NULL,
                        occurred_at TIMESTAMP(9) NOT NULL,
                        amount DECIMAL(24, 10),
                        state VARCHAR(16) NOT NULL
                    )
                    """);
            statement.execute("CREATE TABLE account_fact (external_id VARCHAR(64) PRIMARY KEY, owner_id VARCHAR(64) NOT NULL)");
        }
    }

    @AfterEach
    void tearDown() throws SQLException {
        if (connection != null) {
            connection.close();
        }
    }

    /**
     * 主体、币种及半开窗口约束付款统计；状态过滤仅影响指定 measure，未过滤的总笔数仍包含失败记录。
     */
    @Test
    void testSubjectDimensionFilterAndHalfOpenWindow() throws SQLException {
        insert(1, "user-1", "USD", START, "12.50", "SETTLED");
        insert(2, "user-1", "USD", END.minusNanos(1), "0.25", "SETTLED");
        insert(3, "user-1", "USD", END, "1000", "SETTLED");
        insert(4, "user-1", "USD", START.minusNanos(1), "1000", "SETTLED");
        insert(5, "user-2", "USD", START, "1000", "SETTLED");
        insert(6, "user-1", "EUR", START, "1000", "SETTLED");
        insert(7, "user-1", "USD", START, "1000", "DECLINED");
        MetricDSLDefinition definition = definition(List.of("currency"), Map.of(), null,
                Map.of("totalCount", measure(MetricAggregation.COUNT, null), "count", measure(MetricAggregation.COUNT, settled()),
                        "sum", measure(MetricAggregation.SUM, settled())));

        Map<String, Number> result = execute(definition, new MetricQuery("user-1", START, END, Map.of("currency", "USD"), null));

        assertEquals(2L, result.get("count"));
        assertEquals(3L, result.get("totalCount"));
        assertEquals(new BigDecimal("12.7500000000"), result.get("sum"));
    }

    /**
     * 前 N 笔先筛选成功付款，再按时间和唯一 ID 稳定排序；参数 LIMIT 和固定 LIMIT 都在聚合前生效。
     */
    @Test
    void testTopNFiltersAndOrdersBeforeAggregation() throws SQLException {
        insert(1, "user-1", "USD", START, "1000", "DECLINED");
        insert(2, "user-1", "USD", MIDDLE, "3", "SETTLED");
        insert(3, "user-1", "USD", MIDDLE, "4", "SETTLED");
        insert(4, "user-1", "USD", MIDDLE, "5", "SETTLED");
        List<MetricOrderByDsl> order = List.of(new MetricOrderByDsl("occurredAt", MetricSortDirection.ASC),
                new MetricOrderByDsl("id", MetricSortDirection.ASC));
        Map<String, MetricValueDsl> fields = Map.of("sum", measure(MetricAggregation.SUM, null));
        MetricDSLDefinition parameterized = definition(List.of(), Map.of("limit", new MetricQueryParameterDsl(MetricValueType.INTEGER, 1, 10)),
                new MetricRowSelectionDsl(settled(), order, new MetricLimitDsl(null, "limit")), fields);
        MetricDSLDefinition fixed = definition(List.of(), Map.of(),
                new MetricRowSelectionDsl(settled(), order, new MetricLimitDsl(2, null)), fields);

        assertEquals(new BigDecimal("3.0000000000"), execute(parameterized,
                new MetricQuery("user-1", START, END, null, Map.of("limit", 1))).get("sum"));
        assertEquals(new BigDecimal("7.0000000000"), execute(parameterized,
                new MetricQuery("user-1", START, END, null, Map.of("limit", 2))).get("sum"));
        assertEquals(new BigDecimal("7.0000000000"), execute(fixed, query("user-1", START, END)).get("sum"));
    }

    /**
     * 空增量的 SUM/MIN 保持 SQL NULL；与已有 MIN=7 合并后仍为7，不能提前用 orElse=0 污染累计值。
     */
    @Test
    void testEmptyIncrementDoesNotResetSavedMinimum() throws SQLException {
        insert(1, "user-1", "USD", START, "7", "SETTLED");
        MetricDSLDefinition definition = definition(Map.of("count", measure(MetricAggregation.COUNT, null),
                "sum", measure(MetricAggregation.SUM, null), "minimum", measure(MetricAggregation.MIN, null)));
        Map<String, Number> previous = execute(definition, query("user-1", START, MIDDLE));
        Map<String, Number> increment = execute(definition, query("user-1", MIDDLE, END));

        assertEquals(0L, increment.get("count"));
        assertNull(increment.get("sum"));
        assertNull(increment.get("minimum"));
        MetricValueCalculator calculator = new MetricValueCalculator();
        Map<String, Number> result = calculator.calculate(definition, calculator.merge(definition, List.of(previous, increment)),
                (field, raw) -> { throw new AssertionError("No expression is declared"); });
        assertEquals(1L, result.get("count"));
        assertEquals(new BigDecimal("7.0000"), result.get("minimum"));
        assertEquals(new BigDecimal("7.0000"), result.get("sum"));
    }

    /**
     * 两段 SUM/COUNT 先精确合并再计算均值；结果应与全窗统计相同，不能平均分段均值或提前舍入。
     */
    @Test
    void testSplitAndFullWindowsProduceSameWeightedAverage() throws SQLException {
        insert(1, "user-1", "USD", START, "400.0000000001", "SETTLED");
        insert(2, "user-1", "USD", MIDDLE, "100.0000000002", "SETTLED");
        insert(3, "user-1", "USD", MIDDLE.plusHours(1), "100.0000000003", "SETTLED");
        MetricValueDsl average = new MetricValueDsl(MetricValueType.DECIMAL, 4, RoundingMode.HALF_UP, null,
                new MetricExpressionDsl(MetricExpressionType.SPEL, "count == 0 ? null : ratio(sum, count)"),
                new MetricOrElseDsl(MetricOrElseMode.NULL, null));
        MetricDSLDefinition definition = definition(Map.of("count", measure(MetricAggregation.COUNT, null),
                "sum", measure(MetricAggregation.SUM, null), "average", average));
        MetricExpression expression = new MetricExpressionCompiler().compile(average.expression(), Set.of("sum", "count"), "/metric/fields/average/expression");
        MetricValueCalculator calculator = new MetricValueCalculator();
        Map<String, Number> merged = calculator.merge(definition, List.of(execute(definition, query("user-1", START, MIDDLE)),
                execute(definition, query("user-1", MIDDLE, END))));

        assertEquals(new BigDecimal("600.0000000006"), merged.get("sum"));
        Map<String, Number> result = calculator.calculate(definition, merged,
                (field, raw) -> expression.evaluate(average, raw, Map.of(), "/metric/fields/average"));
        Map<String, Number> full = calculator.calculate(definition, execute(definition, query("user-1", START, END)),
                (field, raw) -> expression.evaluate(average, raw, Map.of(), "/metric/fields/average"));
        assertEquals(full, result);
        assertEquals(3L, result.get("count"));
        assertEquals(new BigDecimal("200.0000"), result.get("average"));
    }

    /** 含 SQL 特殊字符的主体作为参数值绑定，只统计该主体，不能改变 WHERE 范围。 */
    @Test
    void testSubjectIsBoundAsData() throws SQLException {
        String subject = "user' OR 1=1 --";
        insert(1, subject, "USD", START, "7", "SETTLED");
        insert(2, "other", "USD", START, "1000", "SETTLED");
        Map<String, Number> result = execute(definition(Map.of("count", measure(MetricAggregation.COUNT, null))), query(subject, START, END));

        assertEquals(1L, result.get("count"));
    }

    /** 事实表内部主体经多对一关联映射到外部用户；过滤应使用关联表身份，不能统计其他用户。 */
    @Test
    void testJoinedSubjectUsesExternalIdentity() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO account_fact VALUES ('account-1', 'user-1'), ('account-2', 'user-2')");
        }
        insert(1, "account-1", "USD", START, "7", "SETTLED");
        insert(2, "account-2", "USD", START, "1000", "SETTLED");
        MetricJoinDsl join = new MetricJoinDsl("account", "Account", MetricJoinType.INNER, MetricJoinCardinality.MANY_TO_ONE,
                List.of(new MetricJoinOnDsl("subjectId", "externalId")));
        MetricDSLDefinition definition = new MetricDSLDefinition("PAYMENT", 1, MetricValueShape.FIELD_SET, "Payment", List.of(join),
                new MetricSubjectDsl("USER", "account.ownerId"), new MetricTimeDsl("occurredAt"), List.of(), Map.of(), null, null,
                Map.of("sum", measure(MetricAggregation.SUM, null)));

        assertEquals(new BigDecimal("7.0000000000"), execute(definition, query("user-1", START, END)).get("sum"));
    }

    private Map<String, Number> execute(MetricDSLDefinition definition, MetricQuery query) throws SQLException {
        MetricSqlDescriptor sql = compiler.compile(definition, query, mapping);
        try (PreparedStatement statement = connection.prepareStatement(sql.sql())) {
            for (int index = 0; index < sql.bindings().size(); index++) {
                MetricJdbcParameterBinding binding = sql.bindings().get(index);
                statement.setObject(index + 1, binding.value(), binding.jdbcType());
            }
            try (ResultSet rows = statement.executeQuery()) {
                assertTrue(rows.next(), "An aggregate query returns one row even for empty facts");
                assertEquals(sql.projections().size(), rows.getMetaData().getColumnCount());
                Map<String, Number> values = new LinkedHashMap<>();
                for (Map.Entry<String, String> projection : sql.projections().entrySet()) {
                    Object value = rows.getObject(projection.getKey());
                    Number number = null;
                    if (value != null) {
                        number = assertInstanceOf(Number.class, value);
                    }
                    values.put(projection.getValue(), number);
                }
                assertFalse(rows.next(), "A metric query must return exactly one aggregate row");
                return values;
            }
        }
    }

    private void insert(long id, String subject, String currency, LocalDateTime time, String amount, String state) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO payment_fact VALUES (?, ?, ?, ?, ?, ?)")) {
            statement.setLong(1, id);
            statement.setString(2, subject);
            statement.setString(3, currency);
            statement.setObject(4, time);
            statement.setBigDecimal(5, new BigDecimal(amount));
            statement.setString(6, state);
            statement.executeUpdate();
        }
    }

    private static MetricQuery query(String subject, LocalDateTime start, LocalDateTime end) {
        return new MetricQuery(subject, start, end, null, null);
    }

    private static MetricFilterDsl settled() {
        return new ComparisonMetricFilterDsl(MetricFilterOperator.EQ, "state", new StringMetricLiteralDsl("SETTLED"));
    }

    private static MetricValueDsl measure(MetricAggregation aggregation, MetricFilterDsl filter) {
        if (aggregation == MetricAggregation.COUNT) {
            return new MetricValueDsl(MetricValueType.LONG, null, null, new MetricMeasureDsl(aggregation, null, filter),
                    null, new MetricOrElseDsl(MetricOrElseMode.ZERO, null));
        }
        return new MetricValueDsl(MetricValueType.DECIMAL, 4, RoundingMode.HALF_UP, new MetricMeasureDsl(aggregation, "amount", filter),
                null, new MetricOrElseDsl(MetricOrElseMode.ZERO, null));
    }

    private static MetricDSLDefinition definition(Map<String, MetricValueDsl> fields) {
        return definition(List.of(), Map.of(), null, fields);
    }

    private static MetricDSLDefinition definition(List<String> dimensions, Map<String, MetricQueryParameterDsl> parameters,
                                                  MetricRowSelectionDsl selection, Map<String, MetricValueDsl> fields) {
        return new MetricDSLDefinition("PAYMENT", 1, MetricValueShape.FIELD_SET, "Payment", List.of(), new MetricSubjectDsl("USER", "subjectId"),
                new MetricTimeDsl("occurredAt"), dimensions, parameters, selection, null, fields);
    }

    /** 宿主字段映射替身；编译、参数绑定、SQL 执行、合并和表达式均使用真实代码。 */
    private static final class PaymentMapping implements MetricJdbcMapping {

        private final Map<String, String> columns = Map.of("id", "id", "subjectId", "subject_id", "currency", "currency", "occurredAt", "occurred_at",
                "amount", "amount", "state", "state", "account.externalId", "external_id", "account.ownerId", "owner_id");

        @Override
        public String tableName(String factReference) {
            return Map.of("", "payment_fact", "account", "account_fact").get(factReference);
        }

        @Override
        public String columnName(String fieldReference) {
            return columns.get(fieldReference);
        }

        @Override
        public Class<?> javaType(String fieldReference) {
            return switch (fieldReference) {
                case "id" -> Long.class;
                case "amount" -> BigDecimal.class;
                case "occurredAt" -> LocalDateTime.class;
                default -> String.class;
            };
        }

        @Override
        public int jdbcType(String fieldReference) {
            return switch (fieldReference) {
                case "id" -> Types.BIGINT;
                case "amount" -> Types.DECIMAL;
                case "occurredAt" -> Types.TIMESTAMP;
                default -> Types.VARCHAR;
            };
        }

        @Override
        public Object toJdbcValue(String fieldReference, Object normalizedValue) {
            return normalizedValue;
        }
    }
}
