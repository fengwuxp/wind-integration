package com.wind.integration.metrics.dsl.filter;

import com.wind.integration.metrics.enums.MetricFilterOperator;

import java.util.List;
import java.util.Objects;

/**
 * 使用 {@code AND} 或 {@code OR} 组合的过滤条件。
 *
 * @param operator 逻辑操作符
 * @param operands 至少两个子过滤条件
 *
 * @author wuxp
 * @date 2026-07-21 17:51
 */
public record LogicalMetricFilterDsl(MetricFilterOperator operator,
                                     List<MetricFilterDsl> operands) implements MetricFilterDsl {

    public LogicalMetricFilterDsl {
        Objects.requireNonNull(operator, "operator must not be null");
        operands = List.copyOf(operands);
    }
}
