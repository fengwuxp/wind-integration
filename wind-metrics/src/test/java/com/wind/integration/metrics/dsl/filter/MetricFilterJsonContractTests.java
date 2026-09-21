package com.wind.integration.metrics.dsl.filter;

import com.wind.integration.metrics.dsl.definition.MetricMeasureDsl;
import com.wind.integration.metrics.dsl.definition.MetricOrElseDsl;
import com.wind.integration.metrics.dsl.definition.MetricSubjectDsl;
import com.wind.integration.metrics.dsl.definition.MetricTimeDsl;
import com.wind.integration.metrics.dsl.definition.MetricValueDsl;
import com.wind.integration.metrics.dsl.definition.selection.MetricLimitDsl;
import com.wind.integration.metrics.dsl.definition.selection.MetricRowSelectionDsl;
import com.wind.integration.metrics.dsl.literal.BooleanMetricLiteralDsl;
import com.wind.integration.metrics.dsl.literal.DecimalMetricLiteralDsl;
import com.wind.integration.metrics.dsl.literal.IntegralMetricLiteralDsl;
import com.wind.integration.metrics.dsl.literal.MetricLiteralDsl;
import com.wind.integration.metrics.dsl.literal.MetricNumericLiteralDsl;
import com.wind.integration.metrics.dsl.literal.StringMetricLiteralDsl;
import com.wind.integration.metrics.enums.MetricAggregation;
import com.wind.integration.metrics.enums.MetricFilterOperator;
import com.wind.integration.metrics.enums.MetricOrElseMode;
import com.wind.integration.metrics.enums.MetricValueShape;
import com.wind.integration.metrics.enums.MetricValueType;
import com.wind.integration.metrics.spec.MetricDSLDefinition;
import com.wind.integration.metrics.spec.MetricDefinitionSpec;
import com.wind.jackson.WindJson;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 验证封闭 filter/literal AST 的公共 JSON 合同。
 *
 * <p>这些测试从强类型 DSL 创建定义，经 WindJson 保存形态再读取，覆盖宿主
 * DefinitionSpec 实际会走过的嵌套路径；断言不引入 discriminator 字段，避免
 * Capte 持久化内容出现第二套语法。</p>
 */
class MetricFilterJsonContractTests {

    /**
     * 场景：真实 DefinitionSpec 中同时出现比较、集合、空值和逻辑过滤。
     * 输入：ORDER_COUNT 的 schema1 RAW 定义，过滤树含四种节点及 Boolean/String/
     * Integral/Decimal literal。
     * 流程：构造强类型 AST，序列化为保存 JSON，再按公共 DefinitionSpec 读取。
     * 输出：对象完整相等，过滤树和全部精确字面量可继续供发布与查询使用。
     */
    @Test
    void testDefinitionSpecRoundTripsClosedFilterAndLiteralTree() {
        MetricFilterDsl deleted = new ComparisonMetricFilterDsl(
                MetricFilterOperator.EQ, "deleted", new BooleanMetricLiteralDsl(false));
        MetricFilterDsl createdAt = new ComparisonMetricFilterDsl(
                MetricFilterOperator.EQ, "created_at", new StringMetricLiteralDsl("2026-09-20T00:00:00"));
        MetricFilterDsl amount = new ComparisonMetricFilterDsl(
                MetricFilterOperator.GE, "amount",
                new DecimalMetricLiteralDsl(new BigDecimal("12345678901234567890.12345678901234567890")));
        MetricFilterDsl count = new SetMetricFilterDsl(
                MetricFilterOperator.IN, "count",
                List.of(new IntegralMetricLiteralDsl(new BigInteger("9007199254740993")),
                        new IntegralMetricLiteralDsl(BigInteger.valueOf(7))));
        MetricFilterDsl filter = new LogicalMetricFilterDsl(
                MetricFilterOperator.AND, List.of(deleted,
                        new LogicalMetricFilterDsl(MetricFilterOperator.OR, List.of(createdAt, amount)),
                        count,
                        new NullMetricFilterDsl(MetricFilterOperator.IS_NOT_NULL, "created_at")));
        MetricDSLDefinition definition = new MetricDSLDefinition(
                "ORDER_COUNT", 1, MetricValueShape.SCALAR, "ORDER", List.of(),
                new MetricSubjectDsl(MetricSubjectDsl.GLOBAL, null), new MetricTimeDsl("created_at"),
                List.of(), Map.of(), new MetricRowSelectionDsl(
                        new ComparisonMetricFilterDsl(MetricFilterOperator.GT, "created_at",
                                new StringMetricLiteralDsl("2026-09-19T00:00:00")),
                        List.of(), new MetricLimitDsl(10, null)),
                new MetricValueDsl(MetricValueType.LONG, null, null,
                        new MetricMeasureDsl(MetricAggregation.COUNT, null, filter), null,
                        new MetricOrElseDsl(MetricOrElseMode.NULL, null)),
                Map.of(), List.of());
        MetricDefinitionSpec<?> spec = new MetricDefinitionSpec.MetricDSLDefinitionSpec(1, definition);

        String json = WindJson.toJsonString(spec);
        assertFalse(json.contains("\"filterType\""));
        assertFalse(json.contains("\"literalType\""));

        MetricDefinitionSpec<?> restored = WindJson.parseObject(json, MetricDefinitionSpec.class);
        assertEquals(spec, restored);
    }

