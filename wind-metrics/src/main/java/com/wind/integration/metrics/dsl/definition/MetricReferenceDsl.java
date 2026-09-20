package com.wind.integration.metrics.dsl.definition;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Objects;

/**
 * 定义依赖和物化计划成员共用的精确指标版本引用。
 *
 * @param metricCode 非空白指标编码，在所属引用列表内唯一
 * @param definitionRevision 必填的正整数定义修订；草稿显式选择，发布校验并冻结该版本，运行时不追随最新版本
 *
 * @author wuxp
 * @date 2026-09-14 17:00
 */
@Schema(description = "精确指标版本引用")
public record MetricReferenceDsl(
        @Schema(description = "非空白指标编码，在所属引用列表内唯一") String metricCode,
        @Schema(description = "必填正整数定义修订；草稿显式选择，发布不替换为最新版本")
        Integer definitionRevision) {

    public MetricReferenceDsl {
        Objects.requireNonNull(metricCode, "metricCode must not be null");
        Objects.requireNonNull(definitionRevision, "definitionRevision must not be null");
        if (metricCode.isBlank()) {
            throw new IllegalArgumentException("metricCode must not be blank");
        }
        if (definitionRevision <= 0) {
            throw new IllegalArgumentException("definitionRevision must be positive");
        }
    }
}
