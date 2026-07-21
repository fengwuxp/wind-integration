package com.wind.integration.metrics.dsl;

import java.util.Objects;

/** 单值比较过滤条件。 */
public record ComparisonMetricFilterDsl(MetricFilterOperator operator,
                                        String fieldRef,
                                        MetricLiteralDsl value) implements MetricFilterDsl {

    public ComparisonMetricFilterDsl {
        Objects.requireNonNull(operator, "operator must not be null");
        Objects.requireNonNull(fieldRef, "fieldRef must not be null");
        Objects.requireNonNull(value, "value must not be null");
    }
}
