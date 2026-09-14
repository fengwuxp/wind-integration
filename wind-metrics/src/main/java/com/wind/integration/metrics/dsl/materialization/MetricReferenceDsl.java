package com.wind.integration.metrics.dsl.materialization;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * 物化计划关联的独立指标声明，不包含发布生成的依赖或原始度量。
 *
 * @param metricCode 指标编码，在同一计划内唯一
 * @param definitionRevision 可选的正整数定义修订；省略时由计划发布解析最新已发布定义并冻结，运行时不追随最新版本
 */
@Schema(description = "物化计划关联的独立指标声明")
public record MetricReferenceDsl(
        @Schema(description = "指标编码，在同一计划内唯一") String metricCode,
        @Nullable @Schema(description = "可选定义修订；省略时由计划发布解析并冻结，规范 JSON 保持省略")
        Integer definitionRevision) {

    public MetricReferenceDsl {
        Objects.requireNonNull(metricCode, "metricCode must not be null");
    }
}
