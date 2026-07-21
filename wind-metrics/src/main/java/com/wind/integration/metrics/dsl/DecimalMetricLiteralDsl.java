package com.wind.integration.metrics.dsl;

import java.math.BigDecimal;
import java.util.Objects;

/** 十进制字面量。 */
public record DecimalMetricLiteralDsl(BigDecimal value) implements MetricNumericLiteralDsl {

    public DecimalMetricLiteralDsl {
        Objects.requireNonNull(value, "value must not be null");
    }
}
