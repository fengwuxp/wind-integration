package com.wind.integration.metrics;

import com.wind.integration.metrics.fields.SingleValueMetricsField;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * @author wuxp
 * @date 2025-06-24 09:32
 **/
class WindWindMetricsAggregatorFactoryTests {

    WindMetricsAggregatorFactory factory = new WindMetricsAggregatorConverterImpl();

    @Test
    void testFactory() {
        ExamlpeObject example = factory.factory(ExamlpeObject.class)
                .named("success_total", ExamlpeObject.Fields.successTotal)
                .named("total_amount", ExamlpeObject.Fields.totalAmount)
                .aggregate(WindMetricsAggregationQuery.of("example", 1L));
        Assertions.assertNotNull(example.successTotal);
    }


    static class WindMetricsAggregatorConverterImpl implements WindMetricsAggregatorFactory {


        @Override
        public <T> WindMetricsAggregator<T> factory(Class<T> objectType) {
            return new WindMetricsAggregatorImpl<>(objectType);
        }

        @Override
        public <T> WindMetricsAggregator<T> factory(String name, Class<T> objectType) {
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
        public WindMetricsAggregator<T> named(String name, String filedName) {
            fieldMappings.put(name, filedName);
            return this;
        }

        @Override
        public T aggregate(WindMetricsAggregationQuery query) {
            try {
                T bean = classType.newInstance();
                for (Map.Entry<String, String> entry : fieldMappings.entrySet()) {
                    String name = entry.getKey();
                    String filedName = entry.getValue();
                    Field field = bean.getClass().getDeclaredField(filedName);
                    field.setAccessible(true);
                    field.set(bean, new MockSingleValueMetricsField(name).getValue());
                }
                return bean;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    static class MockSingleValueMetricsField implements SingleValueMetricsField<Integer> {

        private final String name;

        private int val = new Random().nextInt();

        public MockSingleValueMetricsField(String name) {
            this.name = name;
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
    }

    @Data
    @FieldNameConstants
    public static class ExamlpeObject {

        private Integer successTotal;

        private Integer totalAmount;
    }
}
