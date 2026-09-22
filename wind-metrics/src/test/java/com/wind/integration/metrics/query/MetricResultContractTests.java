package com.wind.integration.metrics.query;

import com.wind.integration.metrics.MetricValidationException;
import com.wind.integration.metrics.WindMetricsValue;
import com.wind.integration.metrics.enums.MetricErrorCode;
import com.wind.integration.metrics.enums.MetricQueryMode;
import com.wind.integration.metrics.enums.MetricSegmentCode;
import com.wind.integration.metrics.enums.MetricSegmentSourceType;
import com.wind.integration.metrics.enums.MetricValueShape;
import com.wind.integration.metrics.enums.MetricValueType;
import com.wind.integration.metrics.enums.MetricSnapshotGranularity;
import com.wind.jackson.WindJson;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 指标结果模型的公共合同测试。
 *
 * @author wuxp
 * @date 2026-07-21 17:51
 */
class MetricResultContractTests {

    private static final LocalDateTime START_TIME = LocalDateTime.of(2026, 3, 1, 0, 0);

    private static final LocalDateTime END_TIME = LocalDateTime.of(2026, 7, 15, 0, 0);

    private static final LocalDateTime CALCULATED_TIME = LocalDateTime.of(2026, 7, 15, 0, 0, 0, 2_000_000);

    /**
     * 场景：结果类型只承担查询身份、上下文和执行信息，值类型归属于值。
     * 输入：一个 LONG 标量结果。
     * 流程：序列化并检查消费者看到的 JSON 结构。
     * 预期：没有 route 字段和顶层 valueType；value 对象自行携带 LONG 类型。
     */
    @Test
    void testResultJsonOwnsOnlyQueryMetadata() {
        MetricResult result = new MetricResult("SCALAR", 1, MetricQueryMode.REALTIME, MetricValueShape.SCALAR,
                WindMetricsValue.of("value", MetricValueType.LONG, 3L), Map.of(), null, START_TIME, END_TIME,
                CALCULATED_TIME, ZoneId.of("Asia/Shanghai"), null, null, null, null, List.of(), List.of());
        Map<?, ?> json = WindJson.getJsonMapper().readValue(WindJson.toJsonString(result), Map.class);

        Assertions.assertFalse(json.containsKey("routeMetricCode"));
        Assertions.assertFalse(json.containsKey("routeDefinitionRevision"));
        Assertions.assertFalse(json.containsKey("valueType"));
        Map<?, ?> value = Assertions.assertInstanceOf(Map.class, json.get("value"));
        Assertions.assertEquals("LONG", value.get("valueType"));
    }

    /**
     * 场景：分段结果标识使用封闭枚举契约。
     * 输入：MetricSegmentResult 的 record 元信息。
     * 流程：读取第一个组件类型。
     * 预期：类型为 MetricSegmentCode，不能退回自由字符串。
     */
    @Test
    void testSegmentResultUsesClosedSegmentCode() {
        Assertions.assertEquals(MetricSegmentCode.class, MetricSegmentResult.class.getRecordComponents()[0].getType());
    }

    /**
     * 场景：没有数据的标量结果保留非空值对象。
     * 输入：实时 LONG 指标、value=null、空字段集合。
     * 流程：构造 MetricResult。
     * 预期：value 携带指标编码且 payload 为 null，fields 为空。
     */
    @Test
    void testScalarResultAllowsNormalNull() {
        MetricResult result = new MetricResult("VCC_APPROVED_TOTAL", 1, MetricQueryMode.REALTIME, MetricValueShape.SCALAR,
                WindMetricsValue.of("value", MetricValueType.LONG, null), Map.of(), "cust_empty", START_TIME,
                END_TIME, CALCULATED_TIME, ZoneId.of("Asia/Shanghai"), null, null, null, null, List.of(),
                List.of());

        Assertions.assertNotNull(result.value());
        Assertions.assertEquals("VCC_APPROVED_TOTAL", result.value().getCode());
        Assertions.assertNull(result.value().getValue());
        Assertions.assertTrue(result.fields().isEmpty());
    }

