package com.wind.integration.metrics.dsl.materialization;

import com.wind.integration.metrics.MetricValidationException;
import com.wind.integration.metrics.dsl.definition.MetricReferenceDsl;
import com.wind.integration.metrics.enums.MetricErrorCode;
import com.wind.integration.metrics.enums.MetricSegmentSourceType;
import com.wind.integration.metrics.enums.MetricSnapshotGranularity;
import com.wind.integration.metrics.spec.MetricDSLDefinition;
import com.wind.integration.metrics.spec.MetricDefinitionSpec;
import com.wind.jackson.WindJson;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证物化计划独占分段配置及三类时间范围的 JSON 契约，不执行快照读写。
 *
 * @author wuxp
 * @since 2026-09-22
 */
class MetricMaterializationPlanDslTests {

    /**
     * 场景：正式设计中的完整 Definition/Plan 示例必须服从同一公共合同。
     * 输入：两份计算定义及整体历史、年度历史、固定区间、FULL 四个替代计划。
     * 流程：读取共享 JSON 样例，经 WindJson 往返并解析每个精确成员。
     * 预期：样例均可保存，成员版本、主体、时间和维度相同，定义不复制读取模式或分段。
     *
     * @throws IOException 读取测试资源失败
     */
    @Test
    void testCompleteDesignExamplesBindExactDefinitions() throws IOException {
        Map<MetricReferenceDsl, MetricDSLDefinition> definitions = new LinkedHashMap<>();
        for (String name : List.of("definition-approved-count.json", "definition-total-count.json")) {
            MetricDefinitionSpec<?> spec = WindJson.parseObject(example(name), MetricDefinitionSpec.class);
            MetricDSLDefinition definition = (MetricDSLDefinition) spec.definition();
            definitions.put(new MetricReferenceDsl(definition.code(), definition.revision()), definition);
            assertEquals(spec, WindJson.parseObject(WindJson.toJsonString(spec), MetricDefinitionSpec.class));
            assertFalse(properties(definition).containsKey("executionMode"));
            assertFalse(properties(definition).containsKey("segments"));
        }
        for (String name : List.of("plan-whole-history.json", "plan-yearly-history.json", "plan-fixed-ranges.json", "plan-full-snapshot.json")) {
            MetricMaterializationPlanDsl plan = WindJson.parseObject(example(name), MetricMaterializationPlanDsl.class);
            assertEquals(plan, parse(properties(plan)));
            assertEquals(definitions.keySet(), new HashSet<>(plan.metrics()));
            MetricDSLDefinition first = definitions.get(plan.metrics().getFirst());
            for (MetricReferenceDsl reference : plan.metrics()) {
                MetricDSLDefinition definition = definitions.get(reference);
                assertEquals(first.subject(), definition.subject());
                assertEquals(first.time(), definition.time());
                assertEquals(first.dimensions(), definition.dimensions());
            }
        }
    }

    /**
     * 场景：近期实时，全部更早历史作为一个快照段。
     * 输入：P90D 实时规则及省略粒度的历史快照规则。
     * 流程：经真实 JSON 入口读取并保存计划。
     * 预期：来源和声明顺序保持，不增加段编码、重复窗口或分段保存目标。
     */
    @Test
    void testRecentWindowAndWholeHistoryRoundTrip() {
        List<Map<String, Object>> segments = rollingSegments(null);
        MetricMaterializationPlanDsl restored = parse(plan("SEGMENTED", segments));

        assertEquals(segments, properties(restored).get("segments"));
        assertEquals(List.of(MetricSegmentSourceType.REALTIME, MetricSegmentSourceType.SNAPSHOT),
                restored.segments().stream().map(MetricSegmentDsl::sourceType).toList());
        assertEquals(restored, parse(properties(restored)));
        assertFalse(WindJson.toJsonString(restored).contains("segmentCode"));
        assertFalse(properties(restored).containsKey("recentWindow"));
        assertThrows(UnsupportedOperationException.class, () -> restored.segments().clear());
    }

