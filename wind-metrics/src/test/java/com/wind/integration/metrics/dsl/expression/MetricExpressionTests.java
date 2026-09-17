package com.wind.integration.metrics.dsl.expression;

import com.wind.integration.metrics.MetricValidationException;
import com.wind.integration.metrics.dsl.MetricValueCalculator;
import com.wind.integration.metrics.dsl.definition.MetricExpressionDsl;
import com.wind.integration.metrics.dsl.definition.MetricOrElseDsl;
import com.wind.integration.metrics.dsl.definition.MetricValueDsl;
import com.wind.integration.metrics.enums.MetricErrorCode;
import com.wind.integration.metrics.enums.MetricExpressionType;
import com.wind.integration.metrics.enums.MetricOrElseMode;
import com.wind.integration.metrics.enums.MetricValueType;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.expression.spel.standard.SpelExpression;
import org.springframework.expression.spel.standard.SpelExpressionParser;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * 验证真实表达式编译句柄的白名单、精确计算、依赖身份与查询间隔离。
 *
 * @author wuxp
 */
class MetricExpressionTests {

    private static final String VALUE_PATH = "/metric/fields/result";

    private final MetricExpressionCompiler compiler = new MetricExpressionCompiler();

    @Test
    void testLocalExpressionReturnsExactRawResultBeforeFinalNormalization() {
        MetricValueDsl value = value("count * 2", MetricValueType.LONG, null);
        CompiledMetricExpression compiled = fact(value, Set.of("count"));
        Number raw = compiled.evaluate(value, Map.of("count", 7L), Map.of(), VALUE_PATH);
        Assertions.assertEquals(new BigDecimal("14"), raw);
        Assertions.assertEquals(14L, new MetricValueCalculator().normalize(value, raw, VALUE_PATH));
    }

    @Test
    void testCompiledHandleDoesNotReusePreviousRequestValues() {
        MetricValueDsl value = value("count * 2", MetricValueType.LONG, null);
        CompiledMetricExpression compiled = fact(value, Set.of("count"));
        IntStream.range(0, 100)
                .parallel()
                .forEach(
                        count ->
                                Assertions.assertEquals(
                                        BigDecimal.valueOf(count * 2L),
                                        compiled.evaluate(
                                                value,
                                                Map.of("count", count),
                                                Map.of(),
                                                VALUE_PATH)));
    }

    @Test
    void testDerivedRatioKeepsRequestValuesAndPrecisionIsolated() {
        String source = "ratio(metric('PAYMENT', 'approved'), metric('PAYMENT', 'total'))";
        MetricValueDsl four = value(source, MetricValueType.DECIMAL, 4);
        MetricValueDsl six = value(source, MetricValueType.DECIMAL, 6);
        CompiledMetricExpression compiled =
                compiler.compileDerived(four.expression(), VALUE_PATH + "/expression");
        MetricValueReference approved = new MetricValueReference("PAYMENT", "approved");
        MetricValueReference total = new MetricValueReference("PAYMENT", "total");
        IntStream.range(0, 100)
                .parallel()
                .forEach(
                        count -> {
                            MetricValueDsl definition = count % 2 == 0 ? four : six;
                            BigDecimal expected = BigDecimal.valueOf(count).divide(
                                    BigDecimal.valueOf(3), definition.scale(), RoundingMode.HALF_UP);
                            Assertions.assertEquals(
                                    expected,
                                    compiled.evaluate(
                                            definition,
                                            Map.of(),
                                            Map.of(approved, count, total, 3L),
                                            VALUE_PATH));
                        });
    }

