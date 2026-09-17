package com.wind.integration.metrics.dsl.expression;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.Map;

/**
 * 指标表达式唯一允许调用的根方法集合。
 *
 * @param scale 当前结果精度
 * @param roundingMode 当前舍入方式
 * @param measureValues 仅含编译期已确认字段的本次求值快照
 * @param metricValues 仅含编译期已确认依赖的本次求值快照
 * @author wuxp
 * @since 2026-07-23
 */
record MetricExpressionRoot(
        @Nullable Integer scale,
        @Nullable RoundingMode roundingMode,
        Map<String, ?> measureValues,
        Map<MetricValueReference, ?> metricValues) {

    /**
     * 读取查询引擎已经计算并放入当前请求上下文的指标值。
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
     * @param numerator 分子
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
            case null ->
                    throw new IllegalArgumentException(
                            "Metric calculation requires a numeric value");
            default ->
                    throw new IllegalArgumentException(
                            "Metric calculation requires an exact numeric value");
        };
    }
}
