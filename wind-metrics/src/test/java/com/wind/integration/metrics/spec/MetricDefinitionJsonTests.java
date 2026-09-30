package com.wind.integration.metrics.spec;

import com.wind.integration.metrics.MetricValidationException;
import com.wind.integration.metrics.dsl.MetricValueCalculator;
import com.wind.integration.metrics.dsl.filter.ComparisonMetricFilterDsl;
import com.wind.integration.metrics.dsl.literal.DecimalMetricLiteralDsl;
import com.wind.jackson.WindJson;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 验证共同指标定义经过 JSON 保存后仍可供取值与计算使用。
 *
 * @author wuxp
 */
class MetricDefinitionJsonTests {

    /**
     * 场景：SQL 和 DSL 共享相同的值口径，取值声明只保存各自差异。
     * 输入：schema6 SQL 单值指标，显式 LONG/COUNT 与 GLOBAL 主体。
     * 流程：从公共 JSON 入口读取，保存后再次读取。
     * 预期：共同口径与 SQL 声明完整保留，不需要旧类型包装。
     */
    @Test
    void testSqlDefinitionWithSharedValueRoundTrip() {
        String json = """
                {"schemaVersion":6,"definition":{
                  "code":"ORDER_COUNT","revision":2,"valueShape":"SCALAR",
                  "subject":{"type":"GLOBAL"},"dimensions":[],"parameters":{},
                  "valueQuery":{"type":"SQL","sqlTemplate":"SELECT COUNT(*) AS value FROM orders"},
                  "value":{"valueType":"LONG","measure":{"aggregation":"COUNT"},"orElse":{"mode":"ZERO"}},
                  "fields":{},"dependencies":[]}}
                """;
        var definition = WindJson.parseObject(json, MetricDefinitionSpec.class);
        assertEquals(6, definition.schemaVersion());
        assertEquals(definition, WindJson.parseObject(WindJson.toJsonString(definition), MetricDefinitionSpec.class));
    }

    /**
     * 场景：外层属性顺序与旧协议布局不应损失过滤阈值精度。
     * 输入：超过 double 精度的十进制阈值，分别采用旧 schema4 与新 schema6。
     * 流程：definition 放在 schemaVersion 之前读取，保存并重新读取。
     * 预期：literal 保持原 BigDecimal，旧格式保持版本，新格式保持共同取值声明。
     */
    @Test
    void testReorderedLegacyAndUnifiedFilterKeepExactDecimal() {
        String value = """
                {"valueType":"DECIMAL","scale":18,"roundingMode":"HALF_UP",
                 "measure":{"aggregation":"SUM","field":"amount","filter":{
                   "operator":"GT","fieldRef":"amount","value":{"value":12345678901234567890.123456789012345678}}},
                 "orElse":{"mode":"NULL"}}
                """;
        for (int schema : List.of(4, 6)) {
            String query = schema == 4
                    ? "\"fact\":\"ORDER\",\"joins\":[],\"time\":{\"field\":\"createdAt\"}"
                    : "\"valueQuery\":{\"type\":\"DSL\",\"fact\":\"ORDER\",\"joins\":[],\"time\":{\"field\":\"createdAt\"}}";
            String type = schema == 4 ? ",\"definitionType\":\"DSL\"" : "";
            String json = "{\"definition\":{\"code\":\"AMOUNT\",\"revision\":7,\"valueShape\":\"SCALAR\","
                    + "\"subject\":{\"type\":\"GLOBAL\"},\"dimensions\":[],\"parameters\":{},\"fields\":{},"
                    + query + ",\"value\":" + value + "},\"schemaVersion\":" + schema + type + "}";
            MetricDefinitionSpec spec = WindJson.parseObject(json, MetricDefinitionSpec.class);
            ComparisonMetricFilterDsl filter = (ComparisonMetricFilterDsl) spec.definition().value().measure().filter();
            assertEquals(new BigDecimal("12345678901234567890.123456789012345678"),
                    ((DecimalMetricLiteralDsl) filter.value()).value());
            assertEquals(spec, WindJson.parseObject(WindJson.toJsonString(spec), MetricDefinitionSpec.class));
        }
    }

