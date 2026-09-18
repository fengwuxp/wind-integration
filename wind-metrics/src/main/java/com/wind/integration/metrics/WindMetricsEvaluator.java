package com.wind.integration.metrics;

import com.wind.integration.metrics.query.MetricQuery;
import org.jspecify.annotations.Nullable;

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
     * @deprecated 新调用使用 {@link #evaluateWithCriteria(MetricQuery)}
     */
    @Deprecated
    M evaluate(WindMetricsAggregationQuery query);

    /**
     * 使用通用条件求值；新实现应直接消费完整条件。
     *
     * <p>默认实现适配既有求值器，独立维度不能转为旧业务变量时明确失败。
     * 方法使用不同名称，避免 evaluate(null) 的旧调用出现重载歧义。</p>
     *
     * @param criteria 计算条件，null 保持原默认求值语义
     * @return 指标值
     */
    default M evaluateWithCriteria(@Nullable MetricQuery criteria) {
        return evaluate(WindMetricsAggregationQuery.fromQuery(criteria));
    }
}
