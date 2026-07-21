package com.wind.integration.metrics.dsl;

import java.util.Objects;

/** 指标 Definition DSL 根对象。 */
public record MetricDefinitionDsl(Integer schemaVersion, MetricDefinitionSpec metric) {

    public MetricDefinitionDsl {
        Objects.requireNonNull(schemaVersion, "schemaVersion must not be null");
        Objects.requireNonNull(metric, "metric must not be null");
    }
}
