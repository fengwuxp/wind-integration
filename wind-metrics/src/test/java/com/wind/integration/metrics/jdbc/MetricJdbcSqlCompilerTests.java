package com.wind.integration.metrics.jdbc;

import com.wind.integration.metrics.MetricValidationException;
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
import com.wind.integration.metrics.dsl.filter.LogicalMetricFilterDsl;
import com.wind.integration.metrics.dsl.filter.MetricFilterDsl;
import com.wind.integration.metrics.dsl.filter.NullMetricFilterDsl;
import com.wind.integration.metrics.dsl.filter.SetMetricFilterDsl;
import com.wind.integration.metrics.dsl.literal.IntegralMetricLiteralDsl;
import com.wind.integration.metrics.dsl.literal.StringMetricLiteralDsl;
import com.wind.integration.metrics.enums.MetricAggregation;
import com.wind.integration.metrics.enums.MetricErrorCode;
import com.wind.integration.metrics.enums.MetricFilterOperator;
import com.wind.integration.metrics.enums.MetricJoinCardinality;
import com.wind.integration.metrics.enums.MetricJoinType;
import com.wind.integration.metrics.enums.MetricOrElseMode;
import com.wind.integration.metrics.enums.MetricSortDirection;
import com.wind.integration.metrics.enums.MetricValueShape;
import com.wind.integration.metrics.enums.MetricValueType;
import com.wind.integration.metrics.query.MetricQuery;
import com.wind.integration.metrics.spec.MetricDSLDefinition;
import org.jooq.SQLDialect;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link MetricJdbcSqlCompiler} 公开契约的 TDD 用例。
 *
 * <p>只观察 {@code compile} 与 {@code renderValidatedFilter} 的 SQL、有序参数、投影和校验异常，
 * 不感知渲染内部实现。所有标识符均受控，参数统一以 {@code ?} 占位符表达。</p>
 *
 * @author wuxp
 */
class MetricJdbcSqlCompilerTests {

    private static final ZoneId UTC = ZoneId.of("UTC");

    private static final LocalDateTime START = LocalDateTime.of(2026, 9, 1, 0, 0);

    private static final LocalDateTime END = START.plusDays(1);

    private static final Instant START_INSTANT = START.atZone(UTC).toInstant();

    private static final Instant END_INSTANT = END.atZone(UTC).toInstant();

    // 构造器契约

    @Test
    void testConstructorRejectsNullDialect() {
        assertThrows(NullPointerException.class,
                () -> new MetricJdbcSqlCompiler(UTC, 1000, null));
    }

    @Test
    void testConstructorRejectsNullTimeZone() {
        assertThrows(NullPointerException.class,
                () -> new MetricJdbcSqlCompiler(null));
    }

    @Test
    void testConstructorRejectsUnsupportedDialect() {
        assertThrows(IllegalArgumentException.class,
                () -> new MetricJdbcSqlCompiler(UTC, 1000, SQLDialect.SQLITE));
    }

    @Test
    void testConstructorRejectsNonPositiveRowSelectionLimit() {
        for (int limit : List.of(0, -1)) {
            assertThrows(IllegalArgumentException.class,
                    () -> new MetricJdbcSqlCompiler(UTC, limit));
        }
    }

    @Test
    void testConstructorAcceptsSupportedDialects() {
        for (SQLDialect dialect : List.of(SQLDialect.MYSQL, SQLDialect.POSTGRES, SQLDialect.H2)) {
            assertDoesNotThrow(() -> new MetricJdbcSqlCompiler(UTC, 1000, dialect));
        }
    }

    // 实时查询：投影、谓词、关联与有限行集

