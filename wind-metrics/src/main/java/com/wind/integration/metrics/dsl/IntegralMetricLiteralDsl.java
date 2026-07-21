package com.wind.integration.metrics.dsl;

import java.math.BigInteger;
import java.util.Objects;

/** 整数字面量。 */
public record IntegralMetricLiteralDsl(BigInteger value) implements MetricNumericLiteralDsl {

    public IntegralMetricLiteralDsl {
        Objects.requireNonNull(value, "value must not be null");
    }
}
