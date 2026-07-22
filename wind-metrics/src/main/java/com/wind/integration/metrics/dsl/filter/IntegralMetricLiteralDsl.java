package com.wind.integration.metrics.dsl.filter;

import java.math.BigInteger;
import java.util.Objects;

/**
 * 指标过滤条件中的任意精度整数字面量。
 *
 * @param value 整数值
 *
 * @author wuxp
 * @date 2026-07-21 17:51
 */
public record IntegralMetricLiteralDsl(BigInteger value) implements MetricNumericLiteralDsl {

    public IntegralMetricLiteralDsl {
        Objects.requireNonNull(value, "value must not be null");
    }
}
