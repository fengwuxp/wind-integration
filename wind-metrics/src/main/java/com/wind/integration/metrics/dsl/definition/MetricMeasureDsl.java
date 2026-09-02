package com.wind.integration.metrics.dsl.definition;

import com.wind.integration.metrics.dsl.filter.MetricFilterDsl;
import com.wind.integration.metrics.enums.MetricAggregation;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * 从事实源聚合得到的基础度量。
 *
 * @param aggregation 聚合函数
 * @param field 被聚合字段；{@code COUNT} 时为空，其他聚合函数必填
 * @param filter 候选事实关系确定后，仅作用于当前 measure 的过滤条件
 *
 * @author wuxp
 * @date 2026-07-21 17:51
 */
@Schema(description = "从事实源聚合得到的基础度量")
public record MetricMeasureDsl(
        @Schema(description = "聚合函数") MetricAggregation aggregation,
        @Nullable @Schema(description = "被聚合字段；COUNT 时为空") String field,
        @Nullable @Schema(description = "当前 measure 的过滤条件") MetricFilterDsl filter) {

    public MetricMeasureDsl {
        Objects.requireNonNull(aggregation, "aggregation must not be null");
    }
}
