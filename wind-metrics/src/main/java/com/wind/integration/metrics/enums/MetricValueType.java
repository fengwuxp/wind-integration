package com.wind.integration.metrics.enums;

import com.wind.common.enums.DescriptiveEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

/**
 * 指标值声明类型；结果承载支持全部类型，计算能力须按具体执行器校验。
 *
 * @author wuxp
 * @date 2026-07-21 17:51
 */
@Getter
@Schema(description = "指标值声明类型")
public enum MetricValueType implements DescriptiveEnum {

    @Schema(description = "32 位整数")
    INTEGER("32 位整数"),
    @Schema(description = "64 位整数")
    LONG("64 位整数"),
    @Schema(description = "高精度小数")
    DECIMAL("高精度小数"),
    @Schema(description = "字符串")
    STRING("字符串"),
    @Schema(description = "时间戳")
    TIMESTAMP("时间戳");

    /** 枚举描述。 */
    private final String desc;

    MetricValueType(String desc) {
        this.desc = desc;
    }
    @Override
    public String getDesc() {
        return desc;
    }

}
