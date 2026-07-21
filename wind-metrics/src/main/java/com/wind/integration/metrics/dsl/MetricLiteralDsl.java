package com.wind.integration.metrics.dsl;

/** 指标过滤字面量。 */
public sealed interface MetricLiteralDsl permits StringMetricLiteralDsl,
        BooleanMetricLiteralDsl, MetricNumericLiteralDsl {
}
