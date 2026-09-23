package com.wind.integration.metrics.expression;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.Map;

/**
 * 一次表达式求值的只读输入。
 *
 * @param scale         当前结果精度
 * @param roundingMode  当前舍入方式
 * @param measureValues 本指标编译期确认的本地 measure 值
 * @param metricValues  编译期确认的跨指标依赖值
 * @author wuxp
 * @since 2026-07-23
 */
record ExpressionEvaluationContext(
        @Nullable Integer scale,
        @Nullable RoundingMode roundingMode,
        Map<String, ?> measureValues,
        Map<MetricValueReference, ?> metricValues) {

    /**
     * 读取宿主已经准备好的跨指标结果。
     *
     * @param metricCode 指标编码
     * @param valueField 结果字段
     * @return 精确数值或正常空值
     */
    public @Nullable BigDecimal metric(String metricCode, String valueField) {
        MetricValueReference reference = new MetricValueReference(metricCode, valueField);
        if (!metricValues.containsKey(reference)) {
            throw new IllegalArgumentException("Metric dependency value is missing");
        }
        Object value = metricValues.get(reference);
        return value == null ? null : exactDecimal(value);
    }

    /**
     * 使用当前指标值声明的精度执行十进制除法。
     *
     * @param numerator   分子
     * @param denominator 分母
     * @return 已按声明精度量化的商
     */
    public BigDecimal ratio(Object numerator, Object denominator) {
        if (scale == null || roundingMode == null) {
            throw new IllegalArgumentException("ratio requires DECIMAL value type");
        }
        BigDecimal divisor = exactDecimal(denominator);
        if (divisor.signum() == 0) {
            throw new ArithmeticException("Metric ratio denominator must not be zero");
        }
        return exactDecimal(numerator).divide(divisor, scale, roundingMode);
    }

    static BigDecimal exactDecimal(Object value) {
        return switch (value) {
            case Byte number -> BigDecimal.valueOf(number.longValue());
            case Short number -> BigDecimal.valueOf(number.longValue());
            case Integer number -> BigDecimal.valueOf(number.longValue());
            case Long number -> BigDecimal.valueOf(number);
            case BigInteger number -> new BigDecimal(number);
            case BigDecimal number -> number;
            case null -> throw new IllegalArgumentException(
                    "Metric calculation requires a numeric value");
            default -> throw new IllegalArgumentException(
                    "Metric calculation requires an exact numeric value");
        };
    }
}
