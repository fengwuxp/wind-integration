package com.wind.integration.metrics.json;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.wind.integration.metrics.MetricValidationException;
import com.wind.integration.metrics.dsl.definition.MetricJoinDsl;
import com.wind.integration.metrics.dsl.definition.MetricQueryParameterDsl;
import com.wind.integration.metrics.dsl.definition.MetricReferenceDsl;
import com.wind.integration.metrics.dsl.definition.MetricSubjectDsl;
import com.wind.integration.metrics.dsl.definition.MetricTimeDsl;
import com.wind.integration.metrics.dsl.definition.MetricValueDsl;
import com.wind.integration.metrics.dsl.definition.selection.MetricRowSelectionDsl;
import com.wind.integration.metrics.enums.MetricErrorCode;
import com.wind.integration.metrics.enums.MetricValueShape;
import com.wind.integration.metrics.spec.MetricDSLDefinition;
import com.wind.integration.metrics.spec.MetricDefinition;
import com.wind.integration.metrics.spec.MetricDefinitionSpec;
import com.wind.integration.metrics.spec.MetricSqlDefinition;
import com.wind.integration.metrics.spec.MetricValueQueryDefinition;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.util.TokenBuffer;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 按协议版本恢复共同定义，旧协议只在 JSON 边界转换，不向消费者暴露旧包装。
 *
 * <p>缓存原始 token 以支持任意属性顺序，十进制强制保留 BigDecimal，随后交给已有的
 * filter/literal 类型解析器；不经过 Map/Object/Double 转换。</p>
 *
 * @author wuxp
 */
public final class MetricDefinitionSpecDeserializer extends ValueDeserializer<MetricDefinitionSpec> {

    @Override
    public MetricDefinitionSpec deserialize(JsonParser parser, DeserializationContext context) {
        if (!parser.isExpectedStartObjectToken()) {
            return context.reportInputMismatch(MetricDefinitionSpec.class, "Definition spec must be an object");
        }
        Integer schema = null;
        String type = null;
        Set<String> seen = new HashSet<>();
        try (TokenBuffer body = context.bufferForInputBuffering(parser).forceUseOfBigDecimal(true)) {
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                String name = parser.currentName();
                if (parser.currentToken() != JsonToken.PROPERTY_NAME || !seen.add(name)) {
                    return context.reportInputMismatch(MetricDefinitionSpec.class, "Duplicate or malformed spec property");
                }
                parser.nextToken();
                switch (name) {
                    case "schemaVersion" -> schema = context.readValue(parser, Integer.class);
                    case "definitionType" -> type = context.readValue(parser, String.class);
                    case "definition" -> body.copyCurrentStructure(parser);
                    default -> {
                        return context.reportInputMismatch(MetricDefinitionSpec.class, "Unknown spec property: %s", name);
                    }
                }
            }
            if (!seen.contains("definition")) {
                return context.reportInputMismatch(MetricDefinitionSpec.class, "Definition is required");
            }
            try (JsonParser definitionParser = body.asParserOnFirstToken(context)) {
                if (Integer.valueOf(MetricDefinitionSpec.SCHEMA_VERSION).equals(schema) && !seen.contains("definitionType")) {
                    MetricDefinition definition = context.readValue(definitionParser, MetricDefinition.class);
                    if (definition.value() == null && definition.fields().isEmpty()) {
                        throw invalid("value", "Unified schema 6 requires value declarations");
                    }
                    return new MetricDefinitionSpec(schema, definition);
                }
                if ("DSL".equals(type) && Integer.valueOf(MetricDefinitionSpec.SCHEMA_VERSION).equals(schema)) {
                    return context.reportInputMismatch(MetricDefinitionSpec.class, "DSL schema 6 requires the unified layout");
                }
                LegacyDefinition legacy = context.readValue(definitionParser, LegacyDefinition.class);
                return new MetricDefinitionSpec(schema, legacy.toDefinition(type));
            }
        } catch (MetricValidationException exception) {
            throw context.instantiationException(MetricDefinitionSpec.class, exception);
        }
    }

    /**
     * 仅用于恢复旧 JSON 的字段布局，不是运行时指标抽象。
     */
    private record LegacyDefinition(String code, int revision, MetricValueShape valueShape,
                                    @Nullable String fact, @Nullable List<MetricJoinDsl> joins,
                                    @Nullable MetricSubjectDsl subject, @Nullable MetricTimeDsl time,
                                    List<String> dimensions, Map<String, MetricQueryParameterDsl> parameters,
                                    @Nullable MetricRowSelectionDsl rowSelection, @Nullable MetricValueDsl value,
                                    @Nullable Map<String, MetricValueDsl> fields,
                                    @Nullable List<MetricReferenceDsl> dependencies,
                                    @Nullable String subjectType, @Nullable String sqlTemplate) {

        private MetricDefinition toDefinition(@Nullable String type) {
            if ("SQL".equals(type)) {
                if (fact != null || subject != null || time != null || rowSelection != null
                        || value != null || fields != null || dependencies != null || joins != null) {
                    throw invalid("valueQuery", "Legacy SQL contains DSL or shared value declarations");
                }
                return new MetricDefinition(code, revision, valueShape, new MetricSubjectDsl(subjectType, null),
                        dimensions, parameters, new MetricSqlDefinition(sqlTemplate), null, Map.of(), List.of());
            }
            if (!"DSL".equals(type) || sqlTemplate != null || subjectType != null) {
                throw invalid("valueQuery", "Legacy definition requires an unambiguous DSL/SQL type");
            }
            if (fact == null && (time != null || rowSelection != null || joins != null && !joins.isEmpty())) {
                throw invalid("valueQuery", "Derived definitions must not contain fact query fields");
            }
            MetricValueQueryDefinition query = fact == null ? null : new MetricDSLDefinition(fact, joins, time, rowSelection);
            return new MetricDefinition(code, revision, valueShape, subject, dimensions, parameters,
                    query, value, fields, dependencies);
        }

        @SuppressWarnings({"PMD.UnusedPrivateMethod", "PMD.UnusedFormalParameter"})
        @JsonAnySetter
        private void rejectUnknownProperty(String name, @Nullable Object ignored) {
            throw invalid(name, "Unknown legacy metric definition property");
        }
    }

    private static MetricValidationException invalid(String name, String message) {
        return new MetricValidationException(MetricErrorCode.DSL_VALUE_INVALID,
                "/metric/" + name.replace("~", "~0").replace("/", "~1"), message);
    }
}
