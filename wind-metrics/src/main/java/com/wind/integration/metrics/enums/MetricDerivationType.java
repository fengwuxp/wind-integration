package com.wind.integration.metrics.enums;

import com.wind.common.enums.DescriptiveEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 指标是否依赖其他指标的计算结果，与定义表达形式和查询模式分别描述。
 *
 * <p>原始指标可以包含事实聚合和本指标内的表达式；引用其他指标结果时才属于派生指标。
 * DSL 的分类由定义结构确定，不单独维护可与表达式引用矛盾的分类配置。</p>
 *
 * @author wuxp
 * @date 2024-09-12 18:57
 */
@Getter
@AllArgsConstructor
@Schema(description = "指标派生类型")
public enum MetricDerivationType implements DescriptiveEnum {

    /** 不依赖其他指标结果，直接基于事实数据计算。 */
    @Schema(description = "原始指标")
    RAW("原始指标"),

    /** 引用其他指标结果进行计算。 */
    @Schema(description = "派生指标")
    DERIVED("派生指标");

    /** 指标分类的展示描述。 */
    private final String desc;

    /**
     * 判断是否引用其他指标的计算结果。
     *
     * @return 派生指标返回 true
     */
    public boolean isDerived() {
        return this == DERIVED;
    }
}
