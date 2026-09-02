package com.wind.integration.metrics.dsl;

import com.wind.integration.metrics.MetricValidationException;
import com.wind.integration.metrics.dsl.materialization.MetricMaterializationDependencyDsl;
import com.wind.integration.metrics.dsl.materialization.MetricMaterializationMeasureDsl;
import com.wind.integration.metrics.dsl.materialization.MetricMaterializationPlanDsl;
import com.wind.integration.metrics.dsl.materialization.MetricSegmentDsl;
import com.wind.integration.metrics.enums.MetricErrorCode;
import com.wind.integration.metrics.enums.MetricMergeState;
import com.wind.integration.metrics.enums.MetricQueryMode;
import com.wind.integration.metrics.enums.MetricSegmentCode;
import com.wind.integration.metrics.enums.MetricSegmentSourceType;
import com.wind.integration.metrics.enums.SnapshotGranularity;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JsonParser;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.wind.integration.metrics.dsl.MetricDslJson.child;
import static com.wind.integration.metrics.dsl.MetricDslJson.error;
import static com.wind.integration.metrics.dsl.MetricDslJson.required;
import static com.wind.integration.metrics.dsl.MetricDslJson.string;

/**
 * 指标逻辑物化计划 v1 的关闭世界解析、基础校验与确定性规范化入口。
 *
 * <p>该入口只描述快照、分段拓扑和冻结的叶子物化依赖，不解析物理表、数据源或运行时水位。</p>
 *
 * @author wuxp
 * @date 2026-07-21 17:51
 */
public final class MetricMaterializationPlanDslCodec {

    /**
     * 当前支持的 Plan DSL 结构版本。
     */
    private static final int SCHEMA_VERSION = 1;

