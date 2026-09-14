package com.wind.integration.metrics.materialization;

import com.wind.integration.metrics.MetricValidationException;
import com.wind.integration.metrics.enums.MetricErrorCode;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 一次同步物化调用成功后的逐分段完成结果。
 *
 * <p>根 SNAPSHOT 计划返回一个 {@code snapshot} 分段；SEGMENTED 返回计划中实际声明的
 * SNAPSHOT 分段，按计划顺序排列。不同分段的粒度、水位和有效目标分别保留，
 * 不生成全局最大或最小水位。实际分段集合与固定计划一致的责任由实现方承担。</p>
 *
 * @param segments 非空且分段编码唯一的完成结果列表，构造时防御性复制并保持原顺序
 * @author wuxp
 * @since 2026-09-14
 */
@Schema(description = "同步物化成功后的各快照分段完成结果")
public record MetricMaterializationResult(
        @Schema(description = "按计划顺序排列的全部快照分段完成结果")
        List<MetricMaterializationSegmentResult> segments) {

    /**
     * 校验并固定分段列表，保持调用方按计划提供的顺序。
     *
     * @param segments 非空、无 null 项且分段编码唯一的完成结果
     * @throws MetricValidationException 列表为空、包含空项或分段编码重复
     */
    public MetricMaterializationResult {
        // 公共返回模型也可被 JSON 反序列化，构造边界统一校验集合内容。
        if (segments == null || segments.isEmpty()) {
            throw new MetricValidationException(
                    MetricErrorCode.RESULT_INVALID, "/segments", "Snapshot segment results must not be empty");
        }
        Set<String> codes = new HashSet<>();
        for (int index = 0; index < segments.size(); index++) {
            MetricMaterializationSegmentResult segment = segments.get(index);
            if (segment == null) {
                throw new MetricValidationException(
                        MetricErrorCode.RESULT_INVALID, "/segments/" + index, "Segment result must not be null");
            }
            if (!codes.add(segment.segmentCode())) {
                throw new MetricValidationException(
                        MetricErrorCode.RESULT_INVALID,
                        "/segments/" + index + "/segmentCode",
                        "Snapshot segment codes must be unique");
            }
        }
        segments = List.copyOf(segments);
    }
}
