package com.wind.integration.metrics.query;

import com.wind.integration.metrics.WindMetricsValue;
import com.wind.integration.metrics.enums.MetricValueType;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

import java.util.HashSet;
import java.util.Set;

/**
 * 读取自带 code、valueType 和 value 的标量值对象。
 *
 * <p>直接读取数字 token，避免通用 Object/Double 转换丢失精度。
 * 字段顺序无关；类型归一化由 WindMetricsValue 工厂统一负责。</p>
 *
 * @author wuxp
 * @since 2026-09-20
 */
public final class MetricValueJsonDeserializer extends ValueDeserializer<WindMetricsValue<?>> {

    @Override
    public WindMetricsValue<?> deserialize(JsonParser parser, DeserializationContext context)
            throws JacksonException {
        if (parser.currentToken() != JsonToken.START_OBJECT) {
            return context.reportInputMismatch(WindMetricsValue.class, "Expected a typed metric value object");
        }
        String code = null;
        MetricValueType type = null;
        Object payload = null;
        Set<String> seen = new HashSet<>();
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            String field = parser.currentName();
            if (field == null || !seen.add(field)) {
                return context.reportInputMismatch(WindMetricsValue.class, "Invalid or duplicate value field");
            }
            JsonToken token = parser.nextToken();
            switch (field) {
                case "code" -> {
                    if (token != JsonToken.VALUE_STRING) {
                        return context.reportInputMismatch(WindMetricsValue.class, "code must be a string");
                    }
                    code = parser.getString();
                }
                case "valueType" -> {
                    if (token != JsonToken.VALUE_STRING) {
                        return context.reportInputMismatch(WindMetricsValue.class, "valueType must be a string");
                    }
                    type = MetricValueType.valueOf(parser.getString());
                }
                case "value" -> {
                    if (token == JsonToken.VALUE_NUMBER_INT || token == JsonToken.VALUE_NUMBER_FLOAT) {
                        payload = parser.getNumberValueExact();
                    } else if (token == JsonToken.VALUE_STRING) {
                        payload = parser.getString();
                    } else if (token != JsonToken.VALUE_NULL) {
                        return context.reportInputMismatch(WindMetricsValue.class,
                                "Scalar payload must be a number, string or null");
                    }
                }
                default -> {
                    return context.reportInputMismatch(WindMetricsValue.class, "Unknown metric value field: %s", field);
                }
            }
        }
        if (code == null || type == null || !seen.contains("value")) {
            return context.reportInputMismatch(WindMetricsValue.class, "code, valueType and value are required");
        }
        return WindMetricsValue.of(code, type, payload);
    }
}
