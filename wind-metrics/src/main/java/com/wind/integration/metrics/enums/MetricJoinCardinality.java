package com.wind.integration.metrics.enums;

import com.wind.common.enums.DescriptiveEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 关联事实相对主事实的基数约束。
 *
 * @author wuxp
 * @date 2026-07-21 17:51
 */
@Getter
@AllArgsConstructor
public enum MetricJoinCardinality implements DescriptiveEnum {

    ONE_TO_ONE("一对一"),
    MANY_TO_ONE("多对一");

    /** 枚举描述。 */
    private final String desc;
}
