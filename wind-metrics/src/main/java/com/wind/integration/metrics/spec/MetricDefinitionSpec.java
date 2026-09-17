package com.wind.integration.metrics.spec;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.wind.integration.metrics.enums.MetricDefinitionType;
import com.wind.integration.metrics.json.MetricDefinitionDslJsonBinding;
import com.wind.integration.metrics.json.MetricSqlJsonBinding;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.NullMarked;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;

/**
 * 指标定义规范
 *
 * <p>{@link #definitionType()} 由实现类型派生，不是可写字段，也不进入规范 JSON：
 * 定义的声明方式已由 record 类型本身表达，宿主按该值选择 codec 或持久化列，
 * 不需要在载荷中重复同一事实。</p>
 *
 * @author wuxp
 * @date 2026-09-18 04:59
 **/
@NullMarked
public sealed interface MetricDefinitionSpec<O extends MetricDefinitionObject>
        permits MetricDefinitionSpec.MetricDSLDefinitionSpec, MetricDefinitionSpec.MetricSqlDefinitionSpec {

    @Schema(description = "规范版本")
    Integer schemaVersion();

    @Schema(description = "指标定义")
    O definition();

    /**
     * 取得本规范的声明方式，由实现类型固定，不随载荷变化。
     *
     * @return 定义声明方式
     */
    @JsonIgnore
    @Schema(description = "指标定义的声明方式")
    MetricDefinitionType definitionType();

    @JsonDeserialize(using = MetricDefinitionDslJsonBinding.Deserializer.class)
    @JsonSerialize(using = MetricDefinitionDslJsonBinding.Serializer.class)
    @Schema(description = "指标定义 DSL 的根对象")
    record MetricDSLDefinitionSpec(
            @Schema(description = "规范版本") Integer schemaVersion,
            @Schema(description = "指标定义") MetricDSLDefinition definition) implements MetricDefinitionSpec<MetricDSLDefinition> {

        @Override
        public MetricDefinitionType definitionType() {
            return MetricDefinitionType.DSL;
        }
    }

    @JsonDeserialize(using = MetricSqlJsonBinding.Deserializer.class)
    @JsonSerialize(using = MetricSqlJsonBinding.Serializer.class)
    @Schema(description = "SQL 模板指标定义的根对象")
    record MetricSqlDefinitionSpec(
            @Schema(description = "规范版本") Integer schemaVersion,
            @Schema(description = "SQL 模板指标定义") MetricSqlDefinition definition) implements MetricDefinitionSpec<MetricSqlDefinition> {

        @Override
        public MetricDefinitionType definitionType() {
            return MetricDefinitionType.SQL;
        }
    }

}
