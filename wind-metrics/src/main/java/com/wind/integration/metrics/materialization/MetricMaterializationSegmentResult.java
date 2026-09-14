package com.wind.integration.metrics.materialization;

import com.wind.integration.metrics.MetricValidationException;
import com.wind.integration.metrics.enums.MetricErrorCode;
import com.wind.integration.metrics.enums.SnapshotGranularity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.ZoneId;

/**
 * 一个快照分段完成本次有效目标后的实际连续覆盖。
 *
 * <p>覆盖区间为 {@code [queryableStartTime, watermarkTime)}，起点等于水位表示尚无已提交桶。
 * 水位必须达到有效目标；目标早于已有水位时仍返回实际水位，不回退或裁剪覆盖。
 * 所有时刻均为 Instant，时区用于说明自然时间桶的业务解释。</p>
 *
 * <p>本模型只校验结果结构与时间先后关系。分段归属、桶对齐、关闭规则、真实提交事实
 * 及完整覆盖由实现方依据固定计划验证，不能根据 JVM 当前时区推导。</p>
 *
 * @param segmentCode 根 SNAPSHOT 使用 {@code snapshot}，分段计划使用对应的 {@code archive} 或 {@code recent}
 * @param snapshotGranularity 本段冻结的快照桶粒度
 * @param timeZone 本段冻结的业务时区
 * @param queryableStartTime 本段实际连续覆盖起点，包含
 * @param watermarkTime 本段实际已提交连续覆盖上界，不包含
 * @param effectiveTargetTime 本次应用关闭桶及范围规则后的有效目标，可以早于已有水位
 * @author wuxp
 * @since 2026-09-14
 */
@Schema(description = "单个快照分段的实际覆盖、粒度与本次有效目标")
public record MetricMaterializationSegmentResult(
        @Schema(description = "快照分段编码") String segmentCode,
        @Schema(description = "本段冻结的桶粒度") SnapshotGranularity snapshotGranularity,
        @Schema(description = "本段冻结的业务时区") ZoneId timeZone,
        @Schema(description = "连续覆盖起点，包含") Instant queryableStartTime,
        @Schema(description = "实际已提交水位，不包含") Instant watermarkTime,
        @Schema(description = "本次允许推进到的有效目标") Instant effectiveTargetTime) {

    /**
     * 校验必要元数据、连续覆盖先后关系以及本次有效目标已经完成。
     *
     * @param segmentCode 非空白快照分段编码
     * @param snapshotGranularity 本段冻结粒度
     * @param timeZone 本段冻结业务时区
     * @param queryableStartTime 连续覆盖起点
     * @param watermarkTime 已提交上界，不早于起点和有效目标
     * @param effectiveTargetTime 本次有效目标，可早于已有水位
     * @throws MetricValidationException 字段缺失、覆盖倒置或水位未达到有效目标
     */
    public MetricMaterializationSegmentResult {
        // JSON/外部构造同样经过此处；这里不替宿主证明真实桶提交。
        if (segmentCode == null || segmentCode.isBlank()) {
            throw invalid("/segmentCode", "segmentCode must not be blank");
        }
        if (snapshotGranularity == null) {
            throw invalid("/snapshotGranularity", "snapshotGranularity is required");
        }
        if (timeZone == null) {
            throw invalid("/timeZone", "timeZone is required");
        }
        if (queryableStartTime == null) {
            throw invalid("/queryableStartTime", "queryableStartTime is required");
        }
        if (watermarkTime == null) {
            throw invalid("/watermarkTime", "watermarkTime is required");
        }
        if (effectiveTargetTime == null) {
            throw invalid("/effectiveTargetTime", "effectiveTargetTime is required");
        }
        if (watermarkTime.isBefore(queryableStartTime)) {
            throw invalid("/watermarkTime", "watermarkTime must not precede coverage start");
        }
        if (watermarkTime.isBefore(effectiveTargetTime)) {
            throw invalid("/watermarkTime", "Successful materialization must reach its effective target");
        }
    }

    private static MetricValidationException invalid(String path, String message) {
        return new MetricValidationException(MetricErrorCode.RESULT_INVALID, path, message);
    }
}
