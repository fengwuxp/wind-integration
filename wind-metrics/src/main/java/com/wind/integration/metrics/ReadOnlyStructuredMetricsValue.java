package com.wind.integration.metrics;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 一个指标的固定结构化值，只读边界为指标名称及字段容器。
 *
 * <p>字段值保留原对象引用，不保证业务对象深度不可变。
 * 同时实现旧接口以保留已有消费者的类型判断和字段访问。</p>
 *
 * @author wuxp
 * @since 2026-09-15
 */
@SuppressWarnings("deprecation")
final class ReadOnlyStructuredMetricsValue implements WindStructuredMetricsValue<Map<String, Object>> {

    private final String metricName;

    private final Map<String, Object> fieldValues;

    ReadOnlyStructuredMetricsValue(String metricName, Map<String, ?> fieldValues) {
        if (metricName == null || metricName.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        this.metricName = metricName;
        Map<String, Object> copy = new LinkedHashMap<>();
        Objects.requireNonNull(fieldValues, "values must not be null").forEach((field, value) -> {
            if (field == null || field.isBlank()) {
                throw new IllegalArgumentException("field name must not be blank");
            }
            copy.put(field, value);
        });
        this.fieldValues = Collections.unmodifiableMap(copy);
    }

    @Override
    public String getName() {
        return metricName;
    }

    @Override
    public Map<String, Object> getValue() {
        return fieldValues;
    }

    @Override
    public Map<String, Object> asFieldValues() {
        return fieldValues;
    }

}
