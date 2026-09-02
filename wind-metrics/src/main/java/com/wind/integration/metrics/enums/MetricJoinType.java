package com.wind.integration.metrics.enums;

import com.wind.common.enums.DescriptiveEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 指标事实关联支持的连接类型。
 *
 * @author wuxp
 * @date 2026-07-21 17:51
 */
@Getter
@AllArgsConstructor
@Schema(description = "指标事实关联支持的连接类型")
public enum MetricJoinType implements DescriptiveEnum {

    @Schema(description = "内连接")
    INNER("内连接"),
    @Schema(description = "左连接")
    LEFT("左连接");

    /** 枚举描述。 */
    private final String desc;
}
