package com.wind.integration.metrics.dsl.filter;

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
public record DecimalMetricLiteralDsl(BigDecimal value) implements MetricNumericLiteralDsl {

    public DecimalMetricLiteralDsl {
        Objects.requireNonNull(value, "value must not be null");
    }
}
