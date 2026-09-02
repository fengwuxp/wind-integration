package com.wind.integration.metrics.enums;

import com.wind.common.enums.DescriptiveEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 关联事实相对主事实的基数约束。
 *
 * @author wuxp
 * @date 2026-07-21 17:51
 */
@Getter
@AllArgsConstructor
@Schema(description = "关联事实相对主事实的基数约束")
public enum MetricJoinCardinality implements DescriptiveEnum {

    @Schema(description = "一对一")
    ONE_TO_ONE("一对一"),
    @Schema(description = "多对一")
    MANY_TO_ONE("多对一");

    /** 枚举描述。 */
    private final String desc;
}
