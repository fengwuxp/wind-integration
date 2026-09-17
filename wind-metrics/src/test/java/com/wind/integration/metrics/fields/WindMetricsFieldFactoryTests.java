package com.wind.integration.metrics.fields;

import com.wind.integration.metrics.WindMetricsAggregationQuery;
import com.wind.integration.metrics.WindStructuredMetricsValue;
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

    @Test
    void testFieldValuesKeepsLegacyImplementationAndNullFieldWithoutEagerRead() {
        MultipleValueMetricsField<Map<String, Object>> legacy = mock(MultipleValueMetricsField.class, CALLS_REAL_METHODS);
        WindStructuredMetricsValue<Map<String, Object>> fields = legacy;
        verifyNoInteractions(legacy);
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("count", 10L);
        values.put("amount", null);
        when(legacy.getValue()).thenReturn(values);

        assertSame(values, fields.asFieldValues());
        assertTrue(values.containsKey("amount"));
    }
}
