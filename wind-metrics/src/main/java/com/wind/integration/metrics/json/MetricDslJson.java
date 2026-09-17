package com.wind.integration.metrics.json;

import com.wind.integration.metrics.MetricValidationException;
import com.wind.integration.metrics.enums.MetricErrorCode;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.wind.integration.metrics.json.MetricJsonSupport.child;

/**
 * Definition 和 Plan DSL 的字段、类型及枚举校验；JSON 读取由共用协议支持承接。
 *
 * @author wuxp
 * @since 2026-09-15
 */
final class MetricDslJson {

    private MetricDslJson() {
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

    static MetricValidationException error(MetricErrorCode code, String path, String message) {
        return new MetricValidationException(code, path, message);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Map<?, ?> map) {
        return (Map<String, Object>) map;
    }
}
