package com.wind.integration.metrics.spec;

import com.wind.integration.metrics.MetricValidationException;
import com.wind.integration.metrics.dsl.MetricValueCalculator;
import com.wind.integration.metrics.dsl.definition.MetricExpressionDsl;
import com.wind.integration.metrics.dsl.definition.MetricMeasureDsl;
import com.wind.integration.metrics.dsl.definition.MetricOrElseDsl;
import com.wind.integration.metrics.dsl.definition.MetricReferenceDsl;
import com.wind.integration.metrics.dsl.definition.MetricSubjectDsl;
import com.wind.integration.metrics.dsl.definition.MetricTimeDsl;
import com.wind.integration.metrics.dsl.definition.MetricValueDsl;
import com.wind.integration.metrics.enums.MetricAggregation;
import com.wind.integration.metrics.enums.MetricDerivationType;
import com.wind.integration.metrics.enums.MetricExpressionType;
import com.wind.integration.metrics.enums.MetricOrElseMode;
import com.wind.integration.metrics.enums.MetricValueShape;
import com.wind.integration.metrics.enums.MetricValueType;
import com.wind.integration.metrics.expression.MetricExpressionCompiler;
import com.wind.integration.metrics.expression.MetricValueReference;
import com.wind.jackson.WindJson;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 验证定义分类与真实表达式计算一致，且不增加独立的 JSON 分类配置。
 *
 * @author wuxp
 * @since 2026-09-20
 */
class MetricDefinitionObjectTests {

    private static final String VALUE_PATH = "/metric/value";

    private final MetricExpressionCompiler compiler = new MetricExpressionCompiler();

    private final MetricValueCalculator calculator = new MetricValueCalculator();

    /**
     * 场景：事实指标含本地表达式时仍属于 RAW。
     * 输入：ORDER_SUMMARY 含 COUNT 字段及 count * 2，聚合 count=3。
     * 流程：定义 JSON 往返后检查分类，真实编译表达式并计算。
     * 预期：对象往返相等、JSON 无额外 derivationType；RAW 输出 count=3、doubled=6。
     */
    @Test
    void testFactLocalExpressionRemainsRawAfterJsonRoundTrip() {
        MetricValueDsl count = new MetricValueDsl(MetricValueType.LONG, null, null,
                new MetricMeasureDsl(MetricAggregation.COUNT, null, null), null,
                new MetricOrElseDsl(MetricOrElseMode.NULL, null));
        MetricValueDsl doubled = new MetricValueDsl(MetricValueType.LONG, null, null, null,
                new MetricExpressionDsl(MetricExpressionType.SPEL, "count * 2"),
                new MetricOrElseDsl(MetricOrElseMode.NULL, null));
        MetricDSLDefinition definition = roundTrip(new MetricDSLDefinition(
                "ORDER_SUMMARY", 1, MetricValueShape.FIELD_SET, "ORDER", List.of(),
                new MetricSubjectDsl("GLOBAL", null), new MetricTimeDsl("created_at"),
                List.of(), Map.of(), null, null, Map.of("count", count, "doubled", doubled)),
                MetricDSLDefinition.class);

        MetricDefinitionObject contract = definition;
        Assertions.assertEquals(MetricDerivationType.RAW, contract.derivationType());
        var compiled = compiler.compile(doubled.expression(), Set.of("count"), VALUE_PATH);
        Assertions.assertEquals(Map.of("count", 3L, "doubled", 6L),
                calculator.calculate(definition, Map.of("count", 3L),
                        (field, measures) -> compiled.evaluate(doubled, measures, Map.of(), VALUE_PATH)));
    }

    /**
     * 场景：派生定义往返后仍可按依赖结果计算。
     * 输入：DOUBLE_COUNT 绑定 BASE@1，表达式 BASE.value * 2，依赖值3。
     * 流程：JSON 往返、提取引用，再通过真实编译器和计算器求值。
     * 预期：保持 DERIVED 及 BASE.value 引用，输出 value=6，JSON 无重复分类字段。
     */
    @Test
    void testDerivedJsonKeepsDependenciesAndCalculatedResult() {
        MetricDSLDefinition definition = roundTrip(
                derivedDefinition("metric('BASE', 'value') * 2"), MetricDSLDefinition.class);
        MetricDefinitionObject contract = definition;
        Assertions.assertEquals(MetricDerivationType.DERIVED, contract.derivationType());
        var compiled = compiler.compile(definition.value().expression(), Set.of(), VALUE_PATH);
        MetricValueReference dependency = new MetricValueReference("BASE", "value");

        Assertions.assertEquals(Set.of(dependency), compiled.metricValueReferences());
        Assertions.assertEquals(Map.of("value", 6L),
                calculator.calculate(definition, Map.of(), (field, measures) -> compiled.evaluate(
                        definition.value(), measures, Map.of(dependency, 3L), VALUE_PATH)));
    }

    /**
     * 场景：SQL 定义保留原生分类。
     * 输入：ORDER_COUNT 的 COUNT SQL 模板。
     * 流程：序列化并反序列化 MetricSqlDefinition。
     * 预期：对象相等、分类为 RAW，JSON 不额外存储 derivationType。
     */
    @Test
    void testSqlTemplateRemainsRawAfterJsonRoundTrip() {
        MetricDefinitionObject definition = roundTrip(new MetricSqlDefinition(
                "ORDER_COUNT", 1, MetricValueShape.SCALAR, "GLOBAL", List.of(), Map.of(),
                "SELECT COUNT(*) FROM t_order"), MetricSqlDefinition.class);

        Assertions.assertEquals(MetricDerivationType.RAW, definition.derivationType());
    }

    /**
     * 场景：没有事实来源的定义不能仅靠分类被当作有效派生指标。
     * 输入：绑定 BASE@1，却配置 count * 2 或纯常量 1 + 1。
     * 流程：构造派生定义。
     * 预期：抛出 MetricValidationException，拒绝没有静态 metric 引用的表达式。
     */
    @Test
    void testDerivedClassificationDoesNotAcceptMissingMetricReferences() {
        for (String expression : List.of("count * 2", "1 + 1")) {
            Assertions.assertThrows(MetricValidationException.class,
                    () -> derivedDefinition(expression));
        }
    }

    private static MetricDSLDefinition derivedDefinition(String expression) {
        MetricValueDsl value = new MetricValueDsl(MetricValueType.LONG, null, null, null,
                new MetricExpressionDsl(MetricExpressionType.SPEL, expression),
                new MetricOrElseDsl(MetricOrElseMode.NULL, null));
        return new MetricDSLDefinition("DOUBLE_COUNT", 1, MetricValueShape.SCALAR, null, List.of(),
                new MetricSubjectDsl("GLOBAL", null), null, List.of(), Map.of(), null, value, Map.of(),
                List.of(new MetricReferenceDsl("BASE", 1)));
    }

    private static <T extends MetricDefinitionObject> T roundTrip(T definition, Class<T> type) {
        String json = WindJson.toJsonString(definition);
        Map<?, ?> fields = WindJson.parseObject(json, Map.class);
        Assertions.assertFalse(fields.containsKey("derivationType"));
        T restored = WindJson.parseObject(json, type);
        Assertions.assertEquals(definition, restored);
        return restored;
    }
}
