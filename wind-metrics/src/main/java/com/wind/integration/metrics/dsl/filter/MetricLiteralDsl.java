package com.wind.integration.metrics.dsl.filter;

/**
 * 指标过滤条件允许使用的封闭字面量类型。
 *
 * @author wuxp
 * @date 2026-07-21 17:51
 */
public sealed interface MetricLiteralDsl permits StringMetricLiteralDsl,
        BooleanMetricLiteralDsl, MetricNumericLiteralDsl {
}
