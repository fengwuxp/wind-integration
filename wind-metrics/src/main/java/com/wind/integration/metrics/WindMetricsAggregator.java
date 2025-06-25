package com.wind.integration.metrics;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * 聚合指标构建器，用于将一个或多个指标名称映射到目标对象字段，最终构建一个指标对象实例。
 *
 * <p>使用 {@link #named(String, String)} 维护指标名称与对象字段之间的映射关系，
 * 使用 {@link #get()} 执行聚合并构建最终对象。</p>
 *
 * @author wuxp
 * @date 2025-06-24 09:28
 **/
public interface WindMetricsAggregator<T> {

    /**
     * 指标对象名称和字段关系
     *
     * @param metricsName 指标名称
     * @param filedName   指标对象字段名称
     * @return this
     */
    WindMetricsAggregator<T> named(@NotBlank String metricsName, @NotBlank String filedName);

    /**
     * @param query 查询条件
     * @return 获取聚合的指标对象
     */
    @NotNull
    T aggregate(WindMetricsAggregationQuery query);
}
