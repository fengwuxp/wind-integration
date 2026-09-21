package com.wind.integration.metrics;

import com.wind.integration.metrics.enums.MetricValueType;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * 保存已计算的指标编码和值引用，不负责求值或修改外部状态。
 *
 * @param code  指标编码
 * @param valueType 已声明或由固定 payload 推断的标量类型
 * @param value 已计算的值
 * @param <V>   值类型
 * @author wuxp
 * @since 2026-09-15
 */
record ImmutableMetricsValue<V>(String code, @NonNull MetricValueType valueType, @Nullable V value) implements WindMetricsValue<V> {

    ImmutableMetricsValue {
        Objects.requireNonNull(valueType, "valueType must not be null");
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
    public @NonNull MetricValueType getValueType() {
        return valueType;
    }

    @Override
    public @Nullable V getValue() {
        return value;
    }
}
