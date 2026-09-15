package com.wind.integration.metrics;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 聚合指标构建器，用于将一个或多个指标名称映射到目标对象字段，最终构建一个指标对象实例。
 *
 * <p>使用 {@link #named(String, String)} 维护指标名称与对象字段之间的映射关系，
 * 使用 {@link #aggregate} 取得指标值并组装最终对象。这里的聚合是多个指标到对象的组装，
 * 不限定 SUM、COUNT 等计算算法，也不限定实时、快照或分段取数。</p>
 *
 * @author wuxp
 * @date 2025-06-24 09:28
 **/
public interface WindMetricsAggregator<T> {

    /**
     * 指标对象名称和字段关系
     *
     * @param filedName   指标对象字段名称
     * @param metricsName 指标名称
     * @return this
     */
    WindMetricsAggregator<T> named(@NotBlank String filedName, @NotBlank String metricsName);

    /**
     * @param query 查询条件
     * @return 获取聚合的指标对象
     */
    @NotNull
    T aggregate(WindMetricsAggregationQuery query);
}
