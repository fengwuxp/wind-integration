package com.wind.integration.metrics.dsl.definition;

import com.wind.integration.metrics.enums.MetricExpressionType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Objects;

/**
 * 指标字段的受限派生表达式声明。
 *
 * <p>Codec 只恢复表达式类型和文本；实际白名单校验、依赖提取和求值由 expression 包完成。
 * 表达式不携带依赖 revision，也不负责加载指标或访问数据。</p>
 *
 * @param type 表达式语言类型
 * @param value 表达式文本
 *
 * @author wuxp
 * @date 2026-07-21 17:51
 */
@Schema(description = "指标派生计算表达式")
public record MetricExpressionDsl(
        @Schema(description = "表达式语言类型") MetricExpressionType type,
        @Schema(description = "表达式文本") String value) {

    public MetricExpressionDsl {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(value, "value must not be null");
    }
}
