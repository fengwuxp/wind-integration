package com.wind.integration.metrics.spec;

import com.wind.integration.metrics.dsl.definition.MetricQueryParameterDsl;
import com.wind.integration.metrics.enums.MetricValueShape;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * 指标定义规格（顶层抽象）
 *
 * <p>sealed interface 允许两种形态：
 * <ul>
 *   <li>{@link MetricDSLDefinition} - 通过 DSL 定义的指标（支持实时查询和快照物化）</li>
 *   <li>{@link MetricSqlDefinition} - 通过 SQL 模板定义的指标（仅支持实时查询）</li>
 * </ul>
 *
 * @author wuxp
 * @date 2026-09-16
 */
public sealed interface MetricDefinitionObject permits MetricDSLDefinition, MetricSqlDefinition {

    /**
     * 指标编码（全局唯一）
     */
    String code();

    /**
     * 值形态
     */
    MetricValueShape valueShape();

    /**
     * 主体类型
     */
    String subjectType();

    /**
     * 维度列表
     */
    List<String> dimensions();

    /**
     * 参数定义（用于参数化查询）
     */
    @Nullable
    Map<String, MetricQueryParameterDsl> parameters();
}
