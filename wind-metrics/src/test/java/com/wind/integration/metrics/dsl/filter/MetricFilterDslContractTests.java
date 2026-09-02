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

    @Test
    void testComparisonFilterRejectsNonComparisonOperator() {
        Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new ComparisonMetricFilterDsl(MetricFilterOperator.IN, "status", VALUE));
    }

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

    @Test
    void testNullFilterRejectsNonNullOperator() {
        Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new NullMetricFilterDsl(MetricFilterOperator.EQ, "status"));
    }

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
