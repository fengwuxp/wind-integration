package com.wind.integration.metrics.dsl;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/** 指标逻辑物化计划，不包含物理表或数据源绑定。 */
public record MetricMaterializationPlanDsl(Integer schemaVersion,
                                           MetricExecutionMode executionMode,
                                           String snapshotKeyProviderCode,
                                           @Nullable SnapshotGranularity snapshotGranularity,
                                           @Nullable String snapshotTargetCode,
                                           @Nullable String recentWindow,
                                           List<MetricSegmentDsl> segments) {

    public MetricMaterializationPlanDsl {
        Objects.requireNonNull(schemaVersion, "schemaVersion must not be null");
        Objects.requireNonNull(executionMode, "executionMode must not be null");
        Objects.requireNonNull(snapshotKeyProviderCode, "snapshotKeyProviderCode must not be null");
        segments = List.copyOf(segments);
    }
}
