package com.wind.integration.metrics.dsl;

import java.util.Objects;

/** 空值判断过滤条件。 */
public record NullMetricFilterDsl(MetricFilterOperator operator, String fieldRef) implements MetricFilterDsl {

    public NullMetricFilterDsl {
        Objects.requireNonNull(operator, "operator must not be null");
        Objects.requireNonNull(fieldRef, "fieldRef must not be null");
    }
}
