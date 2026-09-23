package com.wind.integration.metrics.expression;

import org.jspecify.annotations.NullMarked;

/**
 * 表达式中对另一个指标结果字段的确定性引用。
 *
 * <p>用于编译引用集合和本次求值的依赖值键。定义修订由宿主按所属定义的 dependencies
 * 固定；本对象不携带 revision，不能单独作为跨版本查询缓存键。</p>
 *
 * @param metricCode 被引用指标编码
 * @param valueField 被引用结果字段
 * @author wuxp
 * @since 2026-07-24
 */
@NullMarked
public record MetricValueReference(String metricCode, String valueField) implements Comparable<MetricValueReference> {

    @Override
    public int compareTo(MetricValueReference other) {
        int metricCodeComparison = metricCode.compareTo(other.metricCode);
        return metricCodeComparison != 0 ? metricCodeComparison : valueField.compareTo(other.valueField);
    }
}
