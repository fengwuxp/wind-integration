package com.wind.integration.metrics.dsl.materialization;

import com.wind.integration.metrics.enums.MetricExecutionMode;
import com.wind.integration.metrics.enums.SnapshotGranularity;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * 指标逻辑物化计划，不包含物理表或数据源绑定。
 *
 * <p>{@code SNAPSHOT} 使用根级快照字段且不包含分段；{@code SEGMENTED} 使用
 * {@code recentWindow} 和固定的 archive、recent 两段。</p>
 *
 * @param schemaVersion Plan DSL 结构版本，当前只支持 {@code 1}
 * @param executionMode 物化执行模式，只允许 {@code SNAPSHOT} 或 {@code SEGMENTED}
 * @param snapshotKeyProviderCode 快照主体与维度键枚举器编码
 * @param dependencies 服务端展开并冻结的叶子物化依赖；单事实计划为空列表
 * @param snapshotGranularity 全量快照桶粒度；分段模式为空
 * @param snapshotTargetCode 全量快照逻辑目标编码；分段模式为空
 * @param recentWindow 分段模式近期窗口，只支持正数天或小时的 ISO-8601 Duration
 * @param segments 分段模式固定的 archive、recent 两段；全量快照模式为空列表
 *
 * @author wuxp
 * @date 2026-07-21 17:51
 */
public record MetricMaterializationPlanDsl(Integer schemaVersion,
                                           MetricExecutionMode executionMode,
                                           String snapshotKeyProviderCode,
                                           List<MetricMaterializationDependencyDsl> dependencies,
                                           @Nullable SnapshotGranularity snapshotGranularity,
                                           @Nullable String snapshotTargetCode,
                                           @Nullable String recentWindow,
                                           List<MetricSegmentDsl> segments) {

    public MetricMaterializationPlanDsl {
        Objects.requireNonNull(schemaVersion, "schemaVersion must not be null");
        Objects.requireNonNull(executionMode, "executionMode must not be null");
        Objects.requireNonNull(snapshotKeyProviderCode, "snapshotKeyProviderCode must not be null");
        dependencies = List.copyOf(dependencies);
        segments = List.copyOf(segments);
    }
}
