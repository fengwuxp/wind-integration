package com.wind.integration.metrics.dsl.materialization;

import com.wind.integration.metrics.enums.MetricMergeState;

import java.util.Objects;

/**
 * 联合物化依赖需要冻结的单个叶子度量及其跨分段合并状态。
 *
 * @param valueField 依赖指标的值字段
 * @param mergeState 跨分段合并状态
 *
 * @author wuxp
 * @since 2026-07-28
 */
public record MetricMaterializationMeasureDsl(String valueField, MetricMergeState mergeState) {

    public MetricMaterializationMeasureDsl {
        Objects.requireNonNull(valueField, "valueField must not be null");
        Objects.requireNonNull(mergeState, "mergeState must not be null");
    }
}
