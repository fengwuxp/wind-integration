package com.wind.integration.metrics.dsl;

import com.wind.integration.metrics.MetricValidationException;
import com.wind.integration.metrics.spec.MetricDSLDefinition;
import com.wind.integration.metrics.dsl.definition.MetricExpressionDsl;
import com.wind.integration.metrics.dsl.definition.MetricReferenceDsl;
import com.wind.integration.metrics.dsl.definition.MetricMeasureDsl;
import com.wind.integration.metrics.dsl.definition.MetricOrElseDsl;
import com.wind.integration.metrics.dsl.definition.MetricSubjectDsl;
import com.wind.integration.metrics.dsl.definition.MetricValueDsl;
import com.wind.integration.metrics.dsl.definition.selection.MetricLimitDsl;
import com.wind.integration.metrics.dsl.definition.selection.MetricRowSelectionDsl;
import com.wind.integration.metrics.expression.MetricExpression;
import com.wind.integration.metrics.expression.MetricExpressionCompiler;
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
 * 除真实编译组合用例外，求值回调是计算器端口夹具，仅证明调用顺序、输入及结果校验；
 * 回调夹具中的表达式文本不作为表达式语法验收证据。
 *
 * @author wuxp
 */
class MetricValueCalculatorTests {

    private final MetricValueCalculator calculator = new MetricValueCalculator();

