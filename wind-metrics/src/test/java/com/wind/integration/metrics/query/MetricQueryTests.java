package com.wind.integration.metrics.query;

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
     * 场景：查询条件只对容器做一次浅复制，值对象仍由调用方管理。
     * 输入：Date、纳秒级 Timestamp、显式 null、业务对象与 entryLimit=3。
     * 流程：构造后清空原 Map，读取查询容器及值引用，再修改日期和尝试修改查询容器。
     * 预期：容器内容独立且只读，getter 返回同一容器，日期和业务对象保持原引用。
     */
    @Test
    void testConditionsShallowCopyContainersAndKeepValueReferences() {
        Date date = new Date(1_720_000_000_000L);
        Timestamp timestamp = Timestamp.valueOf("2026-09-01 12:30:00.123456789");
        Object context = new Object();
        Map<String, Object> dimensions = new HashMap<>(Map.of("date", date, "timestamp", timestamp));
        dimensions.put("optional", null);
        Map<String, Object> parameters = new HashMap<>(Map.of("entryLimit", 3, "context", context));
        MetricQuery criteria = new MetricQuery("user-1", START, END, dimensions, parameters);

        dimensions.clear();
        parameters.clear();
        assertSame(date, criteria.dimensionValues().get("date"));
        assertSame(timestamp, criteria.dimensionValues().get("timestamp"));
        assertEquals(123456789, timestamp.getNanos());
        assertEquals(java.util.Set.of("date", "timestamp", "optional"), criteria.dimensionValues().keySet());
        assertNull(criteria.dimensionValues().get("optional"));
        assertSame(context, criteria.parameterValues().get("context"));
        assertEquals(3, criteria.parameterValues().get("entryLimit"));
        date.setTime(0L);
        timestamp.setNanos(0);
        assertEquals(0L, ((Date) criteria.dimensionValues().get("date")).getTime());
        assertEquals(0, ((Timestamp) criteria.dimensionValues().get("timestamp")).getNanos());
        assertThrows(UnsupportedOperationException.class, () -> criteria.dimensionValues().clear());
        assertThrows(UnsupportedOperationException.class, () -> criteria.parameterValues().clear());
    }

    /**
     * 场景：通用查询可承载比正式 DSL 更宽的旧调用条件。
     * 输入：主体11/12、无界时间、runtime 对象、USER 类型。
     * 流程：构造并读取公共条件。
     * 预期：公共条件保留主体集合、可空窗口和原对象引用，不施加执行模式限制。
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

    /**
     * 快照实现约定的计划身份保留在原参数容器中，公共模型不解释或扩展这些字段。
     */
    @Test
    void testSnapshotIdentityUsesExistingParameterValues() {
        Map<String, Object> parameters = Map.of("planCode", "USER_METRICS", "planRevision", 3);
        MetricQuery query = new MetricQuery("user-1", START, END, Map.of(), parameters);
        MetricQuery built = MetricQuery.builder().subjectId("user-1").timeRange(START, END).planCode("USER_METRICS").planRevision(3).build();

        assertEquals(query, built);
        String json = WindJson.toJsonString(built);
        MetricQuery restored = WindJson.parseObject(json, MetricQuery.class);
        assertEquals(parameters, restored.parameterValues());
        assertEquals("USER_METRICS", restored.planCode());
        assertEquals(3, restored.planRevision());
        assertEquals(6, WindJson.getJsonMapper().readValue(json, Map.class).size());
    }

    /**
     * 计划便捷入口允许显式 null，覆盖旧值但保留参数键，JSON 往返不增加顶层字段。
     */
    @Test
    void testPlanBuilderNullValuesOverwriteAndRoundTrip() {
        MetricQuery query = MetricQuery.builder().parameters(Map.of("planCode", "OLD", "planRevision", 1, "currency", "USD"))
                .planCode(null).planRevision(null).build();
        MetricQuery restored = WindJson.parseObject(WindJson.toJsonString(query), MetricQuery.class);
        Map<String, Object> expected = new HashMap<>();
        expected.put("planCode", null);
        expected.put("planRevision", null);
        expected.put("currency", "USD");

        assertEquals(expected, restored.parameterValues());
        assertNull(restored.planCode());
        assertNull(restored.planRevision());
        assertEquals(query, restored);
    }

    /**
     * 便捷方法、单项 parameter 和批量 parameters 共用同一个参数空间，严格按后写覆盖。
     */
    @Test
    void testPlanBuilderSharesLastWriteWinsWithParameterMethods() {
        MetricQuery.Builder builder = MetricQuery.builder().planCode("INITIAL").planRevision(1);

        MetricQuery single = builder.parameter("planCode", "SINGLE").parameter("planRevision", 2).build();
        assertEquals("SINGLE", single.planCode());
        assertEquals(2, single.planRevision());

        MetricQuery batch = builder.parameters(Map.of("planCode", "BATCH", "planRevision", 3)).build();
        assertEquals("BATCH", batch.planCode());
        assertEquals(3, batch.planRevision());

        MetricQuery typed = builder.planCode("FINAL").planRevision(4).build();
        assertEquals("FINAL", typed.planCode());
        assertEquals(4, typed.planRevision());
    }

    /**
     * 构建后的参数独立且只读；外部 Map 变化与 Builder 再次赋值不影响已构建查询。
     */
    @Test
    void testPlanBuilderKeepsBuiltParametersIsolated() {
        Map<String, Object> parameters = new HashMap<>(Map.of("currency", "USD"));
        MetricQuery.Builder builder = MetricQuery.builder().parameters(parameters).planCode("FIRST").planRevision(1);
        MetricQuery first = builder.build();

        parameters.put("currency", "EUR");
        MetricQuery second = builder.planCode("SECOND").planRevision(2).parameter("currency", "JPY").build();

        assertEquals(Map.of("planCode", "FIRST", "planRevision", 1, "currency", "USD"), first.parameterValues());
        assertEquals(Map.of("planCode", "SECOND", "planRevision", 2, "currency", "JPY"), second.parameterValues());
        assertThrows(UnsupportedOperationException.class, () -> first.parameterValues().put("planCode", "MUTATED"));
    }

    /**
     * 便捷 getter 在参数容器缺失、键缺失或值显式为空时不补默认计划或版本。
     */
    @Test
    void testSnapshotIdentityGettersReturnNullWithMissingParameters() {
        List<MetricQuery> queries = List.of(
                new MetricQuery("user-1", START, END, Map.of(), null),
                MetricQuery.builder().subjectId("user-1").build(),
                MetricQuery.builder().subjectId("user-1").parameter("planCode", null).parameter("planRevision", null).build());

        for (MetricQuery query : queries) {
            assertNull(query.planCode());
            assertNull(query.planRevision());
        }
    }

    /**
     * 便捷 getter 不将错误类型静默转换为另一个计划身份。
     */
    @Test
    void testSnapshotIdentityGettersKeepDeclaredParameterTypes() {
        MetricQuery query = MetricQuery.builder().parameter("planCode", 1).parameter("planRevision", "3").build();

        assertThrows(ClassCastException.class, query::planCode);
        assertThrows(ClassCastException.class, query::planRevision);
    }

}