    @Test
    void testGlobalCountRendersWithoutSubjectPredicate() {
        MetricSqlDescriptor result = compiler().compile(definition().build(), query(), binding());

        assertEquals("SELECT count(*) AS `value` FROM `order_fact` AS `p`"
                + " WHERE (`p`.`created_at` >= ? AND `p`.`created_at` < ?)", result.sql());
        assertEquals(Map.of("value", "value"), result.projections());
        assertBindings(result.bindings(),
                START_INSTANT, Types.TIMESTAMP,
                END_INSTANT, Types.TIMESTAMP);
    }

    @Test
    void testSumMeasureWithFilterRendersConditionalAggregate() {
        MetricMeasureDsl measure = measure(MetricAggregation.SUM, "amount",
                new ComparisonMetricFilterDsl(MetricFilterOperator.GT, "amount",
                        new IntegralMetricLiteralDsl(BigInteger.valueOf(100))));
        MetricDSLDefinition definition = definition().value(value(measure)).build();

        MetricSqlDescriptor result = compiler().compile(definition, query(), binding());

        assertEquals("SELECT SUM(CASE WHEN `p`.`amount` > ? THEN `p`.`amount` END) AS `value`"
                + " FROM `order_fact` AS `p` WHERE (`p`.`created_at` >= ? AND `p`.`created_at` < ?)",
                result.sql());
        assertBindings(result.bindings(),
                new BigDecimal("100"), Types.DECIMAL,
                START_INSTANT, Types.TIMESTAMP,
                END_INSTANT, Types.TIMESTAMP);
    }

    @Test
    void testSubjectMetricFiltersBySubjectId() {
        MetricDSLDefinition definition = definition()
                .subject(new MetricSubjectDsl("USER", "user_id"))
                .build();

        MetricSqlDescriptor result = compiler().compile(definition, query("user-1"), binding());

        assertEquals("SELECT count(*) AS `value` FROM `order_fact` AS `p`"
                + " WHERE (`p`.`user_id` = ? AND `p`.`created_at` >= ? AND `p`.`created_at` < ?)",
                result.sql());
        assertBindings(result.bindings(),
                "user-1", Types.VARCHAR,
                START_INSTANT, Types.TIMESTAMP,
                END_INSTANT, Types.TIMESTAMP);
    }

    @Test
    void testDimensionsRenderAsEqualityInSortedFieldOrder() {
        MetricDSLDefinition definition = definition().dimensions(List.of("region", "channel")).build();

        MetricSqlDescriptor result = compiler().compile(definition,
                query(Map.of("region", "CN", "channel", "APP")), binding());

        assertEquals("SELECT count(*) AS `value` FROM `order_fact` AS `p`"
                + " WHERE (`p`.`created_at` >= ? AND `p`.`created_at` < ?"
                + " AND `p`.`channel` = ? AND `p`.`region` = ?)", result.sql());
        assertBindings(result.bindings(),
                START_INSTANT, Types.TIMESTAMP,
                END_INSTANT, Types.TIMESTAMP,
                "APP", Types.VARCHAR,
                "CN", Types.VARCHAR);
    }

    @Test
    void testFieldSetRendersMultipleMeasuresInFieldOrder() {
        MetricDSLDefinition definition = definition()
                .shape(MetricValueShape.FIELD_SET)
                .fields(Map.of(
                        "revenue", value(measure(MetricAggregation.SUM, "amount", null)),
                        "orders", value(measure(MetricAggregation.COUNT, null, null))))
                .build();

        MetricSqlDescriptor result = compiler().compile(definition, query(), binding());

        assertEquals("SELECT count(*) AS `orders`, SUM(`p`.`amount`) AS `revenue`"
                + " FROM `order_fact` AS `p` WHERE (`p`.`created_at` >= ? AND `p`.`created_at` < ?)",
                result.sql());
        assertEquals(Map.of("orders", "orders", "revenue", "revenue"), result.projections());
        assertBindings(result.bindings(),
                START_INSTANT, Types.TIMESTAMP,
                END_INSTANT, Types.TIMESTAMP);
    }

