package com.wind.integration.metrics.spec;

import com.wind.integration.metrics.dsl.definition.MetricQueryParameterDsl;
import com.wind.integration.metrics.enums.MetricValueShape;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 通过 SQL 定义的指标（仅支持实时查询）
 *
 * <p>SQL 模板指标不支持快照物化，适用于复杂查询逻辑或跨数据源场景。</p>
 *
 * @param code        稳定且唯一的指标编码
 * @param valueShape  指标值结构
 * @param subjectType 主体类型
 * @param dimensions  聚合维度列表
 * @param parameters  查询参数定义
 * @param sqlTemplate SQL 模板文本
 * @author wuxp
 * @date 2026-09-16
 */
@Schema(description = "通过 SQL 定义的指标")
public record MetricSqlDefinition(
        @Schema(description = "稳定且唯一的指标编码") String code,
        @Schema(description = "指标值结构") MetricValueShape valueShape,
        @Schema(description = "主体类型") String subjectType,
        @Schema(description = "聚合维度列表") List<String> dimensions,
        @Schema(description = "查询参数定义") Map<String, MetricQueryParameterDsl> parameters,
        @Schema(description = "SQL 模板文本") String sqlTemplate) implements MetricDefinitionObject {

    public MetricSqlDefinition {
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(valueShape, "valueShape must not be null");
        Objects.requireNonNull(subjectType, "subjectType must not be null");
        Objects.requireNonNull(sqlTemplate, "sqlTemplate must not be null");
        dimensions = List.copyOf(dimensions);
        parameters = immutableMap(parameters);
    }

    private static <T> Map<String, T> immutableMap(Map<String, T> source) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }
}
