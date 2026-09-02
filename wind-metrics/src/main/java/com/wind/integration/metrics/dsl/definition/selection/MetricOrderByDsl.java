package com.wind.integration.metrics.dsl.definition.selection;

import com.wind.integration.metrics.enums.MetricSortDirection;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Objects;

/**
 * 行选择的单个排序字段。
 *
 * @param field 主事实源逻辑字段名
 * @param direction 排序方向
 *
 * @author wuxp
 * @date 2026-07-23 11:10
 */
@Schema(description = "行选择的单个排序字段")
public record MetricOrderByDsl(
        @Schema(description = "主事实源逻辑字段名") String field,
        @Schema(description = "排序方向") MetricSortDirection direction) {

    public MetricOrderByDsl {
        Objects.requireNonNull(field, "field must not be null");
        Objects.requireNonNull(direction, "direction must not be null");
    }
}
