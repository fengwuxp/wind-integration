package com.wind.integration.metrics;

import com.wind.integration.metrics.query.MetricQuery;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * 依据具体指标定义，在其固定的主体维度和维度结构内执行一次求值。
 *
 * <p>指标定义和实际计算能力由实现绑定；主体类型与维度结构由该定义确定，不能通过查询条件切换。
 * MetricQuery.subjectId 可以指定同一主体类型下的不同主体，例如同一用户指标中的用户 A 和用户 B，
 * 但不能将用户指标改为商户指标。实现或受委托查询服务须按定义校验条件，不能将首次结果跨主体复用。
 * 每次调用 evaluate 都执行实现提供的求值逻辑。
 * 实现可以委托指标查询服务，也可以执行本地计算；本接口不规定数据来源、事务或读取视图。
 * 完整条件直接传给实现，不转换为历史查询，不丢弃独立维度或业务参数。</p>
 *
 * <p>可与 {@link WindMetricsValue} 组合：其 getValue 调用本接口时，求值延迟到读取发生；
 * 每次读取都调用时，可重新取得计算结果。只求值一次或缓存结果由调用方显式安排，
 * 本接口不保存上次结果、不自动推进查询时间、不调度刷新，也不累计或写回快照。</p>
 *
 * @param <V> 本次求值的结果类型；正常空值由具体指标合同决定
 * @author wuxp
 * @since 2026-09-23
 */
@FunctionalInterface
public interface WindMetricsValueEvaluator<V> {

    /**
     * 按给定条件执行一次求值，失败直接传播。
     *
     * @param query 非空且符合绑定定义的查询条件；主体类型和维度结构不可改变，subjectId 指定该维度下的主体
     * @return 本次结果，允许指标定义认可的正常 null；异常不能转换成零或空值
     */
    @Nullable
    V evaluate(@NonNull MetricQuery query);
}
