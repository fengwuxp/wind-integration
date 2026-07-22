package com.wind.integration.metrics.query;

import com.wind.integration.metrics.MetricValidationException;
import com.wind.integration.metrics.enums.MetricErrorCode;
import com.wind.integration.metrics.enums.MetricExecutionMode;
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

    @Test
    void testScalarResultAllowsNormalNull() {
        MetricResult result = new MetricResult(
                "VCC_APPROVED_TOTAL",
                1,
                MetricExecutionMode.REALTIME,
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

    @Test
    void testFieldSetResultKeepsImmutableValues() {
        Map<String, MetricFieldValue> fields = new LinkedHashMap<>();
        fields.put("approvalRate", new MetricFieldValue(MetricValueType.DECIMAL, new BigDecimal("0.7500")));

        MetricResult result = new MetricResult(
                "VCC_AUTH_SUMMARY",
                1,
                MetricExecutionMode.REALTIME,
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

    @Test
    void testRejectDoubleMetricValue() {
        MetricValidationException exception = Assertions.assertThrows(
                MetricValidationException.class,
                () -> new MetricFieldValue(MetricValueType.DECIMAL, 0.75D));

        Assertions.assertEquals(MetricErrorCode.RESULT_INVALID, exception.errorCode());
        Assertions.assertEquals("/value", exception.fieldPath());
    }

    @Test
    void testSnapshotSegmentRequiresCoverage() {
        MetricValidationException exception = Assertions.assertThrows(
                MetricValidationException.class,
                () -> new MetricSegmentResult(
                        "archive",
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

    @Test
    void testSegmentedResultRejectsCoverageGap() {
        LocalDateTime cutoverTime = LocalDateTime.of(2026, 4, 15, 0, 0);
        List<MetricSegmentResult> segments = List.of(
                new MetricSegmentResult(
                        "archive",
                        MetricSegmentSourceType.SNAPSHOT,
                        START_TIME,
                        cutoverTime,
                        SnapshotGranularity.DAY,
                        START_TIME,
                        cutoverTime,
                        null),
                new MetricSegmentResult(
                        "recent",
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
                        MetricExecutionMode.SEGMENTED,
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

    @Test
    void testSegmentedResultAcceptsSupportedExecutionShapes() {
        LocalDateTime cutoverTime = LocalDateTime.of(2026, 4, 15, 0, 0);
        MetricSegmentResult archive = new MetricSegmentResult(
                "archive",
                MetricSegmentSourceType.SNAPSHOT,
                START_TIME,
                cutoverTime,
                SnapshotGranularity.DAY,
                START_TIME,
                cutoverTime,
                null);
        MetricSegmentResult realtimeRecent = new MetricSegmentResult(
                "recent",
                MetricSegmentSourceType.REALTIME,
                cutoverTime,
                END_TIME,
                null,
                null,
                null,
                CALCULATED_TIME);
        MetricSegmentResult snapshotRecent = new MetricSegmentResult(
                "recent",
                MetricSegmentSourceType.SNAPSHOT,
                cutoverTime,
                END_TIME,
                SnapshotGranularity.DAY,
                cutoverTime,
                END_TIME,
                null);
        MetricSegmentResult onlyRecent = new MetricSegmentResult(
                "recent",
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
                MetricExecutionMode.SEGMENTED,
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