    /**
     * 场景：同一宽表计划的多个指标共用年度快照配置。
     * 输入：两个精确成员版本、一个保存目标及 YEAR 历史规则。
     * 流程：读取后再次保存计划。
     * 预期：成员共享一份分段和目标，粒度复用公共枚举，JSON 不再出现 splitBy。
     */
    @Test
    void testYearlyHistorySharesOnePolicyAndTarget() {
        Map<String, Object> body = plan("SEGMENTED", rollingSegments("YEAR"));
        body.put("metrics", List.of(Map.of("metricCode", "COUNT", "definitionRevision", 1),
                Map.of("metricCode", "AMOUNT", "definitionRevision", 3)));
        body.put("snapshotTarget", target("WIDE_TABLE"));

        MetricMaterializationPlanDsl restored = parse(body);

        assertEquals(2, restored.metrics().size());
        assertEquals(MetricSnapshotGranularity.YEAR, restored.segments().getLast().snapshotGranularity());
        assertEquals(body, properties(restored));
        assertFalse(WindJson.toJsonString(restored).contains("splitBy"));
        assertThrows(UnsupportedOperationException.class, () -> restored.metrics().clear());
    }

    /**
     * 场景：固定期间按段选择实时或快照来源。
     * 输入：2025 年实时、2024 年快照，近到远且边界相接。
     * 流程：读取并保存计划。
     * 预期：ISO 时间成为强类型边界，来源与区间保持，不引入表达式求值。
     */
    @Test
    void testFixedRangesRetainSourcesAndBoundaries() {
        List<Map<String, Object>> segments = List.of(
                fixed("REALTIME", "2025-01-01T00:00:00", "2026-01-01T00:00:00"),
                fixed("SNAPSHOT", "2024-01-01T00:00:00", "2025-01-01T00:00:00"));
        MetricMaterializationPlanDsl restored = parse(plan("SEGMENTED", segments));

        assertEquals(LocalDateTime.of(2025, 1, 1, 0, 0), restored.segments().getFirst().start());
        assertEquals(restored.segments().getFirst().start(), restored.segments().getLast().end());
        assertEquals(restored, parse(properties(restored)));
        assertEquals(MetricSegmentSourceType.REALTIME, restored.segments().getFirst().sourceType());
    }

    /**
     * 场景：固定边界不能被宿主默认日期格式或精度设置改写。
     * 输入：含九位小数秒的 ISO 本地时间。
     * 流程：通过 WindJson 读取、保存并再次读取。
     * 预期：纳秒及 ISO 字符串不变，边界不会被截断到秒或毫秒。
     */
    @Test
    void testFixedBoundaryPrecisionSurvivesJsonRoundTrip() {
        String start = "2024-01-01T00:00:00.123456789";
        String end = "2025-01-01T00:00:00.123456789";
        MetricMaterializationPlanDsl restored = parse(plan("SEGMENTED", List.of(fixed("SNAPSHOT", start, end))));

        assertEquals(123456789, restored.segments().getFirst().start().getNano());
        assertEquals(start, properties(restored.segments().getFirst()).get("start"));
        assertEquals(end, properties(restored.segments().getFirst()).get("end"));
        assertEquals(restored, parse(properties(restored)));
    }

    /**
     * 场景：最远固定段可以没有下界，全快照也可以按明确的固定范围组织。
     * 输入：两个相接的历史快照区间，最远段 start 为 null。
     * 流程：读取计划。
     * 预期：逻辑无下界保留，不要求原点或补造实时段。
     */
    @Test
    void testOldestFixedRangeMayBeUnbounded() {
        MetricMaterializationPlanDsl restored = parse(plan("SEGMENTED", List.of(
                fixed("SNAPSHOT", "2024-01-01T00:00:00", "2025-01-01T00:00:00"),
                fixed("SNAPSHOT", null, "2024-01-01T00:00:00"))));

        assertNull(restored.segments().getLast().start());
        assertEquals(restored, parse(properties(restored)));
    }

    /**
     * 场景：全量快照不需要分段配置。
     * 输入：SNAPSHOT 模式、统一保存目标、DAY 刷新周期及缺省 segments。
     * 流程：读取后再次保存。
     * 预期：分段为空，根粒度保留，不自动添加实时或历史规则。
     */
    @Test
    void testFullSnapshotDoesNotInventSegments() {
        Map<String, Object> body = plan("SNAPSHOT", List.of());
        body.remove("segments");
        body.put("snapshotGranularity", "DAY");
        MetricMaterializationPlanDsl restored = parse(body);

        assertTrue(restored.segments().isEmpty());
        assertEquals(MetricSnapshotGranularity.DAY, restored.snapshotGranularity());
        assertEquals(restored, parse(properties(restored)));
    }

