package com.wind.integration.metrics.dsl;

import com.wind.integration.metrics.MetricValidationException;
import com.wind.integration.metrics.dsl.definition.MetricDefinitionDsl;
import com.wind.integration.metrics.enums.MetricErrorCode;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.deser.std.StdDeserializer;
import tools.jackson.databind.ser.std.StdSerializer;

/**
 * 指标 Definition DSL 与规范 JSON 的 Jackson 绑定。
 *
 * @author wuxp
 * @since 2026-09-02
 */
public final class MetricDefinitionDslJsonBinding {

    private static final MetricDefinitionDslCodec CODEC = new MetricDefinitionDslCodec();

    private MetricDefinitionDslJsonBinding() {
    }

    /** 指标 Definition DSL 的 Jackson 反序列化器。 */
    public static final class Deserializer extends StdDeserializer<MetricDefinitionDsl> {

        /** 创建反序列化器。 */
        public Deserializer() {
            super(MetricDefinitionDsl.class);
        }

        @Override
        public MetricDefinitionDsl deserialize(JsonParser parser, DeserializationContext context)
                throws JacksonException {
            return CODEC.parse(parser);
        }

        @Override
        public MetricDefinitionDsl getNullValue(DeserializationContext context) {
            throw new MetricValidationException(
                    MetricErrorCode.DSL_ROOT_NOT_OBJECT, "", "Definition DSL root must be an object");
        }
    }

    /** 指标 Definition DSL 的 Jackson 序列化器。 */
    public static final class Serializer extends StdSerializer<MetricDefinitionDsl> {

        /** 创建序列化器。 */
        public Serializer() {
            super(MetricDefinitionDsl.class);
        }

        @Override
        public void serialize(MetricDefinitionDsl value, JsonGenerator generator, SerializationContext context)
                throws JacksonException {
            generator.writeRawValue(CODEC.canonicalize(value));
        }
    }
}