    /**
     * 场景：结果字段集合必须独立于调用方后续修改。
     * 输入：approvalRate=0.7500 的可变输入 Map。
     * 流程：构造 FIELD_SET 结果后清空原 Map，并尝试修改结果字段。
     * 预期：仍保留0.7500，结果字段不可修改。
     */
    @Test
    void testFieldSetResultKeepsImmutableValues() {
        Map<String, MetricFieldValue> fields = new LinkedHashMap<>();
        fields.put("approvalRate", new MetricFieldValue(MetricValueType.DECIMAL, new BigDecimal("0.7500")));

        MetricResult result = new MetricResult("VCC_AUTH_SUMMARY", 1, MetricQueryMode.REALTIME, MetricValueShape.FIELD_SET, null, fields,
                "cust_001", START_TIME, END_TIME, CALCULATED_TIME, ZoneId.of("Asia/Shanghai"), null, null,
                null, null, List.of(), List.of());
        fields.clear();

        Assertions.assertEquals(new BigDecimal("0.7500"), result.fields().get("approvalRate").value().getValue());
        Assertions.assertEquals("approvalRate", result.fields().get("approvalRate").value().getCode());
        Assertions.assertThrows(UnsupportedOperationException.class, () -> result.fields().clear());
    }

    /**
     * 场景：查询结果在领域边界内保存具名值对象。
     * 输入：携带旧 code 的标量 WindMetricsValue，以及一个字段值对象。
     * 流程：构造 SCALAR 与 FIELD_SET 结果并读取公开组件。
     * 预期：标量 code 绑定到 metricCode，字段 code 绑定到字段 Map key；数值精度保持不变。
     */
    @Test
    void testResultComponentsUseNamedMetricValues() {
        BigDecimal scalar = new BigDecimal("12345678901234567890.1234");
        MetricResult scalarResult = new MetricResult("NAMED_SCALAR", 1, MetricQueryMode.REALTIME, MetricValueShape.SCALAR,
                WindMetricsValue.of("value", MetricValueType.DECIMAL, scalar), Map.of(), null, START_TIME,
                END_TIME, CALCULATED_TIME, ZoneId.of("Asia/Shanghai"), null, null, null, null, List.of(),
                List.of());
        MetricResult fieldResult = new MetricResult("NAMED_FIELDS", 1, MetricQueryMode.REALTIME, MetricValueShape.FIELD_SET, null,
                Map.of("amount", new MetricFieldValue(
                        MetricValueType.DECIMAL, WindMetricsValue.of("legacy-field", scalar))),
                null, START_TIME, END_TIME, CALCULATED_TIME, ZoneId.of("Asia/Shanghai"), null, null, null,
                null, List.of(), List.of());

        Assertions.assertEquals("NAMED_SCALAR", scalarResult.value().getCode());
        Assertions.assertEquals(scalar, scalarResult.value().getValue());
        Assertions.assertEquals("amount", fieldResult.fields().get("amount").value().getCode());
        Assertions.assertEquals(scalar, fieldResult.fields().get("amount").value().getValue());
    }

    /**
     * 场景：指标结果拒绝二进制浮点数。
     * 输入：声明 DECIMAL，输入 Double 0.75。
     * 流程：构造 MetricFieldValue。
     * 预期：报 RESULT_INVALID，定位 /value。
     */
    @Test
    void testRejectDoubleMetricValue() {
        MetricValidationException exception = Assertions.assertThrows(
                MetricValidationException.class,
                () -> new MetricFieldValue(MetricValueType.DECIMAL, 0.75D));

        Assertions.assertEquals(MetricErrorCode.RESULT_INVALID, exception.errorCode());
        Assertions.assertEquals("/value", exception.fieldPath());
    }

    /**
     * 场景：快照分段必须说明可查询覆盖起点。
     * 输入：DAY 快照分段缺 queryableStartTime。
     * 流程：构造 MetricSegmentResult。
     * 预期：报 RESULT_INVALID，定位 /queryableStartTime。
     */
    @Test
    void testSnapshotSegmentRequiresCoverage() {
        MetricValidationException exception = Assertions.assertThrows(
                MetricValidationException.class,
                () -> new MetricSegmentResult(
                        MetricSegmentCode.ARCHIVE,
                        MetricSegmentSourceType.SNAPSHOT,
                        START_TIME,
                        END_TIME,
                        MetricSnapshotGranularity.DAY,
                        null,
                        END_TIME,
                        null));

        Assertions.assertEquals(MetricErrorCode.RESULT_INVALID, exception.errorCode());
        Assertions.assertEquals("/queryableStartTime", exception.fieldPath());
    }

