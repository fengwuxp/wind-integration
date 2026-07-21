package com.wind.integration.metrics.query;

import org.jspecify.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.Map;

import static com.wind.integration.metrics.dsl.MetricDslErrorCode.QUERY_INVALID;
import static com.wind.integration.metrics.query.MetricQueryValueSupport.error;

/** 正式单指标查询。 */
public record MetricQuery(String metricCode,
                          @Nullable String subjectId,
                          LocalDateTime startTime,
                          LocalDateTime endTime,
                          Map<String, Object> dimensionValues) {

    public MetricQuery {
        if (metricCode == null || metricCode.isBlank()) {
            throw error(QUERY_INVALID, "/metricCode", "metricCode must not be blank");
        }
        if (subjectId != null && subjectId.isBlank()) {
            throw error(QUERY_INVALID, "/subjectId", "subjectId must not be blank");
        }
        MetricQueryValueSupport.validateWindow(startTime, endTime, QUERY_INVALID);
        dimensionValues = MetricQueryValueSupport.immutableDimensions(dimensionValues);
    }

    @Override
    public Map<String, Object> dimensionValues() {
        return MetricQueryValueSupport.copyDimensions(dimensionValues);
    }
}
