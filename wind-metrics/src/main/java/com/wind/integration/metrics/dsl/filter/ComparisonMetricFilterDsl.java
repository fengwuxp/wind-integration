package com.wind.integration.metrics.dsl.filter;

import com.wind.integration.metrics.enums.MetricFilterOperator;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Objects;

/**
 * 单字段与单个字面量的比较过滤条件。
 *
 * @param operator 等值或大小比较操作符
 * @param fieldRef 事实字段引用，可包含一个关联事实别名前缀
 * @param value 比较值
 *
 * @author wuxp
 * @date 2026-07-21 17:51
 */
@Schema(description = "单字段与单个字面量的比较过滤条件")
public record ComparisonMetricFilterDsl(
        @Schema(description = "等值或大小比较操作符") MetricFilterOperator operator,
        @Schema(description = "事实字段引用") String fieldRef,
        @Schema(description = "比较值") MetricLiteralDsl value) implements MetricFilterDsl {

    public ComparisonMetricFilterDsl {
        Objects.requireNonNull(operator, "operator must not be null");
        Objects.requireNonNull(fieldRef, "fieldRef must not be null");
        Objects.requireNonNull(value, "value must not be null");
    }
}
