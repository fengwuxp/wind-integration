package com.wind.integration.metrics.dsl;

import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 指标计算定义。 */
public record MetricDefinitionSpec(String code,
                                   MetricValueShape valueShape,
                                   @Nullable String fact,
                                   List<MetricJoinDsl> joins,
                                   MetricSubjectDsl subject,
                                   @Nullable MetricTimeDsl time,
                                   List<String> dimensions,
                                   Map<String, MetricReferenceDsl> metricRefs,
                                   @Nullable MetricValueDsl value,
                                   Map<String, MetricValueDsl> fields) {

    public MetricDefinitionSpec {
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(valueShape, "valueShape must not be null");
        Objects.requireNonNull(subject, "subject must not be null");
        joins = List.copyOf(joins);
        dimensions = List.copyOf(dimensions);
        metricRefs = immutableMap(metricRefs);
        fields = immutableMap(fields);
    }

    private static <T> Map<String, T> immutableMap(Map<String, T> source) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }
}