    /**
     * 场景：组合分段不能隐含未覆盖的时间间隙。
     * 输入：归档截至4月15日零点，实时从一小时后开始。
     * 流程：构造覆盖3月1日至7月15日的 SEGMENTED 结果。
     * 预期：报 RESULT_INVALID，定位第二段 startTime。
     */
    @Test
    void testSegmentedResultRejectsCoverageGap() {
        LocalDateTime cutoverTime = LocalDateTime.of(2026, 4, 15, 0, 0);
        List<MetricSegmentResult> segments = List.of(
                new MetricSegmentResult(
                        MetricSegmentCode.ARCHIVE,
                        MetricSegmentSourceType.SNAPSHOT,
                        START_TIME,
                        cutoverTime,
                        MetricSnapshotGranularity.DAY,
                        START_TIME,
                        cutoverTime,
                        null),
                new MetricSegmentResult(
                        MetricSegmentCode.RECENT,
                        MetricSegmentSourceType.REALTIME,
                        cutoverTime.plusHours(1),
                        END_TIME,
                        null,
                        null,
                        null,
                        CALCULATED_TIME));

        MetricValidationException exception = Assertions.assertThrows(
                MetricValidationException.class,
                () -> new MetricResult("VCC_AUTH_SUMMARY", 1, MetricQueryMode.SEGMENTED, MetricValueShape.FIELD_SET, null,
                Map.of("approvedTotal", new MetricFieldValue(MetricValueType.LONG, 3L)), "cust_001",
                START_TIME, END_TIME, CALCULATED_TIME, ZoneId.of("Asia/Shanghai"), null, null, null, null,
                segments, List.of()));

        Assertions.assertEquals(MetricErrorCode.RESULT_INVALID, exception.errorCode());
        Assertions.assertEquals("/segments/1/startTime", exception.fieldPath());
    }

    /**
     * 场景：连续覆盖可由允许的分段形态表达。
     * 输入：仅实时近期段、归档快照加实时近期段、归档快照加近期快照。
     * 流程：分别构造 SEGMENTED 结果。
     * 预期：均接受并保留分段数量和来源类型；此处只验证模型，不执行查询。
     */
    @Test
    void testSegmentedResultAcceptsSupportedExecutionShapes() {
        LocalDateTime cutoverTime = LocalDateTime.of(2026, 4, 15, 0, 0);
        MetricSegmentResult archive = new MetricSegmentResult(
                MetricSegmentCode.ARCHIVE,
                MetricSegmentSourceType.SNAPSHOT,
                START_TIME,
                cutoverTime,
                MetricSnapshotGranularity.DAY,
                START_TIME,
                cutoverTime,
                null);
        MetricSegmentResult realtimeRecent = new MetricSegmentResult(
                MetricSegmentCode.RECENT,
                MetricSegmentSourceType.REALTIME,
                cutoverTime,
                END_TIME,
                null,
                null,
                null,
                CALCULATED_TIME);
        MetricSegmentResult snapshotRecent = new MetricSegmentResult(
                MetricSegmentCode.RECENT,
                MetricSegmentSourceType.SNAPSHOT,
                cutoverTime,
                END_TIME,
                MetricSnapshotGranularity.DAY,
                cutoverTime,
                END_TIME,
                null);
        MetricSegmentResult onlyRecent = new MetricSegmentResult(
                MetricSegmentCode.RECENT,
                MetricSegmentSourceType.REALTIME,
                START_TIME,
                END_TIME,
                null,
                null,
                null,
                CALCULATED_TIME);

        MetricResult singleRecentResult = newSegmentedResult(List.of(onlyRecent));
        MetricResult snapshotRealtimeResult = newSegmentedResult(List.of(archive, realtimeRecent));
        MetricResult doubleSnapshotResult = newSegmentedResult(List.of(archive, snapshotRecent));

        Assertions.assertEquals(1, singleRecentResult.segments().size());
        Assertions.assertEquals(
                MetricSegmentSourceType.REALTIME,
                snapshotRealtimeResult.segments().get(1).sourceType());
        Assertions.assertEquals(MetricSegmentSourceType.SNAPSHOT, doubleSnapshotResult.segments().get(1).sourceType());
    }

