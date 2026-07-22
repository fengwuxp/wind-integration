package com.wind.integration.metrics.dsl.definition;

import com.wind.integration.metrics.enums.MetricExpressionType;

import java.util.Objects;

/**
 * 指标派生计算表达式。
 *
 * <p>Codec 只校验表达式类型和非空文本，不负责执行表达式。</p>
 *
 * @param type 表达式语言类型
 * @param value 表达式文本
 *
 * @author wuxp
 * @date 2026-07-21 17:51
 */
public record MetricExpressionDsl(MetricExpressionType type, String value) {

    public MetricExpressionDsl {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(value, "value must not be null");
    }
}
