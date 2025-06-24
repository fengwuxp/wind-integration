package com.wind.integration.metrics;

import javax.validation.constraints.NotNull;

/**
 * 指标值查询器
 *
 * @author wuxp
 * @date 2025-06-24 13:19
 **/
public interface WindMetricsValueFetcher<V> {

    /**
     * @param name  指标名称
     * @param query 指标查询条件
     * @return 指标值
     */
    @NotNull
    V query(String name, WindMetricsAggregationQuery query);

}
