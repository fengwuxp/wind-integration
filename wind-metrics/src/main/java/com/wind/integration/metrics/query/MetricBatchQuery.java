package com.wind.integration.metrics.query;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import static com.wind.integration.metrics.enums.MetricErrorCode.QUERY_INVALID;
import static com.wind.integration.metrics.query.MetricQueryValueSupport.error;

/**
 * 对同一主体、半开时间窗口和维度条件执行的一组指标查询。
 *
 * @param metricCodes 非空且不重复的指标编码列表
 * @param subjectId 主体标识；全局指标查询为空
 * @param startTime 查询开始时间，包含
 * @param endTime 查询结束时间，不包含
 * @param dimensionValues DSL 已声明维度的查询值
 *
 * @author wuxp
 * @date 2026-07-21 17:51
 */
@Schema(description = "批量指标查询条件")
public record MetricBatchQuery(
        @Schema(description = "指标编码列表，不能为空且不能重复") List<String> metricCodes,
        @Nullable @Schema(description = "主体标识；全局指标查询为空") String subjectId,
        @Schema(description = "查询开始时间，包含") LocalDateTime startTime,
        @Schema(description = "查询结束时间，不包含") LocalDateTime endTime,
        @Schema(description = "DSL 已声明维度的查询值") Map<String, Object> dimensionValues) {

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
