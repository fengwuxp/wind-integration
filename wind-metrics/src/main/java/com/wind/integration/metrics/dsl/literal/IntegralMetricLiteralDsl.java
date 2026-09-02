package com.wind.integration.metrics.dsl.literal;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigInteger;
import java.util.Objects;

/**
 * Metric DSL 中的任意精度整数字面量。
 *
 * @param value 整数值
 *
 * @author wuxp
 * @date 2026-07-21 17:51
 */
@Schema(description = "Metric DSL 中的任意精度整数字面量")
public record IntegralMetricLiteralDsl(
        @Schema(description = "整数值") BigInteger value) implements MetricNumericLiteralDsl {

    public IntegralMetricLiteralDsl {
        Objects.requireNonNull(value, "value must not be null");
    }
}
