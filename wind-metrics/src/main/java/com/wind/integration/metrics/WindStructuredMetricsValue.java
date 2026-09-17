package com.wind.integration.metrics;

import jakarta.validation.constraints.NotNull;

import java.util.Map;

/**
 * 一个指标的结构化值及其具名字段读取能力。
 *
 * <p>{@link #getCode()} 返回所属指标编码，{@link #getValue()} 返回整个指标的值，
 * 可以是 Map 或业务对象。{@link #asFieldValues()} 的键是该指标内部的输出字段名，
 * 值是对应字段的实际结果；字段名只在所属指标内唯一，不表示维度键或物理列名。</p>
 *
 * <p>本接口只提供读取能力，不保证每次读取是否重新计算，也不要求任意业务值深度不可变。
 * 固定结果使用 {@link #of}，仅复制并冻结字段容器，不复制字段值对象。</p>
 *
 * @param <M> 整个指标的值类型
 * @author wuxp
 * @since 2026-09-15
 */
public interface WindStructuredMetricsValue<M> extends WindMetricsValue<M> {

    /**
     * 读取所属指标的字段名称与实际值，不将字段包装成另一个指标。
     *
     * <p>字段值可以为空。使用 {@link Map#containsKey(Object)} 区分字段不存在与字段值为空；
     * 字段值类型由指标合同确定，本接口不进行类型转换。</p>
     *
     * @return 非空字段容器；字段名非空白，字段值可以为空
     */
    @NotNull
    Map<String, Object> asFieldValues();

    /**
     * 构建一个指标的固定字段结果，不触发计算或存储。
     *
     * <p>按传入 Map 的迭代顺序复制字段并冻结容器，保留正常空值。
     * 返回对象的 {@code getValue()} 与 {@code asFieldValues()} 返回同一个只读 Map。
     * 字段值保留原对象引用，其可变性由调用方管理。</p>
     *
     * @param code 非空白指标编码
     * @param fieldValues 非空容器；键为非空白输出字段名，值为实际字段值
     * @return 指标编码及字段容器固定的结果
     */
    static WindStructuredMetricsValue<Map<String, Object>> of(String code, Map<String, ?> fieldValues) {
        return new ReadOnlyStructuredMetricsValue(code, fieldValues);
    }
}
