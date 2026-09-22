package com.wind.integration.metrics;

import com.wind.integration.metrics.enums.MetricQueryMode;
import com.wind.integration.metrics.enums.MetricSegmentCode;
import com.wind.integration.metrics.enums.MetricSegmentSourceType;
import com.wind.integration.metrics.enums.MetricValueShape;
import com.wind.integration.metrics.enums.MetricValueType;
import com.wind.integration.metrics.enums.MetricSnapshotGranularity;
import com.wind.integration.metrics.fields.MultipleValueMetricsField;
import com.wind.integration.metrics.query.MetricFieldValue;
import com.wind.integration.metrics.query.MetricResult;
import com.wind.integration.metrics.query.MetricSegmentResult;
import com.wind.jackson.WindJson;
import org.jspecify.annotations.NonNull;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 从调用者视角验证公共值能力，不把值视图验证当作宿主执行或存储验收。
 *
 * @author wuxp
 * @since 2026-09-15
 */
class WindMetricsValueCapabilityTests {

    private static final LocalDateTime START = LocalDateTime.of(2026, 9, 1, 0, 0);

    private static final LocalDateTime END = START.plusDays(2);

    /**
     * 场景：默认类型在接口实现、固定值工厂和结构化值中都必须非空。
     * 输入：未覆盖类型的提供者、空 payload、通用业务对象，以及显式 STRING 空值。
     * 流程：直接读取各值的 getValueType，不调用值提供者求值。
     * 预期：默认均为 DECIMAL，已声明的 STRING 不被覆盖；读取类型不触发求值。
     */
    @Test
    void testDefaultValueTypeIsNonNullAcrossImplementations() {
        WindMetricsValue<BigDecimal> provider = new WindMetricsValue<>() {
            @Override
            public String getName() {
                return "deferred";
            }

            @Override
            public BigDecimal getValue() {
                throw new AssertionError("Reading type must not evaluate the value");
            }
        };

        assertEquals(MetricValueType.DECIMAL, provider.getValueType());
        assertEquals(MetricValueType.DECIMAL, WindMetricsValue.of("empty", null).getValueType());
        assertEquals(MetricValueType.DECIMAL, WindMetricsValue.of("events", List.of("created")).getValueType());
        assertEquals(MetricValueType.DECIMAL, WindStructuredMetricsValue.of("summary", Map.of()).getValueType());
        assertEquals(MetricValueType.STRING, WindMetricsValue.of("grade", MetricValueType.STRING, null).getValueType());
    }

    /**
     * 场景：调用方在三种查询模式下都可用相同的标量值视图。
     * 输入：各 MetricQueryMode 的 income@7 结果，金额12.5000。
     * 流程：构造结果并转为 toMetricsValue，再序列化原结果。
     * 预期：code/值/版本/模式保留；视图不成为求值器，JSON 仍只有原响应字段。
     */
    @ParameterizedTest
    @EnumSource(MetricQueryMode.class)
    void testScalarDeveloperAccessDoesNotDependOnQueryMode(MetricQueryMode mode) {
        MetricResult detailed = result("income", mode, MetricValueShape.SCALAR, new BigDecimal("12.5000"), Map.of());
        WindMetricsValue<?> value = detailed.toMetricsValue();

        assertEquals("income", value.getCode());
        assertEquals(new BigDecimal("12.5000"), value.getValue());
        assertEquals(7, detailed.definitionRevision());
        assertEquals(mode, detailed.executionMode());
        assertFalse(value instanceof WindMetricsEvaluator<?>);
        // 详细查询结果保留原响应字段，不增加 JavaBean 值属性。
        String json = WindJson.toJsonString(detailed);
        assertFalse(json.contains("\"metricsValue\""));
        assertFalse(json.contains("\"name\""));
        Map<?, ?> payload = WindJson.getJsonMapper().readValue(json, Map.class);
        assertEquals(Set.of("metricCode", "definitionRevision", "executionMode", "valueShape", "value", "fields", "subjectId",
                "startTime", "endTime", "calculatedTime", "timeZone", "snapshotGranularity",
                "queryableStartTime", "watermarkTime", "planCode", "segments"), payload.keySet());
        assertEquals("income", payload.get("metricCode"));
        assertTrue(json.contains("12.5000"));
    }

