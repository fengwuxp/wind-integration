package com.wind.integration.metrics.dsl.filter;

import com.wind.integration.metrics.enums.MetricFilterOperator;

import java.util.List;
import java.util.Objects;

/**
 * 单字段与一组字面量的集合比较过滤条件。
 *
 * @param operator {@code IN} 或 {@code NOT_IN}
 * @param fieldRef 事实字段引用，可包含一个关联事实别名前缀
 * @param values 非空且值唯一的比较集合
 *
 * @author wuxp
 * @date 2026-07-21 17:51
 */
public record SetMetricFilterDsl(MetricFilterOperator operator,
                                 String fieldRef,
                                 List<MetricLiteralDsl> values) implements MetricFilterDsl {

    public SetMetricFilterDsl {
        Objects.requireNonNull(operator, "operator must not be null");
        Objects.requireNonNull(fieldRef, "fieldRef must not be null");
        values = List.copyOf(values);
    }
}
