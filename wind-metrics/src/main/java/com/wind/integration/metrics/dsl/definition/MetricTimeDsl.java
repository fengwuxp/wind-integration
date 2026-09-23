package com.wind.integration.metrics.dsl.definition;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Objects;

/**
 * 主事实源用于限定半开查询窗口的时间字段。
 *
 * <p>它只定义时间字段口径，不定义 now、快照水位或分段边界；这些由查询/物化宿主解释。</p>
 *
 * @param field 时间字段名
 *
 * @author wuxp
 * @date 2026-07-21 17:51
 */
@Schema(description = "主事实源用于限定查询窗口的时间字段")
public record MetricTimeDsl(
        @Schema(description = "时间字段名") String field) {

    public MetricTimeDsl {
        Objects.requireNonNull(field, "field must not be null");
    }
}
