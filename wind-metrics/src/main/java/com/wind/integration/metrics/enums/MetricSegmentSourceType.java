package com.wind.integration.metrics.enums;

import com.wind.common.enums.DescriptiveEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 单个指标查询分段的数据来源类型。
 *
 * @author wuxp
 * @date 2026-07-21 17:51
 */
@Getter
@AllArgsConstructor
@Schema(description = "单个指标查询分段的数据来源类型")
public enum MetricSegmentSourceType implements DescriptiveEnum {

    @Schema(description = "实时聚合")
    REALTIME("实时聚合"),
    @Schema(description = "快照数据")
    SNAPSHOT("快照数据");

    /** 枚举描述。 */
    private final String desc;
}
