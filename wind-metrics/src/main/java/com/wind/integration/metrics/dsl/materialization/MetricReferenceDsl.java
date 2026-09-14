package com.wind.integration.metrics.dsl.materialization;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Objects;

/**
 * 物化计划关联的独立指标声明，不包含发布生成的依赖或原始度量。
 *
 * @param metricCode 指标编码，在同一计划内唯一
 * @param definitionRevision 必填的正整数定义修订；草稿显式选择，发布校验并冻结该版本，运行时不追随最新版本
 *
 * @author wuxp
 * @date 2026-09-14 17:00
 */
@Schema(description = "物化计划关联的独立指标声明")
public record MetricReferenceDsl(
        @Schema(description = "指标编码，在同一计划内唯一") String metricCode,
        @Schema(description = "必填正整数定义修订；草稿显式选择，发布不替换为最新版本")
        Integer definitionRevision) {

    public MetricReferenceDsl {
        Objects.requireNonNull(metricCode, "metricCode must not be null");
        Objects.requireNonNull(definitionRevision, "definitionRevision must not be null");
    }
}