    @Test
    void testInnerJoinRendersJoinAliasAndConditions() {
        MetricDSLDefinition definition = definition()
                .joins(List.of(join(MetricJoinType.INNER)))
                .build();

        MetricSqlDescriptor result = compiler().compile(definition, query(), joinBinding());

        assertEquals("SELECT count(*) AS `value` FROM `order_fact` AS `p` JOIN `customer_fact` AS `j0`"
                + " ON `p`.`customer_id` = `j0`.`id` WHERE (`p`.`created_at` >= ? AND `p`.`created_at` < ?)",
                result.sql());
        assertBindings(result.bindings(),
                START_INSTANT, Types.TIMESTAMP,
                END_INSTANT, Types.TIMESTAMP);
    }

    @Test
    void testLeftJoinRendersOuterJoin() {
        MetricDSLDefinition definition = definition()
                .joins(List.of(join(MetricJoinType.LEFT)))
                .build();

        MetricSqlDescriptor result = compiler().compile(definition, query(), joinBinding());

        assertEquals("SELECT count(*) AS `value` FROM `order_fact` AS `p` LEFT OUTER JOIN `customer_fact` AS `j0`"
                + " ON `p`.`customer_id` = `j0`.`id` WHERE (`p`.`created_at` >= ? AND `p`.`created_at` < ?)",
                result.sql());
        assertBindings(result.bindings(),
                START_INSTANT, Types.TIMESTAMP,
                END_INSTANT, Types.TIMESTAMP);
    }

    @Test
    void testRowSelectionRendersOrderedLimitedSubquery() {
        MetricRowSelectionDsl selection = new MetricRowSelectionDsl(null,
                List.of(new MetricOrderByDsl("created_at", MetricSortDirection.DESC)),
                new MetricLimitDsl(10, null));
        MetricDSLDefinition definition = definition().rowSelection(selection).build();

        MetricSqlDescriptor result = compiler().compile(definition, query(), binding());

        assertEquals("SELECT count(*) AS `value` FROM (SELECT `p`.`created_at` AS `c0`"
                + " FROM `order_fact` AS `p` WHERE (`p`.`created_at` >= ? AND `p`.`created_at` < ?)"
                + " ORDER BY `p`.`created_at` DESC LIMIT ?) AS `r`", result.sql());
        assertEquals(Map.of("value", "value"), result.projections());
        assertBindings(result.bindings(),
                START_INSTANT, Types.TIMESTAMP,
                END_INSTANT, Types.TIMESTAMP,
                10, Types.INTEGER);
    }

    @Test
    void testParameterizedRowSelectionLimitRendersBoundLimit() {
        MetricRowSelectionDsl selection = new MetricRowSelectionDsl(null,
                List.of(new MetricOrderByDsl("created_at", MetricSortDirection.DESC)),
                new MetricLimitDsl(null, "entryLimit"));
        MetricDSLDefinition definition = definition()
                .parameters(Map.of("entryLimit", parameter(null, null)))
                .rowSelection(selection)
                .build();

        MetricSqlDescriptor result = compiler().compile(definition, query(Map.of(), Map.of("entryLimit", 10)), binding());

        assertEquals("SELECT count(*) AS `value` FROM (SELECT `p`.`created_at` AS `c0`"
                + " FROM `order_fact` AS `p` WHERE (`p`.`created_at` >= ? AND `p`.`created_at` < ?)"
                + " ORDER BY `p`.`created_at` DESC LIMIT ?) AS `r`", result.sql());
        assertBindings(result.bindings(),
                START_INSTANT, Types.TIMESTAMP,
                END_INSTANT, Types.TIMESTAMP,
                10, Types.INTEGER);
    }

    // 校验错误

    @Test
    void testCompileRejectsNullDefinitionOrQuery() {
        assertValidation(MetricErrorCode.QUERY_INVALID, "",
                () -> compiler().compile(null, query(), binding()));
        assertValidation(MetricErrorCode.QUERY_INVALID, "",
                () -> compiler().compile(definition().build(), null, binding()));
    }

