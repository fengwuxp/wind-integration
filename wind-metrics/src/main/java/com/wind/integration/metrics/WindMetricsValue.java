package com.wind.integration.metrics;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 基础的指标指描述对象，它包含名称和值
 *
 * @param <V> 值类型
 * @author wuxp
 * @date 2025-06-17 14:27
 **/
public interface WindMetricsValue<V> {

    /**
     * @return 指标名称，全局唯一
     */
    @NotBlank
    String getName();

    /**
     * @return 指标值
     */
    @NotNull
    V getValue();
}
