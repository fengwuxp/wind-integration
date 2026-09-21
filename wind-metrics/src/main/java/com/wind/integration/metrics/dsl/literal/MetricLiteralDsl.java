package com.wind.integration.metrics.dsl.literal;

import io.swagger.v3.oas.annotations.media.Schema;
import tools.jackson.databind.annotation.JsonDeserialize;

/**
 * Metric DSL 允许使用的封闭字面量类型。
 *
 * @author wuxp
 * @date 2026-07-21 17:51
 */
@Schema(description = "Metric DSL 允许使用的封闭字面量")
@JsonDeserialize(using = MetricLiteralDslDeserializer.class)
public sealed interface MetricLiteralDsl permits StringMetricLiteralDsl,
        BooleanMetricLiteralDsl, MetricNumericLiteralDsl {
}
