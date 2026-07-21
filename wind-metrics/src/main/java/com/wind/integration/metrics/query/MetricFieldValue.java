package com.wind.integration.metrics.query;

import com.wind.integration.metrics.dsl.MetricDslErrorCode;
import com.wind.integration.metrics.dsl.MetricValueType;
import org.jspecify.annotations.Nullable;

/** FIELD_SET 中的单个指标值。 */
public record MetricFieldValue(MetricValueType valueType, @Nullable Number value) {

    public MetricFieldValue {
        if (valueType == null) {
            throw MetricQueryValueSupport.error(
                    MetricDslErrorCode.RESULT_INVALID,
                    "/valueType",
                    "valueType must not be null");
        }
        MetricQueryValueSupport.validateMetricValue(valueType, value, "/value");
    }
}
