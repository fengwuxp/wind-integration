package com.wind.integration.metrics.materialization;

import com.wind.integration.metrics.MetricValidationException;
import com.wind.integration.metrics.enums.MetricErrorCode;
import com.wind.integration.metrics.enums.SnapshotGranularity;
import com.wind.jackson.WindJson;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 物化返回的分段覆盖、完成语义和不可变合同测试，不代替消费端的物化执行测试。
 *
 * @author wuxp
 * @since 2026-09-14
 */
class MetricMaterializationResultContractTests {

    private static final ZoneId TIME_ZONE = ZoneId.of("Asia/Shanghai");

    private static final Instant START = Instant.parse("2026-09-12T16:00:00Z");

    private static final Instant DAY_END = Instant.parse("2026-09-13T16:00:00Z");

    private static final Instant HOUR_END = Instant.parse("2026-09-14T07:00:00Z");

    @Test
    void testPreserveIndependentSegmentCoverageAndDefensiveCopy() {
        MetricMaterializationSegmentResult archive = segment("archive", SnapshotGranularity.DAY, DAY_END, DAY_END);
        MetricMaterializationSegmentResult recent = segment("recent", SnapshotGranularity.HOUR, HOUR_END, HOUR_END);
        List<MetricMaterializationSegmentResult> segments = new ArrayList<>(List.of(archive, recent));

        MetricMaterializationResult result = new MetricMaterializationResult(segments);
        segments.clear();

        Assertions.assertEquals(List.of(archive, recent), result.segments());
        Assertions.assertEquals(DAY_END, result.segments().getFirst().watermarkTime());
        Assertions.assertEquals(HOUR_END, result.segments().getLast().watermarkTime());
        Assertions.assertThrows(UnsupportedOperationException.class, () -> result.segments().clear());
    }

    @Test
    void testAllowInitializedEmptyCoverageAndAlreadyCoveredTarget() {
        MetricMaterializationSegmentResult empty = segment("snapshot", SnapshotGranularity.DAY, START, START);
        MetricMaterializationSegmentResult covered = segment("snapshot", SnapshotGranularity.DAY, DAY_END, START);

        Assertions.assertEquals(empty.queryableStartTime(), empty.watermarkTime());
        Assertions.assertEquals(DAY_END, covered.watermarkTime());
        Assertions.assertEquals(START, covered.effectiveTargetTime());
        Assertions.assertEquals(List.of(empty), new MetricMaterializationResult(List.of(empty)).segments());
    }

    @Test
    void testRejectIncompleteTargetAndReversedCoverage() {
        assertInvalid("/watermarkTime", () -> segment("snapshot", SnapshotGranularity.DAY, DAY_END, HOUR_END));
        assertInvalid("/watermarkTime", () -> segment("snapshot", SnapshotGranularity.DAY, START.minusSeconds(1), START));
    }

    @Test
    void testRejectMissingOrDuplicateSegmentResults() {
        MetricMaterializationSegmentResult snapshot = segment("snapshot", SnapshotGranularity.DAY, DAY_END, DAY_END);

        assertInvalid("/segments", () -> new MetricMaterializationResult(null));
        assertInvalid("/segments", () -> new MetricMaterializationResult(List.of()));
        assertInvalid("/segments/1", () -> new MetricMaterializationResult(Arrays.asList(snapshot, null)));
        assertInvalid("/segments/1/segmentCode", () -> new MetricMaterializationResult(List.of(snapshot, snapshot)));
    }

    @Test
    void testRejectMissingSegmentMetadata() {
        assertInvalid("/segmentCode", () -> segment(" ", SnapshotGranularity.DAY, DAY_END, DAY_END));
        assertInvalid("/segmentCode", () -> segment(null, SnapshotGranularity.DAY, DAY_END, DAY_END));
        assertInvalid("/snapshotGranularity", () -> segment("snapshot", null, DAY_END, DAY_END));
        assertInvalid("/timeZone", () -> new MetricMaterializationSegmentResult(
                "snapshot", SnapshotGranularity.DAY, null, START, DAY_END, DAY_END));
        assertInvalid("/queryableStartTime", () -> new MetricMaterializationSegmentResult(
                "snapshot", SnapshotGranularity.DAY, TIME_ZONE, null, DAY_END, DAY_END));
        assertInvalid("/watermarkTime", () -> segment("snapshot", SnapshotGranularity.DAY, null, DAY_END));
        assertInvalid("/effectiveTargetTime", () -> segment("snapshot", SnapshotGranularity.DAY, DAY_END, null));
    }

    @Test
    void testRoundTripIndependentSegmentProgressThroughWindJson() {
        MetricMaterializationResult expected = new MetricMaterializationResult(List.of(
                segment("archive", SnapshotGranularity.DAY, DAY_END, DAY_END),
                segment("recent", SnapshotGranularity.HOUR, HOUR_END, HOUR_END)));

        String json = WindJson.getJsonMapper().writeValueAsString(expected);
        MetricMaterializationResult actual = WindJson.getJsonMapper().readValue(json, MetricMaterializationResult.class);

        Assertions.assertEquals(expected, actual);
        Assertions.assertEquals(TIME_ZONE, actual.segments().getFirst().timeZone());
    }

    private static MetricMaterializationSegmentResult segment(
            String code, SnapshotGranularity granularity, Instant watermark, Instant target) {
        return new MetricMaterializationSegmentResult(code, granularity, TIME_ZONE, START, watermark, target);
    }

    private static void assertInvalid(String path, Executable action) {
        MetricValidationException exception = Assertions.assertThrows(MetricValidationException.class, action);
        Assertions.assertEquals(MetricErrorCode.RESULT_INVALID, exception.errorCode());
        Assertions.assertEquals(path, exception.fieldPath());
    }
}
