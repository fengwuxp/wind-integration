package com.wind.integration.metrics.query;

import com.wind.integration.metrics.WindMetricsValue;
import com.wind.integration.metrics.WindStructuredMetricsValue;
import com.wind.integration.metrics.enums.MetricErrorCode;
import com.wind.integration.metrics.enums.MetricQueryMode;
import com.wind.integration.metrics.enums.MetricSegmentCode;
import com.wind.integration.metrics.enums.MetricSegmentSourceType;
import com.wind.integration.metrics.enums.MetricValueShape;
import com.wind.integration.metrics.enums.SnapshotGranularity;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.wind.integration.metrics.query.MetricQueryValueSupport.error;

/**
 * 指标查询结果及本次顶层查询模式与实际数据来源摘要。
 *
 * <p>{@code value} 始终为非空值对象；{@code SCALAR} 使用自带类型的 {@code value} 且 {@code fields} 为空；
 * {@code FIELD_SET} 的 value 由 fields 生成结构化值视图，JSON 仍只通过 fields 传输。
 * {@code SNAPSHOT} 查询在根级返回实际连续覆盖范围，
 * 不表示已物化全部历史；分段查询只在 {@code segments} 中返回各段覆盖信息。
 * 值对象保存构造时的结果，标量 JSON 的 value 自带类型和 payload；TIMESTAMP payload 为 LocalDateTime，
 * 由 timeZone 解释。支持结果类型不代表 DSL 计算器已支持该类型的计算。</p>
 *
 * @param metricCode 对外查询的指标编码
 * @param definitionRevision 实际执行的指标定义修订号
 * @param executionMode 本次查询采用的顶层查询模式
 * @param valueShape 指标值结构
 * @param value 指标具名值，构造后必不为空；标量正常空结果由其 payload 为 null 表示
 * @param fields 多字段指标结果；单值指标为空映射
 * @param subjectId 主体标识；全局指标为空
 * @param startTime 查询开始时间，包含
 * @param endTime 查询结束时间，不包含
 * @param calculatedTime 本次结果的计算完成时间
 * @param timeZone 时间字段解释所使用的时区
 * @param snapshotGranularity SNAPSHOT 查询模式的快照桶粒度；其他模式为空
 * @param queryableStartTime SNAPSHOT 查询模式的实际连续可读区间下界，包含；其他模式为空
 * @param watermarkTime SNAPSHOT 查询模式的已提交连续覆盖上界，不包含；其他模式为空
 * @param planCode 既有读取证据提供的计划编码；新多来源结果仅作可选溯源，实时模式为空
 * @param segments 分段模式实际执行的连续分段；其他模式为空列表
 * @param sources 本次实际读取的 RAW 完整结果；叶子为空列表，派生结果为扁平、去重的来源集合
 *
 * @author wuxp
 * @date 2026-07-21 17:51
 */
