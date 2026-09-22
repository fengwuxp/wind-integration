package com.wind.integration.metrics.json;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 读取原始 JSON 值，数值保留精度，不从字符串推断类型。
 *
 * @author wuxp
 * @since 2026-09-22
 */
public final class MetricValuePayloadJsonDeserializer extends ValueDeserializer<Object> {

    @Override
    public Object deserialize(JsonParser parser, DeserializationContext context) {
        return switch (parser.currentToken()) {
            case VALUE_NUMBER_INT, VALUE_NUMBER_FLOAT -> parser.getNumberValueExact();
            case VALUE_STRING -> parser.getString();
            case VALUE_TRUE -> true;
            case VALUE_FALSE -> false;
            case VALUE_NULL -> null;
            case START_OBJECT -> {
                Map<String, Object> values = new LinkedHashMap<>();
                while (parser.nextToken() != JsonToken.END_OBJECT) {
                    String name = parser.currentName();
                    parser.nextToken();
                    values.put(name, deserialize(parser, context));
                }
                yield values;
            }
            case START_ARRAY -> {
                List<Object> values = new ArrayList<>();
                while (parser.nextToken() != JsonToken.END_ARRAY) {
                    values.add(deserialize(parser, context));
                }
                yield values;
            }
            case null, default -> context.reportInputMismatch(Object.class, "Expected a JSON metric payload");
        };
    }
}
