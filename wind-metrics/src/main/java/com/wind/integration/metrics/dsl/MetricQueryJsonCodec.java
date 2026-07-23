package com.wind.integration.metrics.dsl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONException;
import com.wind.integration.metrics.MetricValidationException;
import com.wind.integration.metrics.enums.MetricErrorCode;
import com.wind.integration.metrics.query.MetricBatchQuery;
import com.wind.integration.metrics.query.MetricQuery;

import java.math.BigInteger;
import java.util.Map;
import java.util.Set;

/**
 * 指标正式查询 JSON 的关闭世界解析入口。
 *
 * <p>本类型只约束公开查询字段并映射 Wind 查询模型；维度值的事实字段类型绑定由消费方完成。</p>
 *
 * @author wuxp
 * @date 2026-07-22 16:01
 */
public final class MetricQueryJsonCodec {

    /** 单指标查询允许出现的顶层字段。 */
    private static final Set<String> QUERY_FIELDS = Set.of(
            "metricCode", "subjectId", "startTime", "endTime", "dimensionValues", "parameterValues");

    /** 批量指标查询允许出现的顶层字段。 */
    private static final Set<String> BATCH_QUERY_FIELDS = Set.of(
            "metricCodes", "subjectId", "startTime", "endTime", "dimensionValues");

    /**
     * 解析单指标正式查询。
     *
     * @param json 查询 JSON
     * @return 单指标查询条件
     * @throws MetricValidationException JSON 或查询字段不符合公开合同时抛出
     */
    public MetricQuery parse(String json) {
        Map<String, Object> source = MetricDslJsonSupport.parseRootObject(json);
        rejectUnknownFields(source, QUERY_FIELDS);
        if (source.containsKey("parameterValues")) {
            validateParameterValues(source.get("parameterValues"));
        } else {
            source.put("parameterValues", Map.of());
        }
        return deserialize(MetricDslJsonSupport.toJson(source), MetricQuery.class);
    }

    /**
     * 解析批量指标正式查询。
     *
     * @param json 批量查询 JSON
     * @return 批量指标查询条件
     * @throws MetricValidationException JSON 或查询字段不符合公开合同时抛出
     */
    public MetricBatchQuery parseBatch(String json) {
        rejectUnknownFields(MetricDslJsonSupport.parseRootObject(json), BATCH_QUERY_FIELDS);
        return deserialize(json, MetricBatchQuery.class);
    }

    private static void rejectUnknownFields(Map<String, Object> source, Set<String> allowedFields) {
        for (String field : source.keySet()) {
            if (!allowedFields.contains(field)) {
                throw new MetricValidationException(
                        MetricErrorCode.QUERY_INVALID,
                        MetricDslJsonSupport.child("", field),
                        "Unknown query field");
            }
        }
    }

    private static void validateParameterValues(Object value) {
        if (!(value instanceof Map<?, ?> parameters)) {
            throw invalidParameter("/parameterValues");
        }
        parameters.forEach((name, parameter) -> {
            String fieldName = name instanceof String text ? text : "";
            String path = fieldName.isBlank()
                    ? "/parameterValues"
                    : MetricDslJsonSupport.child("/parameterValues", fieldName);
            if (fieldName.isBlank()
                    || !(parameter instanceof BigInteger integer)
                    || integer.compareTo(BigInteger.valueOf(Integer.MIN_VALUE)) < 0
                    || integer.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) > 0) {
                throw invalidParameter(path);
            }
        });
    }

    private static MetricValidationException invalidParameter(String path) {
        return new MetricValidationException(
                MetricErrorCode.METRIC_PARAMETER_TYPE_MISMATCH,
                path,
                "Query parameter must use a non-blank name and integer value");
    }

    private static <T> T deserialize(String json, Class<T> type) {
        try {
            return JSON.parseObject(json, type);
        } catch (MetricValidationException exception) {
            throw exception;
        } catch (JSONException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof MetricValidationException validationException) {
                throw validationException;
            }
            throw new MetricValidationException(
                    MetricErrorCode.QUERY_INVALID, "", "Invalid query JSON", exception);
        }
    }
}
