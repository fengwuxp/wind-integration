package com.wind.integration.metrics;

import com.wind.integration.metrics.enums.MetricQueryMode;
import com.wind.integration.metrics.enums.MetricSegmentCode;
import com.wind.integration.metrics.enums.MetricSegmentSourceType;
import com.wind.integration.metrics.enums.MetricValueShape;
import com.wind.integration.metrics.enums.MetricValueType;
import com.wind.integration.metrics.enums.SnapshotGranularity;
import com.wind.integration.metrics.fields.MultipleValueMetricsField;
import com.wind.integration.metrics.query.MetricFieldValue;
import com.wind.integration.metrics.query.MetricResult;
import com.wind.integration.metrics.query.MetricSegmentResult;
import com.wind.jackson.WindJson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 从调用者视角验证公共值能力，不把值视图验证当作宿主执行或存储验收。
 *
 * @author wuxp
 * @since 2026-09-15
 */
class WindMetricsValueCapabilityTests {

    private static final LocalDateTime START = LocalDateTime.of(2026, 9, 1, 0, 0);

    private static final LocalDateTime END = START.plusDays(2);

    @ParameterizedTest
    @EnumSource(MetricQueryMode.class)
    void testScalarDeveloperAccessDoesNotDependOnQueryMode(MetricQueryMode mode) {
        MetricResult detailed = result("income", mode, MetricValueShape.SCALAR, new BigDecimal("12.5000"), Map.of());
        WindMetricsValue<?> value = detailed.toMetricsValue();

        assertEquals("income", value.getName());
        assertEquals(new BigDecimal("12.5000"), value.getValue());
        assertEquals(7, detailed.definitionRevision());
        assertEquals(mode, detailed.executionMode());
        assertFalse(value instanceof WindMetricsEvaluator<?>);
        // 详细查询结果保留原响应字段，不增加 JavaBean 值属性。
        String json = WindJson.toJsonString(detailed);
        assertFalse(json.contains("\"metricsValue\""));
        assertFalse(json.contains("\"name\""));
        Map<?, ?> payload = WindJson.getJsonMapper().readValue(json, Map.class);
        assertEquals(Set.of("metricCode", "definitionRevision", "executionMode", "routeMetricCode",
                "routeDefinitionRevision", "valueShape", "valueType", "value", "fields", "subjectId",
                "startTime", "endTime", "calculatedTime", "timeZone", "snapshotGranularity",
                "queryableStartTime", "watermarkTime", "planCode", "segments"), payload.keySet());
        assertEquals("income", payload.get("metricCode"));
        assertTrue(json.contains("12.5000"));
    }

    @ParameterizedTest
    @EnumSource(MetricQueryMode.class)
    void testFieldSetKeepsNamesTypesAndNullsAcrossQueryModes(MetricQueryMode mode) {
        Map<String, MetricFieldValue> fields = new LinkedHashMap<>();
        fields.put("amount", new MetricFieldValue(MetricValueType.DECIMAL, new BigDecimal("12.5000")));
        fields.put("count", new MetricFieldValue(MetricValueType.LONG, 3L));
        fields.put("average", new MetricFieldValue(MetricValueType.DECIMAL, null));
        WindStructuredMetricsValue<?> value = assertInstanceOf(WindStructuredMetricsValue.class,
                result("summary", mode, MetricValueShape.FIELD_SET, null, fields).toMetricsValue());

        assertEquals("summary", value.getName());
        assertSame(value.getValue(), value.asFieldValues());
        assertEquals(List.of("amount", "count", "average"), List.copyOf(value.asFieldValues().keySet()));
        assertEquals(new BigDecimal("12.5000"), value.asFieldValues().get("amount"));
        assertEquals(3L, value.asFieldValues().get("count"));
        assertTrue(value.asFieldValues().containsKey("average"));
        assertNull(value.asFieldValues().get("average"));
        assertFalse(value.asFieldValues().containsKey("missing"));
        assertThrows(UnsupportedOperationException.class, () -> value.asFieldValues().put("count", 4L));
    }

