package com.wind.integration.metrics.dsl.filter;

import com.wind.integration.metrics.enums.MetricFilterOperator;

import java.util.Objects;

/**
 * 单字段的空值判断过滤条件。
 *
 * @param operator {@code IS_NULL} 或 {@code IS_NOT_NULL}
 * @param fieldRef 事实字段引用，可包含一个关联事实别名前缀
 *
 * @author wuxp
 * @date 2026-07-21 17:51
 */
public record NullMetricFilterDsl(MetricFilterOperator operator, String fieldRef) implements MetricFilterDsl {

    public NullMetricFilterDsl {
        Objects.requireNonNull(operator, "operator must not be null");
        Objects.requireNonNull(fieldRef, "fieldRef must not be null");
    }
}
