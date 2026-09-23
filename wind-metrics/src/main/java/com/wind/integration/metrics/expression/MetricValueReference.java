package com.wind.integration.metrics.expression;

import org.jspecify.annotations.NullMarked;

/**
 * 表达式中对另一个指标结果字段的确定性引用。
 *
 * @param metricCode 被引用指标编码
 * @param valueField 被引用结果字段
 * @author wuxp
 * @since 2026-07-24
 */
@NullMarked
public record MetricValueReference(String metricCode, String valueField) implements Comparable<MetricValueReference> {

    public MetricValueReference {
        if (metricCode == null || metricCode.isBlank()) {
            throw new IllegalArgumentException("metricCode must not be blank");
        }
        if (valueField == null || valueField.isBlank()) {
            throw new IllegalArgumentException("valueField must not be blank");
        }
    }

    @Override
    public int compareTo(MetricValueReference other) {
        int metricCodeComparison = metricCode.compareTo(other.metricCode);
        return metricCodeComparison != 0 ? metricCodeComparison : valueField.compareTo(other.valueField);
    }
}
