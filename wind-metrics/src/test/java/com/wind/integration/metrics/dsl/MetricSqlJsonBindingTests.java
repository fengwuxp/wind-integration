package com.wind.integration.metrics.dsl;

import com.wind.integration.metrics.json.MetricJsonSupport;
import com.wind.integration.metrics.spec.MetricDefinitionSpec.MetricSqlDefinitionSpec;
import com.wind.integration.metrics.enums.MetricDefinitionType;
import com.wind.integration.metrics.enums.MetricValueShape;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SQL 模板指标定义 JSON 绑定测试（通过 Jackson 注解路径验证 MetricSqlJsonBinding）。
 *
 * @author wuxp
 * @date 2026-09-18
 */
class MetricSqlJsonBindingTests {

    @Test
    void testDeserializeMinimalDefinition() {
        String json = """
                {
                  "schemaVersion": 1,
                  "metric": {
                    "code": "user_daily_count",
                    "valueShape": "SCALAR",
                    "subjectType": "USER",
                    "dimensions": ["region", "platform"],
                    "sqlTemplate": "SELECT user_id, COUNT(*) as cnt FROM events GROUP BY user_id"
                  }
                }
                """;

        MetricSqlDefinitionSpec definition =
                MetricJsonSupport.parseObject(json, MetricSqlDefinitionSpec.class);

        assertEquals(1, definition.schemaVersion());
        assertEquals("user_daily_count", definition.definition().code());
        assertEquals(MetricValueShape.SCALAR, definition.definition().valueShape());
        assertEquals("USER", definition.definition().subjectType());
        // dimensions are sorted on parse
        assertEquals(java.util.List.of("platform", "region"), definition.definition().dimensions());
        assertTrue(definition.definition().parameters().isEmpty());
    }

    @Test
    void testSerializeProducesCanonicalJson() {
        String json = """
                {
                  "schemaVersion": 1,
                  "metric": {
                    "code": "order_amount",
                    "valueShape": "SCALAR",
                    "subjectType": "ORDER",
                    "dimensions": [],
                    "sqlTemplate": "SELECT order_id, SUM(amount) FROM orders GROUP BY order_id"
                  }
                }
                """;

        MetricSqlDefinitionSpec definition =
                MetricJsonSupport.parseObject(json, MetricSqlDefinitionSpec.class);
        String serialized = MetricJsonSupport.toJson(definition);

        assertTrue(serialized.contains("\"schemaVersion\":1"));
        assertTrue(serialized.contains("\"code\":\"order_amount\""));
        assertTrue(serialized.contains("\"valueShape\":\"SCALAR\""));
        assertTrue(serialized.contains("\"subjectType\":\"ORDER\""));
        assertTrue(serialized.contains("\"sqlTemplate\""));
    }

    /** definitionType 由实现类型派生，不得进入规范 JSON，否则已发布定义的内容指纹会失效。 */
    @Test
    void testDefinitionTypeIsDerivedAndAbsentFromCanonicalJson() {
        String json = """
                {
                  "schemaVersion": 1,
                  "metric": {
                    "code": "order_amount",
                    "valueShape": "SCALAR",
                    "subjectType": "ORDER",
                    "dimensions": [],
                    "sqlTemplate": "SELECT order_id, SUM(amount) FROM orders GROUP BY order_id"
                  }
                }
                """;

        MetricSqlDefinitionSpec definition =
                MetricJsonSupport.parseObject(json, MetricSqlDefinitionSpec.class);

        assertEquals(MetricDefinitionType.SQL, definition.definitionType());
        assertFalse(MetricJsonSupport.toJson(definition).contains("definitionType"));
    }

    @Test
    void testDeserializeWithParameters() {
        String json = """
                {
                  "schemaVersion": 1,
                  "metric": {
                    "code": "recent_users",
                    "valueShape": "SCALAR",
                    "subjectType": "USER",
                    "dimensions": ["status"],
                    "parameters": {
                      "days": {
                        "valueType": "INTEGER",
                        "minimum": 1,
                        "maximum": 90
                      }
                    },
                    "sqlTemplate": "SELECT user_id FROM users WHERE created_at > NOW() - INTERVAL :days DAY"
                  }
                }
                """;

        MetricSqlDefinitionSpec definition =
                MetricJsonSupport.parseObject(json, MetricSqlDefinitionSpec.class);

        assertEquals(1, definition.definition().parameters().size());
        assertNotNull(definition.definition().parameters().get("days"));
        assertEquals(1, definition.definition().parameters().get("days").minimum());
        assertEquals(90, definition.definition().parameters().get("days").maximum());
    }

    @Test
    void testRoundTripPreservesStructure() {
        String original = """
                {
                  "schemaVersion": 1,
                  "metric": {
                    "code": "test_metric",
                    "valueShape": "FIELD_SET",
                    "subjectType": "GLOBAL",
                    "dimensions": ["category", "status"],
                    "sqlTemplate": "SELECT * FROM metrics"
                  }
                }
                """;

        MetricSqlDefinitionSpec parsed =
                MetricJsonSupport.parseObject(original, MetricSqlDefinitionSpec.class);
        String serialized = MetricJsonSupport.toJson(parsed);
        MetricSqlDefinitionSpec reparsed =
                MetricJsonSupport.parseObject(serialized, MetricSqlDefinitionSpec.class);

        assertEquals(parsed.schemaVersion(), reparsed.schemaVersion());
        assertEquals(parsed.definition().code(), reparsed.definition().code());
        assertEquals(parsed.definition().valueShape(), reparsed.definition().valueShape());
        assertEquals(parsed.definition().dimensions(), reparsed.definition().dimensions());
    }
}