    /**
     * 场景：字段集合的读取方式不随查询模式变化。
     * 输入：各模式下 amount=12.5000、count=3、average=null。
     * 流程：转为结构化值视图并尝试修改。
     * 预期：顺序、数值类型和显式 null 保留，missing 不存在，容器不可修改。
     */
    @ParameterizedTest
    @EnumSource(MetricQueryMode.class)
    void testFieldSetKeepsNamesTypesAndNullsAcrossQueryModes(MetricQueryMode mode) {
        Map<String, MetricFieldValue> fields = new LinkedHashMap<>();
        fields.put("amount", new MetricFieldValue(MetricValueType.DECIMAL, new BigDecimal("12.5000")));
        fields.put("count", new MetricFieldValue(MetricValueType.LONG, 3L));
        fields.put("average", new MetricFieldValue(MetricValueType.DECIMAL, null));
        WindStructuredMetricsValue<?> value = assertInstanceOf(WindStructuredMetricsValue.class,
                result("summary", mode, MetricValueShape.FIELD_SET, null, fields).toMetricsValue());

        assertEquals("summary", value.getCode());
        assertSame(value.getValue(), value.asFieldValues());
        assertEquals(List.of("amount", "count", "average"), List.copyOf(value.asFieldValues().keySet()));
        assertEquals(new BigDecimal("12.5000"), value.asFieldValues().get("amount"));
        assertEquals(3L, value.asFieldValues().get("count"));
        assertTrue(value.asFieldValues().containsKey("average"));
        assertNull(value.asFieldValues().get("average"));
        assertFalse(value.asFieldValues().containsKey("missing"));
        assertThrows(UnsupportedOperationException.class, () -> value.asFieldValues().put("count", 4L));
    }

    /**
     * 场景：正常空标量在三种模式的只读视图中保持为空。
     * 输入：各 MetricQueryMode 的 SCALAR 结果，value=null。
     * 流程：直接读取 value 以及 toMetricsValue 兼容入口。
     * 预期：两入口返回同一非空具名值，payload 为 null，不编造零值。
     */
    @ParameterizedTest
    @EnumSource(MetricQueryMode.class)
    void testNormalEmptyScalarRemainsNull(MetricQueryMode mode) {
        MetricResult result = result("empty", mode, MetricValueShape.SCALAR, null, Map.of());
        assertEquals("empty", result.value().getCode());
        assertNull(result.value().getValue());
        assertSame(result.value(), result.toMetricsValue());
    }

    /**
     * 场景：不同指标可独立拥有同名结果字段。
     * 输入：A.value=100、B.value=5，顺序复用并修改同一个输入 Map。
     * 流程：分别构造结构化值，再清空原 Map。
     * 预期：A、B 的 code 与各自快照值保持独立。
     */
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
        assertEquals("A", first.getCode());
        assertEquals("B", second.getCode());
    }

    /**
     * 场景：已有多字段实现可直接以只读能力读取。
     * 输入：旧 MultipleValueMetricsField 夹具 codedSummary，count=8。
     * 流程：转为 WindMetricsValue 并读取 asValues。
     * 预期：code 为 codedSummary，值为8，字段视图与旧值一致。
     */
    @Test
    void testExistingEvaluatedFieldsAreUsableThroughReadOnlyCapability() {
        MultipleValueMetricsField<Map<String, Object>> field = new MultipleValueMetricsField<>() {
            @Override
            public @NonNull String getName() {
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
        assertEquals("codedSummary", view.getCode());
        assertEquals(8L, field.asValues().get("count"));
        assertEquals(field.getValue(), field.asValues());
    }

    /**
     * 场景：结构化值只冻结容器，不深拷贝任意业务对象。
     * 输入：events 指向可变列表 created。
     * 流程：构造视图后向原列表加入 settled，并尝试修改容器及 entry。
     * 预期：列表仍为同一对象且变更可见；容器与 entry 均不可改。
     */
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

    /**
     * 场景：固定值工厂拒绝无法标识的值。
     * 输入：空白指标 code、空白字段名或 null 字段集合。
     * 流程：调用标量及结构化工厂。
     * 预期：空白名称抛参数异常，null 集合抛空指针校验异常。
     */
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
                        START, START.plusDays(1), MetricSnapshotGranularity.DAY, START, START.plusDays(1), null),
                new MetricSegmentResult(MetricSegmentCode.RECENT, MetricSegmentSourceType.REALTIME,
                        START.plusDays(1), END, null, null, null, END)) : List.of();
        return new MetricResult(name, 7, mode, shape,
                shape == MetricValueShape.SCALAR ? WindMetricsValue.of("value", MetricValueType.DECIMAL, value) : null,
                fields, "user1", START, END, END, ZoneId.of("Asia/Shanghai"),
                snapshot ? MetricSnapshotGranularity.DAY : null, snapshot ? START : null, snapshot ? END : null,
                mode == MetricQueryMode.REALTIME ? null : "daily", segments, List.of());
    }
}
