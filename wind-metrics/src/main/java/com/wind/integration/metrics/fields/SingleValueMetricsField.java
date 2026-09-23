package com.wind.integration.metrics.fields;

import com.wind.integration.metrics.WindMetricsEvaluator;
import com.wind.integration.metrics.WindMetricsValue;
import com.wind.integration.metrics.WindMetricsValueEvaluator;

/**
 * 历史单值组合接口，同时声明读取、条件求值和数值修改。
 *
 * <p>读取型消费者使用 {@link WindMetricsValue}，新求值实现使用 {@link WindMetricsValueEvaluator}。
 * 本接口继续继承旧求值签名以兼容已有实现，不要求旧实现改写条件类型。
 * 既有 SQL 实现不提供修改能力；保留本接口不表示所有实现都可修改。
 * 修改方法不自动具有完整桶提交、幂等写回或快照水位推进语义。</p>
 *
 * @author wuxp
 * @date 2025-06-17 14:14
 * @deprecated 按实际能力依赖只读值或求值接口；公共消费者迁移完成前保留历史签名。
 **/
@Deprecated(since = "4.0.0", forRemoval = false)
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
