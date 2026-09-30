package com.wind.integration.metrics.enums;

import com.wind.common.enums.DescriptiveEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

/**
 * 指标取值声明的表达形式，供对应的 SQL 生成能力分派。
 *
 * <p>本枚举只标识定义的表达形式，不表示查询路线。实时、快照与分段由
 * {@link MetricQueryMode} 表达；是否可物化取决于共同定义的原始量状态和宿主存储能力。</p>
 *
 * <p>{@code DSL} 和 {@code SQL} 分别由 {@link com.wind.integration.metrics.spec.MetricValueQueryDefinition}
 * 的两个声明实现解释。{@code EXPRESSION} 保留为宿主历史定义的编码，供元数据读取和迁移使用，
 * 不由本模块的 Definition 规范解释。枚举值可识别不代表对应执行能力已提供；持久化和协议使用名称，
 * 不使用 ordinal。</p>
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

    /** 历史表达式定义的稳定编码，由宿主解释。 */
    @Schema(description = "历史表达式定义")
    EXPRESSION("表达式");

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
