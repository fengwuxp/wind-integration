package com.wind.integration.metrics.dsl;

import com.wind.integration.metrics.MetricValidationException;
import com.wind.integration.metrics.dsl.definition.MetricQueryParameterDsl;
import com.wind.integration.metrics.json.MetricSqlCodec;
import com.wind.integration.metrics.spec.MetricDefinitionSpec.MetricSqlDefinitionSpec;
import com.wind.integration.metrics.spec.MetricSqlDefinition;
import com.wind.integration.metrics.enums.MetricValueShape;
import com.wind.integration.metrics.enums.MetricValueType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MetricSqlCodecTests {

    private final MetricSqlCodec codec = new MetricSqlCodec();

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
        MetricSqlDefinitionSpec definition = codec.parse(json);
        assertEquals(1, definition.schemaVersion());
        assertEquals("order_count", definition.definition().code());
        assertEquals(MetricValueShape.SCALAR, definition.definition().valueShape());
        assertEquals("Order", definition.definition().subjectType());
        assertTrue(definition.definition().dimensions().isEmpty());
        assertTrue(definition.definition().parameters().isEmpty());
        assertEquals("SELECT COUNT(*) FROM orders", definition.definition().sqlTemplate());
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
        MetricSqlDefinitionSpec definition = codec.parse(json);
        MetricSqlDefinition metric = definition.definition();
        assertEquals("sales_amount", metric.code());
        assertEquals(List.of("product", "region"), metric.dimensions());
        assertEquals(2, metric.parameters().size());
        
        MetricQueryParameterDsl limitParam = metric.parameters().get("limit");
        assertNotNull(limitParam);
        assertEquals(MetricValueType.INTEGER, limitParam.valueType());
        assertEquals(1, limitParam.minimum());
        assertEquals(1000, limitParam.maximum());
        
        MetricQueryParameterDsl offsetParam = metric.parameters().get("offset");
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
        MetricSqlDefinitionSpec definition = codec.parse(json);
        MetricQueryParameterDsl param = definition.definition().parameters().get("unbounded");
        assertNotNull(param);
        assertNull(param.minimum());
        assertNull(param.maximum());
    }

    @Test
    void testValidateInvalidParameterRange() {
        MetricSqlDefinitionSpec definition = new MetricSqlDefinitionSpec(
                1,
                new MetricSqlDefinition(
                        "test",
                        MetricValueShape.SCALAR,
                        "Test",
                        List.of(),
                        Map.of("invalid", new MetricQueryParameterDsl(
                                MetricValueType.INTEGER, 100, 50)),
                        "SELECT 1"));
        assertThrows(MetricValidationException.class, () -> codec.validateBasic(definition));
    }

    @Test
    void testValidateBlankSqlTemplate() {
        MetricSqlDefinitionSpec definition = new MetricSqlDefinitionSpec(
                1,
                new MetricSqlDefinition(
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
        MetricSqlDefinitionSpec definition = new MetricSqlDefinitionSpec(
                1,
                new MetricSqlDefinition(
                        "test_metric",
                        MetricValueShape.SCALAR,
                        "Test",
                        List.of("dim1", "dim2"),
                        Map.of(
                                "limit", new MetricQueryParameterDsl(
                                        MetricValueType.INTEGER, 1, 100),
                                "offset", new MetricQueryParameterDsl(
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
