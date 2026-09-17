package com.wind.integration.metrics.json;

import com.wind.integration.metrics.spec.MetricDefinitionSpec.MetricSqlDefinitionSpec;

/**
 * SQL 指标定义与规范 JSON 的 Jackson 绑定。
 *
 * @author wuxp
 * @since 2026-09-18
 */
public final class MetricSqlJsonBinding {

    private static final MetricSqlCodec CODEC = new MetricSqlCodec();

    private static final MetricDslJsonBinding<MetricSqlDefinitionSpec> BINDING =
            new MetricDslJsonBinding<>(CODEC::parse, CODEC::canonicalize);

    private MetricSqlJsonBinding() {
    }

    /**
     * SQL 模板指标定义的 Jackson 反序列化器。
     */
    public static final class Deserializer extends MetricDslJsonBinding.Deserializer<MetricSqlDefinitionSpec> {

        public Deserializer() {
            super(MetricSqlDefinitionSpec.class, BINDING);
        }
    }

    /**
     * SQL 模板指标定义的 Jackson 序列化器。
     */
    public static final class Serializer extends MetricDslJsonBinding.Serializer<MetricSqlDefinitionSpec> {

        public Serializer() {
            super(MetricSqlDefinitionSpec.class, BINDING);
        }
    }
}
