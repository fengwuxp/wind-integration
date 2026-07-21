package com.wind.integration.metrics.dsl;

import java.io.Serial;
import java.util.Objects;

/**
 * 指标 DSL 配置错误，使用 RFC 6901 JSON Pointer 定位字段。
 */
public class MetricDslValidationException extends IllegalArgumentException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final MetricDslErrorCode errorCode;

    private final String fieldPath;

    public MetricDslValidationException(MetricDslErrorCode errorCode, String fieldPath, String message) {
        super(message);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode must not be null");
        this.fieldPath = Objects.requireNonNull(fieldPath, "fieldPath must not be null");
    }

    public MetricDslValidationException(MetricDslErrorCode errorCode, String fieldPath, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode must not be null");
        this.fieldPath = Objects.requireNonNull(fieldPath, "fieldPath must not be null");
    }

    public MetricDslErrorCode errorCode() {
        return errorCode;
    }

    public String fieldPath() {
        return fieldPath;
    }
}
