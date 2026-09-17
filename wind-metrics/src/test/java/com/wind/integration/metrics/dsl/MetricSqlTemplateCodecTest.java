package com.wind.integration.metrics.dsl;

import com.wind.integration.metrics.MetricValidationException;
import com.wind.integration.metrics.dsl.definition.MetricQueryParameterDefinitionDsl;
import com.wind.integration.metrics.dsl.definition.MetricSqlTemplateDefinition;
import com.wind.integration.metrics.dsl.definition.MetricSqlTemplateSpec;
import com.wind.integration.metrics.enums.MetricValueShape;
import com.wind.integration.metrics.enums.MetricValueType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MetricSqlTemplateCodecTest {

    private final MetricSqlTemplateCodec codec = new MetricSqlTemplateCodec();

    @Test
    void testParseMinimal() {
        String json = """
                {
                  "schemaVersion": 1,
                  "metric": {
                    "code": "order_count",
                    "valueShape": "SCALAR",
                    "subjectType": "Order",
                    "dimensions": [],
                    "sqlTemplate": "SELECT COUNT(*) FROM orders"
                  }
                }
                """;
        MetricSqlTemplateDefinition definition = codec.parse(json);
        assertEquals(1, definition.schemaVersion());
        assertEquals("order_count", definition.metric().code());
        assertEquals(MetricValueShape.SCALAR, definition.metric().valueShape());
        assertEquals("Order", definition.metric().subjectType());
        assertTrue(definition.metric().dimensions().isEmpty());
        assertTrue(definition.metric().parameters().isEmpty());
        assertEquals("SELECT COUNT(*) FROM orders", definition.metric().sqlTemplate());
    }

    @Test
    void testParseWithParametersAndDimensions() {
        String json = """
                {
                  "schemaVersion": 1,
                  "metric": {
                    "code": "sales_amount",
                    "valueShape": "SCALAR",
                    "subjectType": "Order",
                    "dimensions": ["region", "product"],
                    "parameters": {
                      "limit": {
                        "valueType": "INTEGER",
                        "minimum": 1,
                        "maximum": 1000
                      },
                      "offset": {
                        "valueType": "INTEGER",
                        "minimum": 0
                      }
                    },
                    "sqlTemplate": "SELECT SUM(amount) FROM orders WHERE region = ? AND product = ? LIMIT ? OFFSET ?"
                  }
                }
                """;
        MetricSqlTemplateDefinition definition = codec.parse(json);
        MetricSqlTemplateSpec metric = definition.metric();
        assertEquals("sales_amount", metric.code());
        assertEquals(List.of("product", "region"), metric.dimensions());
        assertEquals(2, metric.parameters().size());
        
        MetricQueryParameterDefinitionDsl limitParam = metric.parameters().get("limit");
        assertNotNull(limitParam);
        assertEquals(MetricValueType.INTEGER, limitParam.valueType());
        assertEquals(1, limitParam.minimum());
        assertEquals(1000, limitParam.maximum());
        
        MetricQueryParameterDefinitionDsl offsetParam = metric.parameters().get("offset");
        assertNotNull(offsetParam);
        assertEquals(MetricValueType.INTEGER, offsetParam.valueType());
        assertEquals(0, offsetParam.minimum());
        assertNull(offsetParam.maximum());
    }

    @Test
    void testParseWithNullableParameters() {
        String json = """
                {
                  "schemaVersion": 1,
                  "metric": {
                    "code": "test_metric",
                    "valueShape": "SCALAR",
                    "subjectType": "Test",
                    "dimensions": [],
                    "parameters": {
                      "unbounded": {
                        "valueType": "INTEGER"
                      }
                    },
                    "sqlTemplate": "SELECT COUNT(*) FROM test"
                  }
                }
                """;
        MetricSqlTemplateDefinition definition = codec.parse(json);
        MetricQueryParameterDefinitionDsl param = definition.metric().parameters().get("unbounded");
        assertNotNull(param);
        assertNull(param.minimum());
        assertNull(param.maximum());
    }

    @Test
    void testValidateInvalidParameterRange() {
        MetricSqlTemplateDefinition definition = new MetricSqlTemplateDefinition(
                1,
                new MetricSqlTemplateSpec(
                        "test",
                        MetricValueShape.SCALAR,
                        "Test",
                        List.of(),
                        Map.of("invalid", new MetricQueryParameterDefinitionDsl(
                                MetricValueType.INTEGER, 100, 50)),
                        "SELECT 1"));
        assertThrows(MetricValidationException.class, () -> codec.validateBasic(definition));
    }

    @Test
    void testValidateBlankSqlTemplate() {
        MetricSqlTemplateDefinition definition = new MetricSqlTemplateDefinition(
                1,
                new MetricSqlTemplateSpec(
                        "test",
                        MetricValueShape.SCALAR,
                        "Test",
                        List.of(),
                        Map.of(),
                        "   "));
        assertThrows(MetricValidationException.class, () -> codec.validateBasic(definition));
    }

    @Test
    void testCanonicalize() {
        MetricSqlTemplateDefinition definition = new MetricSqlTemplateDefinition(
                1,
                new MetricSqlTemplateSpec(
                        "test_metric",
                        MetricValueShape.SCALAR,
                        "Test",
                        List.of("dim1", "dim2"),
                        Map.of(
                                "limit", new MetricQueryParameterDefinitionDsl(
                                        MetricValueType.INTEGER, 1, 100),
                                "offset", new MetricQueryParameterDefinitionDsl(
                                        MetricValueType.INTEGER, null, null)),
                        "SELECT COUNT(*) FROM test"));
        String canonical = codec.canonicalize(definition);
        assertTrue(canonical.contains("\"schemaVersion\":1"));
        assertTrue(canonical.contains("\"code\":\"test_metric\""));
        assertTrue(canonical.contains("\"dim1\""));
        assertTrue(canonical.contains("\"limit\""));
        assertTrue(canonical.contains("\"minimum\":1"));
        assertTrue(canonical.contains("\"maximum\":100"));
    }

    @Test
    void testParseUnsupportedSchemaVersion() {
        String json = """
                {
                  "schemaVersion": 2,
                  "metric": {
                    "code": "test",
                    "valueShape": "SCALAR",
                    "subjectType": "Test",
                    "dimensions": [],
                    "sqlTemplate": "SELECT 1"
                  }
                }
                """;
        assertThrows(MetricValidationException.class, () -> codec.parse(json));
    }

    @Test
    void testParseDuplicateDimensions() {
        String json = """
                {
                  "schemaVersion": 1,
                  "metric": {
                    "code": "test",
                    "valueShape": "SCALAR",
                    "subjectType": "Test",
                    "dimensions": ["region", "region"],
                    "sqlTemplate": "SELECT 1"
                  }
                }
                """;
        assertThrows(MetricValidationException.class, () -> codec.parse(json));
    }
}
