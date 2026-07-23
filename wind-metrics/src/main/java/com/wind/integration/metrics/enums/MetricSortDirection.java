package com.wind.integration.metrics.enums;

import com.wind.common.enums.DescriptiveEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 指标行选择支持的排序方向。
 *
 * @author wuxp
 * @date 2026-07-22 16:01
 */
@Getter
@AllArgsConstructor
public enum MetricSortDirection implements DescriptiveEnum {

    ASC("升序"),
    DESC("降序");

    /** 枚举描述。 */
    private final String desc;
}
