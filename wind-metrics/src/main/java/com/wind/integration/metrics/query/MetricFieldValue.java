package com.wind.integration.metrics.query;

import com.wind.integration.metrics.enums.MetricErrorCode;
import com.wind.integration.metrics.enums.MetricValueType;
import org.jspecify.annotations.Nullable;

/**
 * 多字段指标结果中的单个字段值。
 *
 * @param valueType 字段数值类型
 * @param value 字段值；SQL 正常空结果可以为空
 *
 * @author wuxp
 * @date 2026-07-21 17:51
 */
public record MetricFieldValue(MetricValueType valueType, @Nullable Number value) {

    public MetricFieldValue {
        if (valueType == null) {
            throw MetricQueryValueSupport.error(
                    MetricErrorCode.RESULT_INVALID,
                    "/valueType",
                    "valueType must not be null");
        }
        MetricQueryValueSupport.validateMetricValue(valueType, value, "/value");
    }
}
