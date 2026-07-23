package com.wind.integration.metrics.dsl.definition;

import com.wind.integration.metrics.enums.MetricValueType;

import java.util.Objects;

/**
 * 指标查询参数的取值范围定义。
 *
 * @param valueType 参数类型，首版只支持 {@link MetricValueType#INTEGER}
 * @param minimum 最小值，包含
 * @param maximum 最大值，包含
 *
 * @author wuxp
 * @date 2026-07-23 11:10
 */
public record MetricQueryParameterDefinitionDsl(MetricValueType valueType, int minimum, int maximum) {

    public MetricQueryParameterDefinitionDsl {
        Objects.requireNonNull(valueType, "valueType must not be null");
    }
}
