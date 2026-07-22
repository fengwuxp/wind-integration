package com.wind.integration.metrics.dsl.filter;

import java.util.Objects;

/**
 * 指标过滤条件中的字符串字面量。
 *
 * @param value 字符串值
 *
 * @author wuxp
 * @date 2026-07-21 17:51
 */
public record StringMetricLiteralDsl(String value) implements MetricLiteralDsl {

    public StringMetricLiteralDsl {
        Objects.requireNonNull(value, "value must not be null");
    }
}
