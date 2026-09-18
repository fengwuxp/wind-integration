package com.wind.integration.metrics.enums;

import com.wind.common.enums.DescriptiveEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

/**
 * 物化结果的逻辑保存形态，不指定物理表。
 *
 * @author wuxp
 * @date 2026-09-14 15:30
 */
@Getter
@Schema(description = "物化结果的逻辑保存形态")
public enum MetricSnapshotStorageType implements DescriptiveEnum {

    @Schema(description = "指标值表，不同指标可使用相同逻辑结果字段名")
    METRIC_VALUE_TABLE("指标值表"),

    @Schema(description = "宽表，同一统计行承载多个指标结果")
    WIDE_TABLE("宽表");

    /** 枚举描述。 */
    private final String desc;

    MetricSnapshotStorageType(String desc) {
        this.desc = desc;
    }
    @Override
    public String getDesc() {
        return desc;
    }

}
