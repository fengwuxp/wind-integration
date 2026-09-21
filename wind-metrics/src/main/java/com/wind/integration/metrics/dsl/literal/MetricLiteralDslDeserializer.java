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
 * 从既有 {@code {"value": ...}} JSON 形态还原封闭字面量 AST。
 *
 * <p>JSON token 本身已经区分布尔、字符串、整数和十进制数，因此无需新增
 * discriminator。数值直接从 parser 的原始 token 文本构造，避免经过 Double。</p>
 *
 * @author wuxp
 * @since 2026-09-20
 */
public final class MetricLiteralDslDeserializer extends ValueDeserializer<MetricLiteralDsl> {

    @Override
    public MetricLiteralDsl deserialize(JsonParser parser, DeserializationContext context) throws JacksonException {
        if (parser.currentToken() != JsonToken.START_OBJECT) {
            return context.reportInputMismatch(MetricLiteralDsl.class,
                    "Metric literal must be a JSON object");
        }
        Set<String> fields = new HashSet<>();
        MetricLiteralDsl result = null;
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            if (parser.currentToken() != JsonToken.PROPERTY_NAME) {
                return context.reportInputMismatch(MetricLiteralDsl.class,
                        "Metric literal requires property names");
            }
            String field = parser.currentName();
            if (!fields.add(field) || !"value".equals(field)) {
                parser.skipChildren();
                return context.reportInputMismatch(MetricLiteralDsl.class,
                        "Metric literal must contain exactly one value field");
            }
            result = readValue(parser, parser.nextToken(), context);
        }
        if (!fields.contains("value") || result == null) {
            return context.reportInputMismatch(MetricLiteralDsl.class,
                    "Metric literal must contain a non-null value");
        }
        return result;
    }

    private static MetricLiteralDsl readValue(JsonParser parser, JsonToken token,
                                              DeserializationContext context) throws JacksonException {
        return switch (token) {
            case VALUE_TRUE -> new BooleanMetricLiteralDsl(true);
            case VALUE_FALSE -> new BooleanMetricLiteralDsl(false);
            case VALUE_STRING -> new StringMetricLiteralDsl(parser.getText());
            case VALUE_NUMBER_INT -> new IntegralMetricLiteralDsl(new BigInteger(parser.getText()));
            case VALUE_NUMBER_FLOAT -> new DecimalMetricLiteralDsl(new BigDecimal(parser.getText()));
            default -> context.reportInputMismatch(MetricLiteralDsl.class,
                    "Unsupported metric literal value token: %s", token);
        };
    }
}
