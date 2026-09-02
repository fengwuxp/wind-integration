package com.wind.integration.metrics.dsl.definition;

import com.wind.integration.metrics.dsl.definition.selection.MetricRowSelectionDsl;
import com.wind.integration.metrics.enums.MetricValueShape;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 单个指标的计算定义。
 *
 * <p>事实指标必须提供 {@code fact} 和 {@code time}；派生指标不提供事实字段，改由表达式取值。
 * {@code SCALAR} 只使用 {@code value}，
 * {@code FIELD_SET} 只使用 {@code fields}。</p>
 *
 * @param code 稳定且唯一的指标编码
 * @param valueShape 指标值结构
 * @param fact 主事实源编码；派生指标为空
 * @param joins 主事实源关联定义，最多两个
 * @param subject 被统计主体定义
 * @param time 主事实源时间字段；派生指标为空
 * @param dimensions 聚合维度字段引用
 * @param parameters 查询参数定义
 * @param rowSelection 所有 measure 共享的聚合前有限行集
 * @param value 单值指标定义；多字段指标为空
 * @param fields 多字段指标定义；单值指标为空映射
 *
 * @author wuxp
 * @date 2026-07-21 17:51
 */
@Schema(description = "单个指标的计算定义")
public record MetricDefinitionSpec(
        @Schema(description = "稳定且唯一的指标编码") String code,
        @Schema(description = "指标值结构") MetricValueShape valueShape,
        @Nullable @Schema(description = "主事实源编码；派生指标为空") String fact,
        @Schema(description = "主事实源关联定义") List<MetricJoinDsl> joins,
        @Schema(description = "被统计主体定义") MetricSubjectDsl subject,
        @Nullable @Schema(description = "主事实源时间字段；派生指标为空") MetricTimeDsl time,
        @Schema(description = "聚合维度字段引用") List<String> dimensions,
        @Schema(description = "查询参数定义") Map<String, MetricQueryParameterDefinitionDsl> parameters,
        @Nullable @Schema(description = "所有 measure 共享的聚合前有限行集") MetricRowSelectionDsl rowSelection,
        @Nullable @Schema(description = "单值指标定义；多字段指标为空") MetricValueDsl value,
        @Schema(description = "多字段指标定义；单值指标为空映射") Map<String, MetricValueDsl> fields) {

    public MetricDefinitionSpec {
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(valueShape, "valueShape must not be null");
        Objects.requireNonNull(subject, "subject must not be null");
        joins = List.copyOf(joins);
        dimensions = List.copyOf(dimensions);
        parameters = immutableMap(parameters);
        fields = immutableMap(fields);
    }

    private static <T> Map<String, T> immutableMap(Map<String, T> source) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }
}
