package com.wind.integration.metrics.enums;

import com.wind.common.enums.DescriptiveEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 指标事实关联支持的连接类型。
 *
 * @author wuxp
 * @date 2026-07-21 17:51
 */
@Getter
@AllArgsConstructor
public enum MetricJoinType implements DescriptiveEnum {

    INNER("内连接"),
    LEFT("左连接");

    /** 枚举描述。 */
    private final String desc;
}
