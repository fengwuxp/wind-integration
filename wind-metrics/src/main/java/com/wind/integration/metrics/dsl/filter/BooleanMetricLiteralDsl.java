package com.wind.integration.metrics.dsl.filter;

/**
 * 指标过滤条件中的布尔字面量。
 *
 * @param value 布尔值
 *
 * @author wuxp
 * @date 2026-07-21 17:51
 */
public record BooleanMetricLiteralDsl(boolean value) implements MetricLiteralDsl {
}
