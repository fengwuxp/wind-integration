package com.wind.integration.metrics.query;

import com.wind.integration.metrics.WindMetricsValue;
import com.wind.integration.metrics.WindStructuredMetricsValue;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 将标量写为自带 code、valueType 和 value 的 JSON 对象。
 *
 * <p>值类型只在值对象中保存。时间值使用 ISO 本地时间文本，
 * 保留纳秒精度，时区由所属结果携带。FIELD_SET 的值已在 fields 中传输，
 * 其根级 JSON value 保持历史 null，反序列化时由结果构造器重建结构化值。</p>
 *
 * @author wuxp
 * @since 2026-09-20
 */
public final class MetricValueJsonSerializer extends ValueSerializer<WindMetricsValue<?>> {

    @Override
    public void serialize(WindMetricsValue<?> value, JsonGenerator generator, SerializationContext context)
            throws JacksonException {
        if (value instanceof WindStructuredMetricsValue<?>) {
            generator.writeNull();
            return;
        }
        generator.writeStartObject();
        generator.writeStringProperty("code", value.getCode());
        generator.writeStringProperty("valueType", value.getValueType().name());
        generator.writeName("value");
        Object payload = value.getValue();
        switch (payload) {
            case null -> generator.writeNull();
            case BigDecimal decimal -> generator.writeNumber(decimal);
            case Integer integer -> generator.writeNumber(integer);
            case Long longValue -> generator.writeNumber(longValue);
            case String text -> generator.writeString(text);
            case LocalDateTime timestamp -> generator.writeString(timestamp.toString());
            default -> throw new IllegalArgumentException("Unsupported metric result payload: "
                    + payload.getClass().getName());
        }
        generator.writeEndObject();
    }
}