    @Test
    void testRatioPrecisionAtFourAndSixPlaces() {
        MetricValueDsl four = value("ratio(approvedCount, totalCount)", MetricValueType.DECIMAL, 4);
        CompiledMetricExpression compiled = fact(four, Set.of("approvedCount", "totalCount"));
        Assertions.assertTrue(compiled.ratio());
        for (List<Number> input :
                List.<List<Number>>of(
                        List.of(1L, 3L, new BigDecimal("0.3333")),
                        List.of(-1L, 4L, new BigDecimal("-0.2500")),
                        List.of(2L, 3L, new BigDecimal("0.6667")))) {
            Assertions.assertEquals(
                    input.get(2),
                    compiled.evaluate(
                            four,
                            Map.of("approvedCount", input.get(0), "totalCount", input.get(1)),
                            Map.of(),
                            VALUE_PATH));
        }
        MetricValueDsl six = value(four.expression().value(), MetricValueType.DECIMAL, 6);
        Assertions.assertEquals(
                new BigDecimal("0.333333"),
                fact(six, Set.of("approvedCount", "totalCount"))
                        .evaluate(
                                six,
                                Map.of("approvedCount", 1L, "totalCount", 3L),
                                Map.of(),
                                VALUE_PATH));
    }

    @Test
    void testZeroDenominatorPropagatesArithmeticError() {
        MetricValueDsl value =
                value("ratio(approvedCount, totalCount)", MetricValueType.DECIMAL, 4);
        ArithmeticException exception =
                Assertions.assertThrows(
                        ArithmeticException.class,
                        () ->
                                fact(value, Set.of("approvedCount", "totalCount"))
                                        .evaluate(
                                                value,
                                                Map.of("approvedCount", 1L, "totalCount", 0L),
                                                Map.of(),
                                                VALUE_PATH));
        Assertions.assertEquals(
                "Metric ratio denominator must not be zero", exception.getMessage());
    }

    @Test
    void testCompileRejectsSandboxEscapes() {
        for (String source :
                List.of(
                        "approvedCount / totalCount",
                        "ratio(approvedCount, totalCount) * 3",
                        "T(java.lang.Runtime).getRuntime()",
                        "new java.math.BigDecimal('1')",
                        "#approvedCount",
                        "approvedCount.toString()",
                        "result + 1",
                        "metric('ORDER_COUNT', 'value')",
                        "@bean",
                        "approvedCount = 1",
                        "{1,2}[0]",
                        "getClass()",
                        "approvedCount?.class")) {
            MetricValidationException exception =
                    Assertions.assertThrows(
                            MetricValidationException.class,
                            () ->
                                    fact(
                                            value(source, MetricValueType.DECIMAL, 4),
                                            Set.of("approvedCount", "totalCount")),
                            source);
            Assertions.assertEquals(MetricErrorCode.DSL_VALUE_INVALID, exception.errorCode());
            Assertions.assertEquals(VALUE_PATH + "/expression/value", exception.fieldPath());
        }
    }

    @Test
    void testResourceLimitsAndMalformedSyntax() {
        for (String source :
                List.of(
                        "count" + " + 1".repeat(512),
                        "-(".repeat(33) + "count" + ")".repeat(33),
                        "count + (")) {
            Assertions.assertThrows(
                    MetricValidationException.class,
                    () -> fact(value(source, MetricValueType.LONG, null), Set.of("count")));
        }
    }

    @Test
    void testCompileRejectsNonNumericResults() {
        for (String source :
                List.of("count > 0", "count > 0 ? true : false", "null ?: false", "'text'")) {
            Assertions.assertThrows(
                    MetricValidationException.class,
                    () -> fact(value(source, MetricValueType.LONG, null), Set.of("count")));
        }
    }

    @Test
    void testCompileRejectsIntegralLiteralAndConditionalOverflow() {
        for (String source :
                List.of(
                        "count + (2147483647 + 1)",
                        "count + (9223372036854775807L + 1L)",
                        "(count > 0 ? 9223372036854775807L : 0L) + 1L",
                        "(null ?: 9223372036854775807L) + 1L",
                        "count + (2147483647 * 2)")) {
            Assertions.assertThrows(
                    MetricValidationException.class,
                    () -> fact(value(source, MetricValueType.LONG, null), Set.of("count")));
        }
    }

