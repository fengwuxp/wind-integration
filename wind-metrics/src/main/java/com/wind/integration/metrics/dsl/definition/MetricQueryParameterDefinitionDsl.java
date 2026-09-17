package com.wind.integration.metrics.dsl.definition;

import com.wind.integration.metrics.enums.MetricValueType;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * 指标查询参数的取值范围定义。
 *
 * @param valueType 参数类型，首版只支持 {@link MetricValueType#INTEGER}
 * @param minimum 最小值，包含；空值表示无下限
 * @param maximum 最大值，包含；空值表示无上限
 *
 * @author wuxp
 * @date 2026-07-23 11:10
 */
@Schema(description = "指标查询参数的取值范围定义")
public record MetricQueryParameterDefinitionDsl(
        @Schema(description = "参数类型") MetricValueType valueType,
        @Schema(description = "最小值，包含；空值表示无下限") @Nullable Integer minimum,
        @Schema(description = "最大值，包含；空值表示无上限") @Nullable Integer maximum) {

    public MetricQueryParameterDefinitionDsl {
        Objects.requireNonNull(valueType, "valueType must not be null");
    }
}
