package com.wind.integration.metrics.fields;

import com.wind.integration.metrics.WindMetricsAggregationQuery;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Null;
import java.util.ArrayList;
import java.util.List;

/**
 * 指标字段工厂
 *
 * @author wuxp
 * @date 2025-06-17 14:22
 **/
public interface WindMetricsFieldFactory {

    /**
     * 单个值指标
     *
     * @param name 指标名称，全局唯一
     * @param <M>  指标值类型
     * @return 指标字段
     */
    @NotNull
    default <M extends Number> SingleValueMetricsField<M> single(@NotBlank String name) {
        return single(name, null);
    }

    default <M extends Number> List<SingleValueMetricsField<M>> single(@NotNull List<String> names) {
        return single(names, null);
    }

    default <M extends Number> List<SingleValueMetricsField<M>> single(@NotNull List<String> names, @Null WindMetricsAggregationQuery query) {
        List<SingleValueMetricsField<M>> result = new ArrayList<>();
        names.forEach(name -> result.add(single(name, query)));
        return result;
    }

    /**
     * 单个值指标
     *
     * @param name  指标名称，全局唯一
     * @param query 查询条件，允许为空
     * @param <M>   指标值类型
     * @return 指标字段
     */
    @NotNull
    <M extends Number> SingleValueMetricsField<M> single(@NotBlank String name, @Null WindMetricsAggregationQuery query);

    /**
     * 多个值指标
     *
     * @param name 标名称，全局唯一
     * @param <M>  指标值类型
     * @return 指标字段
     */
    default <M extends Number> MultipleValueMetricsField<M> multiple(@NotBlank String name) {
        return multiple(name, null);
    }

    /**
     * 多个值指标
     *
     * @param name  标名称，全局唯一
     * @param query 查询条件，允许为空
     * @param <M>   指标值类型
     * @return 指标字段
     */
    <M> MultipleValueMetricsField<M> multiple(@NotBlank String name, @Null WindMetricsAggregationQuery query);
}