    /**
     * 场景：叶子结果保留类型完整的值并省略空 sources。
     * 输入：实时标量结果，不提供 sources。
     * 流程：使用当前构造器序列化再反序列化。
     * 预期：空 sources 省略，当前字段可还原（沿用 WindJson 的时间精度）。
     */
    @Test
    void testLeafResultJsonKeepsTypedValueAndOmitsEmptySources() {
        MetricResult result = new MetricResult("LEGACY", 1, MetricQueryMode.REALTIME, MetricValueShape.SCALAR,
                WindMetricsValue.of("value", MetricValueType.LONG, 3L), Map.of(), null, START_TIME, END_TIME,
                CALCULATED_TIME, ZoneId.of("Asia/Shanghai"), null, null, null, null, List.of(), List.of());

        String json = WindJson.toJsonString(result);

        Assertions.assertFalse(json.contains("\"sources\""));
        MetricResult restored = WindJson.parseObject(json, MetricResult.class);
        Assertions.assertEquals(result.metricCode(), restored.metricCode());
        Assertions.assertEquals(result.value().getValue(), restored.value().getValue());
        Assertions.assertTrue(restored.sources().isEmpty());
        // WindJson 的 LocalDateTime 配置按秒还原；这里只验证当前契约。
        Assertions.assertEquals(CALCULATED_TIME.withNano(0), restored.calculatedTime());
    }

    /**
     * 场景：FIELD_SET JSON 中的 LONG 数值由 Jackson 读成窄整型。
     * 输入：count=3L 的类型完整的字段结果。
     * 流程：序列化后还原 MetricResult。
     * 预期：按声明类型归一化为 Long，字段 JSON 可还原。
     */
    @Test
    void testTypedFieldSetJsonNormalizesIntegralNumbers() {
        MetricResult result = new MetricResult("LEGACY_FIELDS", 1, MetricQueryMode.REALTIME, MetricValueShape.FIELD_SET, null,
                Map.of("count", new MetricFieldValue(MetricValueType.LONG, 3L)), null, START_TIME, END_TIME,
                CALCULATED_TIME, ZoneId.of("Asia/Shanghai"), null, null, null, null, List.of(), List.of());

        MetricResult restored = WindJson.parseObject(WindJson.toJsonString(result), MetricResult.class);

        Assertions.assertEquals(3L, restored.fields().get("count").value().getValue());
    }

    /**
     * 场景：高精度 DECIMAL 标量结果必须直接按 JSON 数字字面量还原。
     * 输入：超过 Double 有效精度且带固定小数位的 BigDecimal。
     * 流程：序列化 MetricResult，再反序列化同一结果类型。
     * 预期：仍为 BigDecimal，数值和 scale 均不变，不经过 Double。
     */
    @Test
    void testDecimalScalarJsonRoundTripPreservesExactPrecision() {
        BigDecimal decimal = new BigDecimal("12345678901234567890.12345678901234567890");
        MetricResult result = new MetricResult("DECIMAL_SCALAR", 1, MetricQueryMode.REALTIME, MetricValueShape.SCALAR,
                WindMetricsValue.of("value", MetricValueType.DECIMAL, decimal), Map.of(), null, START_TIME,
                END_TIME, CALCULATED_TIME, ZoneId.of("Asia/Shanghai"), null, null, null, null, List.of(),
                List.of());

        MetricResult restored = WindJson.parseObject(WindJson.toJsonString(result), MetricResult.class);

        Assertions.assertInstanceOf(BigDecimal.class, restored.value().getValue());
        Assertions.assertEquals(decimal, restored.value().getValue());
    }

    /**
     * 场景：FIELD_SET 中每个 DECIMAL 字段都需要独立保持高精度。
     * 输入：金额与极小比率两个不同 scale 的 BigDecimal 字段。
     * 流程：序列化并还原字段结果。
     * 预期：字段值均为 BigDecimal，原始数值和 scale 保持不变。
     */
    @Test
    void testDecimalFieldSetJsonRoundTripPreservesExactPrecision() {
        BigDecimal amount = new BigDecimal("98765432109876543210.000000000000000001");
        BigDecimal ratio = new BigDecimal("0.000000000000000000000000000001");
        MetricResult result = new MetricResult("DECIMAL_FIELDS", 1, MetricQueryMode.REALTIME, MetricValueShape.FIELD_SET, null,
                Map.of(
                        "amount", new MetricFieldValue(MetricValueType.DECIMAL, amount),
                        "ratio", new MetricFieldValue(MetricValueType.DECIMAL, ratio)),
                null, START_TIME, END_TIME, CALCULATED_TIME, ZoneId.of("Asia/Shanghai"), null, null, null,
                null, List.of(), List.of());

        MetricResult restored = WindJson.parseObject(WindJson.toJsonString(result), MetricResult.class);

        Assertions.assertEquals(amount, restored.fields().get("amount").value().getValue());
        Assertions.assertEquals(ratio, restored.fields().get("ratio").value().getValue());
        Assertions.assertInstanceOf(BigDecimal.class, restored.fields().get("amount").value().getValue());
        Assertions.assertInstanceOf(BigDecimal.class, restored.fields().get("ratio").value().getValue());
    }

