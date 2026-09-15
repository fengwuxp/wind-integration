package com.wind.integration.metrics;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import org.jspecify.annotations.Nullable;

/**
 * 面向开发者的具名指标值能力，与定义方式、计算引擎和取数策略无关。
 *
 * <p>值可以由代码、SQL 或 DSL 实现提供。调用方无须区分实时、快照或分段查询；
 * 实现负责选择正确的数据来源并传播失败，不得将执行失败伪装成正常空值。
 * 本接口只提供读取能力，不承诺每次读取是否重新计算；固定查询结果可使用 {@link #of}。
 * 多字段结果可进一步实现 {@link WindMetricsValueSet}。</p>
 *
 * @param <V> 值类型
 * @author wuxp
 * @date 2025-06-17 14:27
 **/
@Schema(description = "指标名称和值的描述对象")
public interface WindMetricsValue<V> {

    /**
     * @return 顶层指标名称，或所属多字段指标内的字段名称；字段名不要求跨指标唯一
     */
    @NotBlank
    String getName();

    /**
     * @return 指标值；定义允许的正常空结果可以为空
     */
    @Nullable
    V getValue();

    /**
     * 将已计算的名称和值保存为只读值对象，不触发计算或存储。
     *
     * @param name 非空白名称
     * @param value 已计算的值，可以为空；可变值的所有权由调用方管理
     * @param <V> 值类型
     * @return 名称与值引用固定的指标值
     */
    static <V> WindMetricsValue<V> of(String name, @Nullable V value) {
        return new ImmutableMetricsValue<>(name, value);
    }
}