    @Test
    void testCompileRejectsDerivedMetric() {
        assertValidation(MetricErrorCode.METRIC_EXECUTION_MODE_UNSUPPORTED, "/metric/fact",
                () -> compiler().compile(definition().fact(null).build(), query(), binding()));
    }

    @Test
    void testCompileRejectsSubjectTypeMismatch() {
        MetricDSLDefinition definition = definition()
                .subject(new MetricSubjectDsl("USER", "user_id"))
                .build();
        MetricQuery query = MetricQuery.builder()
                .subjectId("user-1")
                .subjectType("APP")
                .timeRange(START, END)
                .dimensions(Map.of())
                .parameters(Map.of())
                .build();

        assertValidation(MetricErrorCode.QUERY_INVALID, "/subjectType",
                () -> compiler().compile(definition, query, binding()));
    }

    @Test
    void testCompileRejectsSubjectIdForGlobalMetric() {
        assertValidation(MetricErrorCode.QUERY_INVALID, "/subjectId",
                () -> compiler().compile(definition().build(), query("user-1"), binding()));
    }

    @Test
    void testCompileRejectsMissingSubjectIdForSubjectMetric() {
        MetricDSLDefinition definition = definition()
                .subject(new MetricSubjectDsl("USER", "user_id"))
                .build();

        assertValidation(MetricErrorCode.QUERY_INVALID, "/subjectId",
                () -> compiler().compile(definition, query(), binding()));
    }

    @Test
    void testCompileRejectsDimensionKeyMismatch() {
        MetricDSLDefinition definition = definition().dimensions(List.of("region")).build();
        MetricJdbcSqlCompiler compiler = compiler();
        MetricJdbcBinding binding = binding();

        assertValidation(MetricErrorCode.QUERY_INVALID, "/dimensionValues",
                () -> compiler.compile(definition, query(), binding));
        assertValidation(MetricErrorCode.QUERY_INVALID, "/dimensionValues",
                () -> compiler.compile(definition, query(Map.of("region", "CN", "channel", "APP")), binding));
    }

    @Test
    void testCompileRejectsUndeclaredParameter() {
        MetricQuery query = query(Map.of(), Map.of("entryLimit", 2));

        assertValidation(MetricErrorCode.METRIC_PARAMETER_UNEXPECTED, "/parameterValues/entryLimit",
                () -> compiler().compile(definition().build(), query, binding()));
    }

    @Test
    void testCompileRejectsMissingParameter() {
        MetricDSLDefinition definition = definition()
                .parameters(Map.of("entryLimit", parameter(1, 10)))
                .build();

        assertValidation(MetricErrorCode.METRIC_PARAMETER_MISSING, "/parameterValues/entryLimit",
                () -> compiler().compile(definition, query(), binding()));
    }

    @Test
    void testCompileRejectsOutOfRangeParameter() {
        MetricDSLDefinition definition = definition()
                .parameters(Map.of("entryLimit", parameter(1, 10)))
                .build();

        assertValidation(MetricErrorCode.METRIC_PARAMETER_OUT_OF_RANGE, "/parameterValues/entryLimit",
                () -> compiler().compile(definition, query(Map.of(), Map.of("entryLimit", 11)), binding()));
    }

    @Test
    void testCompileRejectsFixedRowSelectionLimitOutOfRange() {
        MetricRowSelectionDsl selection = new MetricRowSelectionDsl(null,
                List.of(new MetricOrderByDsl("created_at", MetricSortDirection.DESC)),
                new MetricLimitDsl(5000, null));
        MetricDSLDefinition definition = definition().rowSelection(selection).build();

        assertValidation(MetricErrorCode.DSL_VALUE_INVALID, "/metric/rowSelection/limit/value",
                () -> compiler().compile(definition, query(), binding()));
    }

