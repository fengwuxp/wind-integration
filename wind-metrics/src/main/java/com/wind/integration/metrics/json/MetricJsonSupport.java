package com.wind.integration.metrics.json;

import com.wind.integration.metrics.MetricValidationException;
import com.wind.integration.metrics.enums.MetricErrorCode;
import com.wind.jackson.WindJson;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.StreamWriteFeature;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 指标查询协议和 DSL 共用的严格 JSON 读取、路径与规范序列化原语。
 *
 * <p>不决定指标定义、查询路线或物化行为。既有 DSL_* JSON 错误码继续保持协议兼容。</p>
 *
 * @author wuxp
 * @date 2026-07-21 17:51
 */
public final class MetricJsonSupport {

    private static final JsonFactory STRICT_JSON_FACTORY = new JsonFactory();

    private static final JsonMapper JSON_MAPPER = WindJson.getJsonMapper()
            .rebuild()
            .enable(StreamWriteFeature.WRITE_BIGDECIMAL_AS_PLAIN)
            .build();

    private MetricJsonSupport() {
    }

    /**
     * 读取一个完整 JSON 对象，拒绝重复键、非法数字和尾随内容。
     *
     * @param json JSON 文本
     * @return 保留字段顺序的对象
     */
    public static Map<String, Object> parseRootObject(String json) {
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

    /**
     * 从当前对象令牌读取内容，不越过对象边界，供直接和嵌套绑定共用。
     *
     * @param parser 已定位到当前值的解析器
     * @return 保留字段顺序的对象
     */
    public static Map<String, Object> parseRootObject(JsonParser parser) {
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

    /**
     * @param value 要序列化的内容，字段排序由协议 codec 决定
     * @return 使用普通十进制表示的 JSON
     */
    public static String toJson(Object value) {
        return JSON_MAPPER.writeValueAsString(value);
    }

    /**
     * @param path 父级 JSON Pointer
     * @param field 当前字段或数组下标
     * @return 正确转义的子路径
     */
    public static String child(String path, String field) {
        return path + "/" + field.replace("~", "~0").replace("/", "~1");
    }

    private static MetricValidationException error(MetricErrorCode code, String path, String message) {
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
