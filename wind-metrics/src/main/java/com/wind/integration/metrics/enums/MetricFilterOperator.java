package com.wind.integration.metrics.enums;

import com.wind.common.enums.DescriptiveEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 指标事实过滤条件支持的操作符。
 *
 * @author wuxp
 * @date 2026-07-21 17:51
 */
@Getter
@AllArgsConstructor
public enum MetricFilterOperator implements DescriptiveEnum {

    EQ("等于"),
    NE("不等于"),
    IN("属于集合"),
    NOT_IN("不属于集合"),
    GT("大于"),
    GE("大于等于"),
    LT("小于"),
    LE("小于等于"),
    IS_NULL("为空"),
    IS_NOT_NULL("不为空"),
    AND("逻辑与"),
    OR("逻辑或");

    /** 枚举描述。 */
    private final String desc;
}
