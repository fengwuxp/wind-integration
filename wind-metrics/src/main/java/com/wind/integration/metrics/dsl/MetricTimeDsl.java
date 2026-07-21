package com.wind.integration.metrics.dsl;

import java.util.Objects;

/** 主事实表时间字段。 */
public record MetricTimeDsl(String field) {

    public MetricTimeDsl {
        Objects.requireNonNull(field, "field must not be null");
    }
}