    /**
     * 场景：窗口只有一个含义，不能与固定边界或多档历史窗口组合。
     * 输入：非正数、月份、溢出等非法窗口，快照窗口和窗口/固定混合。
     * 流程：经 JSON 保存入口逐项校验。
     * 预期：所有歧义或不支持的声明明确失败。
     */
    @Test
    void testInvalidAndMixedWindowsAreRejected() {
        for (String window : List.of("P0D", "P-1D", "P1M", "PT0H", "P1DT1H", "90d", " ", "P999999999999999999999D")) {
            assertInvalid(plan("SEGMENTED", List.of(Map.of("sourceType", "REALTIME", "window", window),
                    Map.of("sourceType", "SNAPSHOT"))));
        }
        assertInvalid(plan("SEGMENTED", List.of(Map.of("sourceType", "REALTIME", "window", "P30D"),
                Map.of("sourceType", "SNAPSHOT", "window", "P60D", "snapshotGranularity", "MONTH"),
                Map.of("sourceType", "SNAPSHOT", "snapshotGranularity", "YEAR"))));
        Map<String, Object> mixed = fixed("REALTIME", "2025-01-01T00:00:00", "2026-01-01T00:00:00");
        mixed.put("window", "P90D");
        assertInvalid(plan("SEGMENTED", List.of(mixed, Map.of("sourceType", "SNAPSHOT"))));
        assertInvalid(plan("SEGMENTED", List.of(Map.of("sourceType", "REALTIME", "window", "P90D"),
                fixed("SNAPSHOT", "2024-01-01T00:00:00", "2025-01-01T00:00:00"))));
    }

    /**
     * 场景：时间声明应当形成无遗漏、不重复的连续固定区间。
     * 输入：缺终点、空区间、反向区间、无效时间、缺口、重叠、远近顺序颠倒及非最远段无下界。
     * 流程：逐项读取固定范围计划。
     * 预期：全部失败，不能通过排序、裁剪或默认值修补。
     */
    @Test
    void testInvalidFixedRangesAreRejected() {
        for (String end : List.of("2025-01-01T00:00:00", "2024-01-01T00:00:00", "not-a-time")) {
            assertThrows(JacksonException.class, () -> parse(plan("SEGMENTED", List.of(
                    fixed("SNAPSHOT", "2025-01-01T00:00:00", end)))));
        }
        assertInvalid(plan("SEGMENTED", List.of(Map.of("sourceType", "SNAPSHOT", "start", "2025-01-01T00:00:00"))));
        for (String end : List.of("2024-12-31T00:00:00", "2025-01-02T00:00:00")) {
            assertInvalid(plan("SEGMENTED", List.of(
                    fixed("REALTIME", "2025-01-01T00:00:00", "2026-01-01T00:00:00"),
                    fixed("SNAPSHOT", "2024-01-01T00:00:00", end))));
        }
        assertInvalid(plan("SEGMENTED", List.of(
                fixed("SNAPSHOT", "2024-01-01T00:00:00", "2025-01-01T00:00:00"),
                fixed("REALTIME", "2025-01-01T00:00:00", "2026-01-01T00:00:00"))));
        assertInvalid(plan("SEGMENTED", List.of(fixed("REALTIME", null, "2026-01-01T00:00:00"),
                fixed("SNAPSHOT", "2024-01-01T00:00:00", "2025-01-01T00:00:00"))));
    }

    /**
     * 场景：查询只按实际快照合并并接续实时，不解释配置中的中间实时洞。
     * 输入：固定范围连续，但把 REALTIME 放在最远端或与历史快照交错。
     * 流程：通过公共 JSON 保存入口读取计划。
     * 预期：均拒绝；固定快照必须组成连续历史前缀，实时只允许最近端。
     */
    @Test
    void testFixedRangesKeepSnapshotsAsContinuousHistory() {
        assertInvalid(plan("SEGMENTED", List.of(
                fixed("SNAPSHOT", "2025-01-01T00:00:00", "2026-01-01T00:00:00"),
                fixed("REALTIME", "2024-01-01T00:00:00", "2025-01-01T00:00:00"))));
        assertInvalid(plan("SEGMENTED", List.of(
                fixed("REALTIME", "2025-01-01T00:00:00", "2026-01-01T00:00:00"),
                fixed("SNAPSHOT", "2024-01-01T00:00:00", "2025-01-01T00:00:00"),
                fixed("REALTIME", "2023-01-01T00:00:00", "2024-01-01T00:00:00"))));
    }

