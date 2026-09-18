package com.wind.integration.metrics;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * 保存已计算的指标编码和值引用，不负责求值或修改外部状态。
 *
 * @param code  指标编码
 * @param value 已计算的值
 * @param <V>   值类型
 * @author wuxp
 * @since 2026-09-15
 */
record ImmutableMetricsValue<V>(String code, @Nullable V value) implements WindMetricsValue<V> {

    ImmutableMetricsValue {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code must not be blank");
        }
    }

    @Override
    public @NonNull String getName() {
        return getCode();
    }

    @Override
    @NonNull
    public String getCode() {
        return code;
    }

    @Override
    public @Nullable V getValue() {
        return value;
    }
}
