package com.wind.integration.metrics.dsl.definition;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * 指标被统计主体的逻辑定义。
 *
 * <p>主体类型是业务维度契约，字段是事实源中的逻辑引用；GLOBAL 表示不按主体过滤。
 * 主体的物理字段映射和调用条件校验由宿主完成。</p>
 *
 * @param type 主体类型；全局指标使用 {@code GLOBAL}
 * @param field 事实源中的主体字段；全局指标或派生指标为空
 *
 * @author wuxp
 * @date 2026-07-21 17:51
 */
@Schema(description = "指标被统计主体定义")
public record MetricSubjectDsl(
        @Schema(description = "主体类型；全局指标使用 GLOBAL") String type,
        @Nullable @Schema(description = "事实源中的主体字段；全局指标或派生指标为空") String field) {

    /** 全局指标的主体类型哨兵值。 */
    public static final String GLOBAL = "GLOBAL";

    public MetricSubjectDsl {
        Objects.requireNonNull(type, "type must not be null");
    }
}
