package com.wind.integration.metrics.dsl;

import java.util.List;
import java.util.Objects;

/** 逻辑组合过滤条件。 */
public record LogicalMetricFilterDsl(MetricFilterOperator operator,
                                     List<MetricFilterDsl> operands) implements MetricFilterDsl {

    public LogicalMetricFilterDsl {
        Objects.requireNonNull(operator, "operator must not be null");
        operands = List.copyOf(operands);
    }
}
