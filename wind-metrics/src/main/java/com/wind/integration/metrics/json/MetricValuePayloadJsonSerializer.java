package com.wind.integration.metrics.json;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

import java.time.LocalDateTime;

/**
 * 按原始结构写出指标值，直接时间值使用保留纳秒的 ISO 本地时间文本。
 *
 * @author wuxp
 * @since 2026-09-22
 */
public final class MetricValuePayloadJsonSerializer extends ValueSerializer<Object> {

    @Override
    public void serialize(Object value, JsonGenerator generator, SerializationContext context) {
        if (value instanceof LocalDateTime time) {
            generator.writeString(time.toString());
        } else {
            context.writeValue(generator, value);
        }
    }
}
