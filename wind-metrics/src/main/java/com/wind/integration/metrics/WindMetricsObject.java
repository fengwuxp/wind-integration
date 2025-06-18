package com.wind.integration.metrics;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * 基础的指标描述对象，它包含名称和值
 *
 * @param <V> 值类型
 * @author wuxp
 * @date 2025-06-17 14:27
 **/
public interface WindMetricsObject<V> {

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
