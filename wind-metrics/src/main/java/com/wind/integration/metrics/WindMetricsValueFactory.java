package com.wind.integration.metrics;

import com.wind.integration.metrics.query.MetricQuery;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * 按指标编码和查询条件创建只读取值能力的工厂。
 *
 * <p>工厂创建读取对象，不提前调用取值方法；实际读取是否重新计算、查询或使用固定结果，
 * 由具体指标实现决定。调用方无需依赖历史 Field 接口，也不能据此假定结果已缓存。
 * 工厂不提供重新求值、修改数值或多个指标对象组装；精确修订通过带版本的重载显式传入，
 * 不放入查询参数，也不由工厂擅自替换为当前修订。</p>
 *
 * <p>单值入口沿用数值指标合同，多字段入口的整体值可以是 Map 或业务对象。
 * 泛型类型须与指标声明的实际值类型一致，本工厂不进行类型转换。
 * 不支持的指标或条件必须明确失败，不得丢弃条件后执行，也不得将取值失败伪装为正常空值。</p>
 *
 * @author wuxp
 * @since 2026-09-15
 */
public interface WindMetricsValueFactory {

    /**
     * 创建一个具名数值指标的读取能力。
     *
     * @param metricsCode 指标编码
     * @param query       查询条件；null 使用实现的默认条件
     * @param <N>         指标声明的数值类型
     * @return 非空读取对象；getValue 可以返回定义认可的正常 null，错误继续传播
     */
    @NotNull
    <N extends Number> WindMetricsValue<N> value(@NonNull String metricsCode, @Nullable MetricQuery query);

    /**
     * 创建指定定义版本的数值指标读取能力。
     *
     * <p>版本为空时委托原入口，保留其默认版本与查询语义。非空版本必须由实现明确承接；
     * 默认实现直接拒绝，不能忽略版本后读取当前定义。查询或读取失败继续传播。</p>
     *
     * @param metricsCode        指标编码
     * @param definitionRevision 精确定义版本；null 沿用原入口
     * @param query              非空查询条件
     * @param <N>                指标声明的数值类型
     * @return 对应定义版本的非空读取对象
     * @throws UnsupportedOperationException 非空版本未被具体实现支持
     */
    @NotNull
    default <N extends Number> WindMetricsValue<N> value(@NonNull String metricsCode, @Nullable Integer definitionRevision, @NonNull MetricQuery query) {
        if (definitionRevision == null) {
            return value(metricsCode, query);
        }
        throw new UnsupportedOperationException("Exact metric definition revisions are not supported");
    }

    /**
     * 创建一个指标的多个具名输出字段的读取能力。
     *
     * <p>字段名仅在所属指标内唯一，不代表多个独立指标。业务对象到字段 Map 的转换由
     * 具体实现承担；本工厂不依赖 JSON 转换或物理列映射。</p>
     *
     * @param metricsCode 所属指标编码
     * @param query       非空查询条件
     * @param <V>         整个指标的值类型，例如 Map 或业务对象
     * @return 非空多字段读取对象；字段存在但为空与字段不存在保持可区分
     */
    @NotNull
    <V> WindStructuredMetricsValue<V> fields(@NonNull String metricsCode, @NonNull MetricQuery query);

    /**
     * 创建同一个指标在指定定义版本下的多字段读取能力。
     *
     * <p>所有字段属于同一指标版本，不能拼接不同修订的字段。版本为空时委托原入口；
     * 非空版本未被实现支持时直接拒绝，不能降级到当前版本。</p>
     *
     * @param metricsCode        所属指标编码
     * @param definitionRevision 精确定义版本；null 沿用原入口
     * @param query              非空查询条件
     * @param <V>                整个指标的值类型，例如 Map 或业务对象
     * @return 对应定义版本的非空多字段读取对象
     * @throws UnsupportedOperationException 非空版本未被具体实现支持
     */
    @NotNull
    default <V> WindStructuredMetricsValue<V> fields(@NonNull String metricsCode, @Nullable Integer definitionRevision, @NonNull MetricQuery query) {
        if (definitionRevision == null) {
            return fields(metricsCode, query);
        }
        throw new UnsupportedOperationException("Exact metric definition revisions are not supported");
    }
}