    /**
     * 快照键提供者和逻辑目标编码允许使用的格式。
     */
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z][A-Za-z0-9_]*");

    /**
     * 分段近期窗口支持的正数天或小时格式。
     */
    private static final Pattern RECENT_WINDOW = Pattern.compile("(?:P([0-9]+)D|PT([0-9]+)H)");

    /**
     * Plan DSL 根节点允许出现的字段。
     */
    private static final Set<String> ROOT_FIELDS = Set.of(
            "schemaVersion", "executionMode", "snapshotKeyProviderCode", "snapshotGranularity",
            "snapshotTargetCode", "recentWindow", "segments", "dependencies");

    /**
     * 解析并校验指标物化 Plan DSL JSON。
     *
     * @param json Plan DSL JSON
     * @return 不可变的逻辑物化计划
     * @throws MetricValidationException JSON、字段或计划结构不符合 v1 契约时抛出
     */
    public MetricMaterializationPlanDsl parse(String json) {
        return parse(MetricDslJson.parseRootObject(json));
    }

    MetricMaterializationPlanDsl parse(JsonParser parser) {
        return parse(MetricDslJson.parseRootObject(parser));
    }

    private MetricMaterializationPlanDsl parse(Map<String, Object> root) {
        int schemaVersion = MetricDslJson.integer(required(root, "schemaVersion", ""), "/schemaVersion");
        if (schemaVersion != SCHEMA_VERSION) {
            throw error(MetricErrorCode.DSL_SCHEMA_VERSION_UNSUPPORTED, "/schemaVersion", "Unsupported schema version");
        }
        MetricDslJson.rejectUnknown(root, "", ROOT_FIELDS);
        MetricQueryMode executionMode = MetricDslJson.enumValue(
                required(root, "executionMode", ""), MetricQueryMode.class, "/executionMode");
        String keyProviderCode = string(
                required(root, "snapshotKeyProviderCode", ""), "/snapshotKeyProviderCode");
        List<MetricMaterializationDependencyDsl> dependencies = parseDependencies(
                MetricDslJson.optionalValue(root, "dependencies", "/dependencies"));
        SnapshotGranularity granularity = root.containsKey("snapshotGranularity")
                ? MetricDslJson.enumValue(
                root.get("snapshotGranularity"), SnapshotGranularity.class, "/snapshotGranularity")
                : null;
        String targetCode = optionalString(root, "snapshotTargetCode", "/snapshotTargetCode");
        String recentWindow = root.containsKey("recentWindow")
                ? normalizeRecentWindow(string(root.get("recentWindow"), "/recentWindow"))
                : null;
        List<MetricSegmentDsl> segments = parseSegments(
                MetricDslJson.optionalValue(root, "segments", "/segments"));
        MetricMaterializationPlanDsl plan = new MetricMaterializationPlanDsl(
                schemaVersion, executionMode, keyProviderCode, dependencies,
                granularity, targetCode, recentWindow, segments);
        validateBasic(plan);
        return plan;
    }

    /**
     * 校验已构造的逻辑物化计划是否满足 v1 基础结构约束。
     *
     * @param plan 逻辑物化计划
     * @throws MetricValidationException 计划模式、分段或快照字段不符合约束时抛出
     */
    public void validateBasic(MetricMaterializationPlanDsl plan) {
        if (plan.schemaVersion() != SCHEMA_VERSION) {
            throw error(MetricErrorCode.DSL_SCHEMA_VERSION_UNSUPPORTED, "/schemaVersion", "Unsupported schema version");
        }
        validateIdentifier(plan.snapshotKeyProviderCode(), "/snapshotKeyProviderCode");
        validateDependencies(plan.dependencies());
        if (plan.executionMode() == MetricQueryMode.REALTIME) {
            throw error(MetricErrorCode.DSL_PLAN_INVALID, "/executionMode", "REALTIME does not use a plan");
        }
        if (plan.executionMode() == MetricQueryMode.SNAPSHOT) {
            if (plan.snapshotGranularity() == null) {
                throw error(
                        MetricErrorCode.DSL_FIELD_REQUIRED,
                        "/snapshotGranularity",
                        "Snapshot granularity is required");
            }
            if (plan.snapshotTargetCode() == null) {
                throw error(MetricErrorCode.DSL_FIELD_REQUIRED, "/snapshotTargetCode", "Snapshot target is required");
            }
            validateIdentifier(plan.snapshotTargetCode(), "/snapshotTargetCode");
            if (plan.recentWindow() != null || !plan.segments().isEmpty()) {
                throw error(MetricErrorCode.DSL_PLAN_INVALID, "", "SNAPSHOT forbids segmented fields");
            }
            return;
        }
        if (plan.snapshotGranularity() != null || plan.snapshotTargetCode() != null) {
            throw error(MetricErrorCode.DSL_PLAN_INVALID, "", "SEGMENTED forbids root snapshot fields");
        }
        if (plan.recentWindow() == null) {
            throw error(MetricErrorCode.DSL_FIELD_REQUIRED, "/recentWindow", "recentWindow is required");
        }
        normalizeRecentWindow(plan.recentWindow());
        validateSegments(plan.segments());
    }

    /**
     * 将合法逻辑物化计划输出为字段顺序稳定的规范 JSON。
     *
     * @param plan 逻辑物化计划
     * @return 可用于内容比对和签名的规范 JSON
     * @throws MetricValidationException 计划不满足 v1 契约时抛出
     */
    public String canonicalize(MetricMaterializationPlanDsl plan) {
        validateBasic(plan);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("schemaVersion", plan.schemaVersion());
        result.put("executionMode", plan.executionMode().name());
        result.put("snapshotKeyProviderCode", plan.snapshotKeyProviderCode());
        if (!plan.dependencies().isEmpty()) {
            result.put("dependencies", plan.dependencies().stream()
                    .sorted(Comparator.comparing(MetricMaterializationDependencyDsl::metricCode))
                    .map(this::toCanonicalDependency)
                    .toList());
        }
        if (plan.executionMode() == MetricQueryMode.SNAPSHOT) {
            result.put("snapshotGranularity", plan.snapshotGranularity().name());
            result.put("snapshotTargetCode", plan.snapshotTargetCode());
        } else {
            result.put("recentWindow", normalizeRecentWindow(plan.recentWindow()));
            result.put("segments", plan.segments().stream().map(this::toCanonicalSegment).toList());
        }
        return MetricDslJson.toJson(result);
    }

    private List<MetricMaterializationDependencyDsl> parseDependencies(@Nullable Object value) {
        if (value == null) {
            return List.of();
        }
        List<Object> source = MetricDslJson.array(value, "/dependencies");
        List<MetricMaterializationDependencyDsl> result = new ArrayList<>(source.size());
        for (int index = 0; index < source.size(); index++) {
            String path = child("/dependencies", Integer.toString(index));
            Map<String, Object> dependency = MetricDslJson.object(source.get(index), path);
            MetricDslJson.rejectUnknown(
                    dependency, path, Set.of("metricCode", "definitionRevision", "measures"));
            result.add(new MetricMaterializationDependencyDsl(
                    string(required(dependency, "metricCode", path), child(path, "metricCode")),
                    MetricDslJson.integer(
                            required(dependency, "definitionRevision", path), child(path, "definitionRevision")),
                    parseMeasures(required(dependency, "measures", path), child(path, "measures"))));
        }
        return result;
    }

    private List<MetricMaterializationMeasureDsl> parseMeasures(Object value, String path) {
        List<Object> source = MetricDslJson.array(value, path);
        List<MetricMaterializationMeasureDsl> result = new ArrayList<>(source.size());
        for (int index = 0; index < source.size(); index++) {
            String measurePath = child(path, Integer.toString(index));
            Map<String, Object> measure = MetricDslJson.object(source.get(index), measurePath);
            MetricDslJson.rejectUnknown(measure, measurePath, Set.of("valueField", "mergeState"));
            result.add(new MetricMaterializationMeasureDsl(
                    string(required(measure, "valueField", measurePath), child(measurePath, "valueField")),
                    MetricDslJson.enumValue(
                            required(measure, "mergeState", measurePath),
                            MetricMergeState.class,
                            child(measurePath, "mergeState"))));
        }
        return result;
    }

    private void validateDependencies(List<MetricMaterializationDependencyDsl> dependencies) {
        if (dependencies.isEmpty()) {
            return;
        }
        Set<String> metricCodes = new HashSet<>();
        for (int index = 0; index < dependencies.size(); index++) {
            MetricMaterializationDependencyDsl dependency = dependencies.get(index);
            String path = child("/dependencies", Integer.toString(index));
            validateIdentifier(dependency.metricCode(), child(path, "metricCode"), 100);
            if (!metricCodes.add(dependency.metricCode())) {
                throw error(
                        MetricErrorCode.DSL_PLAN_INVALID,
                        child(path, "metricCode"),
                        "Duplicate dependency metricCode");
            }
            if (dependency.definitionRevision() <= 0) {
                throw error(
                        MetricErrorCode.DSL_PLAN_INVALID,
                        child(path, "definitionRevision"),
                        "definitionRevision must be positive");
            }
            validateMeasures(dependency.measures(), child(path, "measures"));
        }
    }

    private void validateMeasures(List<MetricMaterializationMeasureDsl> measures, String path) {
        if (measures.isEmpty()) {
            throw error(MetricErrorCode.DSL_PLAN_INVALID, path, "Dependency measures must not be empty");
        }
        Set<String> valueFields = new HashSet<>();
        for (int index = 0; index < measures.size(); index++) {
            MetricMaterializationMeasureDsl measure = measures.get(index);
            String valueFieldPath = child(child(path, Integer.toString(index)), "valueField");
            validateIdentifier(measure.valueField(), valueFieldPath, 64);
            if (!valueFields.add(measure.valueField())) {
                throw error(MetricErrorCode.DSL_PLAN_INVALID, valueFieldPath, "Duplicate dependency valueField");
            }
        }
    }

    private Map<String, Object> toCanonicalDependency(MetricMaterializationDependencyDsl dependency) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("metricCode", dependency.metricCode());
        result.put("definitionRevision", dependency.definitionRevision());
        result.put("measures", dependency.measures().stream()
                .sorted(Comparator.comparing(MetricMaterializationMeasureDsl::valueField))
                .map(this::toCanonicalMeasure)
                .toList());
        return result;
    }

    private Map<String, Object> toCanonicalMeasure(MetricMaterializationMeasureDsl measure) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("valueField", measure.valueField());
        result.put("mergeState", measure.mergeState().name());
        return result;
    }

    private List<MetricSegmentDsl> parseSegments(@Nullable Object value) {
        if (value == null) {
            return List.of();
        }
        List<Object> source = MetricDslJson.array(value, "/segments");
        List<MetricSegmentDsl> result = new ArrayList<>(source.size());
        for (int index = 0; index < source.size(); index++) {
            String path = child("/segments", Integer.toString(index));
            Map<String, Object> segment = MetricDslJson.object(source.get(index), path);
            MetricDslJson.rejectUnknown(segment, path, Set.of(
                    "segmentCode", "sourceType", "snapshotGranularity", "snapshotTargetCode"));
            MetricSegmentCode segmentCode = parseSegmentCode(
                    string(required(segment, "segmentCode", path), child(path, "segmentCode")),
                    child(path, "segmentCode"));
            MetricSegmentSourceType sourceType = MetricDslJson.enumValue(
                    required(segment, "sourceType", path), MetricSegmentSourceType.class, child(path, "sourceType"));
            SnapshotGranularity granularity = segment.containsKey("snapshotGranularity")
                    ? MetricDslJson.enumValue(
                    segment.get("snapshotGranularity"), SnapshotGranularity.class,
                    child(path, "snapshotGranularity"))
                    : null;
            String targetCode = optionalString(segment, "snapshotTargetCode", child(path, "snapshotTargetCode"));
            result.add(new MetricSegmentDsl(segmentCode, sourceType, granularity, targetCode));
        }
        return result;
    }

    private void validateSegments(List<MetricSegmentDsl> segments) {
        if (segments.size() != 2) {
            throw error(MetricErrorCode.DSL_PLAN_INVALID, "/segments", "SEGMENTED requires exactly two segments");
        }
        if (segments.get(0).segmentCode() != MetricSegmentCode.ARCHIVE
                || segments.get(1).segmentCode() != MetricSegmentCode.RECENT
                || segments.get(0).sourceType() != MetricSegmentSourceType.SNAPSHOT) {
            throw error(MetricErrorCode.DSL_PLAN_INVALID, "/segments", "Expected archive SNAPSHOT followed by recent");
        }
        for (int index = 0; index < segments.size(); index++) {
            MetricSegmentDsl segment = segments.get(index);
            String path = child("/segments", Integer.toString(index));
            if (segment.sourceType() == MetricSegmentSourceType.SNAPSHOT) {
                if (segment.snapshotGranularity() == null || segment.snapshotTargetCode() == null) {
                    throw error(MetricErrorCode.DSL_FIELD_REQUIRED, path, "SNAPSHOT segment requires snapshot fields");
                }
                validateIdentifier(segment.snapshotTargetCode(), child(path, "snapshotTargetCode"));
            } else if (segment.snapshotGranularity() != null || segment.snapshotTargetCode() != null) {
                throw error(MetricErrorCode.DSL_PLAN_INVALID, path, "REALTIME segment forbids snapshot fields");
            }
        }
    }

    private Map<String, Object> toCanonicalSegment(MetricSegmentDsl segment) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("segmentCode", segment.segmentCode().getCode());
        result.put("sourceType", segment.sourceType().name());
        if (segment.sourceType() == MetricSegmentSourceType.SNAPSHOT) {
            result.put("snapshotGranularity", segment.snapshotGranularity().name());
            result.put("snapshotTargetCode", segment.snapshotTargetCode());
        }
        return result;
    }

    private String normalizeRecentWindow(String value) {
        Matcher matcher = RECENT_WINDOW.matcher(value);
        if (!matcher.matches()) {
            throw error(MetricErrorCode.DSL_PLAN_INVALID, "/recentWindow", "Invalid recentWindow");
        }
        String countText = matcher.group(1) == null ? matcher.group(2) : matcher.group(1);
        BigInteger count = new BigInteger(countText);
        if (count.signum() <= 0) {
            throw error(MetricErrorCode.DSL_PLAN_INVALID, "/recentWindow", "recentWindow must be positive");
        }
        return matcher.group(1) == null ? "PT" + count + "H" : "P" + count + "D";
    }

    private MetricSegmentCode parseSegmentCode(String value, String path) {
        try {
            return MetricSegmentCode.fromCode(value);
        } catch (IllegalArgumentException exception) {
            throw error(MetricErrorCode.DSL_PLAN_INVALID, path, "Unsupported segmentCode");
        }
    }

    private @Nullable String optionalString(Map<String, Object> source, String field, String path) {
        if (!source.containsKey(field)) {
            return null;
        }
        if (source.get(field) == null) {
            throw error(MetricErrorCode.DSL_FIELD_TYPE_INVALID, path, "Explicit null is not allowed");
        }
        return string(source.get(field), path);
    }

    private void validateIdentifier(String value, String path) {
        validateIdentifier(value, path, 64);
    }

    private void validateIdentifier(String value, String path, int maxLength) {
        if (value.length() > maxLength || !IDENTIFIER.matcher(value).matches()) {
            throw error(MetricErrorCode.DSL_IDENTIFIER_INVALID, path, "Invalid identifier");
        }
    }
}
