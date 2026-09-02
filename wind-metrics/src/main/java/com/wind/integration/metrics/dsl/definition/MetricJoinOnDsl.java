package com.wind.integration.metrics.dsl.definition;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Objects;

/**
 * 事实关联中的一组等值字段。
 *
 * @param primaryField 主事实源字段名
 * @param joinField 关联事实源字段名
 *
 * @author wuxp
 * @date 2026-07-21 17:51
 */
@Schema(description = "事实关联中的一组等值字段")
public record MetricJoinOnDsl(
        @Schema(description = "主事实源字段名") String primaryField,
        @Schema(description = "关联事实源字段名") String joinField) {

    public MetricJoinOnDsl {
        Objects.requireNonNull(primaryField, "primaryField must not be null");
        Objects.requireNonNull(joinField, "joinField must not be null");
    }
}
