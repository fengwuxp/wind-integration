package com.wind.integration.metrics.enums;

import com.wind.common.enums.DescriptiveEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 单个指标返回值的结构类型。
 *
 * @author wuxp
 * @date 2026-07-21 17:51
 */
@Getter
@AllArgsConstructor
public enum MetricValueShape implements DescriptiveEnum {

    SCALAR("单值"),
    FIELD_SET("多字段值");

    /** 枚举描述。 */
    private final String desc;
}
