package com.wind.integration.metrics.dsl;

import org.jspecify.annotations.Nullable;

import java.util.Objects;

/** 正常空结果的回退合同。 */
public record MetricOrElseDsl(MetricOrElseMode mode, @Nullable MetricNumericLiteralDsl value) {

    public MetricOrElseDsl {
        Objects.requireNonNull(mode, "mode must not be null");
    }
}
