package com.wind.integration.metrics.query;

import com.wind.integration.metrics.enums.MetricErrorCode;
import com.wind.integration.metrics.enums.MetricValueType;
import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "多字段指标结果中的单个字段值")
public record MetricFieldValue(
        @Schema(description = "字段数值类型") MetricValueType valueType,
        @Nullable @Schema(description = "字段值；SQL 正常空结果可以为空") Number value) {

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