    @Test
    void testRawArithmeticDoesNotOverflowBeforeCalculatorChecksDeclaredRange() {
        MetricValueDsl value = value("count * 2", MetricValueType.LONG, null);
        Number raw =
                fact(value, Set.of("count"))
                        .evaluate(value, Map.of("count", Long.MAX_VALUE), Map.of(), VALUE_PATH);
        Assertions.assertEquals(
                new BigDecimal(BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.TWO)), raw);
        MetricValidationException exception =
                Assertions.assertThrows(
                        MetricValidationException.class,
                        () -> new MetricValueCalculator().normalize(value, raw, VALUE_PATH));
        Assertions.assertEquals(MetricErrorCode.RESULT_INVALID, exception.errorCode());
    }

    @Test
    void testInexactInputRejectedForLocalAndDependencyValues() {
        MetricValueDsl local = value("count * 2", MetricValueType.LONG, null);
        Assertions.assertThrows(
                MetricValidationException.class,
                () ->
                        fact(local, Set.of("count"))
                                .evaluate(local, Map.of("count", 0.1d), Map.of(), VALUE_PATH));
        MetricValueDsl derived = value("metric('TOTAL', 'value')", MetricValueType.LONG, null);
        Assertions.assertThrows(
                MetricValidationException.class,
                () ->
                        compiler.compileDerived(derived.expression(), VALUE_PATH + "/expression")
                                .evaluate(
                                        derived,
                                        Map.of(),
                                        Map.of(new MetricValueReference("TOTAL", "value"), 0.1d),
                                        VALUE_PATH));
    }

    @Test
    void testDerivedReferencesAreSortedUniqueAndImmutable() {
        MetricValueDsl value =
                value(
                        "metric('B', 'value') + metric('A', 'amount') + metric('B', 'value')",
                        MetricValueType.LONG,
                        null);
        CompiledMetricExpression compiled =
                compiler.compileDerived(value.expression(), VALUE_PATH + "/expression");
        Assertions.assertEquals(
                List.of(
                        new MetricValueReference("A", "amount"),
                        new MetricValueReference("B", "value")),
                List.copyOf(compiled.metricValueReferences()));
        Assertions.assertTrue(compiled.localValueFields().isEmpty());
        Assertions.assertThrows(
                UnsupportedOperationException.class,
                () -> compiled.metricValueReferences().clear());
        Assertions.assertEquals(
                new BigDecimal("8"),
                compiled.evaluate(
                        value,
                        Map.of(),
                        Map.of(
                                new MetricValueReference("A", "amount"),
                                2L,
                                new MetricValueReference("B", "value"),
                                3L),
                        VALUE_PATH));
    }

    @Test
    void testDerivedRatioUsesPreloadedDependencyResults() {
        MetricValueDsl value =
                value(
                        "ratio(metric('SUMMARY', 'approved'), metric('SUMMARY', 'total'))",
                        MetricValueType.DECIMAL,
                        6);
        CompiledMetricExpression compiled =
                compiler.compileDerived(value.expression(), VALUE_PATH + "/expression");
        Assertions.assertEquals(
                new BigDecimal("0.333333"),
                compiled.evaluate(
                        value,
                        Map.of(),
                        Map.of(
                                new MetricValueReference("SUMMARY", "approved"), 1L,
                                new MetricValueReference("SUMMARY", "total"), 3L),
                        VALUE_PATH));
    }

    @Test
    void testDynamicOrMissingMetricReferencesAreRejected() {
        for (String source :
                List.of(
                        "metric('ORDER_' + 'COUNT', 'value')",
                        "metric('COUNT', 'value' + 'Count')",
                        "metric('', 'value')",
                        "metric('COUNT', '')",
                        "1 + 1",
                        "count + 1")) {
            Assertions.assertThrows(
                    MetricValidationException.class,
                    () ->
                            compiler.compileDerived(
                                    value(source, MetricValueType.LONG, null).expression(),
                                    VALUE_PATH + "/expression"));
        }
    }

    @Test
    void testNormalNullDiffersFromMissingMeasureOrDependency() {
        MetricValueDsl local =
                value("count == null ? null : count * 2", MetricValueType.LONG, null);
        CompiledMetricExpression compiled = fact(local, Set.of("count"));
        Assertions.assertNull(
                compiled.evaluate(
                        local, Collections.singletonMap("count", null), Map.of(), VALUE_PATH));
        Assertions.assertThrows(
                MetricValidationException.class,
                () -> compiled.evaluate(local, Map.of(), Map.of(), VALUE_PATH));
        MetricValueDsl derived = value("metric('COUNT', 'value') ?: 0", MetricValueType.LONG, null);
        CompiledMetricExpression dependency =
                compiler.compileDerived(derived.expression(), VALUE_PATH + "/expression");
        Assertions.assertEquals(
                0,
                dependency.evaluate(
                        derived,
                        Map.of(),
                        Collections.singletonMap(new MetricValueReference("COUNT", "value"), null),
                        VALUE_PATH));
        Assertions.assertThrows(
                MetricValidationException.class,
                () -> dependency.evaluate(derived, Map.of(), Map.of(), VALUE_PATH));
    }

    @Test
    void testCanonicalAstPreservesSemanticComparisonWithoutExposingSpringTypes() {
        MetricValueDsl first = value("count * 2", MetricValueType.LONG, null);
        MetricValueDsl second = value("( count  *  2 )", MetricValueType.LONG, null);
        MetricValueDsl different = value("count * 3", MetricValueType.LONG, null);
        CompiledMetricExpression compiled = fact(first, Set.of("count"));
        Assertions.assertEquals(
                compiled.canonicalAst(), fact(second, Set.of("count")).canonicalAst());
        Assertions.assertNotEquals(
                compiled.canonicalAst(), fact(different, Set.of("count")).canonicalAst());
        Assertions.assertEquals(List.of("count"), List.copyOf(compiled.localValueFields()));
        Assertions.assertThrows(
                UnsupportedOperationException.class, () -> compiled.localValueFields().clear());
    }

    @Test
    void testRuntimeSandboxRejectsInternallyMalformedHandles() {
        MetricValueDsl value = value("count * 2", MetricValueType.LONG, null);
        for (String source :
                List.of(
                        "hashCode()",
                        "count > 0",
                        "T(java.lang.String)",
                        "new java.lang.String()")) {
            CompiledMetricExpression bypassed =
                    new CompiledMetricExpression(parse(source), Set.of("count"), Set.of(), false);
            MetricValidationException exception =
                    Assertions.assertThrows(
                            MetricValidationException.class,
                            () ->
                                    bypassed.evaluate(
                                            value, Map.of("count", 1L), Map.of(), VALUE_PATH));
            Assertions.assertEquals(MetricErrorCode.RESULT_INVALID, exception.errorCode());
            Assertions.assertEquals(VALUE_PATH + "/expression", exception.fieldPath());
        }
    }

    @Test
    void testRuntimeCannotReadOutsideValidatedMetricReferences() {
        MetricValueDsl value = value("metric('DECLARED', 'value')", MetricValueType.LONG, null);
        MetricValueReference declared = new MetricValueReference("DECLARED", "value");
        MetricValueReference undeclared = new MetricValueReference("OTHER", "value");
        CompiledMetricExpression bypassed =
                new CompiledMetricExpression(
                        parse("metric('OTHER', 'value')"), Set.of(), Set.of(declared), false);
        Assertions.assertThrows(
                MetricValidationException.class,
                () ->
                        bypassed.evaluate(
                                value, Map.of(), Map.of(declared, 1L, undeclared, 2L), VALUE_PATH));
    }

    @Test
    void testRuntimeCannotReadOutsideValidatedLocalFields() {
        MetricValueDsl value = value("count * 2", MetricValueType.LONG, null);
        CompiledMetricExpression bypassed =
                new CompiledMetricExpression(parse("extra + 1"), Set.of("count"), Set.of(), false);
        Assertions.assertThrows(
                MetricValidationException.class,
                () ->
                        bypassed.evaluate(
                                value, Map.of("count", 1L, "extra", 99L), Map.of(), VALUE_PATH));
    }

    private CompiledMetricExpression fact(MetricValueDsl value, Set<String> fields) {
        return compiler.compileFact(value.expression(), fields, VALUE_PATH + "/expression");
    }

    private static MetricValueDsl value(String expression, MetricValueType type, Integer scale) {
        return new MetricValueDsl(
                type,
                scale,
                scale == null ? null : RoundingMode.HALF_UP,
                null,
                new MetricExpressionDsl(MetricExpressionType.SPEL, expression),
                new MetricOrElseDsl(MetricOrElseMode.NULL, null));
    }

    private static SpelExpression parse(String expression) {
        return (SpelExpression) new SpelExpressionParser().parseExpression(expression);
    }
}
