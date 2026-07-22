package com.wind.integration.metrics.query;

import org.jspecify.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.Map;

import static com.wind.integration.metrics.enums.MetricErrorCode.QUERY_INVALID;
import static com.wind.integration.metrics.query.MetricQueryValueSupport.error;

/**
 * 单个指标的正式查询条件。
 *
 * @param metricCode 指标编码
 * @param subjectId 主体标识；全局指标查询为空
 * @param startTime 查询开始时间，包含
 * @param endTime 查询结束时间，不包含
 * @param dimensionValues DSL 已声明维度的查询值
 *
 * @author wuxp
 * @date 2026-07-21 17:51
 */
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

    /**
     * 返回防御性复制后的只读维度条件。
     *
     * @return 不允许修改的维度条件
     */
    @Override
    public Map<String, Object> dimensionValues() {
        return MetricQueryValueSupport.copyDimensions(dimensionValues);
    }
}
