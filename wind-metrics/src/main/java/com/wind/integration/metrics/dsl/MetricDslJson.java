package com.wind.integration.metrics.dsl;

import com.wind.integration.metrics.MetricValidationException;
import com.wind.integration.metrics.enums.MetricErrorCode;
import com.wind.jackson.WindJson;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.StreamWriteFeature;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Metric DSL 共用的严格 JSON 读取、字段校验和规范序列化原语。
 *
 * @author wuxp
 * @date 2026-07-21 17:51
 */
final class MetricDslJson {

    private static final JsonFactory STRICT_JSON_FACTORY = new JsonFactory();

    private static final JsonMapper JSON_MAPPER = WindJson.getJsonMapper()
            .rebuild()
            .enable(StreamWriteFeature.WRITE_BIGDECIMAL_AS_PLAIN)
            .build();

    private MetricDslJson() {
    }

    static Map<String, Object> parseRootObject(String json) {
        if (json == null || json.isBlank()) {
            throw error(MetricErrorCode.DSL_JSON_INVALID, "", "JSON must not be blank");
        }
        try (JsonParser parser = STRICT_JSON_FACTORY.createParser(json)) {
            parser.nextToken();
            Map<String, Object> result = parseRootObject(parser);
            if (parser.nextToken() != null) {
                throw error(MetricErrorCode.DSL_JSON_INVALID, "", "Unexpected trailing JSON content");
            }
            return result;
        } catch (MetricValidationException exception) {
            throw exception;
        } catch (JacksonException exception) {
            throw new MetricValidationException(
                    MetricErrorCode.DSL_JSON_INVALID, "", "Invalid JSON", exception);
        }
    }

    static Map<String, Object> parseRootObject(JsonParser parser) {
        try {
            Object value = readValue(parser, "");
            if (!(value instanceof Map<?, ?> map)) {
                throw error(MetricErrorCode.DSL_ROOT_NOT_OBJECT, "", "JSON root must be an object");
            }
            return castMap(map);
        } catch (MetricValidationException exception) {
            throw exception;
        } catch (JacksonException exception) {
            throw new MetricValidationException(
                    MetricErrorCode.DSL_JSON_INVALID, "", "Invalid JSON", exception);
        }
    }

    static String toJson(Object value) {
        return JSON_MAPPER.writeValueAsString(value);
    }

    static void rejectUnknown(Map<String, Object> object, String path, Set<String> allowedFields) {
        for (String field : object.keySet()) {
            if (!allowedFields.contains(field)) {
                throw error(MetricErrorCode.DSL_FIELD_UNKNOWN, child(path, field), "Unknown field");
            }
        }
    }

    static Object required(Map<String, Object> object, String field, String path) {
        if (!object.containsKey(field) || object.get(field) == null) {
            throw error(MetricErrorCode.DSL_FIELD_REQUIRED, child(path, field), "Required field is missing");
        }
        return object.get(field);
    }

    static @Nullable Object optionalValue(Map<String, Object> object, String field, String path) {
        if (!object.containsKey(field)) {
            return null;
        }
        if (object.get(field) == null) {
            throw error(MetricErrorCode.DSL_FIELD_TYPE_INVALID, path, "Explicit null is not allowed");
        }
        return object.get(field);
    }

    static Map<String, Object> object(Object value, String path) {
        if (!(value instanceof Map<?, ?> map)) {
            throw error(MetricErrorCode.DSL_FIELD_TYPE_INVALID, path, "Expected object");
        }
        return castMap(map);
    }

    static List<Object> array(Object value, String path) {
        if (!(value instanceof List<?> list)) {
            throw error(MetricErrorCode.DSL_FIELD_TYPE_INVALID, path, "Expected array");
        }
        return new ArrayList<>(list);
    }

    static String string(Object value, String path) {
        if (!(value instanceof String text) || text.isBlank()) {
            throw error(MetricErrorCode.DSL_FIELD_TYPE_INVALID, path, "Expected non-blank string");
        }
        return text;
    }

    static int integer(Object value, String path) {
        try {
            if (value instanceof BigInteger integer) {
                return integer.intValueExact();
            }
            if (value instanceof BigDecimal decimal) {
                return decimal.intValueExact();
            }
        } catch (ArithmeticException exception) {
            throw error(MetricErrorCode.DSL_FIELD_TYPE_INVALID, path, "Expected exact integer");
        }
        throw error(MetricErrorCode.DSL_FIELD_TYPE_INVALID, path, "Expected integer");
    }

    static <E extends Enum<E>> E enumValue(Object value, Class<E> enumType, String path) {
        String name = string(value, path);
        try {
            return Enum.valueOf(enumType, name);
        } catch (IllegalArgumentException exception) {
            throw error(MetricErrorCode.DSL_VALUE_INVALID, path, "Unsupported enum value");
        }
    }

    static String child(String path, String field) {
        return path + "/" + field.replace("~", "~0").replace("/", "~1");
    }

    static MetricValidationException error(MetricErrorCode code, String path, String message) {
        return new MetricValidationException(code, path, message);
    }

    private static Object readValue(JsonParser parser, String path) {
        return switch (parser.currentToken()) {
            case START_OBJECT -> readObject(parser, path);
            case START_ARRAY -> readArray(parser, path);
            case VALUE_NULL -> null;
            case VALUE_STRING -> parser.getText();
            case VALUE_NUMBER_INT -> parser.getBigIntegerValue();
            case VALUE_NUMBER_FLOAT -> readDecimal(parser, path);
            case VALUE_TRUE -> true;
            case VALUE_FALSE -> false;
            default -> throw error(MetricErrorCode.DSL_JSON_INVALID, path, "Unsupported JSON token");
        };
    }

    private static Map<String, Object> readObject(JsonParser parser, String path) {
        Map<String, Object> result = new LinkedHashMap<>();
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            if (parser.currentToken() != JsonToken.PROPERTY_NAME) {
                throw error(MetricErrorCode.DSL_JSON_INVALID, path, "Expected quoted field name");
            }
            String field = parser.currentName();
            String fieldPath = child(path, field);
            if (result.containsKey(field)) {
                throw error(MetricErrorCode.DSL_FIELD_DUPLICATED, fieldPath, "Duplicate field");
            }
            parser.nextToken();
            result.put(field, readValue(parser, fieldPath));
        }
        return result;
    }

    private static List<Object> readArray(JsonParser parser, String path) {
        List<Object> result = new ArrayList<>();
        int index = 0;
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            result.add(readValue(parser, child(path, Integer.toString(index++))));
        }
        return result;
    }

    private static BigDecimal readDecimal(JsonParser parser, String path) {
        String literal = parser.getText();
        if (literal.indexOf('e') >= 0 || literal.indexOf('E') >= 0) {
            throw error(MetricErrorCode.DSL_JSON_INVALID, path, "Unsupported numeric literal");
        }
        return parser.getDecimalValue();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Map<?, ?> map) {
        return (Map<String, Object>) map;
    }
}
