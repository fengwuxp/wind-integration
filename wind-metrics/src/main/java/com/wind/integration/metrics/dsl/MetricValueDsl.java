package com.wind.integration.metrics.dsl;

import org.jspecify.annotations.Nullable;

import java.math.RoundingMode;
import java.util.Objects;

/** 单个指标值字段的计算合同。 */
public record MetricValueDsl(MetricValueType valueType,
                             @Nullable Integer scale,
                             @Nullable RoundingMode roundingMode,
                             @Nullable MetricMeasureDsl measure,
                             @Nullable MetricExpressionDsl expression,
                             MetricOrElseDsl orElse) {

    public MetricValueDsl {
        Objects.requireNonNull(valueType, "valueType must not be null");
        Objects.requireNonNull(orElse, "orElse must not be null");
    }
}
