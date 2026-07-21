package com.wind.integration.metrics.dsl;

import java.util.Objects;

/** 字符串字面量。 */
public record StringMetricLiteralDsl(String value) implements MetricLiteralDsl {

    public StringMetricLiteralDsl {
        Objects.requireNonNull(value, "value must not be null");
    }
}
