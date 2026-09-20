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

import com.wind.integration.metrics.expression.CompiledMetricExpression;
import com.wind.integration.metrics.expression.MetricExpressionCompiler;
import com.wind.integration.metrics.expression.MetricValueReference;
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

    /**
     * 场景：原生指标在聚合后计算本地表达式。
     * 输入：count=7，表达式 count * 2，声明 LONG。
     * 流程：真实编译、求值，再交计算器归一化。
     * 预期：中间值为精确 BigDecimal 14，最终值为 Long 14。
     */
    @Test
    void testLocalExpressionReturnsExactRawResultBeforeFinalNormalization() {
        MetricValueDsl value = value("count * 2", MetricValueType.LONG, null);
        CompiledMetricExpression compiled = fact(value, Set.of("count"));
        Number raw = compiled.evaluate(value, Map.of("count", 7L), Map.of(), VALUE_PATH);
        Assertions.assertEquals(new BigDecimal("14"), raw);
        Assertions.assertEquals(14L, new MetricValueCalculator().normalize(value, raw, VALUE_PATH));
    }

    /**
     * 场景：多个请求并发复用同一个表达式句柄。
     * 输入：100 组 count=0..99，共用 count * 2。
     * 流程：并行调用同一句柄求值。
     * 预期：每次只使用本次 count，返回对应的两倍值。
     */
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

    /**
     * 场景：派生比例并发计算时隔离请求值和输出精度。
     * 输入：PAYMENT.approved=0..99、total=3，交替使用4位与6位小数。
     * 流程：共用编译句柄并行求值。
     * 预期：每项均按本次精度 HALF_UP 计算 count/3，不串值或精度。
     */
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

    /**
     * 场景：本地比例支持负数与指定精度。
     * 输入：1/3、-1/4、2/3 配置4位，另将1/3配置为6位。
     * 流程：编译 ratio 后分别求值。
     * 预期：得到0.3333、-0.2500、0.6667和0.333333。
     */
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

    /**
     * 场景：比例的零分母不能伪装成正常结果。
     * 输入：approvedCount=1、totalCount=0。
     * 流程：编译并执行本地 ratio。
     * 预期：抛出 ArithmeticException，说明分母不能为零。
     */
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

    /**
     * 场景：配置表达式必须留在允许的语法与字段范围内。
     * 输入：除法、嵌套 ratio 运算、类型/构造器/Bean/方法访问、赋值等非法表达式。
     * 流程：以仅含 approvedCount、totalCount 的本地字段白名单编译。
     * 预期：均返回 DSL_VALUE_INVALID，并定位到 expression/value。
     */
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

    /**
     * 场景：编译入口拒绝超限或不完整的表达式。
     * 输入：重复512次加法、33层嵌套和未闭合括号。
     * 流程：分别编译 LONG 本地表达式。
     * 预期：均抛出 MetricValidationException。
     */
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

    /**
     * 场景：指标表达式输出必须为数值或允许的空值。
     * 输入：布尔比较、布尔条件分支、布尔兜底及字符串常量。
     * 流程：分别编译为 LONG 指标表达式。
     * 预期：均在编译时拒绝非数值输出。
     */
    @Test
    void testCompileRejectsNonNumericResults() {
        for (String source :
                List.of("count > 0", "count > 0 ? true : false", "null ?: false", "'text'")) {
            Assertions.assertThrows(
                    MetricValidationException.class,
                    () -> fact(value(source, MetricValueType.LONG, null), Set.of("count")));
        }
    }

    /**
     * 场景：整数字面量及条件分支不能在求值前发生整数溢出。
     * 输入：INT/LONG 边界加法、乘法及条件/Elvis 分支溢出表达式。
     * 流程：编译这些包含整数运算的本地表达式。
     * 预期：均在编译时抛出校验异常。
     */
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

    /**
     * 场景：中间算术保持精确，声明类型的范围由最终归一化校验。
     * 输入：count=Long.MAX_VALUE，表达式 count * 2，输出 LONG。
     * 流程：先真实求值，再调用 normalize。
     * 预期：中间 BigDecimal 等于精确两倍值；归一化报 RESULT_INVALID。
     */
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

    /**
     * 场景：本地及依赖数值都不能引入二进制浮点误差。
     * 输入：本地 count 和依赖 TOTAL.value 分别传入 Double 0.1。
     * 流程：执行本地乘法及派生引用。
     * 预期：两条路径均拒绝不精确的浮点输入。
     */
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

    /**
     * 场景：多指标表达式提取直接依赖并保留重复引用的计算语义。
     * 输入：B.value + A.amount + B.value，A=2、B=3。
     * 流程：编译、读取依赖集合，再求值并尝试修改集合。
     * 预期：依赖按 A、B 排序去重且不可修改；结果为8。
     */
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

    /**
     * 场景：派生指标从已加载的依赖结果计算比例。
     * 输入：SUMMARY.approved=1、total=3，ratio 输出6位小数。
     * 流程：真实编译派生表达式并传入两个字段值。
     * 预期：结果为0.333333。
     */
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

    /**
     * 场景：派生定义必须包含静态可识别的指标引用。
     * 输入：动态拼接 code/field、空名称、纯常量及本地变量表达式。
     * 流程：调用 compileDerived。
     * 预期：均拒绝，不能生成动态或无依赖的派生句柄。
     */
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

    /**
     * 场景：空值兜底不能掩盖缺失的输入字段或依赖。
     * 输入：本地 count 和依赖 COUNT.value 分别为显式 null 或缺少键。
     * 流程：执行条件表达式及 Elvis 兜底。
     * 预期：显式 null 可返回 null/0；缺少键必须抛出校验异常。
     */
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

    /**
     * 场景：表达式语义比较忽略无意义排版，同时保留实际运算差异。
     * 输入：count * 2、带空格括号的等价式以及 count * 3。
     * 流程：编译并比较 canonicalAst，检查本地字段集合。
     * 预期：前两者一致，乘3不同；字段只有 count 且不可修改。
     */
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

    /**
     * 场景：运行时仍防御内部错误构造的未校验句柄。
     * 输入：手动注入方法调用、布尔结果、类型访问及构造器 AST。
     * 流程：绕过编译入口构造句柄后 evaluate。
     * 预期：均报 RESULT_INVALID，并定位 expression；不把此夹具当公共配置入口。
     */
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

    /**
     * 场景：内部错误句柄不能读取校验集合以外的依赖。
     * 输入：声明 DECLARED.value，但 AST 读取 OTHER.value；输入同时含两者。
     * 流程：直接构造不一致句柄并求值。
     * 预期：即使输入已有 OTHER，也必须拒绝越界引用。
     */
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

    /**
     * 场景：内部错误句柄不能读取校验集合以外的本地字段。
     * 输入：只声明 count，AST 使用 extra + 1，输入含 extra=99。
     * 流程：直接构造不一致句柄并求值。
     * 预期：拒绝读取未声明的 extra。
     */
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
