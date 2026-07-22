package com.wind.integration.metrics;

import com.wind.integration.metrics.enums.MetricErrorCode;

import java.io.Serial;
import java.util.Objects;

/**
 * 指标定义、查询和结果契约的校验异常。
 *
 * <p>{@code fieldPath} 使用 RFC 6901 JSON Pointer 表示错误字段；根对象错误使用空字符串。</p>
 *
 * @author wuxp
 * @date 2026-07-21 17:51
 */
public class MetricValidationException extends IllegalArgumentException {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 校验失败的稳定错误码。 */
    private final MetricErrorCode errorCode;

    /** 校验失败字段的 JSON Pointer。 */
    private final String fieldPath;

    /**
     * 创建不包含底层原因的指标校验异常。
     *
     * @param errorCode 校验错误码
     * @param fieldPath 错误字段的 JSON Pointer
     * @param message 错误说明
     */
    public MetricValidationException(MetricErrorCode errorCode, String fieldPath, String message) {
        super(message);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode must not be null");
        this.fieldPath = Objects.requireNonNull(fieldPath, "fieldPath must not be null");
    }

    /**
     * 创建保留底层原因的指标校验异常。
     *
     * @param errorCode 校验错误码
     * @param fieldPath 错误字段的 JSON Pointer
     * @param message 错误说明
     * @param cause 底层异常
     */
    public MetricValidationException(
            MetricErrorCode errorCode, String fieldPath, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode must not be null");
        this.fieldPath = Objects.requireNonNull(fieldPath, "fieldPath must not be null");
    }

    /**
     * 获取稳定错误码。
     *
     * @return 校验错误码
     */
    public MetricErrorCode errorCode() {
        return errorCode;
    }

    /**
     * 获取错误字段路径。
     *
     * @return RFC 6901 JSON Pointer；根对象错误返回空字符串
     */
    public String fieldPath() {
        return fieldPath;
    }
}