    @ParameterizedTest
    @EnumSource(MetricQueryMode.class)
    void testNormalEmptyScalarRemainsNull(MetricQueryMode mode) {
        assertNull(result("empty", mode, MetricValueShape.SCALAR, null, Map.of()).toMetricsValue().getValue());
    }

    @Test
    void testDifferentMetricsCanOwnTheSameFieldName() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("value", 100L);
        WindStructuredMetricsValue<?> first = WindStructuredMetricsValue.of("A", fields);
        fields.put("value", 5L);
        WindStructuredMetricsValue<?> second = WindStructuredMetricsValue.of("B", fields);
        fields.clear();

        assertEquals(Map.of("value", 100L), first.asFieldValues());
        assertEquals(Map.of("value", 5L), second.asFieldValues());
        assertEquals("A", first.getName());
        assertEquals("B", second.getName());
    }

    @Test
    void testExistingEvaluatedFieldsAreUsableThroughReadOnlyCapability() {
        MultipleValueMetricsField<Map<String, Object>> field = new MultipleValueMetricsField<>() {
            @Override
            public String getName() {
                return "codedSummary";
            }

            @Override
            public Map<String, Object> getValue() {
                return Map.of("count", 8L);
            }

            @Override
            public List<WindMetricsValue<Object>> getMetricsFields() {
                return asValues().entrySet().stream()
                        .map(entry -> WindMetricsValue.of(entry.getKey(), entry.getValue())).toList();
            }

            @Override
            public Map<String, Object> evaluate(WindMetricsAggregationQuery query) {
                return getValue();
            }
        };
        WindMetricsValue<?> view = field;
        assertEquals("codedSummary", view.getName());
        assertEquals(8L, field.asValues().get("count"));
        assertEquals(field.getValue(), field.asValues());
    }

    @Test
    void testReadOnlyContainerRetainsMutableBusinessValues() {
        List<String> businessValue = new ArrayList<>(List.of("created"));
        WindStructuredMetricsValue<Map<String, Object>> value =
                WindStructuredMetricsValue.of("history", Map.of("events", businessValue));

        assertSame(businessValue, value.asFieldValues().get("events"));
        businessValue.add("settled");
        assertEquals(List.of("created", "settled"), value.asFieldValues().get("events"));
        assertThrows(UnsupportedOperationException.class, () -> value.getValue().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> value.asFieldValues().entrySet().iterator().next().setValue(List.of()));
    }

    @Test
    void testFixedValueFactoriesRejectUnnamedValues() {
        assertThrows(IllegalArgumentException.class, () -> WindMetricsValue.of(" ", 1L));
        assertThrows(IllegalArgumentException.class, () -> WindStructuredMetricsValue.of(" ", Map.of("count", 1L)));
        assertThrows(IllegalArgumentException.class, () -> WindStructuredMetricsValue.of("summary", Map.of(" ", 1L)));
        assertThrows(NullPointerException.class, () -> WindStructuredMetricsValue.of("summary", null));
    }

    private static MetricResult result(String name, MetricQueryMode mode, MetricValueShape shape,
                                       Number value, Map<String, MetricFieldValue> fields) {
        boolean snapshot = mode == MetricQueryMode.SNAPSHOT;
        List<MetricSegmentResult> segments = mode == MetricQueryMode.SEGMENTED ? List.of(
                new MetricSegmentResult(MetricSegmentCode.ARCHIVE, MetricSegmentSourceType.SNAPSHOT,
                        START, START.plusDays(1), SnapshotGranularity.DAY, START, START.plusDays(1), null),
                new MetricSegmentResult(MetricSegmentCode.RECENT, MetricSegmentSourceType.REALTIME,
                        START.plusDays(1), END, null, null, null, END)) : List.of();
        return new MetricResult(name, 7, mode, null, null, shape,
                shape == MetricValueShape.SCALAR ? MetricValueType.DECIMAL : null,
                value, fields, "user1", START, END, END, ZoneId.of("Asia/Shanghai"),
                snapshot ? SnapshotGranularity.DAY : null, snapshot ? START : null, snapshot ? END : null,
                mode == MetricQueryMode.REALTIME ? null : "daily", segments);
    }
}
