package com.wind.integration.metrics.dsl;

import java.util.List;
import java.util.Objects;

/** 集合比较过滤条件。 */
public record SetMetricFilterDsl(MetricFilterOperator operator,
                                 String fieldRef,
                                 List<MetricLiteralDsl> values) implements MetricFilterDsl {

    public SetMetricFilterDsl {
        Objects.requireNonNull(operator, "operator must not be null");
        Objects.requireNonNull(fieldRef, "fieldRef must not be null");
        values = List.copyOf(values);
    }
}