    @Test
    void testCompileRejectsParameterizedRowSelectionLimitOutOfRange() {
        MetricRowSelectionDsl selection = new MetricRowSelectionDsl(null,
                List.of(new MetricOrderByDsl("created_at", MetricSortDirection.DESC)),
                new MetricLimitDsl(null, "entryLimit"));
        MetricDSLDefinition definition = definition()
                .parameters(Map.of("entryLimit", parameter(null, null)))
                .rowSelection(selection)
                .build();

        assertValidation(MetricErrorCode.METRIC_PARAMETER_OUT_OF_RANGE, "/parameterValues/entryLimit",
                () -> compiler().compile(definition, query(Map.of(), Map.of("entryLimit", 5000)), binding()));
    }

    @Test
    void testCompileRejectsMetricWithoutMeasures() {
        MetricDSLDefinition definition = definition()
                .shape(MetricValueShape.FIELD_SET)
                .fields(Map.of())
                .build();

        assertValidation(MetricErrorCode.DSL_VALUE_INVALID, "/metric/value",
                () -> compiler().compile(definition, query(), binding()));
    }

    // 过滤条件渲染

    @Test
    void testRenderValidatedFilterComparison() {
        MetricJdbcSqlCompiler compiler = compiler();
        List<MetricSqlBinding> bindings = new ArrayList<>();
        MetricFilterDsl filter = new ComparisonMetricFilterDsl(MetricFilterOperator.EQ, "status",
                new StringMetricLiteralDsl("APPROVED"));

        String sql = compiler.renderValidatedFilter(binding(), filter, bindings, field -> "p." + field);

        assertEquals("p.status = ?", sql);
        assertBindings(bindings, "APPROVED", Types.VARCHAR);
    }

    @Test
    void testRenderValidatedFilterSetMembership() {
        MetricJdbcSqlCompiler compiler = compiler();
        List<MetricSqlBinding> bindings = new ArrayList<>();
        MetricFilterDsl filter = new SetMetricFilterDsl(MetricFilterOperator.IN, "status",
                List.of(new StringMetricLiteralDsl("APPROVED"), new StringMetricLiteralDsl("PENDING")));

        String sql = compiler.renderValidatedFilter(binding(), filter, bindings, field -> "p." + field);

        assertEquals("p.status IN (?, ?)", sql);
        assertBindings(bindings,
                "APPROVED", Types.VARCHAR,
                "PENDING", Types.VARCHAR);
    }

    @Test
    void testRenderValidatedFilterNullPredicate() {
        MetricJdbcSqlCompiler compiler = compiler();
        List<MetricSqlBinding> bindings = new ArrayList<>();
        MetricFilterDsl filter = new NullMetricFilterDsl(MetricFilterOperator.IS_NULL, "status");

        String sql = compiler.renderValidatedFilter(binding(), filter, bindings, field -> "p." + field);

        assertEquals("p.status IS NULL", sql);
        assertEquals(List.of(), bindings);
    }

    @Test
    void testRenderValidatedFilterLogicalCombination() {
        MetricJdbcSqlCompiler compiler = compiler();
        List<MetricSqlBinding> bindings = new ArrayList<>();
        MetricFilterDsl filter = new LogicalMetricFilterDsl(MetricFilterOperator.AND, List.of(
                new ComparisonMetricFilterDsl(MetricFilterOperator.EQ, "status",
                        new StringMetricLiteralDsl("APPROVED")),
                new NullMetricFilterDsl(MetricFilterOperator.IS_NULL, "region")));

        String sql = compiler.renderValidatedFilter(binding(), filter, bindings, field -> "p." + field);

        assertEquals("(p.status = ? AND p.region IS NULL)", sql);
        assertBindings(bindings, "APPROVED", Types.VARCHAR);
    }

