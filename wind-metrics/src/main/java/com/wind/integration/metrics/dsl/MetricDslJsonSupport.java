package com.wind.integration.metrics.dsl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONFactory;
import com.alibaba.fastjson2.JSONException;
import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.JSONWriter;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class MetricDslJsonSupport {

    private MetricDslJsonSupport() {
    }

    static Map<String, Object> parseRootObject(String json) {
        if (json == null || json.isBlank()) {
            throw error(MetricDslErrorCode.DSL_JSON_INVALID, "", "JSON must not be blank");
        }
        JSONReader.Context context = JSONFactory.createReadContext(JSONReader.Feature.DisableSingleQuote);
        try (JSONReader reader = JSONReader.of(json, context)) {
            Object value = readValue(reader, "");
            if (!reader.isEnd()) {
                throw error(MetricDslErrorCode.DSL_JSON_INVALID, "", "Unexpected trailing JSON content");
            }
            if (!(value instanceof Map<?, ?> map)) {
                throw error(MetricDslErrorCode.DSL_ROOT_NOT_OBJECT, "", "JSON root must be an object");
            }
            return castMap(map);
        } catch (MetricDslValidationException exception) {
            throw exception;
        } catch (JSONException exception) {
            throw new MetricDslValidationException(
                    MetricDslErrorCode.DSL_JSON_INVALID, "", "Invalid JSON", exception);
        }
    }

    static String toJson(Object value) {
        return JSON.toJSONString(value, JSONWriter.Feature.WriteBigDecimalAsPlain);
    }

    static void rejectUnknown(Map<String, Object> object, String path, Set<String> allowedFields) {
        for (String field : object.keySet()) {
            if (!allowedFields.contains(field)) {
                throw error(MetricDslErrorCode.DSL_FIELD_UNKNOWN, child(path, field), "Unknown field");
            }
        }
    }

    static Object required(Map<String, Object> object, String field, String path) {
        if (!object.containsKey(field) || object.get(field) == null) {
            throw error(MetricDslErrorCode.DSL_FIELD_REQUIRED, child(path, field), "Required field is missing");
        }
        return object.get(field);
    }

    static Map<String, Object> object(Object value, String path) {
        if (!(value instanceof Map<?, ?> map)) {
            throw error(MetricDslErrorCode.DSL_FIELD_TYPE_INVALID, path, "Expected object");
        }
        return castMap(map);
    }

    static List<Object> array(Object value, String path) {
        if (!(value instanceof List<?> list)) {
            throw error(MetricDslErrorCode.DSL_FIELD_TYPE_INVALID, path, "Expected array");
        }
        return new ArrayList<>(list);
    }

    static String string(Object value, String path) {
        if (!(value instanceof String text) || text.isBlank()) {
            throw error(MetricDslErrorCode.DSL_FIELD_TYPE_INVALID, path, "Expected non-blank string");
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
            throw error(MetricDslErrorCode.DSL_FIELD_TYPE_INVALID, path, "Expected exact integer");
        }
        throw error(MetricDslErrorCode.DSL_FIELD_TYPE_INVALID, path, "Expected integer");
    }

    static <E extends Enum<E>> E enumValue(Object value, Class<E> enumType, String path) {
        String name = string(value, path);
        try {
            return Enum.valueOf(enumType, name);
        } catch (IllegalArgumentException exception) {
            throw error(MetricDslErrorCode.DSL_VALUE_INVALID, path, "Unsupported enum value");
        }
    }

    static String child(String path, String field) {
        return path + "/" + field.replace("~", "~0").replace("/", "~1");
    }

    static MetricDslValidationException error(MetricDslErrorCode code, String path, String message) {
        return new MetricDslValidationException(code, path, message);
    }

    private static Object readValue(JSONReader reader, String path) {
        if (reader.nextIfObjectStart()) {
            Map<String, Object> result = new LinkedHashMap<>();
            while (!reader.nextIfObjectEnd()) {
                String field = reader.readFieldName();
                String fieldPath = child(path, field);
                if (result.containsKey(field)) {
                    throw error(MetricDslErrorCode.DSL_FIELD_DUPLICATED, fieldPath, "Duplicate field");
                }
                result.put(field, readValue(reader, fieldPath));
            }
            return result;
        }
        if (reader.nextIfArrayStart()) {
            List<Object> result = new ArrayList<>();
            int index = 0;
            while (!reader.nextIfArrayEnd()) {
                result.add(readValue(reader, child(path, Integer.toString(index++))));
            }
            return result;
        }
        if (reader.nextIfNull()) {
            return null;
        }
        if (reader.isString()) {
            return reader.readString();
        }
        if (reader.isNumber()) {
            Number number = reader.readNumber();
            if (number instanceof BigInteger integer) {
                return integer;
            }
            if (number instanceof BigDecimal decimal) {
                return decimal;
            }
            if (number instanceof Byte || number instanceof Short || number instanceof Integer || number instanceof Long) {
                return BigInteger.valueOf(number.longValue());
            }
            throw error(MetricDslErrorCode.DSL_JSON_INVALID, path, "Unsupported numeric literal");
        }
        return reader.readBool();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Map<?, ?> map) {
        return (Map<String, Object>) map;
    }
}
