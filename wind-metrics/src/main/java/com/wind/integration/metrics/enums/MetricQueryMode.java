package com.wind.integration.metrics.enums;

import com.wind.common.enums.DescriptiveEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 指标由系统选定的顶层查询模式。
 *
 * <p>该模式决定正式查询使用实时、快照或分段路径；正式查询调用方不能指定，管理面
 * 通过受控动作切换，单个分段的实际数据来源由 {@link MetricSegmentSourceType} 表达。</p>
 *
 * @author wuxp
 * @since 2026-08-24
 */
@Getter
@AllArgsConstructor
@Schema(description = "指标由系统选定的顶层查询模式")
public enum MetricQueryMode implements DescriptiveEnum {

    @Schema(description = "实时查询")
    REALTIME("实时查询"),
    @Schema(description = "快照查询")
    SNAPSHOT("快照查询"),
    @Schema(description = "分段查询")
    SEGMENTED("分段查询");

    /** 枚举描述。 */
    private final String desc;
}
