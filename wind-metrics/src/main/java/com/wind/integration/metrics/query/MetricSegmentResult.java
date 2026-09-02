package com.wind.integration.metrics.query;

import com.wind.integration.metrics.enums.MetricErrorCode;
import com.wind.integration.metrics.enums.MetricSegmentSourceType;
import com.wind.integration.metrics.enums.SnapshotGranularity;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.time.LocalDateTime;

import static com.wind.integration.metrics.query.MetricQueryValueSupport.error;

/**
 * 单次指标查询实际执行的一个连续时间分段摘要。
 *
 * <p>快照分段必须返回粒度和连续覆盖区间，实时分段只返回计算时间。</p>
 *
 * @param segmentCode 实际分段编码，当前为 {@code archive} 或 {@code recent}
 * @param sourceType 分段实际数据来源
 * @param startTime 分段开始时间，包含
 * @param endTime 分段结束时间，不包含
 * @param snapshotGranularity 快照桶粒度；实时分段为空
 * @param queryableStartTime 快照连续可读区间下界，包含；实时分段为空
 * @param watermarkTime 快照连续覆盖上界，不包含；实时分段为空
 * @param calculatedTime 实时计算完成时间；快照分段为空
 *
 * @author wuxp
 * @date 2026-07-21 17:51
 */
@Schema(description = "指标查询结果的连续时间分段摘要")
public record MetricSegmentResult(
        @Schema(description = "实际分段编码") String segmentCode,
        @Schema(description = "分段实际数据来源") MetricSegmentSourceType sourceType,
        @Schema(description = "分段开始时间，包含") LocalDateTime startTime,
        @Schema(description = "分段结束时间，不包含") LocalDateTime endTime,
        @Nullable @Schema(description = "快照桶粒度；实时分段为空") SnapshotGranularity snapshotGranularity,
        @Nullable @Schema(description = "快照连续可读区间下界；实时分段为空") LocalDateTime queryableStartTime,
        @Nullable @Schema(description = "快照连续覆盖上界；实时分段为空") LocalDateTime watermarkTime,
        @Nullable @Schema(description = "实时计算完成时间；快照分段为空") LocalDateTime calculatedTime) {

    public MetricSegmentResult {
        if (segmentCode == null || segmentCode.isBlank()) {
            throw error(MetricErrorCode.RESULT_INVALID, "/segmentCode", "segmentCode must not be blank");
        }
        if (sourceType == null) {
            throw error(MetricErrorCode.RESULT_INVALID, "/sourceType", "sourceType must not be null");
        }
        MetricQueryValueSupport.validateWindow(startTime, endTime, MetricErrorCode.RESULT_INVALID);
        if (sourceType == MetricSegmentSourceType.SNAPSHOT) {
            if (snapshotGranularity == null) {
                throw error(MetricErrorCode.RESULT_INVALID, "/snapshotGranularity", "Snapshot granularity is required");
            }
            if (queryableStartTime == null) {
                throw error(MetricErrorCode.RESULT_INVALID, "/queryableStartTime", "Snapshot coverage is required");
            }
            if (watermarkTime == null) {
                throw error(MetricErrorCode.RESULT_INVALID, "/watermarkTime", "Snapshot watermark is required");
            }
            if (calculatedTime != null) {
                throw error(
                        MetricErrorCode.RESULT_INVALID,
                        "/calculatedTime",
                        "Snapshot segment forbids calculatedTime");
            }
            if (queryableStartTime.isAfter(startTime) || watermarkTime.isBefore(endTime)) {
                throw error(
                        MetricErrorCode.RESULT_INVALID,
                        "/watermarkTime",
                        "Snapshot coverage does not contain segment");
            }
        } else {
            if (snapshotGranularity != null || queryableStartTime != null || watermarkTime != null) {
                throw error(MetricErrorCode.RESULT_INVALID, "", "Realtime segment forbids snapshot coverage");
            }
            if (calculatedTime == null) {
                throw error(MetricErrorCode.RESULT_INVALID, "/calculatedTime", "Realtime calculatedTime is required");
            }
        }
    }
}