    /**
     * 场景：旧 SQL 没有值声明，不能因迁移自动获得累计能力。
     * 输入：schema1 的单值模板，没有 valueType/measure。
     * 流程：读取并往返，再尝试原始量合并；旧 SQL 版本数字6也沿原布局往返。
     * 预期：旧 SQL 模板原样保留，不猜值类型，版本数字不能赋予其累计能力。
     */
    @Test
    void testLegacySqlDoesNotInventValueOrMergeState() {
        MetricDefinitionSpec spec = WindJson.parseObject("""
                {"schemaVersion":1,"definitionType":"SQL","definition":{
                  "code":"LEGACY","revision":3,"valueShape":"SCALAR","subjectType":"GLOBAL",
                  "dimensions":[],"parameters":{},"sqlTemplate":"SELECT 1"}}
                """, MetricDefinitionSpec.class);
        assertNull(spec.definition().value());
        assertEquals(new MetricSqlDefinition("SELECT 1"), spec.definition().valueQuery());
        assertEquals(spec, WindJson.parseObject(WindJson.toJsonString(spec), MetricDefinitionSpec.class));
        assertThrows(MetricValidationException.class,
                () -> new MetricValueCalculator().merge(spec.definition(), List.of(Map.of("value", 1))));
        MetricDefinitionSpec legacySix = new MetricDefinitionSpec(6, spec.definition());
        Map<?, ?> encoded = WindJson.parseObject(WindJson.toJsonString(legacySix), Map.class);
        assertEquals("SQL", encoded.get("definitionType"));
        assertEquals(legacySix, WindJson.parseObject(WindJson.toJsonString(legacySix), MetricDefinitionSpec.class));
        String incompleteUnified = WindJson.toJsonString(Map.of("schemaVersion", 6, "definition", spec.definition()));
        assertThrows(JacksonException.class, () -> WindJson.parseObject(incompleteUnified, MetricDefinitionSpec.class));
    }

    /**
     * 场景：新旧正文混用会形成取值规则双源，不能静默忽略。
     * 输入：schema6 共同定义夹带旧 fact，或夹带 executionMode。
     * 流程：通过公共 JSON 入口读取。
     * 预期：均失败，不把未知规则丢弃后保存为另一指标。
     */
    @Test
    void testUnifiedDefinitionRejectsLegacyAndReadPolicyProperties() {
        for (String extra : List.of("\"fact\":\"ORDER\"", "\"executionMode\":\"SEGMENTED\"")) {
            String json = """
                    {"schemaVersion":6,"definition":{
                      "code":"COUNT","revision":1,"valueShape":"SCALAR","subject":{"type":"GLOBAL"},
                      "dimensions":[],"parameters":{},"valueQuery":{"type":"SQL","sqlTemplate":"SELECT 1 AS value"},
                      "value":{"valueType":"LONG","orElse":{"mode":"NULL"}},"fields":{},%s}}
                    """.formatted(extra);
            assertThrows(JacksonException.class, () -> WindJson.parseObject(json, MetricDefinitionSpec.class));
        }
    }

    /**
     * 场景：取值声明不能偷带共同口径或读取策略，避免用户保存后配置静默丢失。
     * 输入：SQL 声明携带 fact，DSL 声明携带 executionMode。
     * 流程：分别读取这两种 schema6 定义。
     * 预期：均明确拒绝，而不是忽略重复或错层字段。
     */
    @Test
    void testValueQueryRejectsPropertiesOwnedByOtherModels() {
        for (String query : List.of(
                "{\"type\":\"SQL\",\"sqlTemplate\":\"SELECT 1 AS value\",\"fact\":\"ORDER\"}",
                "{\"type\":\"DSL\",\"fact\":\"ORDER\",\"joins\":[],\"time\":{\"field\":\"createdAt\"},\"executionMode\":\"REALTIME\"}")) {
            String json = """
                    {"schemaVersion":6,"definition":{
                      "code":"COUNT","revision":1,"valueShape":"SCALAR","subject":{"type":"GLOBAL"},
                      "dimensions":[],"parameters":{},"valueQuery":%s,
                      "value":{"valueType":"LONG","measure":{"aggregation":"COUNT"},"orElse":{"mode":"ZERO"}},"fields":{}}}
                    """.formatted(query);
            assertThrows(JacksonException.class, () -> WindJson.parseObject(json, MetricDefinitionSpec.class));
        }
    }
}
