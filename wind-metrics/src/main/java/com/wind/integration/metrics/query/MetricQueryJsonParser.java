package com.wind.integration.metrics.query;

import com.wind.integration.metrics.MetricValidationException;
import com.wind.integration.metrics.enums.MetricErrorCode;
import com.wind.integration.metrics.json.MetricJsonSupport;
import com.wind.integration.tag.WindTag;
import com.wind.jackson.WindJson;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.deser.std.StdDeserializer;
import tools.jackson.databind.ext.javatime.deser.LocalDateTimeDeserializer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 指标查询条件 JSON 的关闭世界解析入口。
 *
 * <p>只接收公共条件字段，指标身份由服务参数或宿主协议请求承接。
 * 维度的事实类型绑定由宿主依据已选定义完成。</p>
 *
 * @author wuxp
 * @since 2026-09-15
 */
public final class MetricQueryJsonParser {

    private static final Set<String> CRITERIA_FIELDS = Set.of(
            "subjectId", "startTime", "endTime", "dimensionValues", "parameterValues", "subjectType", "searchTags");

    private static final JsonMapper PAYLOAD_MAPPER = createPayloadMapper();

    private static final MetricQueryJsonParser INSTANCE = new MetricQueryJsonParser();

    /**
     * 解析查询条件，拒绝指标编码、修订、路线及其他未声明字段。
     *
     * @param json 查询条件 JSON
     * @return 校验并冻结的公共查询条件
     * @throws MetricValidationException JSON 或条件不符合公开合同时抛出
     */
    public MetricQuery parse(String json) {
        return parse(MetricJsonSupport.parseRootObject(json));
    }

    private MetricQuery parse(Map<String, Object> source) {
        rejectUnknownFields(source, CRITERIA_FIELDS);
        if (source.get("subjectType") != null && !(source.get("subjectType") instanceof String)) {
            throw new MetricValidationException(MetricErrorCode.QUERY_INVALID, "/subjectType", "Expected subject type string");
        }
        if (!source.containsKey("dimensionValues")) {
            source.put("dimensionValues", Map.of());
        }
        if (!source.containsKey("parameterValues")) {
            source.put("parameterValues", Map.of());
        }
        Collection<WindTag> tags = source.containsKey("searchTags") ? parseTags(source.remove("searchTags")) : List.of();
        QueryPayload payload = deserializePayload(MetricJsonSupport.toJson(source), QueryPayload.class);
        return new MetricQuery(payload.subjectId(), payload.startTime(), payload.endTime(),
                payload.dimensionValues(), payload.parameterValues(), payload.subjectType(), tags);
    }

    private static void rejectUnknownFields(Map<String, Object> source, Set<String> allowedFields) {
        for (String field : source.keySet()) {
            if (!allowedFields.contains(field)) {
                throw new MetricValidationException(MetricErrorCode.QUERY_INVALID,
                        MetricJsonSupport.child("", field), "Unknown query field");
            }
        }
    }

    /**
     * 严格读取公共条件后执行 DSL 形状校验，供宿主的正式查询协议适配使用。
     *
     * @param json 公共条件 JSON，不包含指标身份
     * @return 可进入定义级校验的条件
     * @throws MetricValidationException 输入不符合 DSL 查询合同
     */
    public MetricQuery parseDsl(String json) {
        Map<String, Object> source = MetricJsonSupport.parseRootObject(json);
        rejectUnknownFields(source, CRITERIA_FIELDS);
        validateDslParameters(source);
        MetricQuery criteria = parse(source);
        MetricQueryValidator.validateDsl(criteria);
        return criteria;
    }

    private static void validateDslParameters(Map<String, Object> source) {
        if (!source.containsKey("parameterValues")) {
            return;
        }
        if (!(source.get("parameterValues") instanceof Map<?, ?> parameters)) {
            throw invalidParameter("/parameterValues");
        }
        parameters.forEach((key, value) -> {
            String path = key instanceof String name && !name.isBlank()
                    ? MetricJsonSupport.child("/parameterValues", name) : "/parameterValues";
            if (path.equals("/parameterValues") || !(value instanceof BigInteger integer)
                    || integer.compareTo(BigInteger.valueOf(Integer.MIN_VALUE)) < 0
                    || integer.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) > 0) {
                throw invalidParameter(path);
            }
        });
    }

    private static MetricValidationException invalidParameter(String path) {
        return new MetricValidationException(MetricErrorCode.METRIC_PARAMETER_TYPE_MISMATCH,
                path, "Query parameter must use a non-blank name and integer value");
    }

    private static @Nullable Collection<WindTag> parseTags(@Nullable Object source) {
        if (source == null) {
            return null;
        }
        if (!(source instanceof List<?> tags)) {
            throw new MetricValidationException(MetricErrorCode.QUERY_INVALID, "/searchTags", "Expected tag list");
        }
        List<WindTag> result = new ArrayList<>();
        for (int index = 0; index < tags.size(); index++) {
            String path = "/searchTags/" + index;
            if (!(tags.get(index) instanceof Map<?, ?> tag)
                    || !tag.keySet().equals(Set.of("name", "value"))
                    || !(tag.get("name") instanceof String name)
                    || !(tag.get("value") instanceof String value)) {
                throw new MetricValidationException(MetricErrorCode.QUERY_INVALID, path, "Expected tag name and value");
            }
            result.add(WindTag.of(name, value));
        }
        return result;
    }

    private static <T> T deserializePayload(String json, Class<T> type) {
        try {
            return PAYLOAD_MAPPER.readValue(json, type);
        } catch (MetricValidationException exception) {
            throw exception;
        } catch (JacksonException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof MetricValidationException validationException) {
                throw validationException;
            }
            throw new MetricValidationException(MetricErrorCode.QUERY_INVALID, "", "Invalid query JSON", exception);
        }
    }

    private static JsonMapper createPayloadMapper() {
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
        return WindJson.getJsonMapper().rebuild()
                .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .addModule(module)
                .build();
    }

    /** 查询条件的 Jackson 反序列化器。 */
    public static final class QueryDeserializer extends StdDeserializer<MetricQuery> {

        /** 创建反序列化器。 */
        public QueryDeserializer() {
            super(MetricQuery.class);
        }

        @Override
        public MetricQuery deserialize(JsonParser parser, DeserializationContext context) throws JacksonException {
            return INSTANCE.parse(MetricJsonSupport.parseRootObject(parser));
        }

        @Override
        public MetricQuery getNullValue(DeserializationContext context) {
            throw new MetricValidationException(MetricErrorCode.QUERY_INVALID, "", "Query JSON must not be null");
        }
    }

    private record QueryPayload(@Nullable Object subjectId,
                                   LocalDateTime startTime,
                                   LocalDateTime endTime,
                                   Map<String, Object> dimensionValues,
                                   Map<String, Object> parameterValues,
                                   @Nullable String subjectType) {
    }
}
