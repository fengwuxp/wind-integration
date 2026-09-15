package com.wind.integration.metrics;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 固定的一组字段值，字段容器和子值对象均只读。
 *
 * @author wuxp
 * @since 2026-09-15
 */
final class ImmutableMetricsValueSet implements WindMetricsValueSet<Map<String, Object>> {

    private final String name;

    private final Map<String, Object> values;

    ImmutableMetricsValueSet(String name, Map<String, ?> values) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        this.name = name;
        Map<String, Object> copy = new LinkedHashMap<>();
        Objects.requireNonNull(values, "values must not be null").forEach((field, value) -> {
            if (field == null || field.isBlank()) {
                throw new IllegalArgumentException("field name must not be blank");
            }
            copy.put(field, value);
        });
        this.values = Collections.unmodifiableMap(copy);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Map<String, Object> getValue() {
        return values;
    }

    @Override
    public List<WindMetricsValue<Object>> getMetricsFields() {
        return values.entrySet().stream()
                .map(entry -> WindMetricsValue.of(entry.getKey(), entry.getValue()))
                .toList();
    }

    @Override
    public Map<String, Object> asValues() {
        return values;
    }
}
