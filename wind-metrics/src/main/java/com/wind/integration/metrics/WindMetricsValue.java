package com.wind.integration.metrics;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * 面向开发者的具名指标值能力，与定义方式、计算引擎和取数策略无关。
 *
 * <p>值可以由代码、SQL 或 DSL 实现提供。调用方无须区分实时、快照或分段查询；
 * 实现负责选择正确的数据来源并传播失败，不得将执行失败伪装成正常空值。
 * 本接口只提供读取能力，不承诺每次读取是否重新计算；固定查询结果可使用 {@link #of}。
 * 多字段结果可进一步实现 {@link WindStructuredMetricsValue}。</p>
 *
 * @param <V> 值类型
 * @author wuxp
 * @date 2025-06-17 14:27
 **/
@Schema(description = "指标编码和值的描述对象")
public interface WindMetricsValue<V> {

    /**
     * 获取指标名称
     */
    @NonNull
    @Deprecated(forRemoval = true)
    String getName();

    /**
     * @return 指标编码
     */
    @NonNull
    default String getCode() {
        return getName();
    }

    /**
     * @return 指标值；定义允许的正常空结果可以为空
     */
    @Nullable
    V getValue();

    /**
     * 将已计算的指标编码和值保存为只读值对象，不触发计算或存储。
     *
     * @param code  非空白编码
     * @param value 已计算的值，可以为空；可变值的所有权由调用方管理
     * @param <V>   值类型
     * @return 编码与值引用固定的指标值
     */
    static <V> WindMetricsValue<V> of(String code, @Nullable V value) {
        return new ImmutableMetricsValue<>(code, value);
    }
}
