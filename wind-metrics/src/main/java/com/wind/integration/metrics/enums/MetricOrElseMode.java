package com.wind.integration.metrics.enums;

import com.wind.common.enums.DescriptiveEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * SQL 正常返回空结果时的指标值处理方式。
 *
 * @author wuxp
 * @date 2026-07-21 17:51
 */
@Getter
@AllArgsConstructor
public enum MetricOrElseMode implements DescriptiveEnum {

    ZERO("返回零值"),
    NULL("保留空值"),
    VALUE("返回指定值");

    /** 枚举描述。 */
    private final String desc;
}
