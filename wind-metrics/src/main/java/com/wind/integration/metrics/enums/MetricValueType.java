package com.wind.integration.metrics.enums;

import com.wind.common.enums.DescriptiveEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 指标数值字段支持的 Java 数值类型。
 *
 * @author wuxp
 * @date 2026-07-21 17:51
 */
@Getter
@AllArgsConstructor
public enum MetricValueType implements DescriptiveEnum {

    INTEGER("32 位整数"),
    LONG("64 位整数"),
    DECIMAL("高精度小数");

    /** 枚举描述。 */
    private final String desc;
}
