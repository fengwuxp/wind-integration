package com.wind.integration.metrics.enums;

import com.wind.common.enums.DescriptiveEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

/**
 * 指标行选择支持的排序方向。
 *
 * @author wuxp
 * @date 2026-07-22 16:01
 */
@Getter
@Schema(description = "指标行选择支持的排序方向")
public enum MetricSortDirection implements DescriptiveEnum {

    @Schema(description = "升序")
    ASC("升序"),

    @Schema(description = "降序")
    DESC("降序");

    /** 枚举描述。 */
    private final String desc;

    MetricSortDirection(String desc) {
        this.desc = desc;
    }
    @Override
    public String getDesc() {
        return desc;
    }

}
