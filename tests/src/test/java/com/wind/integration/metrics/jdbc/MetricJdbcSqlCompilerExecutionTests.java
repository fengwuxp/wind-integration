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
import com.wind.integration.metrics.spec.MetricDefinition;
import com.wind.integration.metrics.spec.MetricDefinitionSpec;
import com.wind.integration.metrics.spec.MetricSqlDefinition;
import com.wind.jackson.WindJson;
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
        MetricDefinition definition = definition(List.of("currency"), Map.of(), null,
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
        MetricDefinition parameterized = definition(List.of(), Map.of("limit", new MetricQueryParameterDsl(MetricValueType.INTEGER, 1, 10)),
                new MetricRowSelectionDsl(settled(), order, new MetricLimitDsl(null, "limit")), fields);
        MetricDefinition fixed = definition(List.of(), Map.of(),
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
        MetricDefinition definition = definition(Map.of("count", measure(MetricAggregation.COUNT, null),
                "sum", measure(MetricAggregation.SUM, null), "minimum", measure(MetricAggregation.MIN, null)));
        Map<String, Number> previous = execute(definition, query("user-1", START, MIDDLE));
        Map<String, Number> increment = execute(definition, query("user-1", MIDDLE, END));

        assertEquals(0L, increment.get("count"));
        assertNull(increment.get("sum"));
        assertNull(increment.get("minimum"));
        MetricValueCalculator calculator = new MetricValueCalculator();
        Map<String, Object> result = calculator.calculate(definition, calculator.merge(definition, List.of(previous, increment)),
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
        MetricDefinition definition = definition(Map.of("count", measure(MetricAggregation.COUNT, null),
                "sum", measure(MetricAggregation.SUM, null), "average", average));
        MetricExpression expression = new MetricExpressionCompiler().compile(average.expression(), Set.of("sum", "count"), "/metric/fields/average/expression");
        MetricValueCalculator calculator = new MetricValueCalculator();
        Map<String, Number> merged = calculator.merge(definition, List.of(execute(definition, query("user-1", START, MIDDLE)),
                execute(definition, query("user-1", MIDDLE, END))));

        assertEquals(new BigDecimal("600.0000000006"), merged.get("sum"));
        Map<String, Object> result = calculator.calculate(definition, merged,
                (field, raw) -> expression.evaluate(average, raw, Map.of(), "/metric/fields/average"));
        Map<String, Object> full = calculator.calculate(definition, execute(definition, query("user-1", START, END)),
                (field, raw) -> expression.evaluate(average, raw, Map.of(), "/metric/fields/average"));
        assertEquals(full, result);
        assertEquals(3L, result.get("count"));
        assertEquals(new BigDecimal("200.0000"), result.get("average"));
    }

    /**
     * 场景：SQL 与 DSL 只改变原始量取值方式，跨段累计和本地表达式使用相同流程。
     * 输入：两日三笔付款 400.0000000001、100.0000000002、100.0000000003，另有其他主体干扰。
     * 流程：共同定义 JSON 往返；分别执行 DSL/SQL 两段取值，合并 SUM/COUNT，再运行真实均值表达式。
     * 预期：两种取值的全精度原始量相等，累计结果等于全窗查询；最终均值200.0000。
     */
    @Test
    void testSqlAndDslShareMergeAndExpressionPipeline() throws SQLException {
        insert(1, "user-1", "USD", START, "400.0000000001", "SETTLED");
        insert(2, "user-1", "USD", MIDDLE, "100.0000000002", "SETTLED");
        insert(3, "user-1", "USD", MIDDLE.plusHours(1), "100.0000000003", "SETTLED");
        insert(4, "user-2", "USD", START, "9000", "SETTLED");
        MetricValueDsl average = new MetricValueDsl(MetricValueType.DECIMAL, 4, RoundingMode.HALF_UP, null,
                new MetricExpressionDsl(MetricExpressionType.SPEL, "ratio(sum, count)"),
                new MetricOrElseDsl(MetricOrElseMode.NULL, null));
        MetricDefinition dsl = definition(Map.of("count", measure(MetricAggregation.COUNT, null),
                "sum", measure(MetricAggregation.SUM, null), "average", average));
        Map<String, MetricValueDsl> sqlFields = new LinkedHashMap<>(dsl.fields());
        MetricValueDsl sum = dsl.fields().get("sum");
        sqlFields.put("sum", new MetricValueDsl(sum.valueType(), sum.scale(), sum.roundingMode(),
                new MetricMeasureDsl(MetricAggregation.SUM, null, null), null, sum.orElse()));
        MetricDefinition sql = new MetricDefinition("SQL_PAYMENT", 7, dsl.valueShape(),
                new MetricSubjectDsl("USER", null), List.of(), Map.of(), new MetricSqlDefinition("""
                    SELECT COUNT(*) AS "count", SUM(amount) AS "sum" FROM payment_fact
                    WHERE subject_id = '${subjectId}' AND occurred_at >= '${startTime}' AND occurred_at < '${endTime}'
                    """), null, sqlFields, List.of());
        sql = WindJson.parseObject(WindJson.toJsonString(new MetricDefinitionSpec(6, sql)), MetricDefinitionSpec.class).definition();
        FreemarkerMetricSqlRenderer renderer = new FreemarkerMetricSqlRenderer();
        MetricValueCalculator calculator = new MetricValueCalculator();
        Map<String, Number> sqlFirst = execute(renderer.generate(sql, query("user-1", START, MIDDLE)));
        Map<String, Number> sqlSecond = execute(renderer.generate(sql, query("user-1", MIDDLE, END)));
        assertEquals(execute(dsl, query("user-1", START, MIDDLE)), sqlFirst);
        assertEquals(execute(dsl, query("user-1", MIDDLE, END)), sqlSecond);
        Map<String, Number> merged = calculator.merge(sql, List.of(sqlFirst, sqlSecond));
        assertEquals(new BigDecimal("600.0000000006"), merged.get("sum"));
        MetricExpression expression = new MetricExpressionCompiler().compile(average.expression(), Set.of("sum", "count"), "/metric/fields/average");
        Map<String, Object> result = calculator.calculate(sql, merged,
                (field, inputs) -> expression.evaluate(average, inputs, Map.of(), "/metric/fields/average"));
        assertEquals(calculator.calculate(dsl, execute(dsl, query("user-1", START, END)),
                (field, inputs) -> expression.evaluate(average, inputs, Map.of(), "/metric/fields/average")), result);
        assertEquals(new BigDecimal("200.0000"), result.get("average"));
    }

    /**
     * 场景：同一秒内的付款增量仍是有效半开窗口，SQL 与 DSL 必须保留相同时间边界。
     * 输入：[00:00:00.250,00:00:00.750)，起点及终点前各一笔；另有起点前、终点和其他主体的干扰记录。
     * 流程：分别经真实 DSL 编译和 SQL 模板渲染，在 H2 读取原始 COUNT/SUM。
     * 预期：两种方式都只包含两笔，金额3.00018；边界不能被截成整秒，也不能误包含终点。
     */
    @Test
    void testSqlAndDslKeepMillisecondHalfOpenWindow() throws SQLException {
        LocalDateTime start = START.plusNanos(250_000_000);
        LocalDateTime end = START.plusNanos(750_000_000);
        insert(1, "user-1", "USD", start.minusNanos(1), "9000", "SETTLED");
        insert(2, "user-1", "USD", start, "1.00009", "SETTLED");
        insert(3, "user-1", "USD", end.minusNanos(1), "2.00009", "SETTLED");
        insert(4, "user-1", "USD", end, "9000", "SETTLED");
        insert(5, "user-2", "USD", start, "9000", "SETTLED");
        MetricValueDsl count = measure(MetricAggregation.COUNT, null);
        MetricValueDsl dslSum = measure(MetricAggregation.SUM, null);
        MetricDefinition dsl = definition(Map.of("count", count, "sum", dslSum));
        MetricValueDsl sqlSum = new MetricValueDsl(dslSum.valueType(), dslSum.scale(), dslSum.roundingMode(),
                new MetricMeasureDsl(MetricAggregation.SUM, null, null), null, dslSum.orElse());
        MetricDefinition sql = new MetricDefinition("SQL_PAYMENT", 7, MetricValueShape.FIELD_SET,
                new MetricSubjectDsl("USER", null), List.of(), Map.of(),
                new MetricSqlDefinition("""
                    SELECT COUNT(*) AS "count", SUM(amount) AS "sum" FROM payment_fact
                    WHERE subject_id = '${subjectId}' AND occurred_at >= '${startTime}' AND occurred_at < '${endTime}'
                    """), null, Map.of("count", count, "sum", sqlSum), List.of());
        Map<String, Number> expected = Map.of("count", 2L, "sum", new BigDecimal("3.0001800000"));
        MetricQuery query = query("user-1", start, end);

        assertEquals(expected, execute(dsl, query));
        assertEquals(expected, execute(new FreemarkerMetricSqlRenderer().generate(sql, query)));
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
        MetricDefinition definition = new MetricDefinition("PAYMENT", 1, MetricValueShape.FIELD_SET,
                new MetricSubjectDsl("USER", "account.ownerId"), List.of(), Map.of(),
                new MetricDSLDefinition("Payment", List.of(join), new MetricTimeDsl("occurredAt"), null),
                null, Map.of("sum", measure(MetricAggregation.SUM, null)), List.of());

        assertEquals(new BigDecimal("7.0000000000"), execute(definition, query("user-1", START, END)).get("sum"));
    }

    private Map<String, Number> execute(MetricDefinition definition, MetricQuery query) throws SQLException {
        return execute(compiler.compile(definition, query, mapping));
    }

    private Map<String, Number> execute(MetricSqlDescriptor sql) throws SQLException {
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

    private static MetricDefinition definition(Map<String, MetricValueDsl> fields) {
        return definition(List.of(), Map.of(), null, fields);
    }

    private static MetricDefinition definition(List<String> dimensions, Map<String, MetricQueryParameterDsl> parameters,
                                                  MetricRowSelectionDsl selection, Map<String, MetricValueDsl> fields) {
        return new MetricDefinition("PAYMENT", 1, MetricValueShape.FIELD_SET,
                new MetricSubjectDsl("USER", "subjectId"), dimensions, parameters,
                new MetricDSLDefinition("Payment", List.of(), new MetricTimeDsl("occurredAt"), selection),
                null, fields, List.of());
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
