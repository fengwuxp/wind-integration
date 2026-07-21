package com.wind.integration.metrics.dsl;

import java.util.Objects;

/** 跨指标表达式引用。 */
public record MetricReferenceDsl(String metricCode, String valueField) {

    public MetricReferenceDsl {
        Objects.requireNonNull(metricCode, "metricCode must not be null");
        Objects.requireNonNull(valueField, "valueField must not be null");
    }
}
