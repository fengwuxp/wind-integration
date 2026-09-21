package com.wind.integration.metrics.dsl.literal;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HashSet;
import java.util.Set;

/**
 * 从既有 {@code {"value": ...}} 形态还原数值字面量封闭接口。
 *
 * <p>这里只接受 JSON 数字 token，并从 parser 原文构造 BigInteger/BigDecimal，
 * 使直接以 MetricNumericLiteralDsl 为目标类型的调用方也不经过 Double。</p>
 *
 * @author wuxp
 * @since 2026-09-20
 */
public final class MetricNumericLiteralDslDeserializer extends ValueDeserializer<MetricNumericLiteralDsl> {

    @Override
    public MetricNumericLiteralDsl deserialize(JsonParser parser, DeserializationContext context)
            throws JacksonException {
        if (parser.currentToken() != JsonToken.START_OBJECT) {
            return context.reportInputMismatch(MetricNumericLiteralDsl.class,
                    "Metric numeric literal must be a JSON object");
        }
        Set<String> fields = new HashSet<>();
        MetricNumericLiteralDsl result = null;
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            if (parser.currentToken() != JsonToken.PROPERTY_NAME) {
                return context.reportInputMismatch(MetricNumericLiteralDsl.class,
                        "Metric numeric literal requires property names");
            }
            String field = parser.currentName();
            if (!fields.add(field) || !"value".equals(field)) {
                parser.skipChildren();
                return context.reportInputMismatch(MetricNumericLiteralDsl.class,
                        "Metric numeric literal must contain exactly one value field");
            }
            result = readValue(parser, parser.nextToken(), context);
        }
        if (!fields.contains("value") || result == null) {
            return context.reportInputMismatch(MetricNumericLiteralDsl.class,
                    "Metric numeric literal must contain a non-null numeric value");
        }
        return result;
    }

    private static MetricNumericLiteralDsl readValue(JsonParser parser, JsonToken token,
                                                     DeserializationContext context) throws JacksonException {
        return switch (token) {
            case VALUE_NUMBER_INT -> new IntegralMetricLiteralDsl(new BigInteger(parser.getText()));
            case VALUE_NUMBER_FLOAT -> new DecimalMetricLiteralDsl(new BigDecimal(parser.getText()));
            default -> context.reportInputMismatch(MetricNumericLiteralDsl.class,
                    "Unsupported metric numeric literal value token: %s", token);
        };
    }
}
