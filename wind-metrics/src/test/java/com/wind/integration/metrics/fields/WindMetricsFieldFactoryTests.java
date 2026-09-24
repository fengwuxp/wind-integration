package com.wind.integration.metrics.fields;

import com.wind.integration.metrics.WindMetricsAggregationQuery;
import com.wind.integration.metrics.WindMetricsValue;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
     * 输入：USER/s1、limit=3，按 second、first、first 请求字段。
     * 流程：真实执行默认批量方法。
     * 预期：字段顺序与重复项保留，构造阶段不读值。
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

        List<SingleValueMetricsField<Long>> fields = factory.single(List.of("second", "first", "first"), query);

        assertEquals(List.of(second, first, first), fields);
        verifyNoInteractions(first, second);
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
     * 预期：返回同一 Map，显式空字段保留。
     */
    @Test
    void testFieldValuesKeepsLegacyImplementationAndNullFieldWithoutEagerRead() {
        MultipleValueMetricsField<Map<String, Object>> legacy = mock(MultipleValueMetricsField.class, CALLS_REAL_METHODS);
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("count", 10L);
        values.put("amount", null);
        when(legacy.getValue()).thenReturn(values);

        assertSame(values, legacy.asValues());
        assertTrue(values.containsKey("amount"));
    }

    /** 旧业务对象通过真实 JSON 转换提供字段视图，正常空字段保留，不能当作缺失字段删除。 */
    @Test
    void testLegacyObjectIsConvertedToFieldValues() {
        MultipleValueMetricsField<Summary> legacy = mock(MultipleValueMetricsField.class, CALLS_REAL_METHODS);
        when(legacy.getValue()).thenReturn(new Summary(7L, null));

        Map<String, Object> values = legacy.asValues();

        assertEquals(Set.of("approved", "average"), values.keySet());
        assertEquals(7L, ((Number) values.get("approved")).longValue());
        assertNull(values.get("average"));
    }

    /** 字段查找区分正常 null 与字段缺失，并返回原始具名值。 */
    @Test
    void testLegacyLookupDistinguishesNullFromMissingField() {
        MultipleValueMetricsField<Summary> legacy = mock(MultipleValueMetricsField.class, CALLS_REAL_METHODS);
        WindMetricsValue<Object> approved = WindMetricsValue.of("approved", 7L);
        WindMetricsValue<Object> average = WindMetricsValue.of("average", null);
        when(legacy.getMetricsFields()).thenReturn(List.of(approved, average));

        assertSame(approved, legacy.findByName("approved").orElseThrow());
        assertSame(average, legacy.findByName("average").orElseThrow());
        assertNull(legacy.findByName("average").orElseThrow().getValue());
        assertTrue(legacy.findByName("missing").isEmpty());
    }

    private record Summary(Long approved, Number average) {
    }
}
