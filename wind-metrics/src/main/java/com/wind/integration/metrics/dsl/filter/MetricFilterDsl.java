package com.wind.integration.metrics.dsl.filter;

/**
 * 指标事实过滤条件的封闭语法树根类型。
 *
 * <p>仅允许比较、集合、空值和逻辑组合四类节点，避免任意 SQL 进入 DSL。</p>
 *
 * @author wuxp
 * @date 2026-07-21 17:51
 */
public sealed interface MetricFilterDsl permits ComparisonMetricFilterDsl,
        SetMetricFilterDsl, NullMetricFilterDsl, LogicalMetricFilterDsl {
}
