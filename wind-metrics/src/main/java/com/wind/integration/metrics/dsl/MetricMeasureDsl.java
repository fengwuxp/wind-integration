package com.wind.integration.metrics.dsl;

import org.jspecify.annotations.Nullable;

import java.util.Objects;

/** 基础可聚合度量。 */
public record MetricMeasureDsl(MetricAggregation aggregation,
                               @Nullable String field,
                               @Nullable MetricFilterDsl filter) {

    public MetricMeasureDsl {
        Objects.requireNonNull(aggregation, "aggregation must not be null");
    }
}
