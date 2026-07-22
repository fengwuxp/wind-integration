package com.wind.integration.metrics.query;

import com.wind.integration.metrics.enums.MetricExecutionMode;
import com.wind.integration.metrics.enums.MetricErrorCode;
import com.wind.integration.metrics.enums.MetricSegmentSourceType;
import com.wind.integration.metrics.enums.MetricValueShape;
import com.wind.integration.metrics.enums.MetricValueType;
import com.wind.integration.metrics.enums.SnapshotGranularity;
import org.jspecify.annotations.Nullable;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.wind.integration.metrics.query.MetricQueryValueSupport.error;

/**
 * 指标查询结果及本次实际数据来源摘要。
 *
 * <p>{@code SCALAR} 使用 {@code valueType/value} 且 {@code fields} 为空；
 * {@code FIELD_SET} 只使用 {@code fields}。全量快照在根级返回覆盖范围，分段查询只在
 * {@code segments} 中返回各段覆盖信息。</p>
 *
 * @param metricCode 对外查询的指标编码
 * @param definitionRevision 实际生效的指标定义修订号
 * @param executionMode 本次查询实际使用的数据来源模式
 * @param routeMetricCode 单指标派生结果实际继承路由的指标编码；未继承时为空
 * @param routeDefinitionRevision 路由指标实际修订号；与 routeMetricCode 同时存在或同时为空
 * @param valueShape 指标值结构
 * @param valueType 单值指标的数值类型；多字段指标为空
 * @param value 单值指标结果；SQL 正常空结果可以为空
 * @param fields 多字段指标结果；单值指标为空映射
 * @param subjectId 主体标识；全局指标为空
 * @param startTime 查询开始时间，包含
 * @param endTime 查询结束时间，不包含
 * @param calculatedTime 本次结果的计算完成时间
 * @param timeZone 时间字段解释所使用的时区
 * @param snapshotGranularity 全量快照桶粒度；其他模式为空
 * @param queryableStartTime 全量快照连续可读区间下界，包含；其他模式为空
 * @param watermarkTime 全量快照连续覆盖上界，不包含；其他模式为空
 * @param planCode 本次实际使用的物化计划编码；实时模式为空
 * @param segments 分段模式实际执行的连续分段；其他模式为空列表
 *
 * @author wuxp
 * @date 2026-07-21 17:51
 */
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
            throw error(MetricErrorCode.RESULT_INVALID, "/metricCode", "metricCode must not be blank");
        }
        if (definitionRevision == null || definitionRevision <= 0) {
            throw error(MetricErrorCode.RESULT_INVALID, "/definitionRevision", "definitionRevision must be positive");
        }
        if (executionMode == null) {
            throw error(MetricErrorCode.RESULT_INVALID, "/executionMode", "executionMode must not be null");
        }
        if (valueShape == null) {
            throw error(MetricErrorCode.RESULT_INVALID, "/valueShape", "valueShape must not be null");
        }
        if (calculatedTime == null) {
            throw error(MetricErrorCode.RESULT_INVALID, "/calculatedTime", "calculatedTime must not be null");
        }
        if (timeZone == null) {
            throw error(MetricErrorCode.RESULT_INVALID, "/timeZone", "timeZone must not be null");
        }
        MetricQueryValueSupport.validateWindow(startTime, endTime, MetricErrorCode.RESULT_INVALID);
        if ((routeMetricCode == null) != (routeDefinitionRevision == null)) {
            throw error(
                    MetricErrorCode.RESULT_INVALID,
                    "/routeMetricCode",
                    "Route fields must be both present or absent");
        }
        if (routeMetricCode != null && routeMetricCode.isBlank()) {
            throw error(MetricErrorCode.RESULT_INVALID, "/routeMetricCode", "routeMetricCode must not be blank");
        }
        if (routeDefinitionRevision != null && routeDefinitionRevision <= 0) {
            throw error(MetricErrorCode.RESULT_INVALID, "/routeDefinitionRevision", "Route revision must be positive");
        }
        fields = immutableFields(fields);
        if (segments == null) {
            throw error(MetricErrorCode.RESULT_INVALID, "/segments", "segments must not be null");
        }
        segments = List.copyOf(segments);
        validateValueBranch(valueShape, valueType, value, fields);
        validateExecutionBranch(
                executionMode,
                snapshotGranularity,
                queryableStartTime,
                watermarkTime,
                planCode,
                segments,
                startTime,
                endTime);
    }

    private static Map<String, MetricFieldValue> immutableFields(Map<String, MetricFieldValue> source) {
        if (source == null) {
            throw error(MetricErrorCode.RESULT_INVALID, "/fields", "fields must not be null");
        }
        Map<String, MetricFieldValue> result = new LinkedHashMap<>();
        source.forEach((key, fieldValue) -> {
            if (key == null || key.isBlank() || fieldValue == null) {
                throw error(MetricErrorCode.RESULT_INVALID, "/fields", "Field names and values must be present");
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
                throw error(MetricErrorCode.RESULT_INVALID, "/valueType", "SCALAR valueType is required");
            }
            if (!fields.isEmpty()) {
                throw error(MetricErrorCode.RESULT_INVALID, "/fields", "SCALAR fields must be empty");
            }
            MetricQueryValueSupport.validateMetricValue(valueType, value, "/value");
        } else if (valueType != null || value != null || fields.isEmpty()) {
            throw error(MetricErrorCode.RESULT_INVALID, "/fields", "FIELD_SET requires only non-empty fields");
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
                throw error(MetricErrorCode.RESULT_INVALID, "", "REALTIME contains snapshot route fields");
            }
            return;
        }
        if (executionMode == MetricExecutionMode.SNAPSHOT) {
            if (snapshotGranularity == null || queryableStartTime == null || watermarkTime == null
                    || planCode == null || planCode.isBlank()) {
                throw error(MetricErrorCode.RESULT_INVALID, "", "SNAPSHOT route fields are incomplete");
            }
            if (queryableStartTime.isAfter(startTime) || watermarkTime.isBefore(endTime)) {
                throw error(
                        MetricErrorCode.RESULT_INVALID,
                        "/watermarkTime",
                        "Snapshot coverage does not contain query");
            }
            if (!segments.isEmpty()) {
                throw error(MetricErrorCode.RESULT_INVALID, "/segments", "SNAPSHOT segments must be empty");
            }
            return;
        }
        if (snapshotGranularity != null || queryableStartTime != null || watermarkTime != null) {
            throw error(MetricErrorCode.RESULT_INVALID, "", "SEGMENTED forbids root snapshot coverage");
        }
        if (segments.isEmpty()) {
            throw error(MetricErrorCode.RESULT_INVALID, "/segments", "SEGMENTED requires executed segments");
        }
        if (planCode != null && planCode.isBlank()) {
            throw error(MetricErrorCode.RESULT_INVALID, "/planCode", "planCode must not be blank");
        }
        validateSegmentCoverage(segments, startTime, endTime);
    }

    private static void validateSegmentCoverage(List<MetricSegmentResult> segments,
                                                LocalDateTime startTime,
                                                LocalDateTime endTime) {
        if (segments.size() > 2) {
            throw error(MetricErrorCode.RESULT_INVALID, "/segments", "SEGMENTED allows at most two segments");
        }
        LocalDateTime expectedStartTime = startTime;
        for (int index = 0; index < segments.size(); index++) {
            MetricSegmentResult segment = segments.get(index);
            String path = "/segments/" + index;
            if (!segment.startTime().equals(expectedStartTime)) {
                throw error(MetricErrorCode.RESULT_INVALID, path + "/startTime", "Segment coverage is not continuous");
            }
            if (!"archive".equals(segment.segmentCode()) && !"recent".equals(segment.segmentCode())) {
                throw error(MetricErrorCode.RESULT_INVALID, path + "/segmentCode", "Unsupported segment code");
            }
            if ("archive".equals(segment.segmentCode())
                    && segment.sourceType() != MetricSegmentSourceType.SNAPSHOT) {
                throw error(MetricErrorCode.RESULT_INVALID, path + "/sourceType", "Archive segment must use snapshot");
            }
            if (index == 1 && (!"archive".equals(segments.getFirst().segmentCode())
                    || !"recent".equals(segment.segmentCode()))) {
                throw error(MetricErrorCode.RESULT_INVALID, "/segments", "Expected archive followed by recent");
            }
            expectedStartTime = segment.endTime();
        }
        if (!expectedStartTime.equals(endTime)) {
            throw error(
                    MetricErrorCode.RESULT_INVALID,
                    "/segments/" + (segments.size() - 1) + "/endTime",
                    "Segment coverage does not contain query end");
        }
    }
}
