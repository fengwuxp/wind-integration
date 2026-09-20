package com.wind.integration.metrics.dsl.filter;

import com.wind.integration.metrics.dsl.literal.StringMetricLiteralDsl;
import com.wind.integration.metrics.enums.MetricFilterOperator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 指标过滤 AST 节点的本地不变量测试。
 *
 * @author wuxp
 * @since 2026-09-02
 */
class MetricFilterDslContractTests {

    private static final StringMetricLiteralDsl VALUE = new StringMetricLiteralDsl("APPROVED");

    /**
     * 场景：比较节点不能承载集合操作。
     * 输入：status=APPROVED 的比较节点配置 IN。
     * 流程：构造 ComparisonMetricFilterDsl。
     * 预期：抛出 IllegalArgumentException。
     */
    @Test
    void testComparisonFilterRejectsNonComparisonOperator() {
        Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new ComparisonMetricFilterDsl(MetricFilterOperator.IN, "status", VALUE));
    }

    /**
     * 场景：集合过滤必须使用集合操作且至少包含一个值。
     * 输入：EQ 配合单值列表，或 IN 配合空列表。
     * 流程：分别构造 SetMetricFilterDsl。
     * 预期：两种非法形态都被拒绝。
     */
    @Test
    void testSetFilterRejectsInvalidShape() {
        Assertions.assertAll(
                () -> Assertions.assertThrows(
                        IllegalArgumentException.class,
                        () -> new SetMetricFilterDsl(MetricFilterOperator.EQ, "status", List.of(VALUE))),
                () -> Assertions.assertThrows(
                        IllegalArgumentException.class,
                        () -> new SetMetricFilterDsl(MetricFilterOperator.IN, "status", List.of())));
    }

    /**
     * 场景：空值过滤只能使用空值判断操作。
     * 输入：status 空值节点配置 EQ。
     * 流程：构造 NullMetricFilterDsl。
     * 预期：抛出 IllegalArgumentException。
     */
    @Test
    void testNullFilterRejectsNonNullOperator() {
        Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new NullMetricFilterDsl(MetricFilterOperator.EQ, "status"));
    }

    /**
     * 场景：逻辑组合须使用逻辑操作并具备足够的子节点。
     * 输入：两个子节点却用 EQ，或 AND 只有一个 IS_NULL 子节点。
     * 流程：分别构造 LogicalMetricFilterDsl。
     * 预期：均拒绝不匹配的逻辑结构。
     */
    @Test
    void testLogicalFilterRejectsInvalidShape() {
        MetricFilterDsl operand = new NullMetricFilterDsl(MetricFilterOperator.IS_NULL, "status");

        Assertions.assertAll(
                () -> Assertions.assertThrows(
                        IllegalArgumentException.class,
                        () -> new LogicalMetricFilterDsl(MetricFilterOperator.EQ, List.of(operand, operand))),
                () -> Assertions.assertThrows(
                        IllegalArgumentException.class,
                        () -> new LogicalMetricFilterDsl(MetricFilterOperator.AND, List.of(operand))));
    }
}
