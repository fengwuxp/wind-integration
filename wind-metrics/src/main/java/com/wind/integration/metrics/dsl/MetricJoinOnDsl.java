package com.wind.integration.metrics.dsl;

import java.util.Objects;

/** 事实关联的等值字段对。 */
public record MetricJoinOnDsl(String primaryField, String joinField) {

    public MetricJoinOnDsl {
        Objects.requireNonNull(primaryField, "primaryField must not be null");
        Objects.requireNonNull(joinField, "joinField must not be null");
    }
}
