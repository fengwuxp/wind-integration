package com.wind.integration.metrics.dsl.definition;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * 指标被统计主体定义。
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

    public MetricSubjectDsl {
        Objects.requireNonNull(type, "type must not be null");
    }
}
