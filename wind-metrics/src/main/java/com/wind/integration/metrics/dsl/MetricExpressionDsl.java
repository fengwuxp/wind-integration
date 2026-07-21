package com.wind.integration.metrics.dsl;

import java.util.Objects;

/** 指标表达式。 */
public record MetricExpressionDsl(MetricExpressionType type, String value) {

    public MetricExpressionDsl {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(value, "value must not be null");
    }
}
