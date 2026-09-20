package com.wind.integration.metrics;

import com.wind.common.util.WindReflectUtils;
import com.wind.integration.metrics.fields.SingleValueMetricsField;
import com.wind.integration.tag.EntityTag;
import com.wind.integration.tag.TagSource;
import com.wind.jackson.WindJson;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * 旧工厂的对象映射示例和标签 JSON 契约；工厂与数值来源均为本地测试夹具。
 *
 * @author wuxp
 * @date 2025-06-24 09:32
 **/
class WindWindMetricsAggregatorFactoryTests {

    WindMetricsAggregatorFactory factory = new WindMetricsAggregatorConverterImpl();

    /**
     * 场景：旧聚合器按指标 code 将值装入目标对象。
     * 输入：successTotal 映射 success_total=3，totalAmount 映射 total_amount=120。
     * 流程：用本地工厂夹具配置映射并聚合 example 主体1。
     * 预期：目标对象两字段分别为3和120；该夹具不代表正式查询服务实现。
     */
    @Test
    void testFactory() {
        ExamlpeObject example = factory.factory(ExamlpeObject.class)
                .named(ExamlpeObject.Fields.successTotal, "success_total")
                .named(ExamlpeObject.Fields.totalAmount, "total_amount")
                .aggregate(WindMetricsAggregationQuery.of("example", 1L));
        Assertions.assertEquals(3, example.successTotal);
        Assertions.assertEquals(120, example.totalAmount);
    }

    /**
     * 场景：实体标签保持 JSON 往返兼容。
     * 输入：name=example、value=vv、source=MANUAL、sourceId=mock。
     * 流程：序列化后反序列化 EntityTag。
     * 预期：还原标签与输入整体相等。
     */
    @Test
    void testEntityTagJson(){
        EntityTag expected = new EntityTag("example", "vv", TagSource.MANUAL, "mock");
        String json = WindJson.toJsonString(expected);
        EntityTag entityTag = WindJson.parseObject(json, EntityTag.class);
        Assertions.assertEquals(expected, entityTag);
    }

    static class WindMetricsAggregatorConverterImpl implements WindMetricsAggregatorFactory {


        @Override
        public <T> WindMetricsAggregator<T> factory(Class<T> objectType) {
            return new WindMetricsAggregatorImpl<>(objectType);
        }

        @Override
        public <T> WindMetricsAggregator<T> factory(String metricsName, Class<T> objectType) {
            return factory(objectType);
        }
    }

    static class WindMetricsAggregatorImpl<T> implements WindMetricsAggregator<T> {

        private final Class<T> classType;

        private final Map<String, String> fieldMappings;


        public WindMetricsAggregatorImpl(Class<T> classType) {
            this.classType = classType;
            this.fieldMappings = new HashMap<>();
        }

        @Override
        public WindMetricsAggregator<T> named(String filedName, String metricCode) {
            fieldMappings.put(filedName, metricCode);
            return this;
        }

        @Override
        public T aggregate(WindMetricsAggregationQuery query) {
            Map<String, Object> fieldValues = new HashMap<>();
            for (Map.Entry<String, String> entry : fieldMappings.entrySet()) {
                fieldValues.put(entry.getKey(), new MockSingleValueMetricsField(entry.getValue()).getValue());
            }
            return WindReflectUtils.newInstance(classType, fieldValues);
        }
    }

    static class MockSingleValueMetricsField implements SingleValueMetricsField<Integer> {

        private final String name;

        private int val;

        public MockSingleValueMetricsField(String name) {
            this.name = name;
            this.val = Map.of("success_total", 3, "total_amount", 120).get(name);
        }

        @Override
        public void setValue(Integer value) {
            this.val = value;
        }

        @Override
        public Integer increase(Integer value) {
            val += value;
            return val;
        }

        @Override
        public Integer decrease(Integer value) {
            val -= value;
            return val;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public Integer getValue() {
            return val;
        }

        @Override
        public Integer evaluate(WindMetricsAggregationQuery query) {
            return getValue();
        }
    }

    @Data
    @FieldNameConstants
    public static class ExamlpeObject {

        private Integer successTotal;

        private Integer totalAmount;
    }
}
