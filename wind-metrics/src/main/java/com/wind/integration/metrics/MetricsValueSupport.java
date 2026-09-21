package com.wind.integration.metrics;

import com.wind.integration.metrics.enums.MetricErrorCode;
import com.wind.integration.metrics.enums.MetricValueType;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

/**
 * 具名值的标量类型归一化；与查询方式、执行器和序列化框架无关。
 *
 * @author wuxp
 * @since 2026-09-21
 */
final class MetricsValueSupport {

    private MetricsValueSupport() {
    }

    static @NonNull MetricValueType inferType(@Nullable Object value) {
        return switch (value) {
            case Integer ignored -> MetricValueType.INTEGER;
            case Long ignored -> MetricValueType.LONG;
            case BigDecimal ignored -> MetricValueType.DECIMAL;
            case String ignored -> MetricValueType.STRING;
            case LocalDateTime ignored -> MetricValueType.TIMESTAMP;
            case null, default -> MetricValueType.DECIMAL;
        };
    }

    static @Nullable Object normalize(@Nullable MetricValueType valueType, @Nullable Object payload) {
        if (valueType == null) {
            throw error(MetricErrorCode.RESULT_INVALID, "/valueType", "Scalar valueType is required");
        }
        if (payload == null) {
            return null;
        }
        if (payload instanceof Number number) {
            payload = normalizeJsonNumber(valueType, number);
        } else if (valueType == MetricValueType.TIMESTAMP && payload instanceof String text) {
            try {
                payload = LocalDateTime.parse(text);
            } catch (DateTimeParseException exception) {
                throw error(MetricErrorCode.RESULT_INVALID, "/value", "Expected ISO local timestamp");
            }
        }
        boolean valid = switch (valueType) {
            case INTEGER -> payload instanceof Integer;
            case LONG -> payload instanceof Long;
            case DECIMAL -> payload instanceof BigDecimal;
            case STRING -> payload instanceof String;
            case TIMESTAMP -> payload instanceof LocalDateTime;
        };
        if (!valid) {
            throw error(MetricErrorCode.RESULT_INVALID, "/value", "Metric value type does not match valueType");
        }
        return payload;
    }

    private static Number normalizeJsonNumber(MetricValueType valueType, Number value) {
        if (!isIntegral(value)) {
            return value;
        }
        BigInteger integer = toBigInteger(value);
        if (valueType == MetricValueType.DECIMAL) {
            return new BigDecimal(integer);
        }
        if (valueType == MetricValueType.LONG
                && integer.compareTo(BigInteger.valueOf(Long.MIN_VALUE)) >= 0
                && integer.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) <= 0) {
            return integer.longValue();
        }
        if (valueType == MetricValueType.INTEGER
                && integer.compareTo(BigInteger.valueOf(Integer.MIN_VALUE)) >= 0
                && integer.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) <= 0) {
            return integer.intValue();
        }
        return value;
    }

    private static boolean isIntegral(Number value) {
        return value instanceof Byte || value instanceof Short || value instanceof Integer
                || value instanceof Long || value instanceof BigInteger;
    }

    private static BigInteger toBigInteger(Number value) {
        return value instanceof BigInteger integer ? integer : BigInteger.valueOf(value.longValue());
    }

    private static MetricValidationException error(MetricErrorCode code, String path, String message) {
        return new MetricValidationException(code, path, message);
    }
}
