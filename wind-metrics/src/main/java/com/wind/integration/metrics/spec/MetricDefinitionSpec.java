package com.wind.integration.metrics.spec;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wind.integration.metrics.MetricValidationException;
import com.wind.integration.metrics.enums.MetricDefinitionType;
import com.wind.integration.metrics.enums.MetricErrorCode;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.NullMarked;

import java.util.Objects;

/**
 * 指标定义规范
 *
 * <p>{@link #definitionType()} 标识定义的声明方式，Jackson 使用该字段进行多态反序列化。</p>
 *
 * @author wuxp
 * @date 2026-09-18 04:59
 **/
@NullMarked
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "definitionType",
        include = JsonTypeInfo.As.EXISTING_PROPERTY,
        visible = true
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = MetricDefinitionSpec.MetricDSLDefinitionSpec.class, name = "DSL"),
        @JsonSubTypes.Type(value = MetricDefinitionSpec.MetricSqlDefinitionSpec.class, name = "SQL")
})
public sealed interface MetricDefinitionSpec<O extends MetricDefinitionObject>
        permits MetricDefinitionSpec.MetricDSLDefinitionSpec, MetricDefinitionSpec.MetricSqlDefinitionSpec {

    /** 完整携带派生依赖精确版本的 Definition DSL 结构版本；Plan 独立演进。 */
    int DSL_SCHEMA_VERSION = 4;

    /**
     * @return 规范版本
     */
    @Schema(description = "规范版本")
    Integer schemaVersion();

    /**
     * @return 指标定义
     */
    @Schema(description = "指标定义")
    O definition();

    /**
     * @return 指标定义类型
     */
    @Schema(description = "指标定义类型")
    @JsonProperty("definitionType")
    MetricDefinitionType definitionType();

    @Schema(description = "指标定义 DSL 的根对象")
    record MetricDSLDefinitionSpec(
            @Schema(description = "规范版本") Integer schemaVersion,
            @Schema(description = "指标定义") MetricDSLDefinition definition) implements MetricDefinitionSpec<MetricDSLDefinition> {

        public MetricDSLDefinitionSpec {
            Objects.requireNonNull(definition, "definition must not be null");
            if (schemaVersion == null || schemaVersion < 1 || schemaVersion > DSL_SCHEMA_VERSION
                    || (definition.derivationType().isDerived() && schemaVersion != DSL_SCHEMA_VERSION)) {
                throw new MetricValidationException(MetricErrorCode.DSL_SCHEMA_VERSION_UNSUPPORTED,
                        "/schemaVersion", "Definition DSL supports RAW schemas 1 to 4 and DERIVED schema 4");
            }
        }

        @Override
        public MetricDefinitionType definitionType() {
            return MetricDefinitionType.DSL;
        }
    }

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
