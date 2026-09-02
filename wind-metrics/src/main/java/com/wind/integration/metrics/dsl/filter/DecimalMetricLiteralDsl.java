package com.wind.integration.metrics.dsl.filter;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * 指标过滤条件中的精确十进制字面量。
 *
 * @param value 十进制数值，不接受浮点近似值
 *
 * @author wuxp
 * @date 2026-07-21 17:51
 */
@Schema(description = "指标过滤条件中的精确十进制字面量")
public record DecimalMetricLiteralDsl(
        @Schema(description = "十进制数值，不接受浮点近似值") BigDecimal value) implements MetricNumericLiteralDsl {

    public DecimalMetricLiteralDsl {
        Objects.requireNonNull(value, "value must not be null");
    }
}
