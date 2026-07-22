package com.wind.integration.metrics.dsl.materialization;

import com.wind.integration.metrics.enums.MetricSegmentCode;
import com.wind.integration.metrics.enums.MetricSegmentSourceType;
import com.wind.integration.metrics.enums.SnapshotGranularity;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * 逻辑物化计划中的单个时间分段。
 *
 * @param segmentCode 固定的 archive 或 recent 分段
 * @param sourceType 分段数据来源
 * @param snapshotGranularity 快照分段的桶粒度；实时分段为空
 * @param snapshotTargetCode 快照分段的逻辑目标编码；实时分段为空
 *
 * @author wuxp
 * @date 2026-07-21 17:51
 */
public record MetricSegmentDsl(MetricSegmentCode segmentCode,
                               MetricSegmentSourceType sourceType,
                               @Nullable SnapshotGranularity snapshotGranularity,
                               @Nullable String snapshotTargetCode) {

    public MetricSegmentDsl {
        Objects.requireNonNull(segmentCode, "segmentCode must not be null");
        Objects.requireNonNull(sourceType, "sourceType must not be null");
    }
}
