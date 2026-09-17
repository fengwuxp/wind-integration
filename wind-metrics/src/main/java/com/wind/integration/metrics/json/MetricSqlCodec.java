package com.wind.integration.metrics.json;

import com.wind.integration.metrics.MetricValidationException;
import com.wind.integration.metrics.dsl.definition.MetricQueryParameterDsl;
import com.wind.integration.metrics.enums.MetricErrorCode;
import com.wind.integration.metrics.enums.MetricValueShape;
import com.wind.integration.metrics.enums.MetricValueType;
import com.wind.integration.metrics.spec.MetricDefinitionSpec;
import com.wind.integration.metrics.spec.MetricSqlDefinition;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JsonParser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static com.wind.integration.metrics.json.MetricDslJson.error;
import static com.wind.integration.metrics.json.MetricDslJson.required;
import static com.wind.integration.metrics.json.MetricDslJson.string;
import static com.wind.integration.metrics.json.MetricJsonSupport.child;

/**
 * SQL 模板指标的序列化/反序列化编解码器。
 *
 * <p>负责 SQL 模板指标的 JSON 解析、基础校验和规范化输出。</p>
 *
 * @author wuxp
 * @date 2026-09-17
 */
public final class MetricSqlCodec {

    /**
     * 当前支持的 SQL 模板定义 DSL 结构版本。
     */
    private static final int SCHEMA_VERSION = 1;

