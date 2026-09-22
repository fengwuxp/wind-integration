package com.wind.integration.metrics.dsl.materialization;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wind.integration.metrics.MetricValidationException;
import com.wind.integration.metrics.dsl.definition.MetricReferenceDsl;
import com.wind.integration.metrics.enums.MetricErrorCode;
import com.wind.integration.metrics.enums.MetricQueryMode;
import com.wind.integration.metrics.enums.MetricSegmentSourceType;
import com.wind.integration.metrics.enums.MetricSnapshotGranularity;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * 指标逻辑物化计划，统一管理精确版本的成员、待快照范围及保存目标，不包含查询路由或物理绑定。
 *
 * <p>schema4 的 {@code SEGMENTED} 支持近期实时加整体历史快照、近期实时加周期历史快照，
 * 或显式固定时间范围。配置按近到远保存，由物化执行器计算目标并按远到近推进；
 * REALTIME 规则仅声明不物化的近期范围，SNAPSHOT 规则才参与统计和累计。
 * {@code SNAPSHOT} 按业务键维护完整累计状态，不接受调用方裁切范围；
 * 根级粒度只定义物化刷新及提交周期，不把完整状态变成独立查询桶。
 * 全部范围均左闭右开，同次执行须固定参考时间和时区。</p>
 *
 * <p>滚动历史的自然周期与目标历史范围取交集：年初窗口跨年时，不能把上一整年重复计入。
 * 无下界不隐含业务原点；宿主结合请求或已知覆盖起点确定有限的物化、读取范围。
 * 目标边界不代表已生成快照，缺片、缺字段或不连续覆盖不能当作合法尾部滞后。</p>
 *
 * <p>同一计划的成员共享全部分段及保存目标。宿主发布时校验定义的模式、主体、维度、时间口径
 * 和可合并状态；宽表还须校验成员字段及类型，并按相同实际区间整组提交。
 * 查询按指标编码和定义修订固定读取绑定，以完整业务键读取所有有效快照，按实际时间范围
 * 校验、合并；不展开本计划的分段配置。是否接续实时由宿主读取模式决定，实时起点取实际
 * 连续末端，终点取本次查询条件；固定物化范围不自动替换查询条件。</p>
 *
 * @param schemaVersion Plan DSL 结构版本，只支持4；旧版本须显式迁移
 * @param executionMode 计划的物化结构分支，只允许 SNAPSHOT 或 SEGMENTED，不切换指标默认读取模式
 * @param dimensionKeyProviderCode 业务维度键提供方的逻辑注册编码
 * @param metrics 精确版本的独立指标，非空且编码唯一
 * @param snapshotGranularity 非分段快照必填的刷新及提交周期；分段模式不得设置
 * @param snapshotTarget 所有成员及快照分段共用的逻辑保存目标
 * @param segments 按近到远声明的规则；SNAPSHOT 模式为空
 * @author wuxp
 * @date 2026-07-21 17:51
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "指标逻辑物化计划，统一管理分段和保存目标")
public record MetricMaterializationPlanDsl(
        @Schema(description = "Plan DSL 结构版本，只支持4") Integer schemaVersion,
        @Schema(description = "计划的物化结构分支，不切换指标默认读取模式") MetricQueryMode executionMode,
        @Schema(description = "业务维度键提供方的逻辑注册编码") String dimensionKeyProviderCode,
        @Schema(description = "精确版本的独立指标，非空且编码唯一") List<MetricReferenceDsl> metrics,
        @Nullable @Schema(description = "非分段快照必填的刷新及提交周期；分段模式为空") MetricSnapshotGranularity snapshotGranularity,
        @JsonProperty(required = true) @Schema(description = "成员及快照分段共用的逻辑保存目标") MetricSnapshotTargetDsl snapshotTarget,
        @Schema(description = "按近到远声明的分段；SNAPSHOT 模式为空") List<MetricSegmentDsl> segments) {

    /**
     * 统一分段规则和保存目标的结构版本。
     */
    public static final int SCHEMA_VERSION = 4;

    public MetricMaterializationPlanDsl {
        if (schemaVersion == null || schemaVersion != SCHEMA_VERSION) {
            throw new MetricValidationException(MetricErrorCode.DSL_SCHEMA_VERSION_UNSUPPORTED,
                    "/schemaVersion", "Plan DSL supports schema 4; earlier plans require explicit migration");
        }
        Objects.requireNonNull(executionMode, "executionMode must not be null");
        Objects.requireNonNull(dimensionKeyProviderCode, "dimensionKeyProviderCode must not be null");
        Objects.requireNonNull(snapshotTarget, "snapshotTarget must not be null");
        metrics = List.copyOf(metrics);
        segments = segments == null ? List.of() : List.copyOf(segments);
        if (dimensionKeyProviderCode.isBlank()) {
            throw invalid("/dimensionKeyProviderCode", "Dimension key provider code must not be blank");
        }
        if (metrics.isEmpty() || metrics.stream().map(MetricReferenceDsl::metricCode).distinct().count() != metrics.size()) {
            throw invalid("/metrics", "Plan metrics must be nonempty and unique by metricCode");
        }
        if (executionMode == MetricQueryMode.SNAPSHOT) {
            if (snapshotGranularity == null) {
                throw invalid("/snapshotGranularity", "SNAPSHOT mode requires a refresh and commit granularity");
            }
            if (!segments.isEmpty()) {
                throw invalid("/segments", "SNAPSHOT mode must not declare segments");
            }
        } else if (executionMode == MetricQueryMode.SEGMENTED) {
            if (snapshotGranularity != null) {
                throw invalid("/snapshotGranularity", "SEGMENTED mode declares granularity on its snapshot rules");
            }
            validateSegments(segments);
        } else {
            throw invalid("/executionMode", "Materialization plans require SNAPSHOT or SEGMENTED mode");
        }
    }

    // Jackson 反射调用；拒绝 recentWindow 等退役字段，避免产生第二份配置源。
    @SuppressWarnings({"PMD.UnusedPrivateMethod", "PMD.UnusedFormalParameter"})
    @JsonAnySetter
    private void rejectUnknownProperty(String name, @Nullable Object value) {
        throw invalid("/" + name.replace("~", "~0").replace("/", "~1"), "Unknown materialization plan property");
    }

    private static void validateSegments(List<MetricSegmentDsl> segments) {
        if (segments.isEmpty() || segments.stream().noneMatch(segment -> segment.sourceType() == MetricSegmentSourceType.SNAPSHOT)) {
            throw invalid("/segments", "SEGMENTED plans require at least one snapshot rule");
        }
        if (segments.stream().anyMatch(segment -> segment.window() != null)) {
            if (segments.size() != 2 || segments.getFirst().window() == null
                    || segments.getLast().sourceType() != MetricSegmentSourceType.SNAPSHOT
                    || segments.getLast().start() != null || segments.getLast().end() != null) {
                throw invalid("/segments", "Rolling plans require a recent REALTIME window followed by remaining SNAPSHOT history");
            }
            return;
        }
        for (int index = 0; index < segments.size(); index++) {
            MetricSegmentDsl segment = segments.get(index);
            if (index > 0 && segment.sourceType() == MetricSegmentSourceType.REALTIME) {
                throw invalid("/segments/" + index + "/sourceType", "REALTIME may only be the most recent fixed range; snapshots must form a continuous history");
            }
            if (segment.end() == null) {
                throw invalid("/segments/" + index + "/end", "Fixed segment rules require an exclusive end");
            }
            if (index < segments.size() - 1 && (segment.start() == null || !segment.start().equals(segments.get(index + 1).end()))) {
                throw invalid("/segments/" + index + "/start", "Fixed segments must be contiguous and declared from recent to oldest");
            }
        }
    }

    private static MetricValidationException invalid(String path, String message) {
        return new MetricValidationException(MetricErrorCode.DSL_PLAN_INVALID, path, message);
    }
}
