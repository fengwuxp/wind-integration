package com.wind.integration.metrics.dsl;

/** 指标过滤条件的封闭 AST。 */
public sealed interface MetricFilterDsl permits ComparisonMetricFilterDsl,
        SetMetricFilterDsl, NullMetricFilterDsl, LogicalMetricFilterDsl {
}
