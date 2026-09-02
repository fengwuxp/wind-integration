package com.wind.integration.metrics.dsl;

import com.wind.integration.metrics.MetricValidationException;
import com.wind.integration.metrics.dsl.definition.MetricDefinitionDsl;
import com.wind.integration.metrics.dsl.definition.MetricDefinitionSpec;
import com.wind.integration.metrics.dsl.definition.MetricExpressionDsl;
import com.wind.integration.metrics.dsl.definition.MetricJoinDsl;
import com.wind.integration.metrics.dsl.definition.MetricJoinOnDsl;
import com.wind.integration.metrics.dsl.definition.MetricMeasureDsl;
import com.wind.integration.metrics.dsl.definition.MetricOrElseDsl;
import com.wind.integration.metrics.dsl.definition.MetricQueryParameterDefinitionDsl;
import com.wind.integration.metrics.dsl.definition.MetricSubjectDsl;
import com.wind.integration.metrics.dsl.definition.MetricTimeDsl;
import com.wind.integration.metrics.dsl.definition.MetricValueDsl;
import com.wind.integration.metrics.dsl.definition.selection.MetricLimitDsl;
import com.wind.integration.metrics.dsl.definition.selection.MetricOrderByDsl;
import com.wind.integration.metrics.dsl.definition.selection.MetricRowSelectionDsl;
import com.wind.integration.metrics.dsl.literal.BooleanMetricLiteralDsl;
import com.wind.integration.metrics.dsl.filter.ComparisonMetricFilterDsl;
import com.wind.integration.metrics.dsl.literal.DecimalMetricLiteralDsl;
import com.wind.integration.metrics.dsl.literal.IntegralMetricLiteralDsl;
import com.wind.integration.metrics.dsl.filter.LogicalMetricFilterDsl;
import com.wind.integration.metrics.dsl.filter.MetricFilterDsl;
import com.wind.integration.metrics.dsl.literal.MetricLiteralDsl;
import com.wind.integration.metrics.dsl.literal.MetricNumericLiteralDsl;
import com.wind.integration.metrics.dsl.filter.NullMetricFilterDsl;
import com.wind.integration.metrics.dsl.filter.SetMetricFilterDsl;
import com.wind.integration.metrics.dsl.literal.StringMetricLiteralDsl;
import com.wind.integration.metrics.enums.MetricAggregation;
import com.wind.integration.metrics.enums.MetricErrorCode;
import com.wind.integration.metrics.enums.MetricExpressionType;
import com.wind.integration.metrics.enums.MetricFilterOperator;
import com.wind.integration.metrics.enums.MetricJoinCardinality;
import com.wind.integration.metrics.enums.MetricJoinType;
import com.wind.integration.metrics.enums.MetricOrElseMode;
import com.wind.integration.metrics.enums.MetricSortDirection;
import com.wind.integration.metrics.enums.MetricValueShape;
import com.wind.integration.metrics.enums.MetricValueType;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JsonParser;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

import static com.wind.integration.metrics.dsl.MetricDslJson.child;
import static com.wind.integration.metrics.dsl.MetricDslJson.error;
import static com.wind.integration.metrics.dsl.MetricDslJson.required;
import static com.wind.integration.metrics.dsl.MetricDslJson.string;

/**
 * 指标 Definition DSL 的关闭世界解析、基础校验与确定性规范化入口。
 *
 * <p>只接受白名单字段和封闭 AST，不执行表达式，也不解析物理表或数据源。</p>
 *
 * @author wuxp
 * @date 2026-07-21 17:51
 */
public final class MetricDefinitionDslCodec {

    /** 当前支持的 Definition DSL 结构版本。 */
    private static final int SCHEMA_VERSION = 1;

