package com.wind.integration.metrics.dsl;

import org.jspecify.annotations.Nullable;

import java.util.Objects;

/** 被统计主体合同。 */
public record MetricSubjectDsl(String type, @Nullable String field) {

    public MetricSubjectDsl {
        Objects.requireNonNull(type, "type must not be null");
    }
}
