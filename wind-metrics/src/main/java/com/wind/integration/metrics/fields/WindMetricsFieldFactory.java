package com.wind.integration.metrics.fields;

import com.wind.integration.metrics.WindMetricsAggregationQuery;
import com.wind.integration.metrics.WindMetricsValueFactory;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;

import java.util.ArrayList;
import java.util.List;

/**
 * 指标字段的历史组合工厂。
 *
 * <p>本接口不继承 {@link WindMetricsValueFactory}，也不提供 value/fieldValues 只读入口。
 * 旧字段入口供尚未迁移或需要重新求值的调用方使用；工厂本身不执行查询。
 * 新实现可以直接实现 WindMetricsValueFactory，无需提供历史 Field 方法。</p>
 *
 * @author wuxp
 * @date 2025-06-17 14:22
 * @deprecated 只读取值使用 {@link WindMetricsValueFactory}；旧字段组合能力保留至消费者完成迁移
 **/
@Deprecated(since = "2026-09-15", forRemoval = false)
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
    @Deprecated
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
    @Deprecated
    <M> MultipleValueMetricsField<M> multiple(@NotBlank String name, @Null WindMetricsAggregationQuery query);
}