    /** DSL 编码和别名允许使用的标识符格式。 */
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z][A-Za-z0-9_]*");

    /** 主事实字段或带一个关联事实别名前缀的字段引用格式。 */
    private static final Pattern FIELD_REFERENCE = Pattern.compile(
            "[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z][A-Za-z0-9_]*)?");

    /** Definition DSL 根节点允许出现的字段。 */
    private static final Set<String> ROOT_FIELDS = Set.of("schemaVersion", "metric");

    /** 指标定义节点允许出现的字段。 */
    private static final Set<String> METRIC_FIELDS = Set.of(
            "code", "valueShape", "fact", "joins", "subject", "time", "dimensions", "parameters",
            "rowSelection", "value", "fields");

    /** 不允许用作多值字段名的 SpEL 保留名称。 */
    private static final Set<String> RESERVED_NAMES = Set.of(
            "metric", "T", "new", "true", "false", "null", "root", "this", "and", "or", "not", "div", "mod",
            "eq", "ne", "lt", "le", "gt", "ge", "between", "matches", "instanceof");

    /**
     * 解析并校验指标 Definition DSL JSON。
     *
     * @param json Definition DSL JSON
     * @return 不可变的指标定义对象
     * @throws MetricValidationException JSON、字段或指标结构不符合 v1 契约时抛出
     */
    public MetricDefinitionDsl parse(String json) {
        return parse(MetricDslJson.parseRootObject(json));
    }

    MetricDefinitionDsl parse(JsonParser parser) {
        return parse(MetricDslJson.parseRootObject(parser));
    }

    private MetricDefinitionDsl parse(Map<String, Object> root) {
        int schemaVersion = MetricDslJson.integer(required(root, "schemaVersion", ""), "/schemaVersion");
        if (schemaVersion != SCHEMA_VERSION) {
            throw error(MetricErrorCode.DSL_SCHEMA_VERSION_UNSUPPORTED, "/schemaVersion", "Unsupported schema version");
        }
        MetricDslJson.rejectUnknown(root, "", ROOT_FIELDS);
        MetricDefinitionDsl definition = new MetricDefinitionDsl(
                schemaVersion,
                parseMetric(MetricDslJson.object(required(root, "metric", ""), "/metric")));
        validateBasic(definition);
        return definition;
    }

    /**
     * 校验已构造的指标定义是否满足 v1 基础结构约束。
     *
     * @param definition 指标定义
     * @throws MetricValidationException 定义不满足封闭字段、分支或值约束时抛出
     */
    public void validateBasic(MetricDefinitionDsl definition) {
        if (definition.schemaVersion() != SCHEMA_VERSION) {
            throw error(MetricErrorCode.DSL_SCHEMA_VERSION_UNSUPPORTED, "/schemaVersion", "Unsupported schema version");
        }
        MetricDefinitionSpec metric = definition.metric();
        validateIdentifier(metric.code(), 100, "/metric/code");
        validateIdentifier(metric.subject().type(), 64, "/metric/subject/type");
        validateMetricStructure(metric);
        if (metric.dimensions().size() != new LinkedHashSet<>(metric.dimensions()).size()) {
            throw error(MetricErrorCode.DSL_VALUE_INVALID, "/metric/dimensions", "Dimensions must be unique");
        }
        boolean factBased = metric.fact() != null;
        if (factBased) {
            if (metric.time() == null) {
                throw error(MetricErrorCode.DSL_FIELD_REQUIRED, "/metric/time", "Fact-based metric requires time");
            }
            if ("GLOBAL".equals(metric.subject().type())) {
                if (metric.subject().field() != null) {
                    throw error(
                            MetricErrorCode.DSL_VALUE_BRANCH_INVALID,
                            "/metric/subject/field",
                            "GLOBAL forbids subject field");
                }
            } else if (metric.subject().field() == null) {
                throw error(MetricErrorCode.DSL_FIELD_REQUIRED, "/metric/subject/field", "Subject field is required");
            }
        } else {
            if (metric.time() != null || !metric.joins().isEmpty() || metric.subject().field() != null) {
                throw error(MetricErrorCode.DSL_VALUE_BRANCH_INVALID, "/metric", "Derived metric contains fact fields");
            }
        }
        validateParametersAndRowSelection(metric, factBased);
        validateValueShape(metric, factBased);
        if (metric.value() != null) {
            validateValue(metric.value(), "/metric/value");
        }
        metric.fields().forEach((fieldName, value) -> validateValue(value, child("/metric/fields", fieldName)));
    }

    private void validateMetricStructure(MetricDefinitionSpec metric) {
        if (metric.fact() != null) {
            validateIdentifier(metric.fact(), 100, "/metric/fact");
        }
        if (metric.subject().field() != null) {
            validateFieldName(metric.subject().field(), "/metric/subject/field");
        }
        if (metric.time() != null) {
            validateFieldName(metric.time().field(), "/metric/time/field");
        }
        for (int index = 0; index < metric.dimensions().size(); index++) {
            validateFieldReference(
                    metric.dimensions().get(index),
                    child("/metric/dimensions", Integer.toString(index)));
        }
        validateJoins(metric.fact(), metric.joins());
        metric.fields().keySet().forEach(fieldName -> {
            String path = child("/metric/fields", fieldName);
            validateIdentifier(fieldName, 64, path);
        });
    }

    private void validateJoins(@Nullable String primaryFact, List<MetricJoinDsl> joins) {
        if (joins.size() > 2) {
            throw error(MetricErrorCode.DSL_VALUE_INVALID, "/metric/joins", "At most two joins are supported");
        }
        Set<String> aliases = new LinkedHashSet<>();
        for (int index = 0; index < joins.size(); index++) {
            MetricJoinDsl join = joins.get(index);
            String path = child("/metric/joins", Integer.toString(index));
            validateIdentifier(join.alias(), 64, child(path, "alias"));
            if (join.alias().equals(primaryFact)) {
                throw error(
                        MetricErrorCode.DSL_IDENTIFIER_INVALID,
                        child(path, "alias"),
                        "Join alias conflicts with primary fact");
            }
            if (!aliases.add(join.alias())) {
                throw error(MetricErrorCode.DSL_VALUE_INVALID, child(path, "alias"), "Join alias must be unique");
            }
            validateIdentifier(join.fact(), 100, child(path, "fact"));
            if (join.on().isEmpty()) {
                throw error(MetricErrorCode.DSL_VALUE_INVALID, child(path, "on"), "Join keys must not be empty");
            }
            for (int onIndex = 0; onIndex < join.on().size(); onIndex++) {
                MetricJoinOnDsl joinOn = join.on().get(onIndex);
                String onPath = child(child(path, "on"), Integer.toString(onIndex));
                validateFieldName(joinOn.primaryField(), child(onPath, "primaryField"));
                validateFieldName(joinOn.joinField(), child(onPath, "joinField"));
            }
        }
    }

    /**
     * 将合法指标定义输出为字段顺序稳定的规范 JSON。
     *
     * @param definition 指标定义
     * @return 可用于内容比对和签名的规范 JSON
     * @throws MetricValidationException 定义不满足 v1 契约时抛出
     */
    public String canonicalize(MetricDefinitionDsl definition) {
        validateBasic(definition);
        return MetricDslJson.toJson(toCanonicalMap(definition));
    }

    private MetricDefinitionSpec parseMetric(Map<String, Object> source) {
        MetricDslJson.rejectUnknown(source, "/metric", METRIC_FIELDS);
        String code = string(required(source, "code", "/metric"), "/metric/code");
        MetricValueShape valueShape = MetricDslJson.enumValue(
                required(source, "valueShape", "/metric"), MetricValueShape.class, "/metric/valueShape");
        String fact = optionalString(source, "fact", "/metric/fact");
        List<MetricJoinDsl> joins = parseJoins(
                MetricDslJson.optionalValue(source, "joins", "/metric/joins"));
        MetricSubjectDsl subject = parseSubject(MetricDslJson.object(
                required(source, "subject", "/metric"), "/metric/subject"));
        MetricTimeDsl time = source.containsKey("time")
                ? parseTime(MetricDslJson.object(source.get("time"), "/metric/time"))
                : null;
        List<String> dimensions = parseStringList(
                required(source, "dimensions", "/metric"), "/metric/dimensions", false);
        dimensions = dimensions.stream().sorted().toList();
        Map<String, MetricQueryParameterDefinitionDsl> parameters = parseParameters(
                MetricDslJson.optionalValue(source, "parameters", "/metric/parameters"));
        MetricRowSelectionDsl rowSelection = source.containsKey("rowSelection")
                ? parseRowSelection(MetricDslJson.object(source.get("rowSelection"), "/metric/rowSelection"))
                : null;
        MetricValueDsl value = source.containsKey("value")
                ? parseValue(MetricDslJson.object(source.get("value"), "/metric/value"), "/metric/value")
                : null;
        Map<String, MetricValueDsl> fields = parseFields(
                MetricDslJson.optionalValue(source, "fields", "/metric/fields"));
        return new MetricDefinitionSpec(
                code, valueShape, fact, joins, subject, time, dimensions, parameters, rowSelection, value, fields);
    }

    private Map<String, MetricQueryParameterDefinitionDsl> parseParameters(@Nullable Object value) {
        if (value == null) {
            return Map.of();
        }
        Map<String, Object> source = MetricDslJson.object(value, "/metric/parameters");
        if (source.isEmpty()) {
            throw error(MetricErrorCode.DSL_VALUE_INVALID, "/metric/parameters", "Parameters must not be empty");
        }
        Map<String, MetricQueryParameterDefinitionDsl> result = new LinkedHashMap<>();
        source.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            String path = child("/metric/parameters", entry.getKey());
            validateIdentifier(entry.getKey(), 64, path);
            Map<String, Object> parameter = MetricDslJson.object(entry.getValue(), path);
            MetricDslJson.rejectUnknown(parameter, path, Set.of("valueType", "minimum", "maximum"));
            result.put(entry.getKey(), new MetricQueryParameterDefinitionDsl(
                    MetricDslJson.enumValue(
                            required(parameter, "valueType", path),
                            MetricValueType.class,
                            child(path, "valueType")),
                    MetricDslJson.integer(required(parameter, "minimum", path), child(path, "minimum")),
                    MetricDslJson.integer(required(parameter, "maximum", path), child(path, "maximum"))));
        });
        return result;
    }

    private MetricRowSelectionDsl parseRowSelection(Map<String, Object> source) {
        String path = "/metric/rowSelection";
        MetricDslJson.rejectUnknown(source, path, Set.of("filter", "orderBy", "limit"));
        MetricFilterDsl filter = source.containsKey("filter")
                ? parseFilter(MetricDslJson.object(source.get("filter"), child(path, "filter")), child(path, "filter"))
                : null;
        List<MetricOrderByDsl> orderBy = parseOrderBy(
                MetricDslJson.array(required(source, "orderBy", path), child(path, "orderBy")),
                child(path, "orderBy"));
        MetricLimitDsl limit = parseLimit(
                MetricDslJson.object(required(source, "limit", path), child(path, "limit")),
                child(path, "limit"));
        return new MetricRowSelectionDsl(filter, orderBy, limit);
    }

    private List<MetricOrderByDsl> parseOrderBy(List<Object> source, String path) {
        if (source.isEmpty()) {
            throw error(MetricErrorCode.METRIC_ROW_SELECTION_INVALID, path, "orderBy must not be empty");
        }
        List<MetricOrderByDsl> result = new ArrayList<>(source.size());
        Set<String> fields = new LinkedHashSet<>();
        for (int index = 0; index < source.size(); index++) {
            String itemPath = child(path, Integer.toString(index));
            Map<String, Object> item = MetricDslJson.object(source.get(index), itemPath);
            MetricDslJson.rejectUnknown(item, itemPath, Set.of("field", "direction"));
            String field = string(required(item, "field", itemPath), child(itemPath, "field"));
            if (!fields.add(field)) {
                throw error(MetricErrorCode.METRIC_ROW_SELECTION_INVALID, path, "orderBy fields must be unique");
            }
            result.add(new MetricOrderByDsl(
                    field,
                    MetricDslJson.enumValue(
                            required(item, "direction", itemPath),
                            MetricSortDirection.class,
                            child(itemPath, "direction"))));
        }
        return result;
    }

    private MetricLimitDsl parseLimit(Map<String, Object> source, String path) {
        MetricDslJson.rejectUnknown(source, path, Set.of("value", "parameter"));
        boolean hasValue = source.containsKey("value");
        boolean hasParameter = source.containsKey("parameter");
        if (hasValue == hasParameter) {
            throw error(MetricErrorCode.METRIC_ROW_SELECTION_INVALID, path, "Limit requires exactly one branch");
        }
        if (hasValue) {
            return new MetricLimitDsl(
                    MetricDslJson.integer(source.get("value"), child(path, "value")), null);
        }
        return new MetricLimitDsl(null, string(source.get("parameter"), child(path, "parameter")));
    }

    private MetricSubjectDsl parseSubject(Map<String, Object> source) {
        MetricDslJson.rejectUnknown(source, "/metric/subject", Set.of("type", "field"));
        return new MetricSubjectDsl(
                string(required(source, "type", "/metric/subject"), "/metric/subject/type"),
                optionalString(source, "field", "/metric/subject/field"));
    }

    private MetricTimeDsl parseTime(Map<String, Object> source) {
        MetricDslJson.rejectUnknown(source, "/metric/time", Set.of("field"));
        return new MetricTimeDsl(string(required(source, "field", "/metric/time"), "/metric/time/field"));
    }

    private List<MetricJoinDsl> parseJoins(@Nullable Object value) {
        if (value == null) {
            return List.of();
        }
        List<Object> source = MetricDslJson.array(value, "/metric/joins");
        if (source.isEmpty() || source.size() > 2) {
            throw error(MetricErrorCode.DSL_VALUE_INVALID, "/metric/joins", "Joins must contain one or two items");
        }
        List<MetricJoinDsl> result = new ArrayList<>(source.size());
        for (int index = 0; index < source.size(); index++) {
            String path = child("/metric/joins", Integer.toString(index));
            Map<String, Object> join = MetricDslJson.object(source.get(index), path);
            MetricDslJson.rejectUnknown(join, path, Set.of("alias", "fact", "joinType", "cardinality", "on"));
            List<Object> onSource = MetricDslJson.array(required(join, "on", path), child(path, "on"));
            if (onSource.isEmpty()) {
                throw error(MetricErrorCode.DSL_VALUE_INVALID, child(path, "on"), "Join keys must not be empty");
            }
            List<MetricJoinOnDsl> on = new ArrayList<>(onSource.size());
            for (int onIndex = 0; onIndex < onSource.size(); onIndex++) {
                String onPath = child(child(path, "on"), Integer.toString(onIndex));
                Map<String, Object> item = MetricDslJson.object(onSource.get(onIndex), onPath);
                MetricDslJson.rejectUnknown(item, onPath, Set.of("primaryField", "joinField"));
                on.add(new MetricJoinOnDsl(
                        string(required(item, "primaryField", onPath), child(onPath, "primaryField")),
                        string(required(item, "joinField", onPath), child(onPath, "joinField"))));
            }
            result.add(new MetricJoinDsl(
                    string(required(join, "alias", path), child(path, "alias")),
                    string(required(join, "fact", path), child(path, "fact")),
                    MetricDslJson.enumValue(
                            required(join, "joinType", path), MetricJoinType.class, child(path, "joinType")),
                    MetricDslJson.enumValue(
                            required(join, "cardinality", path),
                            MetricJoinCardinality.class,
                            child(path, "cardinality")),
                    on));
        }
        return result.stream().sorted(Comparator.comparing(MetricJoinDsl::alias)).toList();
    }

    private Map<String, MetricValueDsl> parseFields(@Nullable Object value) {
        if (value == null) {
            return Map.of();
        }
        Map<String, Object> source = MetricDslJson.object(value, "/metric/fields");
        if (source.isEmpty()) {
            throw error(MetricErrorCode.DSL_VALUE_INVALID, "/metric/fields", "fields must not be empty");
        }
        Map<String, MetricValueDsl> result = new LinkedHashMap<>();
        source.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            String path = child("/metric/fields", entry.getKey());
            validateIdentifier(entry.getKey(), 64, path);
            result.put(entry.getKey(), parseValue(MetricDslJson.object(entry.getValue(), path), path));
        });
        return result;
    }

    private MetricValueDsl parseValue(Map<String, Object> source, String path) {
        MetricDslJson.rejectUnknown(
                source, path, Set.of("valueType", "scale", "roundingMode", "measure", "expression", "orElse"));
        MetricValueType valueType = MetricDslJson.enumValue(
                required(source, "valueType", path), MetricValueType.class, child(path, "valueType"));
        Integer scale = null;
        RoundingMode roundingMode = null;
        if (valueType == MetricValueType.DECIMAL) {
            scale = source.containsKey("scale")
                    ? MetricDslJson.integer(source.get("scale"), child(path, "scale"))
                    : 4;
            if (scale < 4 || scale > 6) {
                throw error(MetricErrorCode.DSL_VALUE_INVALID, child(path, "scale"), "Scale must be between 4 and 6");
            }
            roundingMode = source.containsKey("roundingMode")
                    ? MetricDslJson.enumValue(
                            source.get("roundingMode"), RoundingMode.class, child(path, "roundingMode"))
                    : RoundingMode.HALF_UP;
            if (roundingMode != RoundingMode.HALF_UP) {
                throw error(
                        MetricErrorCode.DSL_VALUE_INVALID,
                        child(path, "roundingMode"),
                        "Only HALF_UP is supported");
            }
        } else if (source.containsKey("scale") || source.containsKey("roundingMode")) {
            throw error(MetricErrorCode.DSL_VALUE_BRANCH_INVALID, path, "Non-decimal value forbids precision fields");
        }
        boolean hasMeasure = source.containsKey("measure") && source.get("measure") != null;
        boolean hasExpression = source.containsKey("expression") && source.get("expression") != null;
        if (hasMeasure == hasExpression) {
            throw error(MetricErrorCode.DSL_VALUE_BRANCH_INVALID, path, "Exactly one calculation branch is required");
        }
        MetricMeasureDsl measure = hasMeasure
                ? parseMeasure(
                        MetricDslJson.object(source.get("measure"), child(path, "measure")),
                        child(path, "measure"))
                : null;
        MetricExpressionDsl expression = hasExpression
                ? parseExpression(MetricDslJson.object(
                        source.get("expression"), child(path, "expression")), child(path, "expression"))
                : null;
        MetricOrElseDsl orElse = source.containsKey("orElse")
                ? parseOrElse(MetricDslJson.object(
                        source.get("orElse"), child(path, "orElse")), child(path, "orElse"))
                : new MetricOrElseDsl(MetricOrElseMode.NULL, null);
        return new MetricValueDsl(valueType, scale, roundingMode, measure, expression, orElse);
    }

    private MetricMeasureDsl parseMeasure(Map<String, Object> source, String path) {
        MetricDslJson.rejectUnknown(source, path, Set.of("aggregation", "field", "filter"));
        MetricAggregation aggregation = MetricDslJson.enumValue(
                required(source, "aggregation", path), MetricAggregation.class, child(path, "aggregation"));
        String field = optionalString(source, "field", child(path, "field"));
        if (aggregation == MetricAggregation.COUNT && field != null) {
            throw error(MetricErrorCode.DSL_VALUE_BRANCH_INVALID, child(path, "field"), "COUNT forbids field");
        }
        if (aggregation != MetricAggregation.COUNT && field == null) {
            throw error(MetricErrorCode.DSL_FIELD_REQUIRED, child(path, "field"), "Aggregation field is required");
        }
        MetricFilterDsl filter = source.containsKey("filter")
                ? parseFilter(
                        MetricDslJson.object(source.get("filter"), child(path, "filter")),
                        child(path, "filter"))
                : null;
        return new MetricMeasureDsl(aggregation, field, filter);
    }

    private MetricExpressionDsl parseExpression(Map<String, Object> source, String path) {
        MetricDslJson.rejectUnknown(source, path, Set.of("type", "value"));
        return new MetricExpressionDsl(
                MetricDslJson.enumValue(
                        required(source, "type", path), MetricExpressionType.class, child(path, "type")),
                string(required(source, "value", path), child(path, "value")));
    }

    private MetricOrElseDsl parseOrElse(Map<String, Object> source, String path) {
        MetricDslJson.rejectUnknown(source, path, Set.of("mode", "value"));
        MetricOrElseMode mode = MetricDslJson.enumValue(
                required(source, "mode", path), MetricOrElseMode.class, child(path, "mode"));
        if (mode == MetricOrElseMode.VALUE) {
            return new MetricOrElseDsl(
                    mode,
                    parseNumericLiteral(required(source, "value", path), child(path, "value")));
        }
        if (source.containsKey("value")) {
            throw error(MetricErrorCode.DSL_VALUE_BRANCH_INVALID, child(path, "value"), "Only VALUE accepts a value");
        }
        return new MetricOrElseDsl(mode, null);
    }

    private MetricFilterDsl parseFilter(Map<String, Object> source, String path) {
        if (source.size() != 1) {
            throw error(MetricErrorCode.DSL_VALUE_BRANCH_INVALID, path, "Filter must contain exactly one operator");
        }
        Map.Entry<String, Object> entry = source.entrySet().iterator().next();
        return switch (entry.getKey()) {
            case "eq" -> parseComparison(MetricFilterOperator.EQ, entry.getValue(), path);
            case "ne" -> parseComparison(MetricFilterOperator.NE, entry.getValue(), path);
            case "gt" -> parseComparison(MetricFilterOperator.GT, entry.getValue(), path);
            case "ge" -> parseComparison(MetricFilterOperator.GE, entry.getValue(), path);
            case "lt" -> parseComparison(MetricFilterOperator.LT, entry.getValue(), path);
            case "le" -> parseComparison(MetricFilterOperator.LE, entry.getValue(), path);
            case "in" -> parseSet(MetricFilterOperator.IN, entry.getValue(), path);
            case "notIn" -> parseSet(MetricFilterOperator.NOT_IN, entry.getValue(), path);
            case "isNull" -> new NullMetricFilterDsl(
                    MetricFilterOperator.IS_NULL, string(entry.getValue(), child(path, "isNull")));
            case "isNotNull" -> new NullMetricFilterDsl(
                    MetricFilterOperator.IS_NOT_NULL, string(entry.getValue(), child(path, "isNotNull")));
            case "and" -> parseLogical(MetricFilterOperator.AND, entry.getValue(), path);
            case "or" -> parseLogical(MetricFilterOperator.OR, entry.getValue(), path);
            default -> throw error(
                    MetricErrorCode.DSL_VALUE_INVALID,
                    child(path, entry.getKey()),
                    "Unsupported filter operator");
        };
    }

    private ComparisonMetricFilterDsl parseComparison(MetricFilterOperator operator, Object value, String path) {
        String operatorPath = child(path, operatorName(operator));
        Map.Entry<String, Object> entry = singleEntry(MetricDslJson.object(value, operatorPath), operatorPath);
        return new ComparisonMetricFilterDsl(
                operator,
                entry.getKey(),
                parseLiteral(entry.getValue(), child(operatorPath, entry.getKey())));
    }

    private SetMetricFilterDsl parseSet(MetricFilterOperator operator, Object value, String path) {
        String operatorPath = child(path, operatorName(operator));
        Map.Entry<String, Object> entry = singleEntry(MetricDslJson.object(value, operatorPath), operatorPath);
        List<Object> source = MetricDslJson.array(entry.getValue(), child(operatorPath, entry.getKey()));
        if (source.isEmpty()) {
            throw error(
                    MetricErrorCode.DSL_VALUE_INVALID,
                    child(operatorPath, entry.getKey()),
                    "Set must not be empty");
        }
        String valuePath = child(operatorPath, entry.getKey());
        List<MetricLiteralDsl> values = new ArrayList<>(source.size());
        for (int index = 0; index < source.size(); index++) {
            values.add(parseLiteral(source.get(index), child(valuePath, Integer.toString(index))));
        }
        return new SetMetricFilterDsl(operator, entry.getKey(), values);
    }

    private LogicalMetricFilterDsl parseLogical(MetricFilterOperator operator, Object value, String path) {
        String operatorPath = child(path, operatorName(operator));
        List<Object> source = MetricDslJson.array(value, operatorPath);
        if (source.size() < 2) {
            throw error(
                    MetricErrorCode.DSL_VALUE_INVALID,
                    operatorPath,
                    "Logical filter requires at least two operands");
        }
        List<MetricFilterDsl> operands = new ArrayList<>(source.size());
        for (int index = 0; index < source.size(); index++) {
            String operandPath = child(operatorPath, Integer.toString(index));
            operands.add(parseFilter(MetricDslJson.object(source.get(index), operandPath), operandPath));
        }
        return new LogicalMetricFilterDsl(operator, operands);
    }

    private MetricLiteralDsl parseLiteral(Object value, String path) {
        if (value instanceof String text) {
            return new StringMetricLiteralDsl(text);
        }
        if (value instanceof Boolean bool) {
            return new BooleanMetricLiteralDsl(bool);
        }
        return parseNumericLiteral(value, path);
    }

    private MetricNumericLiteralDsl parseNumericLiteral(Object value, String path) {
        if (value instanceof BigInteger integer) {
            return new IntegralMetricLiteralDsl(integer);
        }
        if (value instanceof BigDecimal decimal) {
            return new DecimalMetricLiteralDsl(decimal);
        }
        throw error(MetricErrorCode.DSL_FIELD_TYPE_INVALID, path, "Expected numeric literal");
    }

    private void validateParametersAndRowSelection(MetricDefinitionSpec metric, boolean factBased) {
        if (!factBased && !metric.parameters().isEmpty()) {
            throw error(
                    MetricErrorCode.DSL_VALUE_BRANCH_INVALID,
                    "/metric/parameters",
                    "Derived metric forbids parameters");
        }
        if (!factBased && metric.rowSelection() != null) {
            throw error(
                    MetricErrorCode.DSL_VALUE_BRANCH_INVALID,
                    "/metric/rowSelection",
                    "Derived metric forbids rowSelection");
        }
        metric.parameters().forEach((name, parameter) -> {
            String path = child("/metric/parameters", name);
            validateIdentifier(name, 64, path);
            if (parameter.valueType() != MetricValueType.INTEGER) {
                throw error(
                        MetricErrorCode.DSL_VALUE_INVALID,
                        child(path, "valueType"),
                        "Only INTEGER parameters are supported");
            }
            if (parameter.maximum() < parameter.minimum()) {
                throw error(MetricErrorCode.DSL_VALUE_INVALID, path, "Invalid parameter range");
            }
        });

        Set<String> referencedParameters = new LinkedHashSet<>();
        if (metric.rowSelection() != null) {
            validateRowSelection(metric.rowSelection(), metric.parameters(), referencedParameters);
        }
        metric.parameters().keySet().stream()
                .filter(name -> !referencedParameters.contains(name))
                .findFirst()
                .ifPresent(name -> {
                    throw error(
                            MetricErrorCode.METRIC_PARAMETER_UNUSED,
                            child("/metric/parameters", name),
                            "Declared parameter is not referenced");
                });
    }

    private void validateRowSelection(MetricRowSelectionDsl rowSelection,
                                      Map<String, MetricQueryParameterDefinitionDsl> parameters,
                                      Set<String> referencedParameters) {
        String path = "/metric/rowSelection";
        if (rowSelection.filter() != null) {
            validateFilter(rowSelection.filter(), child(path, "filter"));
        }
        String orderByPath = child(path, "orderBy");
        if (rowSelection.orderBy().isEmpty()) {
            throw error(MetricErrorCode.METRIC_ROW_SELECTION_INVALID, orderByPath, "orderBy must not be empty");
        }
        Set<String> orderFields = new LinkedHashSet<>();
        for (int index = 0; index < rowSelection.orderBy().size(); index++) {
            MetricOrderByDsl order = rowSelection.orderBy().get(index);
            validateFieldName(order.field(), child(child(orderByPath, Integer.toString(index)), "field"));
            if (!orderFields.add(order.field())) {
                throw error(
                        MetricErrorCode.METRIC_ROW_SELECTION_INVALID,
                        orderByPath,
                        "orderBy fields must be unique");
            }
        }

        MetricLimitDsl limit = rowSelection.limit();
        String limitPath = child(path, "limit");
        if (limit.value() != null && limit.value() < 1) {
            throw error(
                    MetricErrorCode.METRIC_ROW_SELECTION_INVALID,
                    child(limitPath, "value"),
                    "Limit must be positive");
        }
        if (limit.parameter() != null) {
            validateIdentifier(limit.parameter(), 64, child(limitPath, "parameter"));
            if (!parameters.containsKey(limit.parameter())) {
                throw error(
                        MetricErrorCode.METRIC_ROW_SELECTION_INVALID,
                        child(limitPath, "parameter"),
                        "Limit parameter is not declared");
            }
            referencedParameters.add(limit.parameter());
        }
    }

    private void validateValueShape(MetricDefinitionSpec metric, boolean factBased) {
        List<MetricValueDsl> values;
        if (metric.valueShape() == MetricValueShape.SCALAR) {
            if (metric.value() == null || !metric.fields().isEmpty()) {
                throw error(MetricErrorCode.DSL_VALUE_BRANCH_INVALID, "/metric", "SCALAR requires only value");
            }
            values = List.of(metric.value());
        } else {
            if (metric.value() != null || metric.fields().isEmpty()) {
                throw error(MetricErrorCode.DSL_VALUE_BRANCH_INVALID, "/metric", "FIELD_SET requires only fields");
            }
            values = List.copyOf(metric.fields().values());
        }
        long measureCount = values.stream().filter(value -> value.measure() != null).count();
        if (factBased && measureCount == 0) {
            throw error(MetricErrorCode.DSL_VALUE_BRANCH_INVALID, "/metric", "Fact-based metric requires a measure");
        }
        if (!factBased && measureCount > 0) {
            throw error(MetricErrorCode.DSL_VALUE_BRANCH_INVALID, "/metric", "Derived metric forbids measures");
        }
        if (metric.valueShape() == MetricValueShape.SCALAR && factBased && metric.value().measure() == null) {
            throw error(
                    MetricErrorCode.DSL_VALUE_BRANCH_INVALID,
                    "/metric/value",
                    "Fact-based SCALAR requires a measure");
        }
    }

    private void validateValue(MetricValueDsl value, String path) {
        if (value.valueType() == MetricValueType.DECIMAL) {
            if (value.scale() == null) {
                throw error(MetricErrorCode.DSL_FIELD_REQUIRED, child(path, "scale"), "Scale is required");
            }
            if (value.scale() < 4 || value.scale() > 6) {
                throw error(MetricErrorCode.DSL_VALUE_INVALID, child(path, "scale"), "Scale must be between 4 and 6");
            }
            if (value.roundingMode() == null) {
                throw error(
                        MetricErrorCode.DSL_FIELD_REQUIRED,
                        child(path, "roundingMode"),
                        "Rounding mode is required");
            }
            if (value.roundingMode() != RoundingMode.HALF_UP) {
                throw error(
                        MetricErrorCode.DSL_VALUE_INVALID,
                        child(path, "roundingMode"),
                        "Only HALF_UP is supported");
            }
        } else if (value.scale() != null || value.roundingMode() != null) {
            throw error(MetricErrorCode.DSL_VALUE_BRANCH_INVALID, path, "Non-decimal value forbids precision fields");
        }
        if ((value.measure() == null) == (value.expression() == null)) {
            throw error(MetricErrorCode.DSL_VALUE_BRANCH_INVALID, path, "Exactly one calculation branch is required");
        }
        if (value.measure() != null) {
            validateMeasure(value.measure(), child(path, "measure"));
        } else if (value.expression().value().isBlank()) {
            throw error(
                    MetricErrorCode.DSL_VALUE_INVALID,
                    child(child(path, "expression"), "value"),
                    "Expression must not be blank");
        }
        validateOrElse(value.orElse(), value.valueType(), child(path, "orElse"));
    }

    private void validateMeasure(MetricMeasureDsl measure, String path) {
        if (measure.aggregation() == MetricAggregation.COUNT) {
            if (measure.field() != null) {
                throw error(
                        MetricErrorCode.DSL_VALUE_BRANCH_INVALID,
                        child(path, "field"),
                        "COUNT forbids field");
            }
        } else {
            if (measure.field() == null) {
                throw error(
                        MetricErrorCode.DSL_FIELD_REQUIRED,
                        child(path, "field"),
                        "Aggregation field is required");
            }
            validateFieldName(measure.field(), child(path, "field"));
        }
        if (measure.filter() != null) {
            validateFilter(measure.filter(), child(path, "filter"));
        }
    }

    private void validateOrElse(MetricOrElseDsl orElse, MetricValueType valueType, String path) {
        MetricNumericLiteralDsl fallbackValue = orElse.value();
        if (orElse.mode() != MetricOrElseMode.VALUE) {
            if (fallbackValue != null) {
                throw error(
                        MetricErrorCode.DSL_VALUE_BRANCH_INVALID,
                        child(path, "value"),
                        "Only VALUE accepts a value");
            }
            return;
        }
        if (fallbackValue == null) {
            throw error(MetricErrorCode.DSL_FIELD_REQUIRED, child(path, "value"), "Fallback value is required");
        }
        if (valueType == MetricValueType.DECIMAL) {
            return;
        }
        BigInteger integralValue;
        try {
            integralValue = fallbackValue instanceof IntegralMetricLiteralDsl integral
                    ? integral.value()
                    : ((DecimalMetricLiteralDsl) fallbackValue).value().toBigIntegerExact();
        } catch (ArithmeticException exception) {
            throw error(
                    MetricErrorCode.DSL_FIELD_TYPE_INVALID,
                    child(path, "value"),
                    "Fallback value does not fit valueType");
        }
        int maxBitLength = valueType == MetricValueType.INTEGER ? Integer.SIZE - 1 : Long.SIZE - 1;
        if (integralValue.bitLength() > maxBitLength) {
            throw error(
                    MetricErrorCode.DSL_FIELD_TYPE_INVALID,
                    child(path, "value"),
                    "Fallback value does not fit valueType");
        }
    }

    private void validateFilter(MetricFilterDsl filter, String path) {
        if (filter instanceof ComparisonMetricFilterDsl comparison) {
            if (comparison.operator() == MetricFilterOperator.IN
                    || comparison.operator() == MetricFilterOperator.NOT_IN
                    || comparison.operator() == MetricFilterOperator.IS_NULL
                    || comparison.operator() == MetricFilterOperator.IS_NOT_NULL
                    || comparison.operator() == MetricFilterOperator.AND
                    || comparison.operator() == MetricFilterOperator.OR) {
                throw error(MetricErrorCode.DSL_VALUE_BRANCH_INVALID, path, "Invalid comparison operator");
            }
            validateFieldReference(comparison.fieldRef(), child(path, operatorName(comparison.operator())));
            return;
        }
        if (filter instanceof SetMetricFilterDsl set) {
            if (set.operator() != MetricFilterOperator.IN && set.operator() != MetricFilterOperator.NOT_IN) {
                throw error(MetricErrorCode.DSL_VALUE_BRANCH_INVALID, path, "Invalid set operator");
            }
            validateFieldReference(set.fieldRef(), child(path, operatorName(set.operator())));
            if (set.values().isEmpty()) {
                throw error(MetricErrorCode.DSL_VALUE_INVALID, path, "Set must not be empty");
            }
            MetricLiteralDsl first = set.values().getFirst();
            boolean numeric = first instanceof MetricNumericLiteralDsl;
            for (int index = 1; index < set.values().size(); index++) {
                MetricLiteralDsl current = set.values().get(index);
                boolean sameType = numeric
                        ? current instanceof MetricNumericLiteralDsl
                        : current.getClass() == first.getClass();
                if (!sameType) {
                    String valuesPath = child(child(path, operatorName(set.operator())), set.fieldRef());
                    throw error(
                            MetricErrorCode.DSL_FIELD_TYPE_INVALID,
                            child(valuesPath, Integer.toString(index)),
                            "Set values must use one literal type");
                }
            }
            return;
        }
        if (filter instanceof NullMetricFilterDsl nullFilter) {
            if (nullFilter.operator() != MetricFilterOperator.IS_NULL
                    && nullFilter.operator() != MetricFilterOperator.IS_NOT_NULL) {
                throw error(MetricErrorCode.DSL_VALUE_BRANCH_INVALID, path, "Invalid null operator");
            }
            validateFieldReference(nullFilter.fieldRef(), child(path, operatorName(nullFilter.operator())));
            return;
        }
        LogicalMetricFilterDsl logical = (LogicalMetricFilterDsl) filter;
        if (logical.operator() != MetricFilterOperator.AND && logical.operator() != MetricFilterOperator.OR) {
            throw error(MetricErrorCode.DSL_VALUE_BRANCH_INVALID, path, "Invalid logical operator");
        }
        if (logical.operands().size() < 2) {
            throw error(MetricErrorCode.DSL_VALUE_INVALID, path, "Logical filter requires at least two operands");
        }
        for (int index = 0; index < logical.operands().size(); index++) {
            validateFilter(logical.operands().get(index), child(path, Integer.toString(index)));
        }
    }

    private Map<String, Object> toCanonicalMap(MetricDefinitionDsl definition) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("schemaVersion", definition.schemaVersion());
        root.put("metric", toCanonicalMetric(definition.metric()));
        return root;
    }

    private Map<String, Object> toCanonicalMetric(MetricDefinitionSpec metric) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", metric.code());
        result.put("valueShape", metric.valueShape().name());
        putIfNotNull(result, "fact", metric.fact());
        if (!metric.joins().isEmpty()) {
            result.put("joins", metric.joins().stream()
                    .sorted(Comparator.comparing(MetricJoinDsl::alias))
                    .map(this::toCanonicalJoin)
                    .toList());
        }
        result.put("subject", toCanonicalSubject(metric.subject()));
        if (metric.time() != null) {
            result.put("time", Map.of("field", metric.time().field()));
        }
        result.put("dimensions", metric.dimensions().stream().sorted().toList());
        if (!metric.parameters().isEmpty()) {
            Map<String, Object> parameters = new LinkedHashMap<>();
            metric.parameters().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> parameters.put(
                    entry.getKey(),
                    toCanonicalParameter(entry.getValue())));
            result.put("parameters", parameters);
        }
        if (metric.rowSelection() != null) {
            result.put("rowSelection", toCanonicalRowSelection(metric.rowSelection()));
        }
        if (metric.value() != null) {
            result.put("value", toCanonicalValue(metric.value()));
        }
        if (!metric.fields().isEmpty()) {
            Map<String, Object> fields = new LinkedHashMap<>();
            metric.fields().entrySet().stream().sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> fields.put(entry.getKey(), toCanonicalValue(entry.getValue())));
            result.put("fields", fields);
        }
        return result;
    }

    private Map<String, Object> toCanonicalParameter(MetricQueryParameterDefinitionDsl parameter) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("valueType", parameter.valueType().name());
        result.put("minimum", parameter.minimum());
        result.put("maximum", parameter.maximum());
        return result;
    }

    private Map<String, Object> toCanonicalJoin(MetricJoinDsl join) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("alias", join.alias());
        result.put("fact", join.fact());
        result.put("joinType", join.joinType().name());
        result.put("cardinality", join.cardinality().name());
        result.put("on", join.on().stream()
                .sorted(Comparator.comparing(MetricJoinOnDsl::primaryField).thenComparing(MetricJoinOnDsl::joinField))
                .map(item -> orderedMap("primaryField", item.primaryField(), "joinField", item.joinField()))
                .toList());
        return result;
    }

    private Map<String, Object> toCanonicalSubject(MetricSubjectDsl subject) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", subject.type());
        putIfNotNull(result, "field", subject.field());
        return result;
    }

    private Map<String, Object> toCanonicalValue(MetricValueDsl value) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("valueType", value.valueType().name());
        if (value.valueType() == MetricValueType.DECIMAL) {
            result.put("scale", value.scale());
            result.put("roundingMode", value.roundingMode().name());
        }
        if (value.measure() != null) {
            result.put("measure", toCanonicalMeasure(value.measure()));
        } else {
            result.put(
                    "expression",
                    orderedMap(
                            "type",
                            value.expression().type().name(),
                            "value",
                            value.expression().value()));
        }
        result.put("orElse", toCanonicalOrElse(value.orElse()));
        return result;
    }

    private Map<String, Object> toCanonicalMeasure(MetricMeasureDsl measure) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("aggregation", measure.aggregation().name());
        putIfNotNull(result, "field", measure.field());
        if (measure.filter() != null) {
            result.put("filter", toCanonicalFilter(measure.filter()));
        }
        return result;
    }

    private Map<String, Object> toCanonicalRowSelection(MetricRowSelectionDsl rowSelection) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (rowSelection.filter() != null) {
            result.put("filter", toCanonicalFilter(rowSelection.filter()));
        }
        result.put("orderBy", rowSelection.orderBy().stream()
                .map(order -> orderedMap("field", order.field(), "direction", order.direction().name()))
                .toList());
        MetricLimitDsl limit = rowSelection.limit();
        result.put("limit", limit.value() != null
                ? Map.of("value", limit.value())
                : Map.of("parameter", limit.parameter()));
        return result;
    }

    private Map<String, Object> toCanonicalOrElse(MetricOrElseDsl orElse) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mode", orElse.mode().name());
        if (orElse.value() != null) {
            result.put("value", literalValue(orElse.value()));
        }
        return result;
    }

    private Map<String, Object> toCanonicalFilter(MetricFilterDsl filter) {
        if (filter instanceof ComparisonMetricFilterDsl comparison) {
            return Map.of(
                    operatorName(comparison.operator()),
                    Map.of(comparison.fieldRef(), literalValue(comparison.value())));
        }
        if (filter instanceof SetMetricFilterDsl set) {
            Map<String, Object> valuesByCanonicalJson = new TreeMap<>();
            set.values().forEach(literal -> {
                Object value = literalValue(literal);
                valuesByCanonicalJson.put(MetricDslJson.toJson(value), value);
            });
            List<Object> values = List.copyOf(valuesByCanonicalJson.values());
            return Map.of(operatorName(set.operator()), Map.of(set.fieldRef(), values));
        }
        if (filter instanceof NullMetricFilterDsl nullFilter) {
            return Map.of(operatorName(nullFilter.operator()), nullFilter.fieldRef());
        }
        LogicalMetricFilterDsl logical = (LogicalMetricFilterDsl) filter;
        List<Map<String, Object>> operands = logical.operands().stream()
                .map(this::toCanonicalFilter)
                .sorted(Comparator.comparing(MetricDslJson::toJson))
                .toList();
        return Map.of(operatorName(logical.operator()), operands);
    }

    private Object literalValue(MetricLiteralDsl literal) {
        if (literal instanceof StringMetricLiteralDsl stringLiteral) {
            return stringLiteral.value();
        }
        if (literal instanceof BooleanMetricLiteralDsl booleanLiteral) {
            return booleanLiteral.value();
        }
        if (literal instanceof IntegralMetricLiteralDsl integralLiteral) {
            return integralLiteral.value();
        }
        BigDecimal value = ((DecimalMetricLiteralDsl) literal).value().stripTrailingZeros();
        return value.signum() == 0 ? BigDecimal.ZERO : value;
    }

    private String operatorName(MetricFilterOperator operator) {
        return switch (operator) {
            case EQ -> "eq";
            case NE -> "ne";
            case IN -> "in";
            case NOT_IN -> "notIn";
            case GT -> "gt";
            case GE -> "ge";
            case LT -> "lt";
            case LE -> "le";
            case IS_NULL -> "isNull";
            case IS_NOT_NULL -> "isNotNull";
            case AND -> "and";
            case OR -> "or";
        };
    }

    private Map.Entry<String, Object> singleEntry(Map<String, Object> source, String path) {
        if (source.size() != 1) {
            throw error(MetricErrorCode.DSL_VALUE_BRANCH_INVALID, path, "Expected exactly one field");
        }
        return source.entrySet().iterator().next();
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

    private @Nullable String optionalString(Map<String, Object> source, String field, String path) {
        if (!source.containsKey(field)) {
            return null;
        }
        if (source.get(field) == null) {
            throw error(MetricErrorCode.DSL_FIELD_TYPE_INVALID, path, "Explicit null is not allowed");
        }
        return string(source.get(field), path);
    }

    private void validateIdentifier(String value, int maxLength, String path) {
        if (value.length() > maxLength || !IDENTIFIER.matcher(value).matches() || RESERVED_NAMES.contains(value)) {
            throw error(MetricErrorCode.DSL_IDENTIFIER_INVALID, path, "Invalid identifier");
        }
    }

    private void validateFieldReference(String value, String path) {
        if (value.length() > 128 || !FIELD_REFERENCE.matcher(value).matches()) {
            throw error(MetricErrorCode.DSL_IDENTIFIER_INVALID, path, "Invalid field reference");
        }
    }

    private void validateFieldName(String value, String path) {
        if (value.length() > 64 || !IDENTIFIER.matcher(value).matches()) {
            throw error(MetricErrorCode.DSL_IDENTIFIER_INVALID, path, "Invalid field name");
        }
    }

    private void putIfNotNull(Map<String, Object> target, String key, @Nullable Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    private Map<String, Object> orderedMap(String firstKey, Object firstValue, String secondKey, Object secondValue) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put(firstKey, firstValue);
        result.put(secondKey, secondValue);
        return result;
    }
}
