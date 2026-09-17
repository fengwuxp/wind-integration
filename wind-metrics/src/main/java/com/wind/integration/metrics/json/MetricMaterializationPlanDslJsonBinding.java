package com.wind.integration.metrics.json;

import com.wind.integration.metrics.dsl.materialization.MetricMaterializationPlanDsl;

/**
 * 指标物化 Plan DSL 与规范 JSON 的 Jackson 绑定。
 *
 * @author wuxp
 * @since 2026-09-02
 */
public final class MetricMaterializationPlanDslJsonBinding {

    private static final MetricMaterializationPlanDslCodec CODEC = new MetricMaterializationPlanDslCodec();

    private static final MetricDslJsonBinding<MetricMaterializationPlanDsl> BINDING =
            new MetricDslJsonBinding<>(CODEC::parse, CODEC::canonicalize);

    private MetricMaterializationPlanDslJsonBinding() {
    }

    /** 指标物化 Plan DSL 的 Jackson 反序列化器。 */
    public static final class Deserializer extends MetricDslJsonBinding.Deserializer<MetricMaterializationPlanDsl> {

        public Deserializer() {
            super(MetricMaterializationPlanDsl.class, BINDING);
        }
    }

    /** 指标物化 Plan DSL 的 Jackson 序列化器。 */
    public static final class Serializer extends MetricDslJsonBinding.Serializer<MetricMaterializationPlanDsl> {

        public Serializer() {
            super(MetricMaterializationPlanDsl.class, BINDING);
        }
    }
}