    /**
     * 场景：读取未知过滤分支时必须拒绝，不能静默降级为某个合法节点。
     * 输入：未声明的 operator=BETWEEN。
     * 流程：直接按公共 MetricFilterDsl 读取 JSON。
     * 输出：反序列化失败，封闭 AST 的能力边界保持可审计。
     */
    @Test
    void testUnknownFilterBranchIsRejected() {
        String json = "{\"operator\":\"BETWEEN\",\"fieldRef\":\"amount\","
                + "\"value\":{\"value\":1}}";

        assertThrows(JacksonException.class, () -> WindJson.parseObject(json, MetricFilterDsl.class));
    }

    /**
     * 场景：合法 operator 与错误字段形状组合时必须拒绝，不能靠默认值补齐。
     * 输入：比较节点缺 value、集合节点使用 value、逻辑节点把 operands 写成对象。
     * 流程：逐个按公共 MetricFilterDsl 读取。
     * 输出：三种缺失/不匹配形状均反序列化失败。
     */
    @Test
    void testMissingOrMismatchedFilterBranchIsRejected() {
        for (String json : List.of(
                "{\"operator\":\"EQ\",\"fieldRef\":\"amount\"}",
                "{\"operator\":\"IN\",\"fieldRef\":\"amount\",\"value\":{\"value\":1}}",
                "{\"operator\":\"AND\",\"operands\":{}}")) {
            assertThrows(JacksonException.class, () -> WindJson.parseObject(json, MetricFilterDsl.class));
        }
    }

    /**
     * 场景：读取未知字面量形状时必须拒绝，不能把对象误认为字符串或数值。
     * 输入：literal.value 为对象。
     * 流程：直接按公共 MetricLiteralDsl 读取 JSON。
     * 输出：反序列化失败。
     */
    @Test
    void testUnknownLiteralBranchIsRejected() {
        String json = "{\"value\":{\"unexpected\":true}}";

        assertThrows(JacksonException.class, () -> WindJson.parseObject(json,
                MetricLiteralDsl.class));
    }

    /**
     * 场景：Decimal literal 的 JSON token 不能经过 Double 丢失 scale 或指数语义。
     * 输入：1.0、1e3、1.2300 三种 JSON 数字写法。
     * 流程：按公共 MetricLiteralDsl 读取，再比较 BigDecimal 的精确值和 scale。
     * 输出：每个 token 都保持 parser 原文对应的 BigDecimal 表示。
     */
    @Test
    void testDecimalLiteralPreservesScaleAndExponentWithoutDouble() {
        for (String token : List.of("1.0", "1e3", "1.2300")) {
            MetricLiteralDsl restored = WindJson.parseObject(
                    "{\"value\":" + token + "}", MetricLiteralDsl.class);

            assertEquals(new DecimalMetricLiteralDsl(new BigDecimal(token)), restored);
        }
    }

    /**
     * 场景：调用方直接以数值封闭接口读取 literal。
     * 输入：超过 2^53 的整数、带尾零的十进制，以及 Boolean 错误 token。
     * 流程：按 MetricNumericLiteralDsl 读取并检查具体类型，拒绝非数值 token。
     * 输出：Integral/Decimal 精确还原，Boolean 反序列化失败。
     */
    @Test
    void testNumericLiteralInterfaceRoundTripAndRejectsNonNumericToken() {
        MetricNumericLiteralDsl integral = WindJson.parseObject(
                "{\"value\":9007199254740993}", MetricNumericLiteralDsl.class);
        MetricNumericLiteralDsl decimal = WindJson.parseObject(
                "{\"value\":123.4500}", MetricNumericLiteralDsl.class);

        assertEquals(new IntegralMetricLiteralDsl(new BigInteger("9007199254740993")), integral);
        assertEquals(new DecimalMetricLiteralDsl(new BigDecimal("123.4500")), decimal);
        assertThrows(JacksonException.class, () -> WindJson.parseObject(
                "{\"value\":true}", MetricNumericLiteralDsl.class));
    }
}
