package com.wind.integration.metrics.dsl.filter;

import com.wind.integration.metrics.dsl.literal.MetricLiteralDsl;
import com.wind.integration.metrics.dsl.literal.MetricLiteralDslDeserializer;
import com.wind.integration.metrics.enums.MetricFilterOperator;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 从既有无 discriminator 的 JSON 形态还原封闭过滤 AST。
 *
 * <p>过滤分支由 operator 的闭集决定，因此读取时不增加 type/kind 字段，也不
 * 引入宿主侧 Codec。节点字段集合在进入具体 record 前严格校验，未知分支直接
 * 失败。解析过程直接消费 token，保证嵌套的十进制字面量不会先落成 Double。</p>
 *
 * @author wuxp
 * @since 2026-09-20
 */
public final class MetricFilterDslDeserializer extends ValueDeserializer<MetricFilterDsl> {

    private static final Set<String> COMPARISON_FIELDS = Set.of("operator", "fieldRef", "value");

    private static final Set<String> SET_FIELDS = Set.of("operator", "fieldRef", "values");

    private static final Set<String> NULL_FIELDS = Set.of("operator", "fieldRef");

    private static final Set<String> LOGICAL_FIELDS = Set.of("operator", "operands");

    @Override
    public MetricFilterDsl deserialize(JsonParser parser, DeserializationContext context) throws JacksonException {
        requireToken(parser, JsonToken.START_OBJECT, context);
        MetricFilterOperator operator = null;
        String fieldRef = null;
        MetricLiteralDsl value = null;
        List<MetricLiteralDsl> values = null;
        List<MetricFilterDsl> operands = null;
        Set<String> fields = new HashSet<>();

        while (parser.nextToken() != JsonToken.END_OBJECT) {
            if (parser.currentToken() != JsonToken.PROPERTY_NAME) {
                return context.reportInputMismatch(MetricFilterDsl.class,
                        "Metric filter requires property names");
            }
            String field = parser.currentName();
            if (!fields.add(field)) {
                return context.reportInputMismatch(MetricFilterDsl.class,
                        "Duplicate metric filter field: %s", field);
            }
            JsonToken valueToken = parser.nextToken();
            switch (field) {
                case "operator" -> operator = readOperator(parser, valueToken, context);
                case "fieldRef" -> fieldRef = readFieldRef(parser, valueToken, context);
                case "value" -> value = new MetricLiteralDslDeserializer().deserialize(parser, context);
                case "values" -> values = readLiterals(parser, valueToken, context);
                case "operands" -> operands = readOperands(parser, valueToken, context);
                default -> {
                    parser.skipChildren();
                    return context.reportInputMismatch(MetricFilterDsl.class,
                            "Unknown metric filter field: %s", field);
                }
            }
        }
        if (operator == null) {
            return context.reportInputMismatch(MetricFilterDsl.class,
                    "Metric filter requires operator");
        }
        return switch (operator) {
            case EQ, NE, GT, GE, LT, LE -> comparison(operator, fieldRef, value, fields, context);
            case IN, NOT_IN -> set(operator, fieldRef, values, fields, context);
            case IS_NULL, IS_NOT_NULL -> nullFilter(operator, fieldRef, fields, context);
            case AND, OR -> logical(operator, operands, fields, context);
        };
    }

    private static MetricFilterOperator readOperator(JsonParser parser, JsonToken token,
                                                     DeserializationContext context) throws JacksonException {
        if (token != JsonToken.VALUE_STRING) {
            return context.reportInputMismatch(MetricFilterDsl.class,
                    "Metric filter operator must be a string");
        }
        String text = parser.getText();
        try {
            return MetricFilterOperator.valueOf(text);
        } catch (IllegalArgumentException exception) {
            return context.reportInputMismatch(MetricFilterDsl.class,
                    "Unknown metric filter operator: %s", text);
        }
    }

    private static String readFieldRef(JsonParser parser, JsonToken token,
                                       DeserializationContext context) throws JacksonException {
        if (token != JsonToken.VALUE_STRING) {
            return context.reportInputMismatch(MetricFilterDsl.class,
                    "Metric filter fieldRef must be a string");
        }
        return parser.getText();
    }

    private static List<MetricLiteralDsl> readLiterals(JsonParser parser, JsonToken token,
                                                        DeserializationContext context) throws JacksonException {
        requireToken(token, JsonToken.START_ARRAY, context);
        List<MetricLiteralDsl> values = new ArrayList<>();
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            values.add(new MetricLiteralDslDeserializer().deserialize(parser, context));
        }
        return values;
    }

    private static List<MetricFilterDsl> readOperands(JsonParser parser, JsonToken token,
                                                       DeserializationContext context) throws JacksonException {
        requireToken(token, JsonToken.START_ARRAY, context);
        List<MetricFilterDsl> operands = new ArrayList<>();
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            operands.add(new MetricFilterDslDeserializer().deserialize(parser, context));
        }
        return operands;
    }

    private static ComparisonMetricFilterDsl comparison(MetricFilterOperator operator, String fieldRef,
                                                        MetricLiteralDsl value, Set<String> fields,
                                                        DeserializationContext context) throws JacksonException {
        verifyFields(fields, COMPARISON_FIELDS, context);
        return new ComparisonMetricFilterDsl(operator, fieldRef, value);
    }

    private static SetMetricFilterDsl set(MetricFilterOperator operator, String fieldRef,
                                          List<MetricLiteralDsl> values, Set<String> fields,
                                          DeserializationContext context) throws JacksonException {
        verifyFields(fields, SET_FIELDS, context);
        return new SetMetricFilterDsl(operator, fieldRef, values);
    }

    private static NullMetricFilterDsl nullFilter(MetricFilterOperator operator, String fieldRef,
                                                  Set<String> fields, DeserializationContext context)
            throws JacksonException {
        verifyFields(fields, NULL_FIELDS, context);
        return new NullMetricFilterDsl(operator, fieldRef);
    }

    private static LogicalMetricFilterDsl logical(MetricFilterOperator operator, List<MetricFilterDsl> operands,
                                                  Set<String> fields, DeserializationContext context)
            throws JacksonException {
        verifyFields(fields, LOGICAL_FIELDS, context);
        return new LogicalMetricFilterDsl(operator, operands);
    }

    private static void verifyFields(Set<String> actualFields, Set<String> expectedFields,
                                     DeserializationContext context) throws JacksonException {
        if (!actualFields.equals(expectedFields)) {
            context.reportInputMismatch(MetricFilterDsl.class,
                    "Unexpected metric filter fields; expected %s but got %s",
                    expectedFields, actualFields);
        }
    }

    private static void requireToken(JsonParser parser, JsonToken expected,
                                     DeserializationContext context) throws JacksonException {
        requireToken(parser.currentToken(), expected, context);
    }

    private static void requireToken(JsonToken actual, JsonToken expected,
                                     DeserializationContext context) throws JacksonException {
        if (actual != expected) {
            context.reportInputMismatch(MetricFilterDsl.class,
                    "Expected %s for metric filter but got %s", expected, actual);
        }
    }
}
