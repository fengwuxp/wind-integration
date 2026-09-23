package com.wind.integration.metrics;

import com.wind.integration.metrics.query.MetricQuery;

/**
 * 历史指标求值接口，保留旧查询实现与 lambda 的调用合同。
 *
 * <p>与 {@link WindMetricsValue} 组合时，可由 getValue 触发求值；重新读取是否求值由实现决定。
 * 新实现使用 {@link WindMetricsValueEvaluator} 直接接收 MetricQuery，无需实现旧条件入口。
 * 条件类型迁移时由装配方显式转换，无法表示的条件应明确失败。</p>
 *
 * @author wuxp
 * @date 2025-07-03 13:58
 * @deprecated 新实现使用 {@link WindMetricsValueEvaluator}；历史消费者迁移前保留旧查询方法签名
 **/
@FunctionalInterface
@Deprecated(since = "4.0.0", forRemoval = false)
public interface WindMetricsEvaluator<M> {

    /**
     * 计算指标
     *
     * @param query 计算条件
     * @return 指标值
     * @deprecated 新实现使用 {@link WindMetricsValueEvaluator#evaluate(MetricQuery)}
     */
    @Deprecated(forRemoval = true)
    M evaluate(WindMetricsAggregationQuery query);

}
