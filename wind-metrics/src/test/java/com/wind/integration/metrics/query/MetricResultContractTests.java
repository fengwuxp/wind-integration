package com.wind.integration.metrics.query;

import com.wind.integration.metrics.MetricValidationException;
import com.wind.integration.metrics.enums.MetricErrorCode;
import com.wind.integration.metrics.enums.MetricQueryMode;
import com.wind.integration.metrics.enums.MetricSegmentCode;
import com.wind.integration.metrics.enums.MetricSegmentSourceType;
import com.wind.integration.metrics.enums.MetricValueShape;
import com.wind.integration.metrics.enums.MetricValueType;
import com.wind.integration.metrics.enums.SnapshotGranularity;
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
     * 场景：没有数据的标量结果允许正常 null。
     * 输入：实时 LONG 指标、value=null、空字段集合。
     * 流程：构造 MetricResult。
     * 预期：value 保持 null，fields 为空。
     */
    @Test
    void testScalarResultAllowsNormalNull() {
        MetricResult result = new MetricResult(
                "VCC_APPROVED_TOTAL",
                1,
                MetricQueryMode.REALTIME,
                null,
                null,
                MetricValueShape.SCALAR,
                MetricValueType.LONG,
                null,
                Map.of(),
                "cust_empty",
                START_TIME,
                END_TIME,
                CALCULATED_TIME,
                ZoneId.of("Asia/Shanghai"),
                null,
                null,
                null,
                null,
                List.of());

        Assertions.assertNull(result.value());
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

        MetricResult result = new MetricResult(
                "VCC_AUTH_SUMMARY",
                1,
                MetricQueryMode.REALTIME,
                null,
                null,
                MetricValueShape.FIELD_SET,
                null,
                null,
                fields,
                "cust_001",
                START_TIME,
                END_TIME,
                CALCULATED_TIME,
                ZoneId.of("Asia/Shanghai"),
                null,
                null,
                null,
                null,
                List.of());
        fields.clear();

        Assertions.assertEquals(new BigDecimal("0.7500"), result.fields().get("approvalRate").value());
        Assertions.assertThrows(UnsupportedOperationException.class, () -> result.fields().clear());
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
                        SnapshotGranularity.DAY,
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
                        SnapshotGranularity.DAY,
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
                () -> new MetricResult(
                        "VCC_AUTH_SUMMARY",
                        1,
                        MetricQueryMode.SEGMENTED,
                        null,
                        null,
                        MetricValueShape.FIELD_SET,
                        null,
                        null,
                        Map.of("approvedTotal", new MetricFieldValue(MetricValueType.LONG, 3L)),
                        "cust_001",
                        START_TIME,
                        END_TIME,
                        CALCULATED_TIME,
                        ZoneId.of("Asia/Shanghai"),
                        null,
                        null,
                        null,
                        null,
                        segments));

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
                SnapshotGranularity.DAY,
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
                SnapshotGranularity.DAY,
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

    private MetricResult newSegmentedResult(List<MetricSegmentResult> segments) {
        return new MetricResult(
                "VCC_AUTH_SUMMARY",
                1,
                MetricQueryMode.SEGMENTED,
                null,
                null,
                MetricValueShape.FIELD_SET,
                null,
                null,
                Map.of("approvedTotal", new MetricFieldValue(MetricValueType.LONG, 3L)),
                "cust_001",
                START_TIME,
                END_TIME,
                CALCULATED_TIME,
                ZoneId.of("Asia/Shanghai"),
                null,
                null,
                null,
                null,
                segments);
    }
}
