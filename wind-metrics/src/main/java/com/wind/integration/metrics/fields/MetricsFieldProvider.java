package com.wind.integration.metrics.fields;

import java.util.Collection;
import java.util.List;

/**
 * 指标字段提供者
 *
 * @author wuxp
 * @date 2025-06-17 14:22
 **/
public interface MetricsFieldProvider {

    /**
     * 获取指标字段
     *
     * @param name 指标名称，全局唯一
     * @param <M>  指标值类型
     * @return 指标字段
     */
    <M extends Number> SingleMetricsField<M> getSingleMetrics(String name);

    <M extends Number> MultipleMetricsFields<M> getMultipleMetrics(String name);

    /**
     * 获取指标字段
     *
     * @param metricsNames 指标名称列表
     * @param <M>          指标值类型
     * @return 指标字段
     */
    <M extends Number> List<SingleMetricsField<M>> getSingleMetrics(Collection<String> metricsNames);

    <M extends Number> List<MultipleMetricsFields<M>> getMultipleMetrics(Collection<String> metricsNames);
}
