package com.wind.integration.metrics.dsl;

/** 指标过滤操作符。 */
public enum MetricFilterOperator {
    EQ,
    NE,
    IN,
    NOT_IN,
    GT,
    GE,
    LT,
    LE,
    IS_NULL,
    IS_NOT_NULL,
    AND,
    OR
}
