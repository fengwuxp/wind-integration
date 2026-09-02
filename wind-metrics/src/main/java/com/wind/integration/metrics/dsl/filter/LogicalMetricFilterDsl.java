package com.wind.integration.metrics.dsl.filter;

import com.wind.integration.metrics.enums.MetricFilterOperator;
import io.swagger.v3.oas.annotations.media.Schema;

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
@Schema(description = "使用 AND 或 OR 组合的过滤条件")
public record LogicalMetricFilterDsl(
        @Schema(description = "逻辑操作符") MetricFilterOperator operator,
        @Schema(description = "子过滤条件列表，至少包含两个元素") List<MetricFilterDsl> operands) implements MetricFilterDsl {

    public LogicalMetricFilterDsl {
        Objects.requireNonNull(operator, "operator must not be null");
        operands = List.copyOf(operands);
    }
}
