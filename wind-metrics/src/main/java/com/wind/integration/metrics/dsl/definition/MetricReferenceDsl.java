package com.wind.integration.metrics.dsl.definition;

import java.util.Objects;

/**
 * 派生表达式引用的另一个指标值。
 *
 * @param metricCode 被引用指标编码
 * @param valueField 被引用指标的值字段名
 *
 * @author wuxp
 * @date 2026-07-21 17:51
 */
public record MetricReferenceDsl(String metricCode, String valueField) {

    public MetricReferenceDsl {
        Objects.requireNonNull(metricCode, "metricCode must not be null");
        Objects.requireNonNull(valueField, "valueField must not be null");
    }
}
