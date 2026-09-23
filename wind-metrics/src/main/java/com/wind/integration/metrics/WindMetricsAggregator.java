package com.wind.integration.metrics;

import com.wind.integration.metrics.query.MetricQuery;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * 聚合指标构建器，用于将一个或多个指标名称映射到目标对象字段，最终构建一个指标对象实例。
 *
 * <p>使用 {@link #named(String, String)} 维护指标名称与对象字段之间的映射关系，
 * 使用 {@link #aggregate(MetricQuery)} 取得指标值并组装最终对象。这里的聚合是多个指标到对象的组装，
 * 不限定 SUM、COUNT 等计算算法，也不限定实时、快照或分段取数。</p>
 *
 * @author wuxp
 * @date 2025-06-24 09:28
 **/
public interface WindMetricsAggregator<T> {

    /**
     * 指标对象名称和字段关系
     *
     * @param filedName  指标对象字段名称
     * @param metricCode 指标编码
     * @return this
     */
    WindMetricsAggregator<T> named(@NonNull String filedName, @NonNull String metricCode);

    /**
     * 为目标字段映射显式选择指标的精确定义版本。
     *
     * <p>由 {@link WindMetricsAggregatorFactory#factory(Class)} 创建的标量聚合器，版本归属每项
     * 指标映射，不同指标可以使用不同版本；不能用 aggregate 的全局版本覆盖所有映射。</p>
     * <p>由 {@link WindMetricsAggregatorFactory#factory(String, Class)} 创建的 FIELD_SET 聚合器，
     * metricCode 参数表示所属指标内的字段名，definitionRevision 属于工厂已经指定的那个指标。
     * 实现必须保证所有字段来自同一版本；指定的精确版本适用于整个 FIELD_SET，不能混入 current 字段。</p>
     * <p>版本为空时委托原映射入口；非空版本未被具体实现支持时直接拒绝，不能丢弃版本或修改旧映射。</p>
     *
     * @param fieldName          目标对象字段名称
     * @param metricCode         标量指标编码；FIELD_SET 场景为所属指标的输出字段名
     * @param definitionRevision 精确定义版本；null 沿用原入口
     * @return 当前聚合器
     * @throws UnsupportedOperationException 非空版本未被具体实现支持
     */
    default WindMetricsAggregator<T> named(@NonNull String fieldName, @NonNull String metricCode, @Nullable Integer definitionRevision) {
        if (definitionRevision == null) {
            return named(fieldName, metricCode);
        }
        throw new UnsupportedOperationException("Exact metric definition revisions are not supported");
    }

    /**
     * 按通用条件取值并组装目标对象，沿用 named 的字段映射。
     *
     * <p>新实现直接消费完整条件；默认实现适配旧聚合器，不丢弃独立维度。
     * 物化实现可按已确定的增量条件委托公开查询服务读取原始量，再合并旧快照；
     * 不能以最终表达式值替代原始状态，也不因对象组装推进已提交水位。</p>
     *
     * @param query 非空聚合条件
     * @return 组装的指标对象
     */
    @NotNull
    default T aggregate(@NonNull MetricQuery query) {
        return aggregate(WindMetricsAggregationQuery.fromQuery(query));
    }

    /**
     * @param query 查询条件
     * @return 获取聚合的指标对象
     * @deprecated 新调用使用 {@link #aggregate(MetricQuery)}
     */
    @NotNull
    @Deprecated
    T aggregate(WindMetricsAggregationQuery query);
}