    @Test
    void testRenderValidatedFilterRejectsNullArguments() {
        MetricJdbcSqlCompiler compiler = compiler();
        MetricFilterDsl filter = new NullMetricFilterDsl(MetricFilterOperator.IS_NULL, "status");
        List<MetricSqlBinding> bindings = new ArrayList<>();

        assertThrows(NullPointerException.class,
                () -> compiler.renderValidatedFilter(null, filter, bindings, field -> "p." + field));
        assertThrows(NullPointerException.class,
                () -> compiler.renderValidatedFilter(binding(), null, bindings, field -> "p." + field));
        assertThrows(NullPointerException.class,
                () -> compiler.renderValidatedFilter(binding(), filter, null, field -> "p." + field));
        assertThrows(NullPointerException.class,
                () -> compiler.renderValidatedFilter(binding(), filter, bindings, null));
    }

    // 测试夹具

    private static MetricJdbcSqlCompiler compiler() {
        return new MetricJdbcSqlCompiler(UTC);
    }

    private static MetricQuery query() {
        return new MetricQuery(null, START, END, Map.of(), Map.of());
    }

    private static MetricQuery query(String subjectId) {
        return new MetricQuery(subjectId, START, END, Map.of(), Map.of());
    }

    private static MetricQuery query(Map<String, Object> dimensions) {
        return new MetricQuery(null, START, END, dimensions, Map.of());
    }

    private static MetricQuery query(Map<String, Object> dimensions, Map<String, Object> parameters) {
        return new MetricQuery(null, START, END, dimensions, parameters);
    }

    private static Definition definition() {
        return new Definition();
    }

    private static MetricValueDsl value(MetricMeasureDsl measure) {
        return new MetricValueDsl(MetricValueType.DECIMAL, null, null, measure, null,
                new MetricOrElseDsl(MetricOrElseMode.NULL, null));
    }

    private static MetricMeasureDsl measure(MetricAggregation aggregation, String field, MetricFilterDsl filter) {
        return new MetricMeasureDsl(aggregation, field, filter);
    }

    private static MetricQueryParameterDsl parameter(Integer minimum, Integer maximum) {
        return new MetricQueryParameterDsl(MetricValueType.INTEGER, minimum, maximum);
    }

    private static MetricJoinDsl join(MetricJoinType joinType) {
        return new MetricJoinDsl("c", "customer_fact", joinType, MetricJoinCardinality.MANY_TO_ONE,
                List.of(new MetricJoinOnDsl("customer_id", "id")));
    }

    private static MetricJdbcBinding binding() {
        return new Binding()
                .table("", "order_fact")
                .column("created_at", "created_at").javaType("created_at", Instant.class)
                .jdbcType("created_at", Types.TIMESTAMP)
                .column("amount", "amount").javaType("amount", BigDecimal.class)
                .jdbcType("amount", Types.DECIMAL)
                .column("user_id", "user_id").javaType("user_id", String.class)
                .jdbcType("user_id", Types.VARCHAR)
                .column("region", "region").javaType("region", String.class)
                .jdbcType("region", Types.VARCHAR)
                .column("channel", "channel").javaType("channel", String.class)
                .jdbcType("channel", Types.VARCHAR)
                .column("status", "status").javaType("status", String.class)
                .jdbcType("status", Types.VARCHAR)
                .build();
    }

    private static MetricJdbcBinding joinBinding() {
        return new Binding()
                .table("", "order_fact")
                .column("created_at", "created_at").javaType("created_at", Instant.class)
                .jdbcType("created_at", Types.TIMESTAMP)
                .column("customer_id", "customer_id")
                .table("c", "customer_fact")
                .column("c.id", "id")
                .build();
    }

    private static void assertValidation(MetricErrorCode code, String path, Runnable action) {
        MetricValidationException error = assertThrows(MetricValidationException.class, action::run);
        assertEquals(code, error.errorCode());
        assertEquals(path, error.fieldPath());
    }