    /**
     * 场景：模式、保存目标和分段必须只有一份有效配置。
     * 输入：空分段、实时计划、无快照来源、重复根粒度或缺少保存目标。
     * 流程：读取各非法计划。
     * 预期：明确失败，不能存成另一种执行模式。
     */
    @Test
    void testModeAndTargetConflictsAreRejected() {
        assertInvalid(plan("SEGMENTED", List.of()));
        assertInvalid(plan("REALTIME", List.of()));
        assertInvalid(plan("SNAPSHOT", List.of()));
        assertInvalid(plan("SNAPSHOT", rollingSegments(null)));
        assertInvalid(plan("SEGMENTED", List.of(fixed("REALTIME", "2025-01-01T00:00:00", "2026-01-01T00:00:00"))));
        Map<String, Object> rootGrain = plan("SEGMENTED", rollingSegments("YEAR"));
        rootGrain.put("snapshotGranularity", "MONTH");
        assertInvalid(rootGrain);
        Map<String, Object> noTarget = plan("SEGMENTED", rollingSegments(null));
        noTarget.remove("snapshotTarget");
        assertThrows(JacksonException.class, () -> parse(noTarget));
        assertInvalid(plan("SEGMENTED", List.of(
                Map.of("sourceType", "REALTIME", "window", "P90D", "snapshotGranularity", "YEAR"),
                Map.of("sourceType", "SNAPSHOT"))));
    }

    /**
     * 场景：旧协议字段不能在新 schema 中静默丢失。
     * 输入：recentWindow、segmentCode、splitBy 或分段级 snapshotTarget。
     * 流程：把旧字段加入合法的新计划并读取。
     * 预期：逐项拒绝，不产生第二份配置源或兼容别名。
     */
    @Test
    void testRetiredFieldsAreRejected() {
        Map<String, Object> body = plan("SEGMENTED", rollingSegments(null));
        body.put("recentWindow", "P90D");
        assertThrows(JacksonException.class, () -> parse(body));
        for (String field : List.of("segmentCode", "splitBy", "snapshotTarget")) {
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("sourceType", "SNAPSHOT");
            snapshot.put(field, field.equals("snapshotTarget") ? target("METRIC_VALUE_TABLE") : "YEAR");
            assertThrows(JacksonException.class, () -> parse(plan("SEGMENTED", List.of(
                    Map.of("sourceType", "REALTIME", "window", "P90D"), snapshot))));
        }
    }

    private static List<Map<String, Object>> rollingSegments(@Nullable String granularity) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("sourceType", "SNAPSHOT");
        if (granularity != null) {
            snapshot.put("snapshotGranularity", granularity);
        }
        return List.of(Map.of("sourceType", "REALTIME", "window", "P90D"), snapshot);
    }

    private static String example(String name) throws IOException {
        try (InputStream input = MetricMaterializationPlanDslTests.class.getResourceAsStream("/metrics/plan-segments/" + name)) {
            assertNotNull(input);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static Map<String, Object> fixed(String source, @Nullable String start, String end) {
        Map<String, Object> segment = new LinkedHashMap<>();
        segment.put("sourceType", source);
        segment.put("start", start);
        segment.put("end", end);
        return segment;
    }

    private static Map<String, Object> plan(String mode, List<Map<String, Object>> segments) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("schemaVersion", 4);
        body.put("executionMode", mode);
        body.put("dimensionKeyProviderCode", "CUSTOMER");
        body.put("metrics", List.of(Map.of("metricCode", "COUNT", "definitionRevision", 1)));
        body.put("snapshotTarget", target("METRIC_VALUE_TABLE"));
        body.put("segments", new ArrayList<>(segments));
        return body;
    }

    private static Map<String, Object> target(String storageType) {
        return Map.of("storageType", storageType, "bucketTimeField", "bucketTime", "objectTypeClassName", "com.example.Snapshot");
    }

    private static MetricMaterializationPlanDsl parse(Map<String, Object> body) {
        return WindJson.parseObject(WindJson.toJsonString(body), MetricMaterializationPlanDsl.class);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> properties(Object value) {
        return WindJson.parseObject(WindJson.toJsonString(value), Map.class);
    }

    private static void assertInvalid(Map<String, Object> body) {
        Throwable failure = assertThrows(JacksonException.class, () -> parse(body));
        while (failure != null && !(failure instanceof MetricValidationException)) {
            failure = failure.getCause();
        }
        assertTrue(failure instanceof MetricValidationException);
        assertEquals(MetricErrorCode.DSL_PLAN_INVALID, ((MetricValidationException) failure).errorCode());
    }
}
