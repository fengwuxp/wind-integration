package com.wind.integration.metrics.enums;

import com.wind.common.enums.DescriptiveEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 指标快照桶的时间粒度。
 *
 * @author wuxp
 * @date 2026-07-21 17:51
 */
@Getter
@AllArgsConstructor
@Schema(description = "指标快照桶的时间粒度")
public enum SnapshotGranularity implements DescriptiveEnum {

    @Schema(description = "小时")
    HOUR("小时"),
    @Schema(description = "自然日")
    DAY("自然日");

    /** 枚举描述。 */
    private final String desc;
}