    /**
     * DSL 编码和别名允许使用的标识符格式。
     */
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z][A-Za-z0-9_]*");

    /**
     * SQL 模板定义根节点允许出现的字段。
     */
    private static final Set<String> ROOT_FIELDS = Set.of("schemaVersion", "metric");

    /**
     * SQL 模板指标定义节点允许出现的字段。
     */
    private static final Set<String> METRIC_FIELDS = Set.of(
            "code", "valueShape", "subjectType", "dimensions", "parameters", "sqlTemplate");

    /**
     * 解析并校验 SQL 模板指标定义 JSON。
     *
     * @param json SQL 模板定义 JSON
     * @return 不可变的 SQL 模板指标定义对象
     * @throws MetricValidationException JSON、字段或指标结构不符合 v1 契约时抛出
     */
    public MetricDefinitionSpec.MetricSqlDefinitionSpec parse(String json) {
        return parse(MetricJsonSupport.parseRootObject(json));
    }

    MetricDefinitionSpec.MetricSqlDefinitionSpec parse(JsonParser parser) {
        return parse(MetricJsonSupport.parseRootObject(parser));
    }

    private MetricDefinitionSpec.MetricSqlDefinitionSpec parse(Map<String, Object> root) {
        int schemaVersion = MetricDslJson.integer(required(root, "schemaVersion", ""), "/schemaVersion");
        if (schemaVersion != SCHEMA_VERSION) {
            throw error(MetricErrorCode.DSL_SCHEMA_VERSION_UNSUPPORTED, "/schemaVersion", "Unsupported schema version");
        }
        MetricDslJson.rejectUnknown(root, "", ROOT_FIELDS);
        MetricDefinitionSpec.MetricSqlDefinitionSpec definition = new MetricDefinitionSpec.MetricSqlDefinitionSpec(
                schemaVersion,
                parseMetric(MetricDslJson.object(required(root, "metric", ""), "/metric")));
        validateBasic(definition);
        return definition;
    }

    /**
     * 校验已构造的 SQL 模板指标定义是否满足 v1 基础结构约束。
     *
     * @param definition SQL 模板指标定义
     * @throws MetricValidationException 定义不满足封闭字段、分支或值约束时抛出
     */
    public void validateBasic(MetricDefinitionSpec.MetricSqlDefinitionSpec definition) {
        if (definition.schemaVersion() != SCHEMA_VERSION) {
            throw error(MetricErrorCode.DSL_SCHEMA_VERSION_UNSUPPORTED, "/schemaVersion", "Unsupported schema version");
        }
        MetricSqlDefinition metric = definition.definition();
        validateIdentifier(metric.code(), 100, "/metric/code");
        validateIdentifier(metric.subjectType(), 64, "/metric/subjectType");

        if (metric.dimensions().size() != new LinkedHashSet<>(metric.dimensions()).size()) {
            throw error(MetricErrorCode.DSL_VALUE_INVALID, "/metric/dimensions", "Dimensions must be unique");
        }

        for (int index = 0; index < metric.dimensions().size(); index++) {
            validateIdentifier(
                    metric.dimensions().get(index),
                    64,
                    child("/metric/dimensions", Integer.toString(index)));
        }

        metric.parameters().forEach((name, parameter) -> {
            String path = child("/metric/parameters", name);
            validateIdentifier(name, 64, path);
            if (parameter.minimum() != null && parameter.maximum() != null
                    && parameter.maximum() < parameter.minimum()) {
                throw error(MetricErrorCode.DSL_VALUE_INVALID, path, "Invalid parameter range");
            }
        });

        if (metric.sqlTemplate().isBlank()) {
            throw error(MetricErrorCode.DSL_VALUE_INVALID, "/metric/sqlTemplate", "SQL template must not be blank");
        }
    }

    /**
     * 将合法 SQL 模板指标定义输出为字段顺序稳定的规范 JSON。
     *
     * @param definition SQL 模板指标定义
     * @return 可用于内容比对和签名的规范 JSON
     * @throws MetricValidationException 定义不满足 v1 契约时抛出
     */
    public String canonicalize(MetricDefinitionSpec.MetricSqlDefinitionSpec definition) {
        validateBasic(definition);
        return MetricJsonSupport.toJson(toCanonicalMap(definition));
    }

    private MetricSqlDefinition parseMetric(Map<String, Object> source) {
        MetricDslJson.rejectUnknown(source, "/metric", METRIC_FIELDS);
        String code = string(required(source, "code", "/metric"), "/metric/code");
        MetricValueShape valueShape = MetricDslJson.enumValue(
                required(source, "valueShape", "/metric"), MetricValueShape.class, "/metric/valueShape");
        String subjectType = string(required(source, "subjectType", "/metric"), "/metric/subjectType");
        List<String> dimensions = parseStringList(
                required(source, "dimensions", "/metric"), "/metric/dimensions", false);
        dimensions = dimensions.stream().sorted().toList();
        Map<String, MetricQueryParameterDsl> parameters = parseParameters(
                MetricDslJson.optionalValue(source, "parameters", "/metric/parameters"));
        String sqlTemplate = string(required(source, "sqlTemplate", "/metric"), "/metric/sqlTemplate");

        return new MetricSqlDefinition(code, valueShape, subjectType, dimensions, parameters, sqlTemplate);
    }

    private Map<String, MetricQueryParameterDsl> parseParameters(@Nullable Object value) {
        if (value == null) {
            return Map.of();
        }
        Map<String, Object> source = MetricDslJson.object(value, "/metric/parameters");
        if (source.isEmpty()) {
            throw error(MetricErrorCode.DSL_VALUE_INVALID, "/metric/parameters", "Parameters must not be empty");
        }
        Map<String, MetricQueryParameterDsl> result = new LinkedHashMap<>();
        source.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            String path = child("/metric/parameters", entry.getKey());
            validateIdentifier(entry.getKey(), 64, path);
            Map<String, Object> parameter = MetricDslJson.object(entry.getValue(), path);
            MetricDslJson.rejectUnknown(parameter, path, Set.of("valueType", "minimum", "maximum"));
            result.put(entry.getKey(), new MetricQueryParameterDsl(
                    MetricDslJson.enumValue(
                            required(parameter, "valueType", path),
                            MetricValueType.class,
                            child(path, "valueType")),
                    parameter.containsKey("minimum")
                            ? MetricDslJson.integer(parameter.get("minimum"), child(path, "minimum"))
                            : null,
                    parameter.containsKey("maximum")
                            ? MetricDslJson.integer(parameter.get("maximum"), child(path, "maximum"))
                            : null));
        });
        return result;
    }

    private List<String> parseStringList(Object value, String path, boolean requireNonEmpty) {
        List<Object> source = MetricDslJson.array(value, path);
        if (requireNonEmpty && source.isEmpty()) {
            throw error(MetricErrorCode.DSL_VALUE_INVALID, path, "Array must not be empty");
        }
        List<String> result = new ArrayList<>(source.size());
        for (int index = 0; index < source.size(); index++) {
            result.add(string(source.get(index), child(path, Integer.toString(index))));
        }
        if (result.size() != new LinkedHashSet<>(result).size()) {
            throw error(MetricErrorCode.DSL_VALUE_INVALID, path, "Array values must be unique");
        }
        return result;
    }

    private Map<String, Object> toCanonicalMap(MetricDefinitionSpec.MetricSqlDefinitionSpec definition) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("schemaVersion", definition.schemaVersion());
        root.put("metric", toCanonicalMetric(definition.definition()));
        return root;
    }

    private Map<String, Object> toCanonicalMetric(MetricSqlDefinition metric) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", metric.code());
        result.put("valueShape", metric.valueShape().name());
        result.put("subjectType", metric.subjectType());
        result.put("dimensions", metric.dimensions().stream().sorted().toList());
        if (!metric.parameters().isEmpty()) {
            Map<String, Object> parameters = new LinkedHashMap<>();
            metric.parameters().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry ->
                    parameters.put(entry.getKey(), toCanonicalParameter(entry.getValue())));
            result.put("parameters", parameters);
        }
        result.put("sqlTemplate", metric.sqlTemplate());
        return result;
    }

    private Map<String, Object> toCanonicalParameter(MetricQueryParameterDsl parameter) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("valueType", parameter.valueType().name());
        if (parameter.minimum() != null) {
            result.put("minimum", parameter.minimum());
        }
        if (parameter.maximum() != null) {
            result.put("maximum", parameter.maximum());
        }
        return result;
    }

    private void validateIdentifier(String value, int maxLength, String path) {
        if (value.length() > maxLength || !IDENTIFIER.matcher(value).matches()) {
            throw error(MetricErrorCode.DSL_IDENTIFIER_INVALID, path, "Invalid identifier");
        }
    }
}
