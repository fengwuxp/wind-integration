package com.wind.integration.metrics.dsl;

import com.wind.integration.metrics.MetricValidationException;
import com.wind.integration.metrics.dsl.materialization.MetricMaterializationPlanDsl;
import com.wind.integration.metrics.enums.MetricErrorCode;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.deser.std.StdDeserializer;
import tools.jackson.databind.ser.std.StdSerializer;

/**
 * 指标物化 Plan DSL 与规范 JSON 的 Jackson 绑定。
 *
 * @author wuxp
 * @since 2026-09-02
 */
public final class MetricMaterializationPlanDslJsonBinding {

    private static final MetricMaterializationPlanDslCodec CODEC = new MetricMaterializationPlanDslCodec();

    private MetricMaterializationPlanDslJsonBinding() {
    }

    /** 指标物化 Plan DSL 的 Jackson 反序列化器。 */
    public static final class Deserializer extends StdDeserializer<MetricMaterializationPlanDsl> {

        /** 创建反序列化器。 */
        public Deserializer() {
            super(MetricMaterializationPlanDsl.class);
        }

        @Override
        public MetricMaterializationPlanDsl deserialize(JsonParser parser, DeserializationContext context)
                throws JacksonException {
            return CODEC.parse(parser);
        }

        @Override
        public MetricMaterializationPlanDsl getNullValue(DeserializationContext context) {
            throw new MetricValidationException(
                    MetricErrorCode.DSL_ROOT_NOT_OBJECT, "", "Materialization Plan DSL root must be an object");
        }
    }

    /** 指标物化 Plan DSL 的 Jackson 序列化器。 */
    public static final class Serializer extends StdSerializer<MetricMaterializationPlanDsl> {

        /** 创建序列化器。 */
        public Serializer() {
            super(MetricMaterializationPlanDsl.class);
        }

        @Override
        public void serialize(MetricMaterializationPlanDsl value,
                              JsonGenerator generator,
                              SerializationContext context) throws JacksonException {
            generator.writeRawValue(CODEC.canonicalize(value));
        }
    }
}
