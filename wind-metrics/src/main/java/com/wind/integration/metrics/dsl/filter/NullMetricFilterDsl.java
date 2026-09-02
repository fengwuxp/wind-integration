package com.wind.integration.metrics.dsl.filter;

import com.wind.integration.metrics.enums.MetricFilterOperator;
import io.swagger.v3.oas.annotations.media.Schema;

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
@Schema(description = "单字段的空值判断过滤条件")
public record NullMetricFilterDsl(
        @Schema(description = "IS_NULL 或 IS_NOT_NULL 操作符") MetricFilterOperator operator,
        @Schema(description = "事实字段引用") String fieldRef) implements MetricFilterDsl {

    public NullMetricFilterDsl {
        Objects.requireNonNull(operator, "operator must not be null");
        Objects.requireNonNull(fieldRef, "fieldRef must not be null");
        if (operator != MetricFilterOperator.IS_NULL && operator != MetricFilterOperator.IS_NOT_NULL) {
            throw new IllegalArgumentException("Unsupported null operator: " + operator);
        }
    }
}
