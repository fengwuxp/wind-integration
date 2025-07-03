package com.wind.integration.metrics;

/**
 * 用于指标计算
 *
 * @author wuxp
 * @date 2025-07-03 13:58
 **/
public interface WindMetricsEvaluator<M> {

    /**
     * 计算指标
     *
     * @param query 计算条件
     * @return 指标值
     */
    M evaluate(WindMetricsAggregationQuery query);
}
