package com.wind.integration.metrics.dsl;

import com.wind.integration.metrics.MetricValidationException;
import com.wind.integration.metrics.dsl.definition.MetricDslSpec;
import com.wind.integration.metrics.dsl.definition.MetricExpressionDsl;
import com.wind.integration.metrics.dsl.definition.MetricMeasureDsl;
import com.wind.integration.metrics.dsl.definition.MetricOrElseDsl;
import com.wind.integration.metrics.dsl.definition.MetricSubjectDsl;
import com.wind.integration.metrics.dsl.definition.MetricValueDsl;
import com.wind.integration.metrics.dsl.definition.selection.MetricLimitDsl;
import com.wind.integration.metrics.dsl.definition.selection.MetricRowSelectionDsl;
import com.wind.integration.metrics.dsl.expression.CompiledMetricExpression;
import com.wind.integration.metrics.dsl.expression.MetricExpressionCompiler;
import com.wind.integration.metrics.dsl.literal.DecimalMetricLiteralDsl;
import com.wind.integration.metrics.enums.MetricAggregation;
import com.wind.integration.metrics.enums.MetricErrorCode;
import com.wind.integration.metrics.enums.MetricExpressionType;
import com.wind.integration.metrics.enums.MetricOrElseMode;
import com.wind.integration.metrics.enums.MetricValueShape;
import com.wind.integration.metrics.enums.MetricValueType;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 验证原 DSL 的精确合并、表达式输入与最终空值语义。
 *
 * @author wuxp
 */
class MetricValueCalculatorTests {

    private final MetricValueCalculator calculator = new MetricValueCalculator();

    @Test
    void testMergeBeforeRoundingAndExpressions() {
        MetricValueDsl amount =
                measure(MetricAggregation.SUM, MetricValueType.DECIMAL, MetricOrElseMode.NULL);
        MetricDslSpec definition =
                fields(Map.of("amount", amount, "doubleAmount", expression()));
        Map<String, Number> raw =
                calculator.merge(
                        definition,
                        List.of(
                                Map.of("amount", new BigDecimal("0.00004")),
                                Map.of("amount", new BigDecimal("0.00004"))));
        Assertions.assertEquals(new BigDecimal("0.00008"), raw.get("amount"));
        Map<String, Number> result =
                calculator.calculate(
                        definition,
                        raw,
                        (field, measures) -> {
                            Assertions.assertEquals("doubleAmount", field);
                            Assertions.assertEquals(
                                    new BigDecimal("0.0001"), measures.get("amount"));
                            return ((BigDecimal) measures.get("amount"))
                                    .multiply(BigDecimal.valueOf(2));
                        });
        Assertions.assertEquals(new BigDecimal("0.0002"), result.get("doubleAmount"));
    }

    @Test
    void testMergeSumCountMinMaxAndNull() {
        MetricDslSpec definition =
                fields(
                        Map.of(
                                "count",
                                        measure(
                                                MetricAggregation.COUNT,
                                                MetricValueType.LONG,
                                                MetricOrElseMode.NULL),
                                "sum",
                                        measure(
                                                MetricAggregation.SUM,
                                                MetricValueType.DECIMAL,
                                                MetricOrElseMode.NULL),
                                "min",
                                        measure(
                                                MetricAggregation.MIN,
                                                MetricValueType.DECIMAL,
                                                MetricOrElseMode.NULL),
                                "max",
                                        measure(
                                                MetricAggregation.MAX,
                                                MetricValueType.DECIMAL,
                                                MetricOrElseMode.NULL)));
        Map<String, Number> first = new LinkedHashMap<>();
        first.put("count", 2L);
        first.put("sum", null);
        first.put("min", new BigDecimal("-2.5"));
        first.put("max", null);
        Map<String, Number> result =
                calculator.merge(
                        definition,
                        List.of(
                                first,
                                Map.of(
                                        "count",
                                        BigInteger.valueOf(3),
                                        "sum",
                                        8L,
                                        "min",
                                        -5L,
                                        "max",
                                        7L)));
        Assertions.assertEquals(
                Map.of(
                        "count",
                        BigInteger.valueOf(5),
                        "sum",
                        new BigDecimal("8"),
                        "min",
                        new BigDecimal("-5"),
                        "max",
                        new BigDecimal("7")),
                result);
        Assertions.assertEquals(
                Arrays.asList("count", "max", "min", "sum"), List.copyOf(result.keySet()));
        Assertions.assertThrows(UnsupportedOperationException.class, () -> result.put("sum", 0));
        Assertions.assertNull(first.get("sum"));
    }

