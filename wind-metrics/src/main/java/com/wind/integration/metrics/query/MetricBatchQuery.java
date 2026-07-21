package com.wind.integration.metrics.query;

import org.jspecify.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import static com.wind.integration.metrics.dsl.MetricDslErrorCode.QUERY_INVALID;
import static com.wind.integration.metrics.query.MetricQueryValueSupport.error;

/** 同一主体、时间窗和维度的一组指标查询。 */
public record MetricBatchQuery(List<String> metricCodes,
                               @Nullable String subjectId,
                               LocalDateTime startTime,
                               LocalDateTime endTime,
                               Map<String, Object> dimensionValues) {

    public MetricBatchQuery {
        if (metricCodes == null || metricCodes.isEmpty()) {
            throw error(QUERY_INVALID, "/metricCodes", "metricCodes must not be empty");
        }
        if (metricCodes.stream().anyMatch(code -> code == null || code.isBlank())
                || metricCodes.size() != new LinkedHashSet<>(metricCodes).size()) {
            throw error(QUERY_INVALID, "/metricCodes", "metricCodes must be non-blank and unique");
        }
        if (subjectId != null && subjectId.isBlank()) {
            throw error(QUERY_INVALID, "/subjectId", "subjectId must not be blank");
        }
        metricCodes = List.copyOf(metricCodes);
        MetricQueryValueSupport.validateWindow(startTime, endTime, QUERY_INVALID);
        dimensionValues = MetricQueryValueSupport.immutableDimensions(dimensionValues);
    }

    @Override
    public Map<String, Object> dimensionValues() {
        return MetricQueryValueSupport.copyDimensions(dimensionValues);
    }
}
