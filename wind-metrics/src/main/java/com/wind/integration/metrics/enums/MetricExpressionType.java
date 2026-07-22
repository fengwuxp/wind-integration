package com.wind.integration.metrics.enums;

import com.wind.common.enums.DescriptiveEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 指标派生计算支持的表达式类型。
 *
 * @author wuxp
 * @date 2026-07-21 17:51
 */
@Getter
@AllArgsConstructor
public enum MetricExpressionType implements DescriptiveEnum {

    SPEL("Spring 表达式语言");

    /** 枚举描述。 */
    private final String desc;
}
