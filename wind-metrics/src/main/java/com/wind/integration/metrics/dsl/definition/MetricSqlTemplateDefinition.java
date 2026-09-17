package com.wind.integration.metrics.dsl.definition;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Objects;

/**
 * SQL 模板指标定义的根对象（内部使用）
 *
 * <p>与 {@link MetricDefinitionDsl} 平行，用于 SQL 模板指标的序列化/反序列化。</p>
 *
 * @param schemaVersion DSL 结构版本，当前只支持 {@code 1}
 * @param metric SQL 模板指标定义
 *
 * @author wuxp
 * @date 2026-09-17
 */
@Schema(description = "SQL 模板指标定义的根对象")
public record MetricSqlTemplateDefinition(
        @Schema(description = "DSL 结构版本，当前只支持 1") Integer schemaVersion,
        @Schema(description = "SQL 模板指标定义") MetricSqlTemplateSpec metric) {

    public MetricSqlTemplateDefinition {
        Objects.requireNonNull(schemaVersion, "schemaVersion must not be null");
        Objects.requireNonNull(metric, "metric must not be null");
    }
}
