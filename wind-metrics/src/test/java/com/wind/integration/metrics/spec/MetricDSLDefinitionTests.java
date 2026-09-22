package com.wind.integration.metrics.spec;

import com.wind.integration.metrics.MetricValidationException;
import com.wind.integration.metrics.enums.MetricErrorCode;
import com.wind.jackson.WindJson;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证指标定义只保存计算口径和读取模式，分段配置由物化计划独占。
 *
 * @author wuxp
 * @since 2026-09-22
 */
class MetricDSLDefinitionTests {

    /**
     * 场景：计算定义不复制宿主读取模式或计划分段。
     * 输入：RAW schema1 至4，不含读取策略。
     * 流程：通过真实 DefinitionSpec JSON 入口读取、保存并再次读取。
     * 预期：计算口径保持，不添加 executionMode 或 segments。
     */
    @Test
    void testDefinitionRoundTripWithoutReadPolicy() {
        for (int schema = 1; schema <= 4; schema++) {
            MetricDefinitionSpec<?> restored = parse(schema, raw());
            assertFalse(properties(restored.definition()).containsKey("executionMode"));
            assertFalse(properties(restored.definition()).containsKey("segments"));
            assertEquals(restored, WindJson.parseObject(WindJson.toJsonString(restored), MetricDefinitionSpec.class));
        }
    }

    /**
     * 场景：读取模式或分段误放到定义中，不能在保存时静默丢失。
     * 输入：schema1 至4定义分别携带 executionMode、空或非空 segments。
     * 流程：分别读取 DefinitionSpec。
     * 预期：全部明确拒绝，模式归宿主元信息，分段归 Plan。
     */
    @Test
    void testDefinitionRejectsReadPolicy() {
        for (int schema = 1; schema <= 4; schema++) {
            Map<String, Object> mode = raw();
            mode.put("executionMode", "SEGMENTED");
            assertEquals("/metric/executionMode", validationFailure(schema, mode).fieldPath());
            for (List<?> segments : List.of(List.of(), List.of(Map.of("sourceType", "SNAPSHOT")))) {
                Map<String, Object> body = raw();
                body.put("segments", segments);
                assertEquals("/metric/segments", validationFailure(schema, body).fieldPath());
            }
        }
    }

    /**
     * 场景：前 N 笔的计算定义可以保存，物化资格由宿主发布时校验。
     * 输入：schema4 COUNT 与 limit=10 的稳定行选择。
     * 流程：经公共 JSON 入口往返。
     * 预期：行选择完整保留，不把读取模式混入计算定义。
     */
    @Test
    void testRowSelectionRemainsAComputationRule() {
        Map<String, Object> body = raw();
        body.put("rowSelection", Map.of("orderBy", List.of(Map.of("field", "id", "direction", "DESC")),
                "limit", Map.of("value", 10)));
        MetricDefinitionSpec<?> restored = parse(4, body);
        assertEquals(restored, WindJson.parseObject(WindJson.toJsonString(restored), MetricDefinitionSpec.class));
        assertFalse(properties(restored.definition()).containsKey("executionMode"));
    }

    /**
     * 场景：撤回未发布的读取模式候选后，schema5 不能被误解释。
     * 输入：schema5 的计算定义。
     * 流程：读取 DefinitionSpec。
     * 预期：明确报版本不支持，不猜测迁移字段。
     */
    @Test
    void testWithdrawnSchemaFiveIsRejected() {
        assertEquals(MetricErrorCode.DSL_SCHEMA_VERSION_UNSUPPORTED, validationFailure(5, raw()).errorCode());
    }

    /**
     * 场景：派生指标只声明计算表达式及精确依赖。
     * 输入：schema4 绑定 BASE@1 的派生表达式，另尝试指定 REALTIME。
     * 流程：读取合法依赖定义及重复读取模式配置。
     * 预期：精确依赖保留，独立读取模式被拒绝。
     */
    @Test
    void testDerivedDefinitionRetainsExactDependencies() {
        Map<String, Object> body = raw();
        body.remove("fact");
        body.remove("time");
        body.put("value", Map.of("valueType", "LONG", "expression", Map.of("type", "SPEL", "value", "metric('BASE', 'value')"),
                "orElse", Map.of("mode", "NULL")));
        body.put("dependencies", List.of(Map.of("metricCode", "BASE", "definitionRevision", 1)));
        MetricDSLDefinition definition = (MetricDSLDefinition) parse(4, body).definition();
        assertTrue(definition.derivationType().isDerived());
        assertEquals(1, definition.dependencies().size());
        body.put("executionMode", "REALTIME");
        assertEquals("/metric/executionMode", validationFailure(4, body).fieldPath());
    }

    private static Map<String, Object> raw() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", "ORDER_COUNT");
        body.put("revision", 1);
        body.put("valueShape", "SCALAR");
        body.put("fact", "ORDER");
        body.put("joins", List.of());
        body.put("subject", Map.of("type", "GLOBAL"));
        body.put("time", Map.of("field", "createdAt"));
        body.put("dimensions", List.of());
        body.put("parameters", Map.of());
        body.put("value", Map.of("valueType", "LONG", "measure", Map.of("aggregation", "COUNT"), "orElse", Map.of("mode", "ZERO")));
        body.put("fields", Map.of());
        body.put("dependencies", List.of());
        return body;
    }

    private static MetricDefinitionSpec<?> parse(int schema, Map<String, Object> body) {
        return WindJson.parseObject(WindJson.toJsonString(Map.of("definitionType", "DSL", "schemaVersion", schema, "definition", body)),
                MetricDefinitionSpec.class);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> properties(Object value) {
        return WindJson.parseObject(WindJson.toJsonString(value), Map.class);
    }

    private static MetricValidationException validationFailure(int schema, Map<String, Object> body) {
        Throwable failure = assertThrows(JacksonException.class, () -> parse(schema, body));
        while (failure != null && !(failure instanceof MetricValidationException)) {
            failure = failure.getCause();
        }
        assertTrue(failure instanceof MetricValidationException);
        return (MetricValidationException) failure;
    }
}
