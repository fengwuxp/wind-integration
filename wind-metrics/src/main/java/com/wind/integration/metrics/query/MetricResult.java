package com.wind.integration.metrics.query;

import com.wind.integration.metrics.dsl.MetricDslErrorCode;
import com.wind.integration.metrics.dsl.MetricExecutionMode;
import com.wind.integration.metrics.dsl.MetricSegmentSourceType;
import com.wind.integration.metrics.dsl.MetricValueShape;
import com.wind.integration.metrics.dsl.MetricValueType;
import com.wind.integration.metrics.dsl.SnapshotGranularity;
import org.jspecify.annotations.Nullable;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.wind.integration.metrics.query.MetricQueryValueSupport.error;

/** 指标查询结果及本次实际数据来源摘要。 */
public record MetricResult(String metricCode,
                           Integer definitionRevision,
                           MetricExecutionMode executionMode,
                           @Nullable String routeMetricCode,
                           @Nullable Integer routeDefinitionRevision,
                           MetricValueShape valueShape,
                           @Nullable MetricValueType valueType,
                           @Nullable Number value,
                           Map<String, MetricFieldValue> fields,
                           @Nullable String subjectId,
                           LocalDateTime startTime,
                           LocalDateTime endTime,
                           LocalDateTime calculatedTime,
                           ZoneId timeZone,
                           @Nullable SnapshotGranularity snapshotGranularity,
                           @Nullable LocalDateTime queryableStartTime,
                           @Nullable LocalDateTime watermarkTime,
                           @Nullable String planCode,
                           List<MetricSegmentResult> segments) {

    public MetricResult {
        if (metricCode == null || metricCode.isBlank()) {
            throw error(MetricDslErrorCode.RESULT_INVALID, "/metricCode", "metricCode must not be blank");
        }
        if (definitionRevision == null || definitionRevision <= 0) {
            throw error(MetricDslErrorCode.RESULT_INVALID, "/definitionRevision", "definitionRevision must be positive");
        }
        if (executionMode == null) {
            throw error(MetricDslErrorCode.RESULT_INVALID, "/executionMode", "executionMode must not be null");
        }
        if (valueShape == null) {
            throw error(MetricDslErrorCode.RESULT_INVALID, "/valueShape", "valueShape must not be null");
        }
        if (calculatedTime == null) {
            throw error(MetricDslErrorCode.RESULT_INVALID, "/calculatedTime", "calculatedTime must not be null");
        }
        if (timeZone == null) {
            throw error(MetricDslErrorCode.RESULT_INVALID, "/timeZone", "timeZone must not be null");
        }
        MetricQueryValueSupport.validateWindow(startTime, endTime, MetricDslErrorCode.RESULT_INVALID);
        if ((routeMetricCode == null) != (routeDefinitionRevision == null)) {
            throw error(MetricDslErrorCode.RESULT_INVALID, "/routeMetricCode", "Route fields must be both present or absent");
        }
        if (routeMetricCode != null && routeMetricCode.isBlank()) {
            throw error(MetricDslErrorCode.RESULT_INVALID, "/routeMetricCode", "routeMetricCode must not be blank");
        }
        if (routeDefinitionRevision != null && routeDefinitionRevision <= 0) {
            throw error(MetricDslErrorCode.RESULT_INVALID, "/routeDefinitionRevision", "Route revision must be positive");
        }
        fields = immutableFields(fields);
        if (segments == null) {
            throw error(MetricDslErrorCode.RESULT_INVALID, "/segments", "segments must not be null");
        }
        segments = List.copyOf(segments);
        validateValueBranch(valueShape, valueType, value, fields);
        validateExecutionBranch(
                executionMode, snapshotGranularity, queryableStartTime, watermarkTime, planCode, segments, startTime, endTime);
    }

    private static Map<String, MetricFieldValue> immutableFields(Map<String, MetricFieldValue> source) {
        if (source == null) {
            throw error(MetricDslErrorCode.RESULT_INVALID, "/fields", "fields must not be null");
        }
        Map<String, MetricFieldValue> result = new LinkedHashMap<>();
        source.forEach((key, fieldValue) -> {
            if (key == null || key.isBlank() || fieldValue == null) {
                throw error(MetricDslErrorCode.RESULT_INVALID, "/fields", "Field names and values must be present");
            }
            result.put(key, fieldValue);
        });
        return Collections.unmodifiableMap(result);
    }

    private static void validateValueBranch(MetricValueShape valueShape,
                                            @Nullable MetricValueType valueType,
                                            @Nullable Number value,
                                            Map<String, MetricFieldValue> fields) {
        if (valueShape == MetricValueShape.SCALAR) {
            if (valueType == null) {
                throw error(MetricDslErrorCode.RESULT_INVALID, "/valueType", "SCALAR valueType is required");
            }
            if (!fields.isEmpty()) {
                throw error(MetricDslErrorCode.RESULT_INVALID, "/fields", "SCALAR fields must be empty");
            }
            MetricQueryValueSupport.validateMetricValue(valueType, value, "/value");
        } else if (valueType != null || value != null || fields.isEmpty()) {
            throw error(MetricDslErrorCode.RESULT_INVALID, "/fields", "FIELD_SET requires only non-empty fields");
        }
    }

    private static void validateExecutionBranch(MetricExecutionMode executionMode,
                                                @Nullable SnapshotGranularity snapshotGranularity,
                                                @Nullable LocalDateTime queryableStartTime,
                                                @Nullable LocalDateTime watermarkTime,
                                                @Nullable String planCode,
                                                List<MetricSegmentResult> segments,
                                                LocalDateTime startTime,
                                                LocalDateTime endTime) {
        if (executionMode == MetricExecutionMode.REALTIME) {
            if (snapshotGranularity != null || queryableStartTime != null || watermarkTime != null
                    || planCode != null || !segments.isEmpty()) {
                throw error(MetricDslErrorCode.RESULT_INVALID, "", "REALTIME contains snapshot route fields");
            }
            return;
        }
        if (executionMode == MetricExecutionMode.SNAPSHOT) {
            if (snapshotGranularity == null || queryableStartTime == null || watermarkTime == null
                    || planCode == null || planCode.isBlank()) {
                throw error(MetricDslErrorCode.RESULT_INVALID, "", "SNAPSHOT route fields are incomplete");
            }
            if (queryableStartTime.isAfter(startTime) || watermarkTime.isBefore(endTime)) {
                throw error(MetricDslErrorCode.RESULT_INVALID, "/watermarkTime", "Snapshot coverage does not contain query");
            }
            if (!segments.isEmpty()) {
                throw error(MetricDslErrorCode.RESULT_INVALID, "/segments", "SNAPSHOT segments must be empty");
            }
            return;
        }
        if (snapshotGranularity != null || queryableStartTime != null || watermarkTime != null) {
            throw error(MetricDslErrorCode.RESULT_INVALID, "", "SEGMENTED forbids root snapshot coverage");
        }
        if (segments.isEmpty()) {
            throw error(MetricDslErrorCode.RESULT_INVALID, "/segments", "SEGMENTED requires executed segments");
        }
        if (planCode != null && planCode.isBlank()) {
            throw error(MetricDslErrorCode.RESULT_INVALID, "/planCode", "planCode must not be blank");
        }
        validateSegmentCoverage(segments, startTime, endTime);
    }

    private static void validateSegmentCoverage(List<MetricSegmentResult> segments,
                                                LocalDateTime startTime,
                                                LocalDateTime endTime) {
        if (segments.size() > 2) {
            throw error(MetricDslErrorCode.RESULT_INVALID, "/segments", "SEGMENTED allows at most two segments");
        }
        LocalDateTime expectedStartTime = startTime;
        for (int index = 0; index < segments.size(); index++) {
            MetricSegmentResult segment = segments.get(index);
            String path = "/segments/" + index;
            if (!segment.startTime().equals(expectedStartTime)) {
                throw error(MetricDslErrorCode.RESULT_INVALID, path + "/startTime", "Segment coverage is not continuous");
            }
            if (!"archive".equals(segment.segmentCode()) && !"recent".equals(segment.segmentCode())) {
                throw error(MetricDslErrorCode.RESULT_INVALID, path + "/segmentCode", "Unsupported segment code");
            }
            if ("archive".equals(segment.segmentCode())
                    && segment.sourceType() != MetricSegmentSourceType.SNAPSHOT) {
                throw error(MetricDslErrorCode.RESULT_INVALID, path + "/sourceType", "Archive segment must use snapshot");
            }
            if (index == 1 && (!"archive".equals(segments.getFirst().segmentCode())
                    || !"recent".equals(segment.segmentCode()))) {
                throw error(MetricDslErrorCode.RESULT_INVALID, "/segments", "Expected archive followed by recent");
            }
            expectedStartTime = segment.endTime();
        }
        if (!expectedStartTime.equals(endTime)) {
            throw error(
                    MetricDslErrorCode.RESULT_INVALID,
                    "/segments/" + (segments.size() - 1) + "/endTime",
                    "Segment coverage does not contain query end");
        }
    }
}
