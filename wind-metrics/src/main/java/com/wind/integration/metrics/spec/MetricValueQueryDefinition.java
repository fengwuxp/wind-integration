package com.wind.integration.metrics.spec;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.wind.integration.metrics.enums.MetricDefinitionType;

/**
 * 指标原生取值的静态声明，只隔离 DSL 事实查询与 SQL 模板的差异。
 *
 * <p>共同口径归 {@link MetricDefinition}；查询条件归 MetricQuery；数据读取归仓储。
 * 本接口不运行表达式、不读写数据，也不持有路线、分段、计划或快照水位。</p>
 *
 * @author wuxp
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", include = JsonTypeInfo.As.EXISTING_PROPERTY)
@JsonSubTypes({
        @JsonSubTypes.Type(value = MetricDSLDefinition.class, name = "DSL"),
        @JsonSubTypes.Type(value = MetricSqlDefinition.class, name = "SQL")
})
public sealed interface MetricValueQueryDefinition permits MetricDSLDefinition, MetricSqlDefinition {

    /**
     * @return 取值声明形式，不表示 RAW/DERIVED 分类或查询路线。
     */
    @JsonProperty("type")
    MetricDefinitionType type();
}
