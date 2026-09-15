package com.wind.integration.metrics.dsl;

import com.wind.integration.metrics.MetricValidationException;
import com.wind.integration.metrics.json.MetricJsonSupport;
import com.wind.integration.metrics.dsl.materialization.MetricMaterializationPlanDsl;
import com.wind.integration.metrics.dsl.materialization.MetricReferenceDsl;
import com.wind.integration.metrics.dsl.materialization.MetricSegmentDsl;
import com.wind.integration.metrics.dsl.materialization.MetricSnapshotTargetDsl;
import com.wind.integration.metrics.dsl.materialization.MetricSnapshotTargetMappingDsl;
import com.wind.integration.metrics.enums.MetricErrorCode;
import com.wind.integration.metrics.enums.MetricQueryMode;
import com.wind.integration.metrics.enums.MetricSegmentCode;
import com.wind.integration.metrics.enums.MetricSegmentSourceType;
import com.wind.integration.metrics.enums.MetricSnapshotStorageType;
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
import java.util.stream.Collectors;

import static com.wind.integration.metrics.json.MetricJsonSupport.child;
import static com.wind.integration.metrics.dsl.MetricDslJson.error;
import static com.wind.integration.metrics.dsl.MetricDslJson.required;
import static com.wind.integration.metrics.dsl.MetricDslJson.string;

/**
 * 指标逻辑物化计划 v2 的关闭世界解析、基础校验与确定性规范化入口。
 *
 * <p>该入口只描述快照、分段拓扑和指标关联声明，不解析定义版本、计算依赖、物理绑定或运行时水位。</p>
 *
 * @author wuxp
 * @date 2026-07-21 17:51
 */
public final class MetricMaterializationPlanDslCodec {

    /**
     * 当前支持的 Plan DSL 结构版本。
     */
    private static final int SCHEMA_VERSION = 2;

    /**
     * 维度键提供方、指标编码和逻辑结果字段允许使用的格式。
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
            "schemaVersion", "executionMode", "dimensionKeyProviderCode", "snapshotGranularity",
            "snapshotTarget", "recentWindow", "segments", "metrics");

    /**
     * 解析并校验指标物化 Plan DSL JSON。
     *
     * @param json Plan DSL JSON
     * @return 不可变的逻辑物化计划
     * @throws MetricValidationException JSON、字段或计划结构不符合 v2 契约时抛出
     */
    public MetricMaterializationPlanDsl parse(String json) {
        return parse(MetricJsonSupport.parseRootObject(json));
    }

    MetricMaterializationPlanDsl parse(JsonParser parser) {
        return parse(MetricJsonSupport.parseRootObject(parser));
    }

    private MetricMaterializationPlanDsl parse(Map<String, Object> root) {
        int schemaVersion = MetricDslJson.integer(required(root, "schemaVersion", ""), "/schemaVersion");
        if (schemaVersion != SCHEMA_VERSION) {
            throw error(MetricErrorCode.DSL_SCHEMA_VERSION_UNSUPPORTED, "/schemaVersion", "Unsupported schema version");
        }
        MetricDslJson.rejectUnknown(root, "", ROOT_FIELDS);
        MetricQueryMode executionMode = MetricDslJson.enumValue(
                required(root, "executionMode", ""), MetricQueryMode.class, "/executionMode");
        String dimensionKeyProviderCode = string(
                required(root, "dimensionKeyProviderCode", ""), "/dimensionKeyProviderCode");
        List<MetricReferenceDsl> metrics = parseMetrics(required(root, "metrics", ""));
        SnapshotGranularity granularity = root.containsKey("snapshotGranularity")
                ? MetricDslJson.enumValue(
                root.get("snapshotGranularity"), SnapshotGranularity.class, "/snapshotGranularity")
                : null;
        MetricSnapshotTargetDsl snapshotTarget = parseSnapshotTarget(
                MetricDslJson.optionalValue(root, "snapshotTarget", "/snapshotTarget"), "/snapshotTarget");
        String recentWindow = root.containsKey("recentWindow")
                ? normalizeRecentWindow(string(root.get("recentWindow"), "/recentWindow"))
                : null;
        List<MetricSegmentDsl> segments = parseSegments(
                MetricDslJson.optionalValue(root, "segments", "/segments"));
        MetricMaterializationPlanDsl plan = new MetricMaterializationPlanDsl(
                schemaVersion, executionMode, dimensionKeyProviderCode, metrics,
                granularity, snapshotTarget, recentWindow, segments);
        validateBasic(plan);
        return plan;
    }