    /**
     * 场景：派生指标只读取多个同模式 RAW 来源。
     * 输入：A@1 与 B@2 两个实时来源，顶层暂不传执行摘要。
     * 流程：构造带 sources 的 FIELD_SET 结果。
     * 预期：顶层保留共同 REALTIME，清空 plan 和 segments，不伪造单来源摘要。
     */
    @Test
    void testMultipleSameModeSourcesKeepOnlyCommonMode() {
        MetricResult result = resultWithSources(
                List.of(source("A", 1, MetricQueryMode.REALTIME), source("B", 2, MetricQueryMode.REALTIME)));

        Assertions.assertEquals(MetricQueryMode.REALTIME, result.executionMode());
        Assertions.assertNull(result.planCode());
        Assertions.assertTrue(result.segments().isEmpty());
        Assertions.assertEquals(List.of("A", "B"), result.sources().stream().map(MetricResult::metricCode).toList());
    }

    /**
     * 场景：派生指标混合实时与快照 RAW 来源。
     * 输入：A@1 为实时，B@2 为无计划编码的快照来源。
     * 流程：构造来源摘要并序列化还原。
     * 预期：顶层 executionMode 为 null，根级覆盖、计划和分段均为空，来源明细保留。
     */
    @Test
    void testMixedSourcesExposeNullTopLevelModeWithoutFakingCoverage() {
        MetricResult result = resultWithSources(List.of(
                source("A", 1, MetricQueryMode.REALTIME),
                sourceResult("B", 2, MetricQueryMode.SNAPSHOT, MetricSnapshotGranularity.DAY,
                        START_TIME, END_TIME, null, List.of())));

        Assertions.assertNull(result.executionMode());
        Assertions.assertNull(result.snapshotGranularity());
        Assertions.assertNull(result.queryableStartTime());
        Assertions.assertNull(result.watermarkTime());
        Assertions.assertNull(result.planCode());
        Assertions.assertTrue(result.segments().isEmpty());
        MetricResult restored = WindJson.parseObject(WindJson.toJsonString(result), MetricResult.class);
        Assertions.assertEquals(result.sources(), restored.sources());
        Assertions.assertNull(restored.executionMode());
        Assertions.assertTrue(restored.segments().isEmpty());
    }

    /**
     * 场景：单一快照 RAW 来源允许没有计划编码，计划只作为可选溯源。
     * 输入：SUMMARY@3 的 DAY 快照覆盖完整查询窗口，planCode=null。
     * 流程：构造单来源标量结果并读取顶层摘要。
     * 预期：顶层摘要精确继承该 RAW 来源，缺少计划编码不阻塞结果。
     */
    @Test
    void testSingleSnapshotSourceMayOmitPlanTrace() {
        MetricResult result = resultWithSources(List.of(sourceResult(
                "SUMMARY", 3, MetricQueryMode.SNAPSHOT, MetricSnapshotGranularity.DAY,
                START_TIME, END_TIME, null, List.of())));

        Assertions.assertEquals(MetricQueryMode.SNAPSHOT, result.executionMode());
        Assertions.assertEquals("SUMMARY", result.sources().getFirst().metricCode());
        Assertions.assertEquals(3, result.sources().getFirst().definitionRevision());
        Assertions.assertEquals(MetricSnapshotGranularity.DAY, result.snapshotGranularity());
        Assertions.assertNull(result.planCode());
    }

    /**
     * 场景：多来源结果不能用一个不完整的快照来源冒充完整查询覆盖。
     * 输入：SNAPSHOT 来源的可读起点晚于查询起点。
     * 流程：构造带 sources 的结果。
     * 预期：来源结果在自身构造时拒绝缺失查询窗口覆盖。
     */
    @Test
    void testSnapshotSourceMustCoverQueryWindow() {
        MetricValidationException exception = Assertions.assertThrows(MetricValidationException.class,
                () -> resultWithSources(List.of(
                        sourceResult("SNAPSHOT", 1, MetricQueryMode.SNAPSHOT,
                                MetricSnapshotGranularity.DAY, START_TIME.plusHours(1), END_TIME,
                                null, List.of()))));

        Assertions.assertEquals(MetricErrorCode.RESULT_INVALID, exception.errorCode());
        Assertions.assertEquals("/watermarkTime", exception.fieldPath());
    }

