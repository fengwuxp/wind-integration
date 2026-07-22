package com.wind.integration.metrics.dsl.definition;

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
public record MetricDefinitionDsl(Integer schemaVersion, MetricDefinitionSpec metric) {

    public MetricDefinitionDsl {
        Objects.requireNonNull(schemaVersion, "schemaVersion must not be null");
        Objects.requireNonNull(metric, "metric must not be null");
    }
}
