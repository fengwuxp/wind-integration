package com.wind.integration.metrics.dsl;

import com.wind.integration.metrics.MetricValidationException;
import com.wind.integration.metrics.enums.MetricErrorCode;
import com.wind.integration.metrics.query.MetricBatchQuery;
import com.wind.integration.metrics.query.MetricQuery;
import com.wind.jackson.WindJson;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.deser.std.StdDeserializer;
import tools.jackson.databind.ext.javatime.deser.LocalDateTimeDeserializer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.List;
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
public final class MetricQueryJsonParser {

    /** 单指标查询允许出现的顶层字段。 */
    private static final Set<String> QUERY_FIELDS = Set.of(
            "metricCode", "subjectId", "startTime", "endTime", "dimensionValues", "parameterValues");

    /** 批量指标查询允许出现的顶层字段。 */
    private static final Set<String> BATCH_QUERY_FIELDS = Set.of(
            "metricCodes", "subjectId", "startTime", "endTime", "dimensionValues");

    private static final JsonMapper QUERY_PAYLOAD_MAPPER = createQueryPayloadMapper();

    private static final MetricQueryJsonParser INSTANCE = new MetricQueryJsonParser();

    /**
     * 解析单指标正式查询。
     *
     * @param json 查询 JSON
     * @return 单指标查询条件
     * @throws MetricValidationException JSON 或查询字段不符合公开合同时抛出
     */
    public MetricQuery parse(String json) {
        return parse(MetricDslJson.parseRootObject(json));
    }

    private MetricQuery parse(JsonParser parser) {
        return parse(MetricDslJson.parseRootObject(parser));
    }

    private MetricQuery parse(Map<String, Object> source) {
        rejectUnknownFields(source, QUERY_FIELDS);
        if (source.containsKey("parameterValues")) {
            validateParameterValues(source.get("parameterValues"));
        } else {
            source.put("parameterValues", Map.of());
        }
        MetricQueryPayload payload = deserialize(MetricDslJson.toJson(source), MetricQueryPayload.class);
        return new MetricQuery(
                payload.metricCode(),
                payload.subjectId(),
                payload.startTime(),
                payload.endTime(),
                payload.dimensionValues(),
                payload.parameterValues());
    }

    /**
     * 解析批量指标正式查询。
     *
     * @param json 批量查询 JSON
     * @return 批量指标查询条件
     * @throws MetricValidationException JSON 或查询字段不符合公开合同时抛出
     */
    public MetricBatchQuery parseBatch(String json) {
        return parseBatch(MetricDslJson.parseRootObject(json));
    }

    private MetricBatchQuery parseBatch(Map<String, Object> source) {
        rejectUnknownFields(source, BATCH_QUERY_FIELDS);
        MetricBatchQueryPayload payload = deserialize(MetricDslJson.toJson(source), MetricBatchQueryPayload.class);
        return new MetricBatchQuery(
                payload.metricCodes(),
                payload.subjectId(),
                payload.startTime(),
                payload.endTime(),
                payload.dimensionValues());
    }

    private static void rejectUnknownFields(Map<String, Object> source, Set<String> allowedFields) {
        for (String field : source.keySet()) {
            if (!allowedFields.contains(field)) {
                throw new MetricValidationException(
                        MetricErrorCode.QUERY_INVALID,
                        MetricDslJson.child("", field),
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
                    : MetricDslJson.child("/parameterValues", fieldName);
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
            return QUERY_PAYLOAD_MAPPER.readValue(json, type);
        } catch (MetricValidationException exception) {
            throw exception;
        } catch (JacksonException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof MetricValidationException validationException) {
                throw validationException;
            }
            throw new MetricValidationException(
                    MetricErrorCode.QUERY_INVALID, "", "Invalid query JSON", exception);
        }
    }

    private static JsonMapper createQueryPayloadMapper() {
        DateTimeFormatter spaceSeparatedDateTime = new DateTimeFormatterBuilder()
                .append(DateTimeFormatter.ISO_LOCAL_DATE)
                .appendLiteral(' ')
                .append(DateTimeFormatter.ISO_LOCAL_TIME)
                .toFormatter();
        DateTimeFormatter queryDateTime = new DateTimeFormatterBuilder()
                .appendOptional(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                .appendOptional(spaceSeparatedDateTime)
                .toFormatter();
        SimpleModule module = new SimpleModule("MetricQueryJavaTimeModule");
        module.addDeserializer(LocalDateTime.class, new LocalDateTimeDeserializer(queryDateTime));
        return WindJson.getJsonMapper().rebuild().addModule(module).build();
    }

    /** 单指标查询的 Jackson 反序列化器。 */
    public static final class QueryDeserializer extends StdDeserializer<MetricQuery> {

        /** 创建反序列化器。 */
        public QueryDeserializer() {
            super(MetricQuery.class);
        }

        @Override
        public MetricQuery deserialize(JsonParser parser, DeserializationContext context) throws JacksonException {
            return INSTANCE.parse(parser);
        }

        @Override
        public MetricQuery getNullValue(DeserializationContext context) {
            throw new MetricValidationException(MetricErrorCode.QUERY_INVALID, "", "Query JSON must not be null");
        }
    }

    private record MetricQueryPayload(String metricCode,
                                      @Nullable String subjectId,
                                      LocalDateTime startTime,
                                      LocalDateTime endTime,
                                      Map<String, Object> dimensionValues,
                                      Map<String, Object> parameterValues) {
    }

    private record MetricBatchQueryPayload(List<String> metricCodes,
                                           @Nullable String subjectId,
                                           LocalDateTime startTime,
                                           LocalDateTime endTime,
                                           Map<String, Object> dimensionValues) {
    }
}