    private static void assertBindings(List<MetricSqlBinding> actual, Object... expected) {
        assertEquals(expected.length / 2, actual.size(), "binding count");
        for (int i = 0; i < actual.size(); i++) {
            assertEquals(expected[i * 2], actual.get(i).value(), "binding[" + i + "] value");
            assertEquals(expected[i * 2 + 1], actual.get(i).jdbcType(), "binding[" + i + "] jdbcType");
        }
    }

    /**
     * {@link MetricDSLDefinition} 的构建器，默认是一个全局 SCALAR COUNT 指标。
     */
    private static final class Definition {

        private String fact = "order_fact";

        private MetricValueShape shape = MetricValueShape.SCALAR;

        private MetricSubjectDsl subject = new MetricSubjectDsl(MetricSubjectDsl.GLOBAL, null);

        private MetricTimeDsl time = new MetricTimeDsl("created_at");

        private List<String> dimensions = List.of();

        private Map<String, MetricQueryParameterDsl> parameters = Map.of();

        private MetricRowSelectionDsl rowSelection;

        private MetricValueDsl scalarValue =
                MetricJdbcSqlCompilerTests.value(measure(MetricAggregation.COUNT, null, null));

        private Map<String, MetricValueDsl> fields = Map.of();

        private List<MetricJoinDsl> joins = List.of();

        Definition fact(String fact) {
            this.fact = fact;
            return this;
        }

        Definition shape(MetricValueShape shape) {
            this.shape = shape;
            return this;
        }

        Definition subject(MetricSubjectDsl subject) {
            this.subject = subject;
            return this;
        }

        Definition dimensions(List<String> dimensions) {
            this.dimensions = dimensions;
            return this;
        }

        Definition parameters(Map<String, MetricQueryParameterDsl> parameters) {
            this.parameters = parameters;
            return this;
        }

        Definition rowSelection(MetricRowSelectionDsl rowSelection) {
            this.rowSelection = rowSelection;
            return this;
        }

        Definition value(MetricValueDsl value) {
            this.scalarValue = value;
            return this;
        }

        Definition fields(Map<String, MetricValueDsl> fields) {
            this.fields = fields;
            return this;
        }

        Definition joins(List<MetricJoinDsl> joins) {
            this.joins = joins;
            return this;
        }

        MetricDSLDefinition build() {
            return new MetricDSLDefinition("metric_code", 1, shape, fact, joins, subject, time, dimensions,
                    parameters, rowSelection, scalarValue, fields);
        }
    }

    /**
     * 构建 {@link MetricJdbcBinding} 内存版测试替身的构建器，按字段引用返回冻结的物理映射。
     */
    private static final class Binding {

        private final Map<String, String> tables = new HashMap<>();

        private final Map<String, String> columns = new HashMap<>();

        private final Map<String, Class<?>> javaTypes = new HashMap<>();

        private final Map<String, Integer> jdbcTypes = new HashMap<>();

        Binding table(String reference, String name) {
            tables.put(reference, name);
            return this;
        }

        Binding column(String field, String name) {
            columns.put(field, name);
            return this;
        }

        Binding javaType(String field, Class<?> type) {
            javaTypes.put(field, type);
            return this;
        }

        Binding jdbcType(String field, int type) {
            jdbcTypes.put(field, type);
            return this;
        }

        MetricJdbcBinding build() {
            return new MetricJdbcBinding() {
                @Override
                public String tableName(String factReference) {
                    return tables.get(factReference);
                }

                @Override
                public String columnName(String fieldReference) {
                    return columns.get(fieldReference);
                }

                @Override
                public Class<?> javaType(String fieldReference) {
                    return javaTypes.get(fieldReference);
                }

                @Override
                public int jdbcType(String fieldReference) {
                    return jdbcTypes.get(fieldReference);
                }

                @Override
                public Object toJdbcValue(String fieldReference, Object normalizedValue) {
                    return normalizedValue;
                }
            };
        }
    }
}
