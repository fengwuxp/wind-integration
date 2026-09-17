package com.wind.integration.metrics;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 一个指标的固定结构化值，只读边界为指标编码及字段容器。
 *
 * <p>字段值保留原对象引用，不保证业务对象深度不可变。
 * 同时实现旧接口以保留已有消费者的类型判断和字段访问。</p>
 *
 * @author wuxp
 * @since 2026-09-15
 */
@SuppressWarnings("deprecation")
final class ReadOnlyStructuredMetricsValue implements WindStructuredMetricsValue<Map<String, Object>> {

    private final String metricCode;

    private final Map<String, Object> fieldValues;

    ReadOnlyStructuredMetricsValue(String metricCode, Map<String, ?> fieldValues) {
        if (metricCode == null || metricCode.isBlank()) {
            throw new IllegalArgumentException("code must not be blank");
        }
        this.metricCode = metricCode;
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
    public String getCode() {
        return metricCode;
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
