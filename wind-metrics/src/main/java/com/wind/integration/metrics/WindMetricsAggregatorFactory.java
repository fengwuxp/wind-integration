package com.wind.integration.metrics;

import com.wind.integration.metrics.fields.MultipleValueMetricsField;
import com.wind.integration.metrics.fields.SingleValueMetricsField;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 聚合一个或多个指标转换为指标（java）对象的工厂
 * 指标对象通常用于报表展示、数据导出或其他统计目的。
 *
 * @author wuxp
 * @date 2025-06-24 09:17
 **/
public interface WindMetricsAggregatorFactory {

    /**
     * 创建一个指标对象工厂
     * 一般用于指标输出单个值的场景
     *
     * @param objectType 对象类型
     * @param <T>        指标聚合对象类型
     * @return 指标对象工厂
     * @see SingleValueMetricsField
     */
    <T> WindMetricsAggregator<T> factory(@NotNull Class<T> objectType);

    /**
     * 通过指标名称和指标对象类型创建一个指标对象
     * 一般用于指标输出多个值的场景
     *
     * @param metricsName 指标名称
     * @param objectType  指标聚合对象类型
     * @return 指标对象工厂
     * @see MultipleValueMetricsField
     */
    <T> WindMetricsAggregator<T> factory(@NotBlank String metricsName, @NotNull Class<T> objectType);


}