    /**
     * 校验已构造的逻辑物化计划是否满足 v2 基础结构约束。
     *
     * @param plan 逻辑物化计划
     * @throws MetricValidationException 计划模式、分段或快照字段不符合约束时抛出
     */
    public void validateBasic(MetricMaterializationPlanDsl plan) {
        if (plan.schemaVersion() != SCHEMA_VERSION) {
            throw error(MetricErrorCode.DSL_SCHEMA_VERSION_UNSUPPORTED, "/schemaVersion", "Unsupported schema version");
        }
        validateIdentifier(plan.dimensionKeyProviderCode(), "/dimensionKeyProviderCode");
        validateMetrics(plan.metrics());
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
            if (plan.snapshotTarget() == null) {
                throw error(MetricErrorCode.DSL_FIELD_REQUIRED, "/snapshotTarget", "Snapshot target is required");
            }
            validateSnapshotTarget(plan.snapshotTarget(), "/snapshotTarget", plan.metrics());
            if (plan.recentWindow() != null || !plan.segments().isEmpty()) {
                throw error(MetricErrorCode.DSL_PLAN_INVALID, "", "SNAPSHOT forbids segmented fields");
            }
            return;
        }
        if (plan.snapshotGranularity() != null || plan.snapshotTarget() != null) {
            throw error(MetricErrorCode.DSL_PLAN_INVALID, "", "SEGMENTED forbids root snapshot fields");
        }
        if (plan.recentWindow() == null) {
            throw error(MetricErrorCode.DSL_FIELD_REQUIRED, "/recentWindow", "recentWindow is required");
        }
        normalizeRecentWindow(plan.recentWindow());
        validateSegments(plan.segments(), plan.metrics());
    }

    /**
     * 将合法逻辑物化计划输出为字段顺序稳定的规范 JSON。
     *
     * @param plan 逻辑物化计划
     * @return 可用于内容比对和签名的规范 JSON
     * @throws MetricValidationException 计划不满足 v2 契约时抛出
     */
    public String canonicalize(MetricMaterializationPlanDsl plan) {
        validateBasic(plan);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("schemaVersion", plan.schemaVersion());
        result.put("executionMode", plan.executionMode().name());
        result.put("dimensionKeyProviderCode", plan.dimensionKeyProviderCode());
        result.put("metrics", plan.metrics().stream()
                .sorted(Comparator.comparing(MetricReferenceDsl::metricCode))
                .map(this::toCanonicalMetric)
                .toList());
        if (plan.executionMode() == MetricQueryMode.SNAPSHOT) {
            result.put("snapshotGranularity", plan.snapshotGranularity().name());
            result.put("snapshotTarget", toCanonicalSnapshotTarget(plan.snapshotTarget()));
        } else {
            result.put("recentWindow", normalizeRecentWindow(plan.recentWindow()));
            result.put("segments", plan.segments().stream().map(this::toCanonicalSegment).toList());
        }
        return MetricJsonSupport.toJson(result);
    }

    private List<MetricReferenceDsl> parseMetrics(Object value) {
        List<Object> source = MetricDslJson.array(value, "/metrics");
        List<MetricReferenceDsl> result = new ArrayList<>(source.size());
        for (int index = 0; index < source.size(); index++) {
            String path = child("/metrics", Integer.toString(index));
            Map<String, Object> metric = MetricDslJson.object(source.get(index), path);
            MetricDslJson.rejectUnknown(metric, path, Set.of("metricCode", "definitionRevision"));
            result.add(new MetricReferenceDsl(
                    string(required(metric, "metricCode", path), child(path, "metricCode")),
                    MetricDslJson.integer(required(metric, "definitionRevision", path), child(path, "definitionRevision"))));
        }
        return result;
    }

    private void validateMetrics(List<MetricReferenceDsl> metrics) {
        if (metrics.isEmpty()) {
            throw error(MetricErrorCode.DSL_PLAN_INVALID, "/metrics", "Plan metrics must not be empty");
        }
        Set<String> metricCodes = new HashSet<>();
        for (int index = 0; index < metrics.size(); index++) {
            MetricReferenceDsl metric = metrics.get(index);
            String path = child("/metrics", Integer.toString(index));
            validateIdentifier(metric.metricCode(), child(path, "metricCode"), 100);
            if (!metricCodes.add(metric.metricCode())) {
                throw error(
                        MetricErrorCode.DSL_PLAN_INVALID,
                        child(path, "metricCode"),
                        "Duplicate metricCode");
            }
            if (metric.definitionRevision() <= 0) {
                throw error(
                        MetricErrorCode.DSL_PLAN_INVALID,
                        child(path, "definitionRevision"),
                        "definitionRevision must be positive");
            }
        }
    }

    private Map<String, Object> toCanonicalMetric(MetricReferenceDsl metric) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("metricCode", metric.metricCode());
        result.put("definitionRevision", metric.definitionRevision());
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
                    "segmentCode", "sourceType", "snapshotGranularity", "snapshotTarget"));
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
            MetricSnapshotTargetDsl snapshotTarget = parseSnapshotTarget(
                    MetricDslJson.optionalValue(segment, "snapshotTarget", child(path, "snapshotTarget")),
                    child(path, "snapshotTarget"));
            result.add(new MetricSegmentDsl(segmentCode, sourceType, granularity, snapshotTarget));
        }
        return result;
    }

    private void validateSegments(List<MetricSegmentDsl> segments, List<MetricReferenceDsl> metrics) {
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
                if (segment.snapshotGranularity() == null || segment.snapshotTarget() == null) {
                    throw error(MetricErrorCode.DSL_FIELD_REQUIRED, path, "SNAPSHOT segment requires snapshot fields");
                }
                validateSnapshotTarget(segment.snapshotTarget(), child(path, "snapshotTarget"), metrics);
            } else if (segment.snapshotGranularity() != null || segment.snapshotTarget() != null) {
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
            result.put("snapshotTarget", toCanonicalSnapshotTarget(segment.snapshotTarget()));
        }
        return result;
    }

    private @Nullable MetricSnapshotTargetDsl parseSnapshotTarget(@Nullable Object value, String path) {
        if (value == null) {
            return null;
        }
        Map<String, Object> target = MetricDslJson.object(value, path);
        MetricDslJson.rejectUnknown(target, path, Set.of("storageType", "bucketTimeField", "valueMappings"));
        MetricSnapshotStorageType storageType = MetricDslJson.enumValue(
                required(target, "storageType", path), MetricSnapshotStorageType.class, child(path, "storageType"));
        String bucketTimeField = string(required(target, "bucketTimeField", path), child(path, "bucketTimeField"));
        List<MetricSnapshotTargetMappingDsl> valueMappings = parseTargetMappings(
                required(target, "valueMappings", path), child(path, "valueMappings"));
        return new MetricSnapshotTargetDsl(storageType, bucketTimeField, valueMappings);
    }

    private void validateSnapshotTarget(MetricSnapshotTargetDsl target,
                                        String path,
                                        List<MetricReferenceDsl> metrics) {
        validateIdentifier(target.bucketTimeField(), child(path, "bucketTimeField"));
        if (target.valueMappings().isEmpty()) {
            throw error(MetricErrorCode.DSL_PLAN_INVALID, child(path, "valueMappings"), "valueMappings must not be empty");
        }
        Set<String> planMetricCodes = metrics.stream()
                .map(MetricReferenceDsl::metricCode)
                .collect(Collectors.toSet());
        for (int index = 0; index < target.valueMappings().size(); index++) {
            MetricSnapshotTargetMappingDsl mapping = target.valueMappings().get(index);
            String mappingPath = child(child(path, "valueMappings"), Integer.toString(index));
            validateIdentifier(mapping.metricCode(), child(mappingPath, "metricCode"), 100);
            validateIdentifier(mapping.fieldName(), child(mappingPath, "fieldName"));
            if (!planMetricCodes.contains(mapping.metricCode())) {
                throw error(MetricErrorCode.DSL_PLAN_INVALID, child(mappingPath, "metricCode"),
                        "Mapping metricCode must be declared by the plan");
            }
        }
    }

    private Map<String, Object> toCanonicalSnapshotTarget(MetricSnapshotTargetDsl target) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("storageType", target.storageType().name());
        result.put("bucketTimeField", target.bucketTimeField());
        result.put("valueMappings", target.valueMappings().stream()
                .sorted(Comparator.comparing(MetricSnapshotTargetMappingDsl::metricCode)
                        .thenComparing(MetricSnapshotTargetMappingDsl::fieldName))
                .map(this::toCanonicalTargetMapping)
                .toList());
        return result;
    }

    private List<MetricSnapshotTargetMappingDsl> parseTargetMappings(Object value, String path) {
        List<Object> source = MetricDslJson.array(value, path);
        List<MetricSnapshotTargetMappingDsl> result = new ArrayList<>(source.size());
        for (int index = 0; index < source.size(); index++) {
            String mappingPath = child(path, Integer.toString(index));
            Map<String, Object> mapping = MetricDslJson.object(source.get(index), mappingPath);
            MetricDslJson.rejectUnknown(mapping, mappingPath,
                    Set.of("metricCode", "fieldName"));
            result.add(new MetricSnapshotTargetMappingDsl(
                    string(required(mapping, "metricCode", mappingPath), child(mappingPath, "metricCode")),
                    string(required(mapping, "fieldName", mappingPath), child(mappingPath, "fieldName"))));
        }
        return result;
    }

    private Map<String, Object> toCanonicalTargetMapping(MetricSnapshotTargetMappingDsl mapping) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("metricCode", mapping.metricCode());
        result.put("fieldName", mapping.fieldName());
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

    private void validateIdentifier(String value, String path) {
        validateIdentifier(value, path, 64);
    }

    private void validateIdentifier(String value, String path, int maxLength) {
        if (value.length() > maxLength || !IDENTIFIER.matcher(value).matches()) {
            throw error(MetricErrorCode.DSL_IDENTIFIER_INVALID, path, "Invalid identifier");
        }
    }
}