    /**
     * 场景：每个 SEGMENTED RAW 来源独立验证连续覆盖。
     * 输入：ARCHIVE 快照段接 RECENT 实时段，覆盖完整窗口。
     * 流程：构造一个带两段来源的结果，再构造有间隙的来源。
     * 预期：连续来源成功；间隙在来源结果自身构造时拒绝，不把多个来源拼成一条分段链。
     */
    @Test
    void testSegmentedSourceValidatesItsOwnContinuousWindow() {
        MetricSegmentResult archive = new MetricSegmentResult(
                MetricSegmentCode.ARCHIVE, MetricSegmentSourceType.SNAPSHOT, START_TIME,
                START_TIME.plusDays(1), MetricSnapshotGranularity.DAY, START_TIME, START_TIME.plusDays(1), null);
        MetricSegmentResult recent = new MetricSegmentResult(
                MetricSegmentCode.RECENT, MetricSegmentSourceType.REALTIME, START_TIME.plusDays(1), END_TIME,
                null, null, null, CALCULATED_TIME);
        MetricResult valid = sourceResult(
                "SEGMENTED", 4, MetricQueryMode.SEGMENTED, null, null, null, null, List.of(archive, recent));
        Assertions.assertEquals(2, valid.segments().size());

        MetricSegmentResult gap = new MetricSegmentResult(
                MetricSegmentCode.RECENT, MetricSegmentSourceType.REALTIME, START_TIME.plusDays(1).plusHours(1),
                END_TIME, null, null, null, CALCULATED_TIME);
        Assertions.assertThrows(MetricValidationException.class, () -> sourceResult(
                "BROKEN", 1, MetricQueryMode.SEGMENTED, null, null, null, null, List.of(archive, gap)));
    }

    /**
     * 场景：同一个 RAW 编码和修订不能在来源列表中重复。
     * 输入：A@1 两次作为来源。
     * 流程：构造多来源结果。
     * 预期：在结果边界拒绝重复来源，避免把同一读取身份伪装为两份来源。
     */
    @Test
    void testDuplicateSourcesAreRejected() {
        Assertions.assertThrows(MetricValidationException.class, () -> resultWithSources(
                List.of(source("A", 1, MetricQueryMode.REALTIME), source("A", 1, MetricQueryMode.REALTIME))));
    }

    private MetricResult newSegmentedResult(List<MetricSegmentResult> segments) {
        return new MetricResult("VCC_AUTH_SUMMARY", 1, MetricQueryMode.SEGMENTED, MetricValueShape.FIELD_SET, null,
                Map.of("approvedTotal", new MetricFieldValue(MetricValueType.LONG, 3L)), "cust_001",
                START_TIME, END_TIME, CALCULATED_TIME, ZoneId.of("Asia/Shanghai"), null, null, null, null,
                segments, List.of());
    }

    private static MetricResult sourceResult(String code, int revision, MetricQueryMode mode,
                                             MetricSnapshotGranularity granularity, LocalDateTime coverageStart,
                                             LocalDateTime watermark, String plan, List<MetricSegmentResult> segments) {
        return new MetricResult(code, revision, mode, MetricValueShape.SCALAR,
                WindMetricsValue.of(code, MetricValueType.LONG, 3L), Map.of(), null,
                START_TIME, END_TIME, CALCULATED_TIME.withNano(0), ZoneId.of("Asia/Shanghai"),
                granularity, coverageStart, watermark, plan, segments, List.of());
    }

    private static MetricResult source(String code, int revision, MetricQueryMode mode) {
        return sourceResult(code, revision, mode, null, null, null, null, List.of());
    }

    private static MetricResult resultWithSources(List<MetricResult> sources) {
        return new MetricResult("DERIVED", 1, null, MetricValueShape.SCALAR,
                WindMetricsValue.of("value", MetricValueType.LONG, 6L), Map.of(), null, START_TIME, END_TIME,
                CALCULATED_TIME, ZoneId.of("Asia/Shanghai"), MetricSnapshotGranularity.DAY, START_TIME, END_TIME,
                "stale-plan", List.of(), sources);
    }
}
