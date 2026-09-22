package com.wind.integration.metrics.enums;

import com.wind.common.enums.DescriptiveEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

/**
 * 指标快照的时间粒度，也用于声明分段范围的自然周期拆分。
 *
 * <p>枚举只定义粒度词汇；可执行的粒度及边界对齐规则由宿主校验。</p>
 *
 * @author wuxp
 * @date 2026-07-21 17:51
 */
@Getter
@Schema(description = "指标快照及分段范围的时间粒度")
public enum MetricSnapshotGranularity implements DescriptiveEnum {

    @Schema(description = "小时")
    HOUR("小时"),

    @Schema(description = "自然日")
    DAY("自然日"),

    @Schema(description = "自然周")
    WEEK("自然周"),

    @Schema(description = "自然月")
    MONTH("自然月"),

    @Schema(description = "自然年")
    YEAR("自然年");

    /** 枚举描述。 */
    private final String desc;

    MetricSnapshotGranularity(String desc) {
        this.desc = desc;
    }
    @Override
    public String getDesc() {
        return desc;
    }

}
