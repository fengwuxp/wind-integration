package com.wind.integration.metrics.dsl.literal;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Metric DSL 允许使用的封闭字面量类型。
 *
 * @author wuxp
 * @date 2026-07-21 17:51
 */
@Schema(description = "Metric DSL 允许使用的封闭字面量")
public sealed interface MetricLiteralDsl permits StringMetricLiteralDsl,
        BooleanMetricLiteralDsl, MetricNumericLiteralDsl {
}
