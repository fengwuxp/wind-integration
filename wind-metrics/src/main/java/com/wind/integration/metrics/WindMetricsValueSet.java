package com.wind.integration.metrics;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 一个指标的多个具名字段值，支持整体取值和按字段读取。
 *
 * <p>字段名称只在所属指标内唯一。整体值可以是 Map 或业务对象，
 * 不依赖 DSL、查询模式、物理存储或修改能力。</p>
 *
 * @param <M> 整体值类型
 * @author wuxp
 * @since 2026-09-15
 */
public interface WindMetricsValueSet<M> extends WindMetricsValue<M> {

    /**
     * @return 所属指标的具名字段，不包含空对象；字段本身的值可以为空
     */
    List<WindMetricsValue<Object>> getMetricsFields();

    /**
     * @return 以字段名定位的值，不依赖字段列表顺序
     */
    @NotNull
    Map<String, Object> asValues();

    /**
     * 按所属指标内的字段名查找值对象，区分字段不存在与字段值为空。
     *
     * @param name 字段名称
     * @param <V> 调用方依据指标合同选择的字段值类型，不进行数值转换
     * @return 字段存在时返回值对象，否则为空
     */
    @SuppressWarnings("unchecked")
    default <V> Optional<WindMetricsValue<V>> findByName(String name) {
        return getMetricsFields().stream()
                .filter(value -> value.getName().equals(name))
                .findFirst()
                .map(value -> (WindMetricsValue<V>) value);
    }

    /**
     * 保存已计算的多个字段，复制并冻结字段容器，保留顺序和正常空值。
     *
     * @param name 非空白指标名称
     * @param values 非空容器；字段名非空白，可变字段值由调用方管理
     * @return 不负责计算或写回的多字段值
     */
    static WindMetricsValueSet<Map<String, Object>> of(String name, Map<String, ?> values) {
        return new ImmutableMetricsValueSet(name, values);
    }
}
