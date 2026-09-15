package com.wind.integration.metrics;

import org.jspecify.annotations.Nullable;

/**
 * 保存已计算的名称和值引用，不负责求值或修改外部状态。
 *
 * @param name 指标或所属指标内的字段名称
 * @param value 已计算的值
 * @param <V> 值类型
 * @author wuxp
 * @since 2026-09-15
 */
record ImmutableMetricsValue<V>(String name, @Nullable V value) implements WindMetricsValue<V> {

    ImmutableMetricsValue {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public @Nullable V getValue() {
        return value;
    }
}
