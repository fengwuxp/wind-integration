package com.wind.integration.metrics.dsl.materialization;

import com.wind.integration.metrics.dsl.MetricMaterializationPlanDslJsonBinding;
import com.wind.integration.metrics.enums.MetricQueryMode;
import com.wind.integration.metrics.enums.SnapshotGranularity;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;

import java.util.List;
import java.util.Objects;

/**
 * 指标逻辑物化计划，不包含物理表或数据源绑定。
 *
 * <p>{@code SNAPSHOT} 使用根级快照字段且不包含分段；{@code SEGMENTED} 使用
 * {@code recentWindow} 和固定的 archive、recent 两段。</p>
 *
 * @param schemaVersion Plan DSL 结构版本，当前只支持 {@code 1}
 * @param executionMode 计划对应的顶层查询模式，只允许 {@code SNAPSHOT} 或 {@code SEGMENTED}
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
@Schema(description = "指标逻辑物化计划，不包含物理表或数据源绑定")
@JsonDeserialize(using = MetricMaterializationPlanDslJsonBinding.Deserializer.class)
@JsonSerialize(using = MetricMaterializationPlanDslJsonBinding.Serializer.class)
public record MetricMaterializationPlanDsl(
        @Schema(description = "Plan DSL 结构版本，当前只支持 1") Integer schemaVersion,
        @Schema(description = "计划对应的顶层查询模式") MetricQueryMode executionMode,
        @Schema(description = "快照主体与维度键枚举器编码") String snapshotKeyProviderCode,
        @Schema(description = "服务端展开并冻结的叶子物化依赖") List<MetricMaterializationDependencyDsl> dependencies,
        @Nullable @Schema(description = "全量快照桶粒度；分段模式为空") SnapshotGranularity snapshotGranularity,
        @Nullable @Schema(description = "全量快照逻辑目标编码；分段模式为空") String snapshotTargetCode,
        @Nullable @Schema(description = "分段模式近期窗口") String recentWindow,
        @Schema(description = "分段模式固定的 archive、recent 两段") List<MetricSegmentDsl> segments) {

    public MetricMaterializationPlanDsl {
        Objects.requireNonNull(schemaVersion, "schemaVersion must not be null");
        Objects.requireNonNull(executionMode, "executionMode must not be null");
        Objects.requireNonNull(snapshotKeyProviderCode, "snapshotKeyProviderCode must not be null");
        dependencies = List.copyOf(dependencies);
        segments = List.copyOf(segments);
    }
}
