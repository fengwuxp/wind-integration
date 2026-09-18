package com.wind.integration.metrics.enums;

import com.wind.common.enums.DescriptiveEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

/**
 * 指标派生计算支持的表达式类型。
 *
 * @author wuxp
 * @date 2026-07-21 17:51
 */
@Getter
@Schema(description = "指标派生计算支持的表达式类型")
public enum MetricExpressionType implements DescriptiveEnum {

    @Schema(description = "Spring 表达式语言")
    SPEL("Spring 表达式语言");

    /** 枚举描述。 */
    private final String desc;

    MetricExpressionType(String desc) {
        this.desc = desc;
    }
    @Override
    public String getDesc() {
        return desc;
    }

}
