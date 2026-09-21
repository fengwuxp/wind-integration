package com.wind.integration.metrics.query;

import com.wind.integration.metrics.MetricValidationException;
import com.wind.integration.metrics.enums.MetricErrorCode;
import org.junit.jupiter.api.Test;
import com.wind.jackson.WindJson;
import tools.jackson.core.JacksonException;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 指标查询条件的输入及隔离合同。 */
class MetricQueryTests {

    private static final LocalDateTime START = LocalDateTime.of(2026, 9, 1, 0, 0);

    private static final LocalDateTime END = START.plusDays(1);

    /**
     * 场景：新查询 JSON 不再承载标签，旧请求不能因字段被删除而扩大查询范围。
     * 输入：合法无标签查询，以及含 searchTags 或错误拼写属性的 JSON。
     * 流程：序列化查询，再通过 WindJson 读取各非法输入。
     * 预期：输出恰好为六个查询字段；所有未知属性明确拒绝。
     */
    @Test
    void testRemovedTagsAreNotSerializedOrSilentlyAccepted() {
        String json = WindJson.toJsonString(new MetricQuery("customer", START, END, Map.of(), Map.of()));
        Map<?, ?> fields = WindJson.getJsonMapper().readValue(json, Map.class);
        assertEquals(java.util.Set.of("subjectId", "startTime", "endTime", "dimensionValues", "parameterValues", "subjectType"),
                fields.keySet());
        for (String extra : List.of("\"searchTags\":[]", "\"searchTags\":null",
                "\"searchTags\":[{\"name\":\"region\",\"value\":\"CN\"}]", "\"subjectTypo\":\"customer\"")) {
            String invalid = json.substring(0, json.length() - 1) + "," + extra + "}";
            assertThrows(JacksonException.class, () -> WindJson.parseObject(invalid, MetricQuery.class));
        }
    }

    /**
     * 场景：全局查询条件不强制携带主体或指标身份。
     * 输入：subjectId=null，2026-09-01至09-02，空维度和参数。
     * 流程：构造 MetricQuery。
     * 预期：主体仍为空，起止时间原样保留。
     */
    @Test
    void testGlobalCriteriaDoesNotRequireMetricIdentity() {
        MetricQuery criteria = new MetricQuery(null, START, END, Map.of(), Map.of());

        assertNull(criteria.subjectId());
        assertEquals(START, criteria.startTime());
        assertEquals(END, criteria.endTime());
    }

    /**
     * 场景：正式 DSL 查询必须有合法主体表示与正向时间窗口。
     * 输入：空白主体、缺起止时间、终点等于或早于起点。
     * 流程：通过 MetricQueryValidator.validateDsl 校验。
     * 预期：分别定位 subjectId/startTime/endTime；非法窗口报 QUERY_INVALID。
     */
    @Test
    void testDslEntryRejectsInvalidSubjectAndWindow() {
        assertEquals("/subjectId", assertThrows(MetricValidationException.class,
                () -> validate(" ", START, END, Map.of(), Map.of())).fieldPath());
        assertEquals("/startTime", assertThrows(MetricValidationException.class,
                () -> validate(null, null, END, Map.of(), Map.of())).fieldPath());
        assertEquals("/endTime", assertThrows(MetricValidationException.class,
                () -> validate(null, START, null, Map.of(), Map.of())).fieldPath());
        for (LocalDateTime end : List.of(START, START.minusSeconds(1))) {
            MetricValidationException error = assertThrows(MetricValidationException.class,
                    () -> validate(null, START, end, Map.of(), Map.of()));
            assertEquals(MetricErrorCode.QUERY_INVALID, error.errorCode());
            assertEquals("/endTime", error.fieldPath());
        }
    }

    /**
     * 场景：查询条件对输入 Map 和可变日期做防御性隔离。
     * 输入：Date、纳秒级 Timestamp 与 entryLimit=3。
     * 流程：构造后修改原容器/日期和 getter 返回的日期，再尝试改查询 Map。
     * 预期：原时间与纳秒精度、参数保持不变，查询 Map 不可修改。
     */
    @Test
    void testConditionsStayStableWhenCallerMutatesMapsAndDates() {
        Date date = new Date(1_720_000_000_000L);
        Timestamp timestamp = Timestamp.valueOf("2026-09-01 12:30:00.123456789");
        Timestamp expected = (Timestamp) timestamp.clone();
        Map<String, Object> dimensions = new HashMap<>(Map.of("date", date, "timestamp", timestamp));
        Map<String, Object> parameters = new HashMap<>(Map.of("entryLimit", 3));
        MetricQuery criteria = new MetricQuery("user-1", START, END, dimensions, parameters);

        dimensions.clear();
        parameters.clear();
        date.setTime(0L);
        timestamp.setTime(0L);
        ((Date) criteria.dimensionValues().get("date")).setTime(1L);
        ((Timestamp) criteria.dimensionValues().get("timestamp")).setNanos(0);

        assertEquals(1_720_000_000_000L, ((Date) criteria.dimensionValues().get("date")).getTime());
        assertEquals(expected, criteria.dimensionValues().get("timestamp"));
        assertEquals(Map.of("entryLimit", 3), criteria.parameterValues());
        assertThrows(UnsupportedOperationException.class, () -> criteria.dimensionValues().clear());
        assertThrows(UnsupportedOperationException.class, () -> criteria.parameterValues().clear());
    }

