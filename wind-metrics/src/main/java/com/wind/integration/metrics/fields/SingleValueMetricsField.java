package com.wind.integration.metrics.fields;

import com.wind.integration.metrics.WindMetricsValue;

/**
 * 单个值的指标字段，支持{@link #increase}、{@link #decrease}、{@link #setValue}
 *
 * @author wuxp
 * @date 2025-06-17 14:14
 **/
public interface SingleValueMetricsField<M extends Number> extends WindMetricsValue<M> {

    /**
     * 设置指标值
     *
     * @param value 指标值
     */
    void setValue(M value);

    /**
     * 递增（实现请保证线程安全）
     *
     * @param value 增加的值
     * @return 增加后的最新值
     */
    M increase(M value);

    /**
     * 递减（实现请保证线程安全）
     *
     * @param value 减少的值
     * @return 减少后的最新值
     */
    M decrease(M value);
}
