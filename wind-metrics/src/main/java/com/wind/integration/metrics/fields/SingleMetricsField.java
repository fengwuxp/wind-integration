package com.wind.integration.metrics.fields;

import com.wind.integration.metrics.WindMetricsObject;

/**
 * 单个值的指标字段，支持{@link #increase}、{@link #decrease}、{@link #setValue}
 *
 * @author wuxp
 * @date 2025-06-17 14:14
 **/
public interface SingleMetricsField<M extends Number> extends WindMetricsObject<M> {

    void setValue(M value);

    /**
     * 递增
     *
     * @param value 增加的值
     * @return 增加后的最新值
     */
    M increase(M value);

    /**
     * 递减
     *
     * @param value 减少的值
     * @return 减少后的最新值
     */
    M decrease(M value);
}
