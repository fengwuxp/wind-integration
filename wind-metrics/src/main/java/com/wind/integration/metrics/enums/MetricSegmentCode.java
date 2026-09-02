package com.wind.integration.metrics.enums;

import com.wind.common.enums.DescriptiveEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 分段物化计划中固定的时间分段标识。
 *
 * @author wuxp
 * @date 2026-07-21 17:51
 */
@Getter
@AllArgsConstructor
@Schema(description = "分段物化计划中固定的时间分段标识")
public enum MetricSegmentCode implements DescriptiveEnum {

    @Schema(description = "历史分段")
    ARCHIVE("历史分段"),
    @Schema(description = "近期分段")
    RECENT("近期分段");

    /** 枚举描述。 */
    private final String desc;
}
