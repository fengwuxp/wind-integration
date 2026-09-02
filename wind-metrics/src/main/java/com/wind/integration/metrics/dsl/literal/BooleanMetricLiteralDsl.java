package com.wind.integration.metrics.dsl.literal;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Metric DSL 中的布尔字面量。
 *
 * @param value 布尔值
 *
 * @author wuxp
 * @date 2026-07-21 17:51
 */
@Schema(description = "Metric DSL 中的布尔字面量")
public record BooleanMetricLiteralDsl(
        @Schema(description = "布尔值") boolean value) implements MetricLiteralDsl {
}
