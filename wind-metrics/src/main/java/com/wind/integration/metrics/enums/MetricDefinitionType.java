package com.wind.integration.metrics.enums;

import com.wind.common.enums.DescriptiveEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

/**
 * 指标定义的声明方式，决定由哪个 Definition 规范和 codec 解释该指标。
 *
 * <p>本枚举只标识定义的表达形式，不表示查询路线。实时、快照与分段由
 * {@link MetricQueryMode} 表达；{@code DSL} 指标可参与物化，{@code SQL} 仅支持实时查询。</p>
 *
 * <p>{@code DSL} 和 {@code SQL} 分别由 {@link com.wind.integration.metrics.spec.MetricDefinitionSpec}
 * 的两个规范实现解释。{@code SCRIPT} 标识宿主既有的表达式与脚本声明方式，保留用于识别历史定义，
 * 不由本模块的 Definition 规范解释。</p>
 *
 * @author wuxp
 * @date 2026-09-18
 */
@Getter
@Schema(description = "指标定义的声明方式")
public enum MetricDefinitionType implements DescriptiveEnum {

    @Schema(description = "通用指标 DSL")
    DSL("通用指标 DSL"),

    @Schema(description = "SQL 模板 或 SQL 定义")
    SQL("SQL指标定义"),

    @Schema(description = "表达式或脚本计算")
    SCRIPT("表达式或脚本计算");

    /**
     * 枚举描述。
     */
    private final String desc;

    MetricDefinitionType(String desc) {
        this.desc = desc;
    }
    @Override
    public String getDesc() {
        return desc;
    }

}