@Schema(description = "指标查询结果及实际执行摘要")
public record MetricResult(
        @Schema(description = "对外查询的指标编码") String metricCode,
        @Schema(description = "实际执行的指标定义修订号") Integer definitionRevision,
        @Nullable @Schema(description = "共同执行模式；混合来源为空") MetricQueryMode executionMode,
        @Schema(description = "指标值结构") MetricValueShape valueShape,
        @NonNull
        @JsonSerialize(using = MetricValueJsonSerializer.class)
        @JsonDeserialize(using = MetricValueJsonDeserializer.class)
        @Schema(implementation = Object.class, description = "非空具名指标值；标量类型与可空 payload 由值自身持有")
        WindMetricsValue<?> value,
        @Schema(description = "多字段指标结果；单值指标为空映射") Map<String, MetricFieldValue> fields,
        @Nullable @Schema(description = "主体标识；全局指标为空") String subjectId,
        @Schema(description = "查询开始时间，包含") LocalDateTime startTime,
        @Schema(description = "查询结束时间，不包含") LocalDateTime endTime,
        @Schema(description = "本次结果的计算完成时间") LocalDateTime calculatedTime,
        @Schema(description = "时间字段解释所使用的时区") ZoneId timeZone,
        @Nullable @Schema(description = "SNAPSHOT 查询模式的快照桶粒度；其他模式为空") SnapshotGranularity snapshotGranularity,
        @Nullable @Schema(description = "SNAPSHOT 查询模式的实际连续可读区间下界；其他模式为空") LocalDateTime queryableStartTime,
        @Nullable @Schema(description = "SNAPSHOT 查询模式的已提交连续覆盖上界；其他模式为空") LocalDateTime watermarkTime,
        @Nullable @Schema(description = "读取证据提供的计划编码；仅作可选溯源，实时模式为空") String planCode,
        @Schema(description = "分段模式实际执行的连续分段；其他模式为空列表") List<MetricSegmentResult> segments,
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        @Schema(description = "实际参与计算的 RAW 完整结果；叶子为空列表，来源不得再包含 sources") List<MetricResult> sources) {

    public MetricResult {
        if (metricCode == null || metricCode.isBlank()) {
            throw error(MetricErrorCode.RESULT_INVALID, "/metricCode", "metricCode must not be blank");
        }
        if (definitionRevision == null || definitionRevision <= 0) {
            throw error(MetricErrorCode.RESULT_INVALID, "/definitionRevision", "definitionRevision must be positive");
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
        fields = immutableFields(fields);
        if (segments == null) {
            throw error(MetricErrorCode.RESULT_INVALID, "/segments", "segments must not be null");
        }
        segments = List.copyOf(segments);
        sources = sources == null ? List.of() : List.copyOf(sources);
        value = normalizeResultValue(metricCode, valueShape, value, fields);
        if (sources.isEmpty()) {
            if (executionMode == null) {
                throw error(MetricErrorCode.RESULT_INVALID, "/executionMode", "executionMode must not be null");
            }
            validateExecutionBranch(executionMode, snapshotGranularity, queryableStartTime, watermarkTime,
                    planCode, segments, startTime, endTime);
        } else {
            SourceSummary summary = sourceSummary(sources);
            executionMode = summary.executionMode();
            snapshotGranularity = summary.snapshotGranularity();
            queryableStartTime = summary.queryableStartTime();
            watermarkTime = summary.watermarkTime();
            planCode = summary.planCode();
            segments = summary.segments();
            validateSources(sources, subjectId, startTime, endTime, timeZone);
        }
    }

    /**
     * 兼容原有值视图入口，直接返回已固定的 value，不重新查询、合并或写回。
     *
     * <p>SCALAR 返回具名值，FIELD_SET 返回 {@link WindStructuredMetricsValue}，
     * 名称取 metricCode，子字段保留自身名称和正常空值。版本、覆盖及执行信息
     * 仍由本对象承载；调用方需要这些信息时保留本对象，不从具名值重建存储身份。</p>
     *
     * @return 只读指标值；不包含执行策略信息
     */
    public WindMetricsValue<?> toMetricsValue() {
        return value;
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
            result.put(key, fieldValue.withCode(key));
        });
        return Collections.unmodifiableMap(result);
    }

    private static WindMetricsValue<?> normalizeResultValue(String metricCode, MetricValueShape valueShape,
                                                            @Nullable WindMetricsValue<?> value,
                                                            Map<String, MetricFieldValue> fields) {
        if (valueShape == MetricValueShape.SCALAR) {
            if (!fields.isEmpty()) {
                throw error(MetricErrorCode.RESULT_INVALID, "/fields", "SCALAR fields must be empty");
            }
            return MetricQueryValueSupport.normalizeMetricValue(metricCode, value);
        }
        if (fields.isEmpty()) {
            throw error(MetricErrorCode.RESULT_INVALID, "/fields", "FIELD_SET requires only non-empty fields");
        }
        Map<String, Object> values = new LinkedHashMap<>();
        fields.forEach((name, field) -> values.put(name, field.value().getValue()));
        if (value != null && (!(value instanceof WindStructuredMetricsValue<?> structured)
                || !values.equals(structured.asFieldValues()))) {
            throw error(MetricErrorCode.RESULT_INVALID, "/value", "FIELD_SET value must match fields");
        }
        return WindStructuredMetricsValue.of(metricCode, values);
    }

    private static void validateExecutionBranch(MetricQueryMode executionMode,
                                                @Nullable SnapshotGranularity snapshotGranularity,
                                                @Nullable LocalDateTime queryableStartTime,
                                                @Nullable LocalDateTime watermarkTime,
                                                @Nullable String planCode,
                                                List<MetricSegmentResult> segments,
                                                LocalDateTime startTime,
                                                LocalDateTime endTime) {
        if (planCode != null && planCode.isBlank()) {
            throw error(MetricErrorCode.RESULT_INVALID, "/planCode", "planCode must not be blank");
        }
        if (executionMode == MetricQueryMode.REALTIME) {
            if (snapshotGranularity != null || queryableStartTime != null || watermarkTime != null
                    || planCode != null || !segments.isEmpty()) {
                throw error(MetricErrorCode.RESULT_INVALID, "", "REALTIME contains snapshot execution fields");
            }
            return;
        }
        if (executionMode == MetricQueryMode.SNAPSHOT) {
            if (snapshotGranularity == null || queryableStartTime == null || watermarkTime == null) {
                throw error(MetricErrorCode.RESULT_INVALID, "", "SNAPSHOT coverage fields are incomplete");
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
        validateSegmentCoverage(segments, startTime, endTime);
    }

    private static SourceSummary sourceSummary(List<MetricResult> sourceList) {
        long distinctKeys = sourceList.stream()
                .map(source -> source.metricCode() + "\u0000" + source.definitionRevision())
                .distinct()
                .count();
        if (distinctKeys != sourceList.size()) {
            throw error(MetricErrorCode.RESULT_INVALID, "/sources", "Sources must be unique by metricCode and revision");
        }
        MetricQueryMode commonMode = sourceList.stream().map(MetricResult::executionMode).distinct().count() == 1
                ? sourceList.getFirst().executionMode() : null;
        if (sourceList.size() == 1) {
            MetricResult source = sourceList.getFirst();
            return new SourceSummary(commonMode,
                    source.snapshotGranularity(), source.queryableStartTime(), source.watermarkTime(),
                    source.planCode(), source.segments());
        } else {
            return new SourceSummary(commonMode, null, null, null, null, List.of());
        }
    }

    private record SourceSummary(
            @Nullable MetricQueryMode executionMode,
            @Nullable SnapshotGranularity snapshotGranularity,
            @Nullable LocalDateTime queryableStartTime,
            @Nullable LocalDateTime watermarkTime,
            @Nullable String planCode,
            List<MetricSegmentResult> segments) {
    }

    private static void validateSources(List<MetricResult> sources, @Nullable String subjectId,
                                        LocalDateTime startTime, LocalDateTime endTime, ZoneId timeZone) {
        for (int index = 0; index < sources.size(); index++) {
            MetricResult source = sources.get(index);
            String path = "/sources/" + index;
            if (!source.sources().isEmpty()) {
                throw error(MetricErrorCode.RESULT_INVALID, path + "/sources", "Sources must be flat RAW results");
            }
            if (!Objects.equals(subjectId, source.subjectId())) {
                throw error(MetricErrorCode.RESULT_INVALID, path + "/subjectId", "Source subject must match query");
            }
            if (!startTime.equals(source.startTime()) || !endTime.equals(source.endTime())) {
                throw error(MetricErrorCode.RESULT_INVALID, path, "Source window must match query");
            }
            if (!timeZone.equals(source.timeZone())) {
                throw error(MetricErrorCode.RESULT_INVALID, path + "/timeZone", "Source time zone must match query");
            }
        }
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
            if (segment.segmentCode() == MetricSegmentCode.ARCHIVE
                    && segment.sourceType() != MetricSegmentSourceType.SNAPSHOT) {
                throw error(MetricErrorCode.RESULT_INVALID, path + "/sourceType", "Archive segment must use snapshot");
            }
            if (index == 1 && (segments.getFirst().segmentCode() != MetricSegmentCode.ARCHIVE
                    || segment.segmentCode() != MetricSegmentCode.RECENT)) {
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