    @Test
    void testAllNullStaysNullUntilFinalOrElse() {
        for (MetricAggregation aggregation :
                List.of(MetricAggregation.SUM, MetricAggregation.MIN, MetricAggregation.MAX)) {
            MetricDslSpec definition =
                    scalar(measure(aggregation, MetricValueType.DECIMAL, MetricOrElseMode.ZERO));
            Map<String, Number> raw =
                    calculator.merge(
                            definition,
                            List.of(
                                    Collections.singletonMap("value", null),
                                    Collections.singletonMap("value", null)));
            Assertions.assertTrue(raw.containsKey("value"));
            Assertions.assertNull(raw.get("value"));
            Assertions.assertEquals(
                    new BigDecimal("0.0000"),
                    calculator.calculate(definition, raw, (field, measures) -> null).get("value"));
        }
    }

    @Test
    void testExpressionsSeeMeasuresBeforeOrElseAndCannotMutateThem() {
        MetricDslSpec definition =
                fields(
                        Map.of(
                                "amount",
                                measure(
                                        MetricAggregation.SUM,
                                        MetricValueType.DECIMAL,
                                        MetricOrElseMode.ZERO),
                                "ratio",
                                expression()));
        Map<String, Number> result =
                calculator.calculate(
                        definition,
                        Collections.singletonMap("amount", null),
                        (field, measures) -> {
                            Assertions.assertTrue(measures.containsKey("amount"));
                            Assertions.assertNull(measures.get("amount"));
                            Assertions.assertThrows(
                                    UnsupportedOperationException.class,
                                    () -> measures.put("amount", 1));
                            return null;
                        });
        Assertions.assertEquals(new BigDecimal("0.0000"), result.get("amount"));
        Assertions.assertNull(result.get("ratio"));
        Assertions.assertThrows(UnsupportedOperationException.class, () -> result.put("ratio", 1));
    }

    @Test
    void testCountRejectsNullFractionAndMissingFields() {
        MetricDslSpec definition =
                scalar(
                        measure(
                                MetricAggregation.COUNT,
                                MetricValueType.LONG,
                                MetricOrElseMode.ZERO));
        for (Map<String, Number> raw :
                List.<Map<String, Number>>of(
                        Collections.singletonMap("value", null),
                        Map.of("value", new BigDecimal("1.5")),
                        Map.of(),
                        Map.of("value", 1L, "extra", 2L))) {
            Assertions.assertThrows(
                    MetricValidationException.class,
                    () -> calculator.merge(definition, List.of(raw)));
            Assertions.assertThrows(
                    MetricValidationException.class,
                    () -> calculator.calculate(definition, raw, (field, measures) -> null));
        }
    }

