package com.wind.integration.metrics.dsl.literal;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Objects;

/**
 * Metric DSL 中的字符串字面量。
 *
 * @param value 字符串值
 *
 * @author wuxp
 * @date 2026-07-21 17:51
 */
@Schema(description = "Metric DSL 中的字符串字面量")
public record StringMetricLiteralDsl(
        @Schema(description = "字符串值") String value) implements MetricLiteralDsl {

    public StringMetricLiteralDsl {
        Objects.requireNonNull(value, "value must not be null");
    }
}
