package com.wind.integration.metrics.enums;

import com.wind.common.enums.DescriptiveEnum;
import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "指标事实过滤条件支持的操作符")
public enum MetricFilterOperator implements DescriptiveEnum {

    @Schema(description = "等于")
    EQ("等于"),
    @Schema(description = "不等于")
    NE("不等于"),
    @Schema(description = "属于集合")
    IN("属于集合"),
    @Schema(description = "不属于集合")
    NOT_IN("不属于集合"),
    @Schema(description = "大于")
    GT("大于"),
    @Schema(description = "大于等于")
    GE("大于等于"),
    @Schema(description = "小于")
    LT("小于"),
    @Schema(description = "小于等于")
    LE("小于等于"),
    @Schema(description = "为空")
    IS_NULL("为空"),
    @Schema(description = "不为空")
    IS_NOT_NULL("不为空"),
    @Schema(description = "逻辑与")
    AND("逻辑与"),
    @Schema(description = "逻辑或")
    OR("逻辑或");

    /** 枚举描述。 */
    private final String desc;
}
