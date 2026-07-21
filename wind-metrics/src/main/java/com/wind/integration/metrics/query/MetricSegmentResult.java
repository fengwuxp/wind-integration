package com.wind.integration.metrics.query;

import com.wind.integration.metrics.dsl.MetricDslErrorCode;
import com.wind.integration.metrics.dsl.MetricSegmentSourceType;
import com.wind.integration.metrics.dsl.SnapshotGranularity;
import org.jspecify.annotations.Nullable;

import java.time.LocalDateTime;

import static com.wind.integration.metrics.query.MetricQueryValueSupport.error;

/** 单次指标查询实际执行的分段摘要。 */
public record MetricSegmentResult(String segmentCode,
                                  MetricSegmentSourceType sourceType,
                                  LocalDateTime startTime,
                                  LocalDateTime endTime,
                                  @Nullable SnapshotGranularity snapshotGranularity,
                                  @Nullable LocalDateTime queryableStartTime,
                                  @Nullable LocalDateTime watermarkTime,
                                  @Nullable LocalDateTime calculatedTime) {

    public MetricSegmentResult {
        if (segmentCode == null || segmentCode.isBlank()) {
            throw error(MetricDslErrorCode.RESULT_INVALID, "/segmentCode", "segmentCode must not be blank");
        }
        if (sourceType == null) {
            throw error(MetricDslErrorCode.RESULT_INVALID, "/sourceType", "sourceType must not be null");
        }
        MetricQueryValueSupport.validateWindow(startTime, endTime, MetricDslErrorCode.RESULT_INVALID);
        if (sourceType == MetricSegmentSourceType.SNAPSHOT) {
            if (snapshotGranularity == null) {
                throw error(MetricDslErrorCode.RESULT_INVALID, "/snapshotGranularity", "Snapshot granularity is required");
            }
            if (queryableStartTime == null) {
                throw error(MetricDslErrorCode.RESULT_INVALID, "/queryableStartTime", "Snapshot coverage is required");
            }
            if (watermarkTime == null) {
                throw error(MetricDslErrorCode.RESULT_INVALID, "/watermarkTime", "Snapshot watermark is required");
            }
            if (calculatedTime != null) {
                throw error(MetricDslErrorCode.RESULT_INVALID, "/calculatedTime", "Snapshot segment forbids calculatedTime");
            }
            if (queryableStartTime.isAfter(startTime) || watermarkTime.isBefore(endTime)) {
                throw error(MetricDslErrorCode.RESULT_INVALID, "/watermarkTime", "Snapshot coverage does not contain segment");
            }
        } else {
            if (snapshotGranularity != null || queryableStartTime != null || watermarkTime != null) {
                throw error(MetricDslErrorCode.RESULT_INVALID, "", "Realtime segment forbids snapshot coverage");
            }
            if (calculatedTime == null) {
                throw error(MetricDslErrorCode.RESULT_INVALID, "/calculatedTime", "Realtime calculatedTime is required");
            }
        }
    }
}
