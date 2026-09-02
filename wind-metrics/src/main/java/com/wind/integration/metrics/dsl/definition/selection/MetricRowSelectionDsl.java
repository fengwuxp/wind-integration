package com.wind.integration.metrics.dsl.definition.selection;

import com.wind.integration.metrics.dsl.filter.MetricFilterDsl;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * 所有 measure 共享的聚合前有限行集定义。
 *
 * @param filter 行选择前应用的事实过滤条件
 * @param orderBy 稳定排序字段
 * @param limit 固定数量或查询参数引用
 *
 * @author wuxp
 * @date 2026-07-23 11:10
 */
@Schema(description = "所有 measure 共享的聚合前有限行集定义")
public record MetricRowSelectionDsl(
        @Nullable @Schema(description = "行选择前应用的事实过滤条件") MetricFilterDsl filter,
        @Schema(description = "稳定排序字段") List<MetricOrderByDsl> orderBy,
        @Schema(description = "固定数量或查询参数引用") MetricLimitDsl limit) {

    public MetricRowSelectionDsl {
        orderBy = List.copyOf(orderBy);
        Objects.requireNonNull(limit, "limit must not be null");
    }
}
