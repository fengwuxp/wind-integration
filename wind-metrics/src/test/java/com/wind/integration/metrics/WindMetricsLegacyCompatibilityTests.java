package com.wind.integration.metrics;

import com.wind.integration.metrics.query.MetricQuery;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 新 DSL 引入后的旧指标 API 兼容合同测试。
 */
class WindMetricsLegacyCompatibilityTests {

    /**
     * 场景：引入 DSL 后旧查询工厂仍保留原调用语义。
     * 输入：customer=1、currency=CNY、2026-07-01至07-02。
     * 流程：使用 builder 构造并另调用 of 工厂。
     * 预期：主体、参数、起止时间均保留，of 工厂仍提供 customer 维度。
     */
    @Test
    void testAggregationQueryFactoriesKeepExistingContract() {
        LocalDateTime beginTime = LocalDateTime.of(2026, 7, 1, 0, 0);
        LocalDateTime endTime = LocalDateTime.of(2026, 7, 2, 0, 0);

        WindMetricsAggregationQuery query = WindMetricsAggregationQuery.newBuilder("customer", 1L)
                .queryVariable("currency", "CNY")
                .minGmtCreate(beginTime)
                .maxGmtCreate(endTime)
                .build();

        Assertions.assertEquals("customer", query.getDimensions());
        Assertions.assertEquals(1L, query.getDimensionsId());
        Assertions.assertEquals("CNY", query.getQueryVariables().get("currency"));
        Assertions.assertEquals(beginTime, query.getMinGmtCreate());
        Assertions.assertEquals(endTime, query.getMaxGmtCreate());
        Assertions.assertEquals("customer", WindMetricsAggregationQuery.of("customer", 1L).getDimensions());
    }

    /**
     * 场景：旧聚合器工厂的两种签名继续可用。
     * 输入：WindMetricsAggregatorFactory 公共类型。
     * 流程：反射查找 factory(Class) 和 factory(String, Class)。
     * 预期：两个方法都存在；此用例验证签名兼容，不执行聚合。
     */
    @Test
    void testAggregatorFactoryKeepsBothFactoryMethods() throws NoSuchMethodException {
        Assertions.assertNotNull(WindMetricsAggregatorFactory.class.getMethod("factory", Class.class));
        Assertions.assertNotNull(WindMetricsAggregatorFactory.class.getMethod("factory", String.class, Class.class));
    }

    /**
     * 未指定定义版本时，新入口保留旧实现的返回对象及完整查询条件。
     */
    @Test
    void testValueFactoryNullRevisionDelegatesToLegacyMethods() {
        LegacyValueFactory factory = new LegacyValueFactory();
        MetricQuery query = MetricQuery.builder().subjectId("user-1").parameter("currency", "USD").build();

        Assertions.assertSame(factory.scalar, factory.value("COUNT", null, query));
        Assertions.assertEquals("COUNT", factory.metricCode);
        Assertions.assertSame(query, factory.query);
        Assertions.assertSame(factory.structured, factory.fields("SUMMARY", null, query));
        Assertions.assertEquals("SUMMARY", factory.metricCode);
        Assertions.assertSame(query, factory.query);
        Assertions.assertEquals(2, factory.calls);
    }

    /**
     * 旧实现不支持精确版本时立即失败，不能调用旧入口并静默读取 current。
     */
    @Test
    void testValueFactoryExactRevisionRejectsWithoutLegacyFallback() {
        LegacyValueFactory factory = new LegacyValueFactory();
        MetricQuery query = MetricQuery.builder().subjectId("user-1").build();

        Assertions.assertThrows(UnsupportedOperationException.class, () -> factory.value("COUNT", 2, query));
        Assertions.assertThrows(UnsupportedOperationException.class, () -> factory.fields("SUMMARY", 3, query));
        Assertions.assertEquals(0, factory.calls);
    }

    /**
     * 空版本只委托原映射方法，保留字段、指标名称与链式返回对象。
     */
    @Test
    void testAggregatorNullRevisionDelegatesToLegacyMapping() {
        LegacyAggregator aggregator = new LegacyAggregator();

        Assertions.assertSame(aggregator, aggregator.named("count", "COUNT", null));
        Assertions.assertEquals("count", aggregator.fieldName);
        Assertions.assertEquals("COUNT", aggregator.metricCode);
    }

    /**
     * 精确版本不支持时不改动已有映射，也不把该版本丢弃后继续组装。
     */
    @Test
    void testAggregatorExactRevisionRejectsWithoutChangingLegacyMapping() {
        LegacyAggregator aggregator = new LegacyAggregator();
        aggregator.named("count", "COUNT");

        Assertions.assertThrows(UnsupportedOperationException.class, () -> aggregator.named("amount", "AMOUNT", 2));
        Assertions.assertEquals("count", aggregator.fieldName);
        Assertions.assertEquals("COUNT", aggregator.metricCode);
    }

    private static final class LegacyValueFactory implements WindMetricsValueFactory {

        private final WindMetricsValue<Long> scalar = WindMetricsValue.of("COUNT", 7L);

        private final WindStructuredMetricsValue<Map<String, Object>> structured =
                WindStructuredMetricsValue.of("SUMMARY", Map.of("count", 7L));

        private String metricCode;

        private MetricQuery query;

        private int calls;

        @Override
        @SuppressWarnings("unchecked")
        public <N extends Number> WindMetricsValue<N> value(String metricCode, MetricQuery query) {
            this.metricCode = metricCode;
            this.query = query;
            calls++;
            return (WindMetricsValue<N>) scalar;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <V> WindStructuredMetricsValue<V> fields(String metricCode, MetricQuery query) {
            this.metricCode = metricCode;
            this.query = query;
            calls++;
            return (WindStructuredMetricsValue<V>) structured;
        }
    }

    private static final class LegacyAggregator implements WindMetricsAggregator<Object> {

        private String fieldName;

        private String metricCode;

        @Override
        public WindMetricsAggregator<Object> named(String fieldName, String metricCode) {
            this.fieldName = fieldName;
            this.metricCode = metricCode;
            return this;
        }

        @Override
        public Object aggregate(WindMetricsAggregationQuery query) {
            throw new UnsupportedOperationException("This fixture only records field mappings");
        }
    }
}
