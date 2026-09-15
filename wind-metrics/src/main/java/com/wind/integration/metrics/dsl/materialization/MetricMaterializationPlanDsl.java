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
 * {@code recentWindow} 和固定的 archive、recent 两段。查询模式不规定一次物化的起止范围；
 * 覆盖范围、目标截止时间和已提交水位由宿主管理，不属于逻辑保存目标。
 * 初次物化从配置覆盖起点开始，后续从已提交水位继续处理已关闭桶，不因追赶目标重算已提交桶。</p>
 *
 * @param schemaVersion Plan DSL 结构版本，当前只支持 {@code 2}
 * @param executionMode 计划对应的顶层查询模式，只允许 {@code SNAPSHOT} 或 {@code SEGMENTED}
 * @param dimensionKeyProviderCode 业务维度键提供方的逻辑注册编码
 * @param metrics 计划关联的一个或多个独立指标，每项显式指定定义修订，不包含发布生成的依赖或原始度量
 * @param snapshotGranularity SNAPSHOT 查询拓扑采用的快照桶粒度；SEGMENTED 时由各 SNAPSHOT 分段声明
 * @param snapshotTarget SNAPSHOT 查询拓扑的逻辑快照保存目标；SEGMENTED 时由各 SNAPSHOT 分段声明
 * @param recentWindow 分段模式近期窗口，只支持正数天或小时的 ISO-8601 Duration
 * @param segments SEGMENTED 查询拓扑固定的 archive、recent 两段；SNAPSHOT 时为空列表
 *
 * @author wuxp
 * @date 2026-07-21 17:51
 */
@Schema(description = "指标逻辑物化计划，不包含物理表或数据源绑定")
@JsonDeserialize(using = MetricMaterializationPlanDslJsonBinding.Deserializer.class)
@JsonSerialize(using = MetricMaterializationPlanDslJsonBinding.Serializer.class)
public record MetricMaterializationPlanDsl(
        @Schema(description = "Plan DSL 结构版本，当前只支持 2") Integer schemaVersion,
        @Schema(description = "计划对应的顶层查询模式") MetricQueryMode executionMode,
        @Schema(description = "业务维度键提供方的逻辑注册编码") String dimensionKeyProviderCode,
        @Schema(description = "计划关联的独立指标，非空且指标编码唯一，每项定义修订必填") List<MetricReferenceDsl> metrics,
        @Nullable @Schema(description = "SNAPSHOT 查询拓扑采用的快照桶粒度；SEGMENTED 时由各 SNAPSHOT 分段声明") SnapshotGranularity snapshotGranularity,
        @Nullable @Schema(description = "SNAPSHOT 查询拓扑的逻辑快照保存目标；SEGMENTED 时由各 SNAPSHOT 分段声明") MetricSnapshotTargetDsl snapshotTarget,
        @Nullable @Schema(description = "分段模式近期窗口") String recentWindow,
        @Schema(description = "SEGMENTED 查询拓扑固定的 archive、recent 两段；SNAPSHOT 时为空列表") List<MetricSegmentDsl> segments) {

    public MetricMaterializationPlanDsl {
        Objects.requireNonNull(schemaVersion, "schemaVersion must not be null");
        Objects.requireNonNull(executionMode, "executionMode must not be null");
        Objects.requireNonNull(dimensionKeyProviderCode, "dimensionKeyProviderCode must not be null");
        metrics = List.copyOf(metrics);
        segments = List.copyOf(segments);
    }
}
