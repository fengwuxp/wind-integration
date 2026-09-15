package com.wind.integration.metrics.materialization;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Objects;

/**
 * 服务端从已发布定义展开并冻结的叶子物化依赖。
 *
 * @param metricCode 叶子指标编码
 * @param definitionRevision 叶子指标定义修订号
 * @param measures 需要物化的值字段及合并状态
 *
 * @author wuxp
 * @since 2026-07-28
 */
@Schema(description = "服务端展开并冻结的叶子物化依赖")
public record MetricMaterializationDependency(
        @Schema(description = "叶子指标编码") String metricCode,
        @Schema(description = "叶子指标定义修订号") Integer definitionRevision,
        @Schema(description = "需要物化的值字段及合并状态") List<MetricMaterializationMeasure> measures) {

    public MetricMaterializationDependency {
        Objects.requireNonNull(metricCode, "metricCode must not be null");
        Objects.requireNonNull(definitionRevision, "definitionRevision must not be null");
        measures = List.copyOf(measures);
    }
}
