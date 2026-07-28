package com.wind.integration.metrics.enums;

import com.wind.common.enums.DescriptiveEnum;
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
public enum MetricMergeState implements DescriptiveEnum {

    SUM("求和"),
    MIN("最小值"),
    MAX("最大值");

    /** 枚举描述。 */
    private final String desc;
}
