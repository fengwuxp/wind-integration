package com.wind.integration.metrics.dsl.filter;

/**
 * 不使用二进制浮点近似表示的精确数值字面量。
 *
 * @author wuxp
 * @date 2026-07-21 17:51
 */
public sealed interface MetricNumericLiteralDsl extends MetricLiteralDsl
        permits IntegralMetricLiteralDsl, DecimalMetricLiteralDsl {
}
