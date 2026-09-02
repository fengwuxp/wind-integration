package com.wind.integration.metrics.dsl.definition;

import com.wind.integration.metrics.dsl.MetricDefinitionDslJsonBinding;
import io.swagger.v3.oas.annotations.media.Schema;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;

import java.util.Objects;

/**
 * 指标定义 DSL 的根对象。
 *
 * @param schemaVersion DSL 结构版本，当前只支持 {@code 1}
 * @param metric 指标计算定义
 *
 * @author wuxp
 * @date 2026-07-21 17:51
 */
@JsonDeserialize(using = MetricDefinitionDslJsonBinding.Deserializer.class)
@JsonSerialize(using = MetricDefinitionDslJsonBinding.Serializer.class)
@Schema(description = "指标定义 DSL 的根对象")
public record MetricDefinitionDsl(
        @Schema(description = "DSL 结构版本，当前只支持 1") Integer schemaVersion,
        @Schema(description = "指标计算定义") MetricDefinitionSpec metric) {

    public MetricDefinitionDsl {
        Objects.requireNonNull(schemaVersion, "schemaVersion must not be null");
        Objects.requireNonNull(metric, "metric must not be null");
    }
}