    /**
     * 场景：DSL 条件仅接受约定的维度值与整数参数。
     * 输入：null 容器，集合/Map/Double 维度，字符串/Long/Double/列表参数。
     * 流程：逐项进入 DSL 校验。
     * 预期：错误定位具体容器或字段；参数类型不符报 METRIC_PARAMETER_TYPE_MISMATCH。
     */
    @Test
    void testDslEntryRejectsUnsupportedDimensionsAndNonIntegerParameters() {
        assertEquals("/dimensionValues", assertThrows(MetricValidationException.class,
                () -> validate(null, START, END, null, Map.of())).fieldPath());
        for (Object dimension : List.of(List.of("USD"), Map.of("code", "USD"), 1.5D)) {
            assertEquals("/dimensionValues/currency", assertThrows(MetricValidationException.class,
                    () -> validate(null, START, END, Map.of("currency", dimension), Map.of()))
                    .fieldPath());
        }
        for (Object parameter : List.of("2", 2L, 2.0D, List.of(2))) {
            MetricValidationException error = assertThrows(MetricValidationException.class,
                    () -> validate(null, START, END, Map.of(), Map.of("entryLimit", parameter)));
            assertEquals(MetricErrorCode.METRIC_PARAMETER_TYPE_MISMATCH, error.errorCode());
            assertEquals("/parameterValues/entryLimit", error.fieldPath());
        }
        assertEquals("/parameterValues", assertThrows(MetricValidationException.class,
                () -> validate(null, START, END, Map.of(), null)).fieldPath());
    }


    /**
     * 场景：通用查询可承载比正式 DSL 更宽的旧调用条件。
     * 输入：主体11/12、无界时间、runtime 对象、USER 类型。
     * 流程：构造并读取公共条件，再进入 DSL 校验。
     * 预期：公共条件原样保留对象身份等信息；DSL 在 subjectId 处拒绝多主体。
     */
    @Test
    void testGeneralCriteriaKeepsMultipleSubjectsUnboundedTimeAndRuntimeVariables() {
        Object context = new Object();
        MetricQuery criteria = new MetricQuery(List.of(11L, 12L), "USER", null, null,
                Map.of(), Map.of("currency", "USD", "context", context));

        assertEquals(List.of(11L, 12L), criteria.subjectId());
        assertNull(criteria.startTime());
        assertNull(criteria.endTime());
        assertSame(context, criteria.parameterValues().get("context"));
        assertEquals("USER", criteria.subjectType());
        assertEquals("/subjectId", assertThrows(MetricValidationException.class,
                () -> MetricQueryValidator.validateDsl(criteria)).fieldPath());
    }

    /**
     * 场景：移除标签后 Builder 与标准构造器提供相同查询条件。
     * 输入：USER/customer、固定窗口、CN维度和整数参数。
     * 流程：使用 Builder 构造并 JSON 往返，与六参数构造值比较。
     * 预期：主体类型、窗口、维度和参数保留，没有额外过滤字段。
     */
    @Test
    void testBuilderAndSixArgumentConstructorRoundTrip() {
        MetricQuery query = MetricQuery.builder().subjectId("customer").subjectType("USER")
                .timeRange(START, END).dimension("region", "CN").parameter("limit", 3).build();
        MetricQuery expected = new MetricQuery("customer", "USER", START, END, Map.of("region", "CN"), Map.of("limit", 3));

        assertEquals(expected, query);
        assertEquals(expected, WindJson.parseObject(WindJson.toJsonString(query), MetricQuery.class));
    }

    private static void validate(Object subjectId, LocalDateTime start, LocalDateTime end,
                                 Map<String, Object> dimensions, Map<String, Object> parameters) {
        MetricQueryValidator.validateDsl(new MetricQuery(subjectId, start, end, dimensions, parameters));
    }
}
