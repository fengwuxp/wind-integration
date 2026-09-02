package com.wind.integration.metrics.enums;

import com.wind.common.enums.DescriptiveEnum;
import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "指标派生计算支持的表达式类型")
public enum MetricExpressionType implements DescriptiveEnum {

    @Schema(description = "Spring 表达式语言")
    SPEL("Spring 表达式语言");

    /** 枚举描述。 */
    private final String desc;
}