    @Test
    void testCountAccumulatesExactlyBeforeFinalRangeCheck() {
        MetricDslSpec definition =
                scalar(
                        measure(
                                MetricAggregation.COUNT,
                                MetricValueType.LONG,
                                MetricOrElseMode.NULL));
        Map<String, Number> raw =
                calculator.merge(
                        definition, List.of(Map.of("value", Long.MAX_VALUE), Map.of("value", 1L)));
        Assertions.assertEquals(
                BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE), raw.get("value"));
        MetricValidationException exception =
                Assertions.assertThrows(
                        MetricValidationException.class,
                        () -> calculator.calculate(definition, raw, (field, measures) -> null));
        Assertions.assertEquals(MetricErrorCode.RESULT_INVALID, exception.errorCode());
        Assertions.assertEquals("/metric/value", exception.fieldPath());
    }

    @Test
    void testNormalizeExactTypesScaleAndNullWithoutDefault() {
        MetricValueDsl integer =
                measure(MetricAggregation.SUM, MetricValueType.INTEGER, MetricOrElseMode.ZERO);
        MetricValueDsl longValue =
                measure(MetricAggregation.SUM, MetricValueType.LONG, MetricOrElseMode.NULL);
        MetricValueDsl decimal =
                measure(MetricAggregation.SUM, MetricValueType.DECIMAL, MetricOrElseMode.NULL);
        Assertions.assertNull(calculator.normalize(integer, null, "/value"));
        Assertions.assertEquals(
                12, calculator.normalize(integer, new BigDecimal("12.000"), "/value"));
        Assertions.assertEquals(
                Long.MAX_VALUE,
                calculator.normalize(longValue, BigInteger.valueOf(Long.MAX_VALUE), "/value"));
        Assertions.assertEquals(
                new BigDecimal("12345678901234567890.1235"),
                calculator.normalize(
                        decimal, new BigDecimal("12345678901234567890.12345"), "/value"));
        for (Number invalid :
                List.of(
                        new BigDecimal("1.5"),
                        BigInteger.valueOf(Integer.MAX_VALUE).add(BigInteger.ONE),
                        1.0d,
                        1.0f,
                        new AtomicInteger(1))) {
            MetricValidationException exception =
                    Assertions.assertThrows(
                            MetricValidationException.class,
                            () -> calculator.normalize(integer, invalid, "/value"));
            Assertions.assertEquals("/value", exception.fieldPath());
        }
    }

    @Test
    void testMergeRejectsFloatingPointAndBadSegments() {
        MetricDslSpec definition =
                scalar(
                        measure(
                                MetricAggregation.SUM,
                                MetricValueType.DECIMAL,
                                MetricOrElseMode.NULL));
        for (List<Map<String, Number>> segments :
                List.<List<Map<String, Number>>>of(
                        List.of(),
                        Collections.singletonList(null),
                        List.of(Map.of()),
                        List.of(Map.of("value", 0.1d)))) {
            Assertions.assertThrows(
                    MetricValidationException.class, () -> calculator.merge(definition, segments));
        }
    }

    @Test
    void testAvgIsAllowedForSingleQueryAndRejectedForMerge() {
        MetricDslSpec definition =
                scalar(
                        measure(
                                MetricAggregation.AVG,
                                MetricValueType.DECIMAL,
                                MetricOrElseMode.NULL));
        Assertions.assertEquals(
                new BigDecimal("3.1235"),
                calculator
                        .calculate(
                                definition,
                                Map.of("value", new BigDecimal("3.12345")),
                                (field, measures) -> null)
                        .get("value"));
        MetricValidationException exception =
                Assertions.assertThrows(
                        MetricValidationException.class,
                        () -> calculator.validateMergeable(definition));
        Assertions.assertEquals(
                MetricErrorCode.METRIC_EXECUTION_MODE_UNSUPPORTED, exception.errorCode());
        Assertions.assertEquals("/metric/value/measure/aggregation", exception.fieldPath());
        Assertions.assertThrows(
                MetricValidationException.class,
                () -> calculator.merge(definition, List.of(Map.of("value", 3))));
    }

    @Test
    void testRowSelectionRejectsBucketMerge() {
        MetricDslSpec original =
                scalar(
                        measure(
                                MetricAggregation.COUNT,
                                MetricValueType.LONG,
                                MetricOrElseMode.NULL));
        MetricDslSpec limited =
                new MetricDslSpec(
                        original.code(),
                        original.valueShape(),
                        original.fact(),
                        original.joins(),
                        original.subject(),
                        original.time(),
                        original.dimensions(),
                        original.parameters(),
                        new MetricRowSelectionDsl(null, List.of(), new MetricLimitDsl(10, null)),
                        original.value(),
                        original.fields());
        MetricValidationException exception =
                Assertions.assertThrows(
                        MetricValidationException.class,
                        () -> calculator.validateMergeable(limited));
        Assertions.assertEquals("/metric/rowSelection", exception.fieldPath());
        Assertions.assertEquals(
                3L,
                calculator
                        .calculate(limited, Map.of("value", 3L), (field, measures) -> null)
                        .get("value"));
    }

    @Test
    void testDerivedExpressionUsesEmptyMeasuresAndNormalizesResult() {
        MetricDslSpec definition =
                new MetricDslSpec(
                        "DERIVED",
                        MetricValueShape.SCALAR,
                        null,
                        List.of(),
                        new MetricSubjectDsl("GLOBAL", null),
                        null,
                        List.of(),
                        Map.of(),
                        null,
                        expression(),
                        Map.of());
        Map<String, Number> result =
                calculator.calculate(
                        definition,
                        Map.of(),
                        (field, measures) -> {
                            Assertions.assertEquals("value", field);
                            Assertions.assertTrue(measures.isEmpty());
                            return new BigDecimal("2.12345");
                        });
        Assertions.assertEquals(new BigDecimal("2.1235"), result.get("value"));
        Assertions.assertThrows(
                MetricValidationException.class, () -> calculator.validateMergeable(definition));
        Assertions.assertThrows(
                MetricValidationException.class,
                () ->
                        calculator.calculate(
                                definition, Map.of("unexpected", 1), (field, measures) -> 1));
    }

    @Test
    void testExpressionFailuresDoNotBecomeOrElse() {
        MetricValueDsl expression = expression();
        MetricValueDsl defaulted =
                new MetricValueDsl(
                        expression.valueType(),
                        expression.scale(),
                        expression.roundingMode(),
                        null,
                        expression.expression(),
                        new MetricOrElseDsl(MetricOrElseMode.ZERO, null));
        MetricDslSpec definition =
                fields(
                        Map.of(
                                "count",
                                measure(
                                        MetricAggregation.COUNT,
                                        MetricValueType.LONG,
                                        MetricOrElseMode.NULL),
                                "ratio",
                                defaulted));
        for (Object value : List.of("not a number", 0.1d)) {
            MetricValidationException exception =
                    Assertions.assertThrows(
                            MetricValidationException.class,
                            () ->
                                    calculator.calculate(
                                            definition,
                                            Map.of("count", 1L),
                                            (field, measures) -> value));
            Assertions.assertEquals(MetricErrorCode.RESULT_INVALID, exception.errorCode());
            Assertions.assertEquals("/metric/fields/ratio/expression", exception.fieldPath());
        }
        ArithmeticException failure = new ArithmeticException("division by zero");
        Assertions.assertSame(
                failure,
                Assertions.assertThrows(
                        ArithmeticException.class,
                        () ->
                                calculator.calculate(
                                        definition,
                                        Map.of("count", 1L),
                                        (field, measures) -> {
                                            throw failure;
                                        })));
    }

    @Test
    void testLiteralFallbackUsesDeclaredTypeAndPrecision() {
        MetricValueDsl fallback =
                new MetricValueDsl(
                        MetricValueType.DECIMAL,
                        4,
                        RoundingMode.HALF_UP,
                        new MetricMeasureDsl(MetricAggregation.SUM, "amount", null),
                        null,
                        new MetricOrElseDsl(
                                MetricOrElseMode.VALUE,
                                new DecimalMetricLiteralDsl(new BigDecimal("9.12345"))));
        Assertions.assertEquals(
                new BigDecimal("9.1235"),
                calculator
                        .calculate(
                                scalar(fallback),
                                Collections.singletonMap("value", null),
                                (field, measures) -> null)
                        .get("value"));
    }

    @Test
    void testMergedSufficientStateIsEvaluatedByRealCompiledExpression() {
        MetricValueDsl average =
                new MetricValueDsl(
                        MetricValueType.DECIMAL,
                        4,
                        RoundingMode.HALF_UP,
                        null,
                        new MetricExpressionDsl(
                                MetricExpressionType.SPEL, "count == 0 ? null : ratio(sum, count)"),
                        new MetricOrElseDsl(MetricOrElseMode.ZERO, null));
        MetricDslSpec definition =
                fields(
                        Map.of(
                                "count",
                                        measure(
                                                MetricAggregation.COUNT,
                                                MetricValueType.LONG,
                                                MetricOrElseMode.NULL),
                                "sum",
                                        measure(
                                                MetricAggregation.SUM,
                                                MetricValueType.DECIMAL,
                                                MetricOrElseMode.NULL),
                                "average", average));
        CompiledMetricExpression expression =
                new MetricExpressionCompiler()
                        .compileFact(
                                average.expression(),
                                Set.of("sum", "count"),
                                "/metric/fields/average/expression");
        Map<String, Number> merged =
                calculator.merge(
                        definition,
                        List.of(
                                Map.of("sum", new BigDecimal("400"), "count", 1L),
                                Map.of("sum", new BigDecimal("200"), "count", 9L)));
        Map<String, Number> result =
                calculator.calculate(
                        definition,
                        merged,
                        (field, measures) ->
                                expression.evaluate(
                                        average, measures, Map.of(), "/metric/fields/average"));
        Assertions.assertEquals(new BigDecimal("60.0000"), result.get("average"));
        Assertions.assertEquals(10L, result.get("count"));
        Map<String, Number> empty = new LinkedHashMap<>();
        empty.put("count", 0L);
        empty.put("sum", null);
        Assertions.assertEquals(
                new BigDecimal("0.0000"),
                calculator
                        .calculate(
                                definition,
                                empty,
                                (field, measures) ->
                                        expression.evaluate(
                                                average,
                                                measures,
                                                Map.of(),
                                                "/metric/fields/average"))
                        .get("average"));
    }

    private static MetricValueDsl measure(
            MetricAggregation aggregation, MetricValueType type, MetricOrElseMode mode) {
        return new MetricValueDsl(
                type,
                type == MetricValueType.DECIMAL ? 4 : null,
                type == MetricValueType.DECIMAL ? RoundingMode.HALF_UP : null,
                new MetricMeasureDsl(
                        aggregation,
                        aggregation == MetricAggregation.COUNT ? null : "amount",
                        null),
                null,
                new MetricOrElseDsl(mode, null));
    }

    private static MetricValueDsl expression() {
        return new MetricValueDsl(
                MetricValueType.DECIMAL,
                4,
                RoundingMode.HALF_UP,
                null,
                new MetricExpressionDsl(MetricExpressionType.SPEL, "#measure('amount')"),
                new MetricOrElseDsl(MetricOrElseMode.NULL, null));
    }

    private static MetricDslSpec scalar(MetricValueDsl value) {
        return new MetricDslSpec(
                "TOTAL",
                MetricValueShape.SCALAR,
                "ORDER",
                List.of(),
                new MetricSubjectDsl("GLOBAL", null),
                null,
                List.of(),
                Map.of(),
                null,
                value,
                Map.of());
    }

    private static MetricDslSpec fields(Map<String, MetricValueDsl> fields) {
        return new MetricDslSpec(
                "SUMMARY",
                MetricValueShape.FIELD_SET,
                "ORDER",
                List.of(),
                new MetricSubjectDsl("GLOBAL", null),
                null,
                List.of(),
                Map.of(),
                null,
                null,
                fields);
    }
}
