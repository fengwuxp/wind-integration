package com.wind.integration.metrics;

import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;

/**
 * 业务维度指标值获取器，值来源可以是历史累计 + 最新数据的实时统计
 *
 * @author wuxp
 * @date 2025-06-24 13:19
 **/
public interface WindMetricsValueResolver<V> {

    /**
     * 聚合多个业务维度 id 的指标值
     *
     * @param dimensionsIds    业务维度标识集合（如用户 ID）
     * @param metricsValueType 指标值类型
     * @return 指标值
     */
    V resolve(@NotNull Collection<? extends Serializable> dimensionsIds, Class<?> metricsValueType);

    /**
     * @param dimensionsId     业务维度标识（如用户 ID）
     * @param metricsValueType 指标值类型
     * @return 指标值
     */
    @NotNull
    default V resolve(@NotNull Serializable dimensionsId, @NotNull Class<?> metricsValueType) {
        return resolve(Collections.singletonList(dimensionsId), metricsValueType);
    }

    /**
     * 指标值查询条件支持
     *
     * @param metricsValueType 指标值类型
     */
    boolean supports(@NotNull Class<V> metricsValueType);

}