    /**
     * 场景：多个分段合并后才舍入并计算表达式。
     * 输入：两段 amount 都为0.00004，输出4位小数。
     * 流程：先 merge，再 calculate；回调检查收到的已归一化度量。
     * 预期：合并原值0.00008，回调收到0.0001，doubleAmount=0.0002。
     */
    @Test
    void testMergeBeforeRoundingAndExpressions() {
        MetricValueDsl amount =
                measure(MetricAggregation.SUM, MetricValueType.DECIMAL, MetricOrElseMode.NULL);
        MetricDSLDefinition definition =
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

    /**
     * 场景：不同聚合类型按各自语义合并且保留输入。
     * 输入：两段 count=2/3、sum=null/8、min=-2.5/-5、max=null/7。
     * 流程：调用 merge，再检查结果顺序及可变性。
     * 预期：count=5、sum=8、min=-5、max=7；键排序、结果不可改，原输入 null 保留。
     */
    @Test
    void testMergeSumCountMinMaxAndNull() {
        MetricDSLDefinition definition =
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

    /**
     * 场景：全空 SUM/MIN/MAX 的兜底只在最终结果阶段应用。
     * 输入：两段 value=null，配置 ZERO、4位小数。
     * 流程：先合并，再计算最终输出。
     * 预期：中间值仍为 null，最终为0.0000。
     */
    @Test
    void testAllNullStaysNullUntilFinalOrElse() {
        for (MetricAggregation aggregation :
                List.of(MetricAggregation.SUM, MetricAggregation.MIN, MetricAggregation.MAX)) {
            MetricDSLDefinition definition =
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

    /**
     * 场景：表达式应观察真实空度量，不能读取兜底伪值或修改度量。
     * 输入：amount=null 且配置 ZERO，ratio 回调返回 null。
     * 流程：calculate 回调检查并尝试修改度量，再检查输出。
     * 预期：回调看到 null 且不可修改；最终 amount=0.0000、ratio=null，输出也不可改。
     */
    @Test
    void testExpressionsSeeMeasuresBeforeOrElseAndCannotMutateThem() {
        MetricDSLDefinition definition =
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

    /**
     * 场景：COUNT 必须是完整且精确的计数输入。
     * 输入：value 为 null/1.5、缺少 value 或多出 extra 字段。
     * 流程：分别走 merge 和 calculate。
     * 预期：所有输入均拒绝，ZERO 不能掩盖非法计数或字段集合。
     */
    @Test
    void testCountRejectsNullFractionAndMissingFields() {
        MetricDSLDefinition definition =
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

    /**
     * 场景：计数合并不发生静默溢出。
     * 输入：两段计数 Long.MAX_VALUE 和1，最终声明 LONG。
     * 流程：merge 后将原值交给 calculate。
     * 预期：中间 BigInteger 精确加1；最终报 RESULT_INVALID，定位 /metric/value。
     */
    @Test
    void testCountAccumulatesExactlyBeforeFinalRangeCheck() {
        MetricDSLDefinition definition =
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

    /**
     * 场景：归一化负责类型、范围与精度，不提前使用 orElse。
     * 输入：null、12.000、LONG 上界、大额小数，以及小数计数/越界/浮点/AtomicInteger。
     * 流程：直接调用 normalize。
     * 预期：null 保留，合法整数精确转换，小数按4位舍入；非法值报指定路径错误。
     */
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

    /**
     * 场景：分段合并必须收到非空且结构完整的精确度量。
     * 输入：空分段列表、null 分段、缺失 value、Double 0.1。
     * 流程：分别调用 merge。
     * 预期：均抛出 MetricValidationException。
     */
    @Test
    void testMergeRejectsFloatingPointAndBadSegments() {
        MetricDSLDefinition definition =
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

    /**
     * 场景：AVG 可用于单次查询，不能直接合并分段均值。
     * 输入：AVG 原值3.12345，配置4位小数。
     * 流程：单次 calculate 后尝试 validateMergeable 和 merge。
     * 预期：单次结果3.1235；分段路径拒绝，错误指向 aggregation。
     */
    @Test
    void testAvgIsAllowedForSingleQueryAndRejectedForMerge() {
        MetricDSLDefinition definition =
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

    /**
     * 场景：限制行数的定义不支持跨桶合并。
     * 输入：COUNT 定义配置 limit=10，单次聚合值3。
     * 流程：先校验可合并性，再做单次 calculate。
     * 预期：合并校验在 /metric/rowSelection 失败；单次结果仍为3。
     */
    @Test
    void testRowSelectionRejectsBucketMerge() {
        MetricDSLDefinition original =
                scalar(
                        measure(
                                MetricAggregation.COUNT,
                                MetricValueType.LONG,
                                MetricOrElseMode.NULL));
        MetricDSLDefinition limited =
                new MetricDSLDefinition(
                        original.code(),
                        original.revision(),
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

    /**
     * 场景：派生计算器使用外部依赖求值回调，不接受本地度量。
     * 输入：绑定 BASE@1，空 measures，回调返回2.12345。
     * 流程：calculate 后尝试分段合并及带 unexpected 度量的计算。
     * 预期：回调只收到空度量，结果2.1235；合并和额外度量均拒绝。
     */
    @Test
    void testDerivedExpressionUsesEmptyMeasuresAndNormalizesResult() {
        MetricDSLDefinition definition =
                new MetricDSLDefinition(
                        "DERIVED",
                        1,
                        MetricValueShape.SCALAR,
                        null,
                        List.of(),
                        new MetricSubjectDsl("GLOBAL", null),
                        null,
                        List.of(),
                        Map.of(),
                        null,
                        new MetricValueDsl(MetricValueType.DECIMAL, 4, RoundingMode.HALF_UP, null,
                                new MetricExpressionDsl(MetricExpressionType.SPEL, "metric('BASE', 'value')"),
                                new MetricOrElseDsl(MetricOrElseMode.NULL, null)),
                        Map.of(),
                        List.of(new MetricReferenceDsl("BASE", 1)));
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

    /**
     * 场景：ZERO 兜底不能吞掉求值错误或非法类型。
     * 输入：回调分别返回字符串、Double 0.1，或抛出除零异常。
     * 流程：通过 calculate 执行配置 ZERO 的 ratio 字段。
     * 预期：非法类型报 RESULT_INVALID 并定位表达式；算术异常原样传播。
     */
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
        MetricDSLDefinition definition =
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

    /**
     * 场景：字面量兜底遵循输出类型和精度。
     * 输入：SUM value=null，VALUE 兜底9.12345，DECIMAL 4位 HALF_UP。
     * 流程：计算最终结果。
     * 预期：得到 BigDecimal 9.1235。
     */
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

    /**
     * 场景：按合并后的 SUM/COUNT 计算均值，避免平均分段均值。
     * 输入：两段 sum/count 为400/1和200/9；另有 count=0、sum=null。
     * 流程：真实编译条件 ratio，先合并再 calculate；另执行空数据路径。
     * 预期：总 count=10、average=60.0000；零计数条件返回 null 后兜底为0.0000。
     */
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
        MetricDSLDefinition definition =
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
        MetricExpression expression =
                new MetricExpressionCompiler()
                        .compile(
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

    private static MetricDSLDefinition scalar(MetricValueDsl value) {
        return new MetricDSLDefinition(
                "TOTAL",
                1,
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

    private static MetricDSLDefinition fields(Map<String, MetricValueDsl> fields) {
        return new MetricDSLDefinition(
                "SUMMARY",
                1,
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
