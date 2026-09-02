package com.wind.integration.metrics.enums;

import com.wind.common.enums.DescriptiveEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 指标事实字段支持的聚合函数。
 *
 * @author wuxp
 * @date 2026-07-21 17:51
 */
@Getter
@AllArgsConstructor
@Schema(description = "指标事实字段支持的聚合函数")
public enum MetricAggregation implements DescriptiveEnum {

    @Schema(description = "计数")
    COUNT("计数"),
    @Schema(description = "求和")
    SUM("求和"),
    @Schema(description = "平均值")
    AVG("平均值"),
    @Schema(description = "最小值")
    MIN("最小值"),
    @Schema(description = "最大值")
    MAX("最大值");

    /** 枚举描述。 */
    private final String desc;
}
