package com.wind.integration.metrics.enums;

import com.wind.common.enums.DescriptiveEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 指标物化度量跨分段合并时保留的聚合状态。
 *
 * @author wuxp
 * @since 2026-07-28
 */
@Getter
@AllArgsConstructor
@Schema(description = "指标物化度量跨分段合并时保留的聚合状态")
public enum MetricMergeState implements DescriptiveEnum {

    @Schema(description = "求和")
    SUM("求和"),
    @Schema(description = "最小值")
    MIN("最小值"),
    @Schema(description = "最大值")
    MAX("最大值");

    /** 枚举描述。 */
    private final String desc;
}
