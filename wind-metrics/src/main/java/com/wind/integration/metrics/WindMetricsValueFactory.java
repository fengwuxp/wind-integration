package com.wind.integration.metrics;

import com.wind.integration.metrics.query.MetricQuery;
import org.jspecify.annotations.Nullable;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 按指标编码和查询条件创建只读取值能力的工厂。
 *
 * <p>工厂创建读取对象，不提前调用取值方法；实际读取是否重新计算、查询或使用固定结果，
 * 由具体指标实现决定。调用方无需依赖历史 Field 接口，也不能据此假定结果已缓存。
 * 工厂不提供重新求值、修改数值、多个指标对象组装或定义修订选择。</p>
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
     * @param code 指标编码
     * @param query 查询条件；null 使用实现的默认条件
     * @param <N> 指标声明的数值类型
     * @return 非空读取对象；getValue 可以返回定义认可的正常 null，错误继续传播
     */
    @NotNull
    <N extends Number> WindMetricsValue<N> value(@NotBlank String code, @Nullable MetricQuery query);

    /**
     * 创建一个指标的多个具名输出字段的读取能力。
     *
     * <p>字段名仅在所属指标内唯一，不代表多个独立指标。业务对象到字段 Map 的转换由
     * 具体实现承担；本工厂不依赖 JSON 转换或物理列映射。</p>
     *
     * @param code 所属指标编码
     * @param query 查询条件；null 使用实现的默认条件
     * @param <V> 整个指标的值类型，例如 Map 或业务对象
     * @return 非空多字段读取对象；字段存在但为空与字段不存在保持可区分
     */
    @NotNull
    <V> WindStructuredMetricsValue<V> fields(@NotBlank String code, @Nullable MetricQuery query);
}
