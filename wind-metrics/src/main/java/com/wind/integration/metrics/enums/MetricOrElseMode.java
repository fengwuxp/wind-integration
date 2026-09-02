package com.wind.integration.metrics.enums;

import com.wind.common.enums.DescriptiveEnum;
import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "SQL 正常返回空结果时的指标值处理方式")
public enum MetricOrElseMode implements DescriptiveEnum {

    @Schema(description = "返回零值")
    ZERO("返回零值"),
    @Schema(description = "保留空值")
    NULL("保留空值"),
    @Schema(description = "返回指定值")
    VALUE("返回指定值");

    /** 枚举描述。 */
    private final String desc;
}
