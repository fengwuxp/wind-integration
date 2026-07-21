package com.wind.integration.metrics.dsl;

/** 精确数值字面量。 */
public sealed interface MetricNumericLiteralDsl extends MetricLiteralDsl
        permits IntegralMetricLiteralDsl, DecimalMetricLiteralDsl {
}
