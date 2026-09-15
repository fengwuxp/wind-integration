package com.wind.integration.metrics.fields;

import com.wind.integration.metrics.WindMetricsEvaluator;
import com.wind.integration.metrics.WindMetricsValue;

/**
 * 同时支持条件求值和数值修改的单值指标字段。
 *
 * <p>只读单值结果使用 {@link WindMetricsValue}，不必实现本接口。
 * 本接口仅用于确实提供 {@link #increase}、{@link #decrease}、{@link #setValue} 的实现，
 * 这些修改不自动具有完整桶提交、幂等写回或快照水位推进语义。</p>
 *
 * @author wuxp
 * @date 2025-06-17 14:14
 **/
public interface SingleValueMetricsField<M extends Number> extends WindMetricsValue<M>, WindMetricsEvaluator<M> {

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
