package com.wind.integration.metrics.fields;

import com.wind.integration.metrics.WindMetricsAggregationQuery;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 历史工厂保留的批量、默认条件与字段读取行为。
 *
 * @author wuxp
 */
@SuppressWarnings({"deprecation", "unchecked"})
class WindMetricsFieldFactoryTests {

    /**
     * 场景：旧批量工厂保持顺序、查询条件与惰性取值。
     * 输入：USER/s1、limit=3，按 second、first 请求两个字段替身。
     * 流程：真实执行默认批量方法，再两次读取 first。
     * 预期：字段顺序保留，构造阶段不读值；两次读取分别返回10和20。
     */
    @Test
    void testLegacyBatchPreservesNamesConditionsAndLazyReads() {
        WindMetricsFieldFactory factory = mock(WindMetricsFieldFactory.class, CALLS_REAL_METHODS);
        WindMetricsAggregationQuery query = WindMetricsAggregationQuery.newBuilder("USER", "s1")
                .queryVariable(Map.of("limit", 3)).build();
        SingleValueMetricsField<Long> first = mock(SingleValueMetricsField.class);
        SingleValueMetricsField<Long> second = mock(SingleValueMetricsField.class);
        when(factory.<Long>single("first", query)).thenReturn(first);
        when(factory.<Long>single("second", query)).thenReturn(second);

        List<SingleValueMetricsField<Long>> fields = factory.single(List.of("second", "first"), query);

        assertEquals(List.of(second, first), fields);
        verifyNoInteractions(first, second);
        when(first.getValue()).thenReturn(10L, 20L);
        assertEquals(10L, fields.get(1).getValue());
        assertEquals(20L, fields.get(1).getValue());
    }

    /**
     * 场景：旧工厂无条件重载保留默认条件和异常语义。
     * 输入：metric 对应字段替身，missing 会抛 Unknown metric。
     * 流程：调用单个/批量无条件重载，再请求含 missing 的批次。
     * 预期：默认传 null、返回同一字段，失败原样传播且不提前读取已创建字段。
     */
    @Test
    void testLegacyDefaultConditionsAndFailuresArePreserved() {
        WindMetricsFieldFactory factory = mock(WindMetricsFieldFactory.class, CALLS_REAL_METHODS);
        SingleValueMetricsField<Long> legacy = mock(SingleValueMetricsField.class);
        when(factory.<Long>single("metric", null)).thenReturn(legacy);
        IllegalArgumentException missing = new IllegalArgumentException("Unknown metric");
        when(factory.single("missing", null)).thenThrow(missing);

        assertSame(legacy, factory.single("metric"));
        assertEquals(List.of(legacy), factory.single(List.of("metric")));
        assertSame(missing, assertThrows(IllegalArgumentException.class,
                () -> factory.single(List.of("metric", "missing"))));
        verifyNoInteractions(legacy);
    }

    /**
     * 场景：旧多值实现可按原对象提供字段视图。
     * 输入：getValue 返回 count=10、amount=null 的 Map。
     * 流程：真实执行默认 asValues。
     * 预期：返回同一 Map，显式空字段保留；建立引用时不提前读取。
     */
    @Test
    void testFieldValuesKeepsLegacyImplementationAndNullFieldWithoutEagerRead() {
        MultipleValueMetricsField<Map<String, Object>> legacy = mock(MultipleValueMetricsField.class, CALLS_REAL_METHODS);
        MultipleValueMetricsField<Map<String, Object>> fields = legacy;
        verifyNoInteractions(legacy);
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("count", 10L);
        values.put("amount", null);
        when(legacy.getValue()).thenReturn(values);

        assertSame(values, fields.asValues());
        assertTrue(values.containsKey("amount"));
    }
}
