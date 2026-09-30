package com.wind.integration.metrics.json;

import com.wind.integration.metrics.spec.MetricDSLDefinition;
import com.wind.integration.metrics.spec.MetricDefinition;
import com.wind.integration.metrics.spec.MetricDefinitionSpec;
import com.wind.integration.metrics.spec.MetricSqlDefinition;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * schema6 输出共同定义；旧版本按原 JSON 布局往返，不在读取时隐式升级协议。
 *
 * @author wuxp
 */
public final class MetricDefinitionSpecSerializer extends ValueSerializer<MetricDefinitionSpec> {

    @Override
    public void serialize(MetricDefinitionSpec spec, JsonGenerator generator, SerializationContext context) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("schemaVersion", spec.schemaVersion());
        MetricDefinition definition = spec.definition();
        boolean legacySql = definition.valueQuery() instanceof MetricSqlDefinition
                && definition.value() == null && definition.fields().isEmpty();
        if (spec.schemaVersion() == MetricDefinitionSpec.SCHEMA_VERSION && !legacySql) {
            result.put("definition", spec.definition());
        } else {
            result.put("definitionType", spec.definition().valueQuery() instanceof MetricSqlDefinition ? "SQL" : "DSL");
            result.put("definition", legacy(spec.definition()));
        }
        context.writeValue(generator, result);
    }

    private static Map<String, Object> legacy(MetricDefinition definition) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", definition.code());
        body.put("revision", definition.revision());
        body.put("valueShape", definition.valueShape());
        body.put("dimensions", definition.dimensions());
        body.put("parameters", definition.parameters());
        if (definition.valueQuery() instanceof MetricSqlDefinition sql) {
            body.put("subjectType", definition.subjectType());
            body.put("sqlTemplate", sql.sqlTemplate());
        } else {
            MetricDSLDefinition dsl = (MetricDSLDefinition) definition.valueQuery();
            body.put("subject", definition.subject());
            body.put("fact", dsl == null ? null : dsl.fact());
            body.put("joins", dsl == null ? List.of() : dsl.joins());
            body.put("time", dsl == null ? null : dsl.time());
            body.put("rowSelection", dsl == null ? null : dsl.rowSelection());
            body.put("value", definition.value());
            body.put("fields", definition.fields());
            body.put("dependencies", definition.dependencies());
        }
        return body;
    }
}
