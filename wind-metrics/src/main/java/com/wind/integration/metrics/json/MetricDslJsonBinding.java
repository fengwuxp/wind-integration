package com.wind.integration.metrics.json;

import com.wind.integration.metrics.MetricValidationException;
import com.wind.integration.metrics.enums.MetricErrorCode;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.deser.std.StdDeserializer;
import tools.jackson.databind.ser.std.StdSerializer;

import java.util.function.Function;

/**
 * 指标 DSL Jackson 绑定的通用基础实现，消除三类 DSL 绑定的结构重复。
 *
 * <p>每种 DSL 类型提供一对具名内部类（{@code Deserializer} / {@code Serializer}）
 * 并在具体绑定类中持有各自的 Codec 实例，这里只实现公共的 parse-then-validate
 * 和 canonicalize-then-write 逻辑。</p>
 *
 * @param <T> DSL 根对象类型
 * @author wuxp
 * @since 2026-09-18
 */
final class MetricDslJsonBinding<T> {

    private final Function<JsonParser, T> parser;

    private final Function<T, String> canonicalizer;

    MetricDslJsonBinding(Function<JsonParser, T> parser, Function<T, String> canonicalizer) {
        this.parser = parser;
        this.canonicalizer = canonicalizer;
    }

    T deserialize(JsonParser jsonParser) {
        return parser.apply(jsonParser);
    }

    void serialize(T value, JsonGenerator generator) throws JacksonException {
        generator.writeRawValue(canonicalizer.apply(value));
    }

    /** 通用反序列化器基类，子类仅需传入目标类型和绑定实例。 */
    abstract static class Deserializer<T> extends StdDeserializer<T> {

        private final MetricDslJsonBinding<T> binding;

        protected Deserializer(Class<T> type, MetricDslJsonBinding<T> binding) {
            super(type);
            this.binding = binding;
        }

        @Override
        public T deserialize(JsonParser parser, DeserializationContext context) throws JacksonException {
            return binding.deserialize(parser);
        }

        @Override
        public T getNullValue(DeserializationContext context) {
            throw new MetricValidationException(
                    MetricErrorCode.DSL_ROOT_NOT_OBJECT, "", "DSL root must be an object");
        }
    }

    /** 通用序列化器基类，子类仅需传入目标类型和绑定实例。 */
    abstract static class Serializer<T> extends StdSerializer<T> {

        private final MetricDslJsonBinding<T> binding;

        protected Serializer(Class<T> type, MetricDslJsonBinding<T> binding) {
            super(type);
            this.binding = binding;
        }

        @Override
        public void serialize(T value, JsonGenerator generator, SerializationContext context) throws JacksonException {
            binding.serialize(value, generator);
        }
    }
}
