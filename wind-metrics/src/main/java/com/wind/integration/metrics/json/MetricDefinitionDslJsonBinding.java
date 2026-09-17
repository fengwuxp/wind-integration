package com.wind.integration.metrics.json;

import com.wind.integration.metrics.spec.MetricDefinitionSpec.MetricDSLDefinitionSpec;

/**
 * 指标 Definition DSL 与规范 JSON 的 Jackson 绑定。
 *
 * @author wuxp
 * @since 2026-09-02
 */
public final class MetricDefinitionDslJsonBinding {

    private static final MetricDefinitionDslCodec CODEC = new MetricDefinitionDslCodec();

    private static final MetricDslJsonBinding<MetricDSLDefinitionSpec> BINDING =
            new MetricDslJsonBinding<>(CODEC::parse, CODEC::canonicalize);

    private MetricDefinitionDslJsonBinding() {
    }

    /** 指标 Definition DSL 的 Jackson 反序列化器。 */
    public static final class Deserializer extends MetricDslJsonBinding.Deserializer<MetricDSLDefinitionSpec> {

        public Deserializer() {
            super(MetricDSLDefinitionSpec.class, BINDING);
        }
    }

    /** 指标 Definition DSL 的 Jackson 序列化器。 */
    public static final class Serializer extends MetricDslJsonBinding.Serializer<MetricDSLDefinitionSpec> {

        public Serializer() {
            super(MetricDSLDefinitionSpec.class, BINDING);
        }
    }
}
