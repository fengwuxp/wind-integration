package com.wind.integration.metrics.enums;

import com.wind.common.enums.DescriptiveEnum;
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
public enum MetricAggregation implements DescriptiveEnum {

    COUNT("计数"),
    SUM("求和"),
    AVG("平均值"),
    MIN("最小值"),
    MAX("最大值");

    /** 枚举描述。 */
    private final String desc;
}
