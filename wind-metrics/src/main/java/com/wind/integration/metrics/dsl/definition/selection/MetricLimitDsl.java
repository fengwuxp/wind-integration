package com.wind.integration.metrics.dsl.definition.selection;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

/**
 * 行选择数量，固定值和查询参数引用必须二选一。
 *
 * @param value 固定选择数量
 * @param parameter 查询参数名称
 *
 * @author wuxp
 * @date 2026-07-23 11:10
 */
@Schema(description = "行选择数量定义，固定值和查询参数引用二选一")
public record MetricLimitDsl(
        @Nullable @Schema(description = "固定选择数量") Integer value,
        @Nullable @Schema(description = "查询参数名称") String parameter) {

    public MetricLimitDsl {
        if ((value == null) == (parameter == null)) {
            throw new IllegalArgumentException("Exactly one limit branch is required");
        }
    }
}
