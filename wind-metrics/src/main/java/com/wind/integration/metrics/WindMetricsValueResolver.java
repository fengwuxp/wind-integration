package com.wind.integration.metrics;

import com.wind.integration.metrics.query.MetricQuery;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;

/**
 * 按主体取得已注册业务视图的指标对象，由业务实现确定指标集合与字段映射。
 *
 * <p>一个视图绑定固定的主体类型和维度结构；调用方可以查询该维度下的不同主体，
 * 不能用相同视图任意切换主体维度。实现可以组合指标查询与完整宽表读取，
 * 快照实际覆盖、原始量合并和最终计算仍遵循各自合同，不按所有数字属性直接求和。</p>
 *
 * <p>历史单主体和集合入口保留。集合表示业务实现定义的合计范围，不保证支持所有视图，
 * 也不表示按主体返回结果列表。supports 只用于视图类型选择，不保证各重载都已实现。</p>
 *
 * @param <V> 历史入口返回的业务指标对象类型
 * @author wuxp
 * @date 2025-06-24 13:19
 **/
public interface WindMetricsValueResolver<V> {

    /**
     * 按完整条件解析一个业务指标对象，不丢弃主体、时间、独立维度或参数。
     *
     * <p>query 与 resultType 非空，subjectType 必须明确；实现校验主体类型和维度结构
     * 与所选业务视图一致。resultType 表示已注册的业务视图，不用于任意选择物理表。
     * 对完整宽表可直接读取整条记录，对需实时补充的字段按明确口径取值；
     * 读取最新分段不等于取得全部历史累计，正常 NULL 字段不触发隐式补查。</p>
     *
     * <p>旧实现默认明确拒绝本能力，不转换成旧 ID 入口，以免丢失查询条件。
     * 本接口不改变历史单项/集合语义，不自动引入缓存、事务或快照写回。</p>
     *
     * @param query 非空主体条件，必须明确主体类型；其他字段按视图合同校验
     * @param resultType 已注册业务视图类型
     * @param <T> 本次返回的业务指标对象类型
     * @return 非空业务指标对象，其字段可以包含定义允许的正常 NULL
     * @throws UnsupportedOperationException 当前实现未支持完整条件入口
     * @throws RuntimeException 视图或条件不支持，或读取、计算、装配失败；异常不得转为空对象
     */
    @NotNull
    default <T> T resolve(@NotNull MetricQuery query, @NotNull Class<T> resultType) {
        throw new UnsupportedOperationException("MetricQuery business-view resolution is not supported");
    }

    /**
     * 将同一主体维度的一组 ID 解析为一个业务指标对象，具体视图可明确拒绝集合调用。
     *
     * @param dimensionsIds    业务维度标识集合（如用户 ID）
     * @param metricsValueType 指标值类型
     * @return 指标值
     */
    V resolve(@NotNull Collection<? extends Serializable> dimensionsIds, Class<?> metricsValueType);

    /**
     * @param dimensionsId     业务维度标识（如用户 ID）
     * @param metricsValueType 指标值类型
     * @return 指标值
     */
    @NotNull
    default V resolve(@NotNull Serializable dimensionsId, @NotNull Class<?> metricsValueType) {
        return resolve(Collections.singletonList(dimensionsId), metricsValueType);
    }

    /**
     * 指标值查询条件支持
     *
     * @param metricsValueType 指标值类型
     */
    boolean supports(@NotNull Class<V> metricsValueType);

}
