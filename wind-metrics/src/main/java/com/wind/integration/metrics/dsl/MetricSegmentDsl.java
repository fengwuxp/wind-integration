package com.wind.integration.metrics.dsl;

import org.jspecify.annotations.Nullable;

import java.util.Objects;

/** 逻辑物化计划中的单个分段。 */
public record MetricSegmentDsl(MetricSegmentCode segmentCode,
                               MetricSegmentSourceType sourceType,
                               @Nullable SnapshotGranularity snapshotGranularity,
                               @Nullable String snapshotTargetCode) {

    public MetricSegmentDsl {
        Objects.requireNonNull(segmentCode, "segmentCode must not be null");
        Objects.requireNonNull(sourceType, "sourceType must not be null");
    }
}
