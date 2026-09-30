package com.wind.integration.metrics.spec;

import com.wind.integration.metrics.MetricValidationException;
import com.wind.integration.metrics.enums.MetricErrorCode;
import com.wind.integration.metrics.enums.MetricValueShape;
import com.wind.integration.metrics.json.MetricDefinitionSpecDeserializer;
import com.wind.integration.metrics.json.MetricDefinitionSpecSerializer;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;

import java.util.Objects;

/**
 * 指标定义的版本化协议容器，不再按 SQL/DSL 拆分共同口径。
 *
 * <p>schema6 保存共同定义及嵌套的取值声明。读取旧 DSL schema1—4 与旧 SQL 时显式转换，
 * 不猜测旧 SQL 缺失的值类型；旧 DSL schema5 已撤回，仍拒绝读取。
 * 旧 SQL 曾允许任意正数版本，因此保留其原 definitionType 布局（包括数字6）；
 * 它缺少值声明时不会因版本号而获得累计能力。新值声明与 SQL 累计能力必须使用共同 schema6。</p>
 *
 * @param schemaVersion 协议版本，不是定义修订
 * @param definition 精确修订的共同定义
 * @author wuxp
 */
@JsonDeserialize(using = MetricDefinitionSpecDeserializer.class)
@JsonSerialize(using = MetricDefinitionSpecSerializer.class)
public record MetricDefinitionSpec(Integer schemaVersion, MetricDefinition definition) {

    /**
     * 共同定义与取值声明分离后的协议版本。
     */
    public static final int SCHEMA_VERSION = 6;

    public MetricDefinitionSpec {
        Objects.requireNonNull(definition, "definition must not be null");
        if (schemaVersion == null || schemaVersion < 1) {
            throw new MetricValidationException(MetricErrorCode.DSL_SCHEMA_VERSION_UNSUPPORTED,
                    "/schemaVersion", "Schema version must be positive");
        }
        boolean sql = definition.valueQuery() instanceof MetricSqlDefinition;
        if (!sql && schemaVersion != SCHEMA_VERSION) {
            boolean unsupportedLegacySchema = schemaVersion > 4;
            boolean missingExactReferenceSchema = definition.derivationType().isDerived() && schemaVersion < 4;
            if (unsupportedLegacySchema || missingExactReferenceSchema) {
                throw new MetricValidationException(MetricErrorCode.DSL_SCHEMA_VERSION_UNSUPPORTED,
                        "/schemaVersion", "Legacy DSL supports RAW schemas 1 to 4 and DERIVED schema 4");
            }
        }
        if (schemaVersion == SCHEMA_VERSION) {
            boolean legacySql = sql && definition.value() == null && definition.fields().isEmpty();
            boolean scalar = definition.valueShape() == MetricValueShape.SCALAR;
            boolean invalidScalar = scalar && (definition.value() == null || !definition.fields().isEmpty());
            boolean invalidFieldSet = !scalar && (definition.value() != null || definition.fields().isEmpty());
            if (!legacySql && (invalidScalar || invalidFieldSet)) {
                throw new MetricValidationException(MetricErrorCode.DSL_VALUE_BRANCH_INVALID,
                        "/metric/value", "SCALAR requires value only; FIELD_SET requires non-empty fields only");
            }
        } else if (sql && (definition.value() != null || !definition.fields().isEmpty())) {
            throw new MetricValidationException(MetricErrorCode.DSL_SCHEMA_VERSION_UNSUPPORTED,
                    "/schemaVersion", "SQL shared value declarations require schema 6");
        }
    }
}
