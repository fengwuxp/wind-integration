package com.wind.integration.metrics.query;

import com.wind.integration.metrics.enums.MetricErrorCode;
import com.wind.integration.metrics.MetricValidationException;
import com.wind.integration.metrics.WindMetricsValue;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 指标查询契约共享的值校验与防御性复制工具。
 *
 * @author wuxp
 * @date 2026-07-21 17:51
 */
final class MetricQueryValueSupport {

    private MetricQueryValueSupport() {
    }

    static Map<String, Object> immutableDimensions(Map<String, Object> source) {
        if (source == null) {
            throw error(MetricErrorCode.QUERY_INVALID, "/dimensionValues", "dimensionValues must not be null");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (key == null || key.isBlank()) {
                throw error(MetricErrorCode.QUERY_INVALID, "/dimensionValues", "Dimension name must not be blank");
            }
            if (!isSupportedDimensionValue(value)) {
                throw error(
                        MetricErrorCode.QUERY_INVALID,
                        "/dimensionValues/" + escape(key),
                        "Unsupported dimension value");
            }
            result.put(key, copyDimensionValue(value));
        });
        return Collections.unmodifiableMap(result);
    }

    static Map<String, Object> copyDimensions(Map<String, Object> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(key, copyDimensionValue(value)));
        return Collections.unmodifiableMap(result);
    }

    static Map<String, Object> immutableParameters(Map<String, Object> source) {
        if (source == null) {
            throw error(
                    MetricErrorCode.METRIC_PARAMETER_TYPE_MISMATCH,
                    "/parameterValues",
                    "parameterValues must not be null");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            String path = key == null || key.isBlank() ? "/parameterValues" : "/parameterValues/" + escape(key);
            if (key == null || key.isBlank() || !(value instanceof Integer)) {
                throw error(
                        MetricErrorCode.METRIC_PARAMETER_TYPE_MISMATCH,
                        path,
                        "Query parameter must use a non-blank name and integer value");
            }
            result.put(key, value);
        });
        return Collections.unmodifiableMap(result);
    }

    static void validateWindow(LocalDateTime startTime, LocalDateTime endTime, MetricErrorCode code) {
        if (startTime == null) {
            throw error(code, "/startTime", "startTime must not be null");
        }
        if (endTime == null) {
            throw error(code, "/endTime", "endTime must not be null");
        }
        if (!startTime.isBefore(endTime)) {
            throw error(code, "/endTime", "endTime must be after startTime");
        }
    }

    /** 固定提供者的类型和值；不能在 getter 中反复求值或转换。 */
    static WindMetricsValue<?> normalizeMetricValue(String code, @Nullable WindMetricsValue<?> value) {
        if (value == null) {
            throw error(MetricErrorCode.RESULT_INVALID, "/value", "Metric value must not be null");
        }
        return WindMetricsValue.of(code, value.getValueType(), value.getValue());
    }

    static MetricValidationException error(MetricErrorCode code, String path, String message) {
        return new MetricValidationException(code, path, message);
    }

    private static boolean isSupportedDimensionValue(Object value) {
        return value instanceof String
                || value instanceof Character
                || value instanceof Boolean
                || value instanceof Enum<?>
                || value instanceof UUID
                || value instanceof Byte
                || value instanceof Short
                || value instanceof Integer
                || value instanceof Long
                || value instanceof BigDecimal
                || value instanceof LocalDateTime
                || value instanceof Instant
                || value instanceof OffsetDateTime
                || value instanceof ZonedDateTime
                || value instanceof Date
                || value instanceof Timestamp;
    }

    private static Object copyDimensionValue(Object value) {
        if (value instanceof Timestamp timestamp) {
            return timestamp.clone();
        }
        if (value instanceof Date date) {
            return date.clone();
        }
        return value;
    }

    private static String escape(String value) {
        return value.replace("~", "~0").replace("/", "~1");
    }
}
