package com.wind.integration.metrics.dsl;

import com.wind.integration.metrics.MetricValidationException;
import com.wind.integration.metrics.dsl.definition.MetricDefinitionDsl;
import com.wind.integration.metrics.dsl.definition.MetricDefinitionSpec;
import com.wind.integration.metrics.dsl.definition.MetricMeasureDsl;
import com.wind.integration.metrics.dsl.definition.MetricOrElseDsl;
import com.wind.integration.metrics.dsl.definition.MetricSubjectDsl;
import com.wind.integration.metrics.dsl.definition.MetricTimeDsl;
import com.wind.integration.metrics.dsl.definition.MetricValueDsl;
import com.wind.integration.metrics.dsl.filter.DecimalMetricLiteralDsl;
import com.wind.integration.metrics.dsl.filter.IntegralMetricLiteralDsl;
import com.wind.integration.metrics.dsl.filter.MetricFilterDsl;
import com.wind.integration.metrics.dsl.filter.SetMetricFilterDsl;
import com.wind.integration.metrics.enums.MetricAggregation;
import com.wind.integration.metrics.enums.MetricErrorCode;
import com.wind.integration.metrics.enums.MetricFilterOperator;
import com.wind.integration.metrics.enums.MetricOrElseMode;
import com.wind.integration.metrics.enums.MetricValueShape;
import com.wind.integration.metrics.enums.MetricValueType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;

/**
 * 指标 Definition DSL 的公共 JSON 合同测试。
 *
 * @author wuxp
 * @date 2026-07-21 17:51
 */
class MetricDefinitionDslCodecTests {

    private final MetricDefinitionDslCodec codec = new MetricDefinitionDslCodec();

    @Test
    void testParseAndCanonicalizeRealtimeCountDefinition() {
        String source = """
                {
                  "metric": {
                    "dimensions": ["region", "currency"],
                    "value": {
                      "measure": {"aggregation": "COUNT"},
                      "valueType": "LONG"
                    },
                    "time": {"field": "authTime"},
                    "subject": {"field": "customerId", "type": "CUSTOMER"},
                    "fact": "VccTransaction",
                    "valueShape": "SCALAR",
                    "code": "VCC_APPROVED_TOTAL"
                  },
                  "schemaVersion": 1
                }
                """;

        MetricDefinitionDsl definition = codec.parse(source);

        Assertions.assertEquals("VCC_APPROVED_TOTAL", definition.metric().code());
        Assertions.assertEquals(MetricValueShape.SCALAR, definition.metric().valueShape());
        Assertions.assertEquals(
                "{\"schemaVersion\":1,\"metric\":{\"code\":\"VCC_APPROVED_TOTAL\",\"valueShape\":\"SCALAR\","
                        + "\"fact\":\"VccTransaction\",\"subject\":{\"type\":\"CUSTOMER\",\"field\":\"customerId\"},"
                        + "\"time\":{\"field\":\"authTime\"},\"dimensions\":[\"currency\",\"region\"],"
                        + "\"value\":{\"valueType\":\"LONG\",\"measure\":{\"aggregation\":\"COUNT\"},"
                        + "\"orElse\":{\"mode\":\"NULL\"}}}}",
                codec.canonicalize(definition));
    }

    @Test
    @DisplayName("DSL-T001 Definition 拒绝未知字段")
    void testRejectUnknownField() {
        String source = """
                {
                  "schemaVersion": 1,
                  "metric": {
                    "code": "VCC_APPROVED_TOTAL",
                    "valueShape": "SCALAR",
                    "fact": "VccTransaction",
                    "subject": {"type": "CUSTOMER", "field": "customerId"},
                    "time": {"field": "authTime"},
                    "dimensions": [],
                    "value": {"valueType": "LONG", "measure": {"aggregation": "COUNT"}},
                    "unexpected": true
                  }
                }
                """;

        MetricValidationException exception = Assertions.assertThrows(
                MetricValidationException.class,
                () -> codec.parse(source));

        Assertions.assertEquals(MetricErrorCode.DSL_FIELD_UNKNOWN, exception.errorCode());
        Assertions.assertEquals("/metric/unexpected", exception.fieldPath());
    }

    @Test
    void testRejectDuplicateField() {
        String source = """
                {
                  "schemaVersion": 1,
                  "schemaVersion": 1,
                  "metric": {}
                }
                """;

        MetricValidationException exception = Assertions.assertThrows(
                MetricValidationException.class,
                () -> codec.parse(source));

        Assertions.assertEquals(MetricErrorCode.DSL_FIELD_DUPLICATED, exception.errorCode());
        Assertions.assertEquals("/schemaVersion", exception.fieldPath());
    }

    @Test
    void testRejectExplicitNullOptionalCollections() {
        for (Map.Entry<String, String> input : Map.of(
                "\"joins\": null,", "/metric/joins",
                "\"metricRefs\": null,", "/metric/metricRefs",
                "\"fields\": null,", "/metric/fields").entrySet()) {
            MetricValidationException exception = Assertions.assertThrows(
                    MetricValidationException.class,
                    () -> codec.parse(realtimeCountDefinition(input.getKey(), "")));

            Assertions.assertEquals(MetricErrorCode.DSL_FIELD_TYPE_INVALID, exception.errorCode());
            Assertions.assertEquals(input.getValue(), exception.fieldPath());
        }
    }

    @Test
    void testCanonicalizationIsIdempotentForEquivalentNumericSetLiterals() {
        String source = """
                {
                  "schemaVersion": 1,
                  "metric": {
                    "code": "VCC_AMOUNT_MATCH_TOTAL",
                    "valueShape": "SCALAR",
                    "fact": "VccTransaction",
                    "subject": {"type": "GLOBAL"},
                    "time": {"field": "authTime"},
                    "dimensions": [],
                    "value": {
                      "valueType": "LONG",
                      "measure": {
                        "aggregation": "COUNT",
                        "filter": {"in": {"amount": [1, 1.0]}}
                      }
                    }
                  }
                }
                """;

        String canonical = codec.canonicalize(codec.parse(source));

        Assertions.assertEquals(canonical, codec.canonicalize(codec.parse(canonical)));
    }

    @Test
    void testCanonicalizationDeduplicatesEquivalentTypedNumericLiterals() {
        MetricFilterDsl filter = new SetMetricFilterDsl(
                MetricFilterOperator.IN,
                "amount",
                List.of(
                        new IntegralMetricLiteralDsl(BigInteger.ONE),
                        new DecimalMetricLiteralDsl(new BigDecimal("1.0"))));
        MetricValueDsl value = new MetricValueDsl(
                MetricValueType.LONG,
                null,
                null,
                new MetricMeasureDsl(MetricAggregation.COUNT, null, filter),
                null,
                new MetricOrElseDsl(MetricOrElseMode.NULL, null));
        MetricDefinitionDsl definition = new MetricDefinitionDsl(
                1,
                new MetricDefinitionSpec(
                        "VCC_AMOUNT_MATCH_TOTAL",
                        MetricValueShape.SCALAR,
                        "VccTransaction",
                        List.of(),
                        new MetricSubjectDsl("GLOBAL", null),
                        new MetricTimeDsl("authTime"),
                        List.of(),
                        Map.of(),
                        value,
                        Map.of()));

        String canonical = codec.canonicalize(definition);

        Assertions.assertTrue(canonical.contains("\"amount\":[1]"));
        Assertions.assertEquals(canonical, codec.canonicalize(codec.parse(canonical)));
    }

    @Test
    @DisplayName("DSL-T004 filter literal 类型必须唯一")
    void testRejectMixedSetLiteralTypes() {
        String source = """
                {
                  "schemaVersion": 1,
                  "metric": {
                    "code": "VCC_STATUS_MATCH_TOTAL",
                    "valueShape": "SCALAR",
                    "fact": "VccTransaction",
                    "subject": {"type": "GLOBAL"},
                    "time": {"field": "authTime"},
                    "dimensions": [],
                    "value": {
                      "valueType": "LONG",
                      "measure": {
                        "aggregation": "COUNT",
                        "filter": {"in": {"status": ["APPROVED", 1]}}
                      }
                    }
                  }
                }
                """;

        MetricValidationException exception = Assertions.assertThrows(
                MetricValidationException.class,
                () -> codec.parse(source));

        Assertions.assertEquals(MetricErrorCode.DSL_FIELD_TYPE_INVALID, exception.errorCode());
        Assertions.assertEquals("/metric/value/measure/filter/in/status/1", exception.fieldPath());
    }

    @Test
    void testRejectInvalidSetLiteralAtExactArrayPath() {
        MetricValidationException exception = Assertions.assertThrows(
                MetricValidationException.class,
                () -> codec.parse(realtimeCountDefinition(
                        "",
                        ", \"filter\": {\"in\": {\"status\": [\"APPROVED\", null]}}")));

        Assertions.assertEquals(MetricErrorCode.DSL_FIELD_TYPE_INVALID, exception.errorCode());
        Assertions.assertEquals("/metric/value/measure/filter/in/status/1", exception.fieldPath());
    }

    @Test
    void testRejectInvalidLogicalOperandAtExactArrayPath() {
        MetricValidationException exception = Assertions.assertThrows(
                MetricValidationException.class,
                () -> codec.parse(realtimeCountDefinition(
                        "",
                        ", \"filter\": {\"and\": [{\"isNull\": \"status\"}, null]}")));

        Assertions.assertEquals(MetricErrorCode.DSL_FIELD_TYPE_INVALID, exception.errorCode());
        Assertions.assertEquals("/metric/value/measure/filter/and/1", exception.fieldPath());
    }

    @Test
    void testAllowJsonSyntaxCharactersInsideStringLiteral() {
        Assertions.assertDoesNotThrow(() -> codec.parse(realtimeCountDefinition(
                "",
                ", \"filter\": {\"eq\": {\"resource\": \"https://example.test/a,}\"}}")));
    }

    @Test
    void testCanonicalizeExactLongOrElseValue() {
        String canonical = codec.canonicalize(codec.parse(derivedDefinitionWithOrElse("LONG", "1.0")));

        Assertions.assertTrue(canonical.contains("\"orElse\":{\"mode\":\"VALUE\",\"value\":1}"));
    }

    @Test
    void testRejectNonIntegralLongOrElseValue() {
        MetricValidationException exception = Assertions.assertThrows(
                MetricValidationException.class,
                () -> codec.parse(derivedDefinitionWithOrElse("LONG", "1.5")));

        Assertions.assertEquals(MetricErrorCode.DSL_FIELD_TYPE_INVALID, exception.errorCode());
        Assertions.assertEquals("/metric/value/orElse/value", exception.fieldPath());
    }

    @Test
    void testRejectOverflowingIntegerOrElseValue() {
        MetricValidationException exception = Assertions.assertThrows(
                MetricValidationException.class,
                () -> codec.parse(derivedDefinitionWithOrElse("INTEGER", "2147483648")));

        Assertions.assertEquals(MetricErrorCode.DSL_FIELD_TYPE_INVALID, exception.errorCode());
        Assertions.assertEquals("/metric/value/orElse/value", exception.fieldPath());
    }

    @Test
    void testDirectConstructedInvalidDecimalReturnsStructuredError() {
        MetricValueDsl invalidValue = new MetricValueDsl(
                MetricValueType.DECIMAL,
                null,
                null,
                new MetricMeasureDsl(MetricAggregation.COUNT, null, null),
                null,
                new MetricOrElseDsl(MetricOrElseMode.NULL, null));
        MetricDefinitionDsl definition = new MetricDefinitionDsl(
                1,
                new MetricDefinitionSpec(
                        "VCC_APPROVED_TOTAL",
                        MetricValueShape.SCALAR,
                        "VccTransaction",
                        List.of(),
                        new MetricSubjectDsl("CUSTOMER", "customerId"),
                        new MetricTimeDsl("authTime"),
                        List.of(),
                        Map.of(),
                        invalidValue,
                        Map.of()));

        MetricValidationException exception = Assertions.assertThrows(
                MetricValidationException.class,
                () -> codec.canonicalize(definition));

        Assertions.assertEquals(MetricErrorCode.DSL_FIELD_REQUIRED, exception.errorCode());
        Assertions.assertEquals("/metric/value/scale", exception.fieldPath());
    }

    @Test
    void testRejectDuplicateJoinAlias() {
        String source = """
                {
                  "schemaVersion": 1,
                  "metric": {
                    "code": "VCC_APPROVED_TOTAL_BY_REGION",
                    "valueShape": "SCALAR",
                    "fact": "VccTransaction",
                    "joins": [
                      {
                        "alias": "merchant",
                        "fact": "Merchant",
                        "joinType": "LEFT",
                        "cardinality": "MANY_TO_ONE",
                        "on": [{"primaryField": "merchantId", "joinField": "id"}]
                      },
                      {
                        "alias": "merchant",
                        "fact": "MerchantProfile",
                        "joinType": "LEFT",
                        "cardinality": "MANY_TO_ONE",
                        "on": [{"primaryField": "merchantId", "joinField": "merchantId"}]
                      }
                    ],
                    "subject": {"type": "CUSTOMER", "field": "customerId"},
                    "time": {"field": "authTime"},
                    "dimensions": ["merchant.region"],
                    "value": {"valueType": "LONG", "measure": {"aggregation": "COUNT"}}
                  }
                }
                """;

        MetricValidationException exception = Assertions.assertThrows(
                MetricValidationException.class,
                () -> codec.parse(source));

        Assertions.assertEquals(MetricErrorCode.DSL_VALUE_INVALID, exception.errorCode());
        Assertions.assertEquals("/metric/joins/1/alias", exception.fieldPath());
    }

    @Test
    @DisplayName("DSL-T002 SCALAR 与 FIELD_SET 分支无歧义")
    void testParseFieldSetRatioAndDerivedOnlyDefinitions() {
        String fieldSet = """
                {
                  "schemaVersion": 1,
                  "metric": {
                    "code": "VCC_AUTH_SUMMARY",
                    "valueShape": "FIELD_SET",
                    "fact": "VccTransaction",
                    "subject": {"type": "CUSTOMER", "field": "customerId"},
                    "time": {"field": "authTime"},
                    "dimensions": ["currency"],
                    "fields": {
                      "approvedTotal": {"valueType": "LONG", "measure": {"aggregation": "COUNT"}},
                      "approvalRate": {
                        "valueType": "DECIMAL",
                        "scale": 4,
                        "roundingMode": "HALF_UP",
                        "expression": {"type": "SPEL", "value": "ratio(approvedTotal, approvedTotal)"}
                      }
                    }
                  }
                }
                """;
        String derivedOnly = """
                {
                  "schemaVersion": 1,
                  "metric": {
                    "code": "VCC_APPROVAL_RATE",
                    "valueShape": "SCALAR",
                    "subject": {"type": "CUSTOMER"},
                    "dimensions": ["currency"],
                    "metricRefs": {
                      "approvedTotal": {"metricCode": "VCC_APPROVED_TOTAL", "valueField": "value"}
                    },
                    "value": {
                      "valueType": "DECIMAL",
                      "scale": 4,
                      "roundingMode": "HALF_UP",
                      "expression": {"type": "SPEL", "value": "metric('approvedTotal')"}
                    }
                  }
                }
                """;

        MetricDefinitionDsl fieldSetDefinition = codec.parse(fieldSet);
        MetricDefinitionDsl derivedDefinition = codec.parse(derivedOnly);

        Assertions.assertEquals(2, fieldSetDefinition.metric().fields().size());
        Assertions.assertNull(derivedDefinition.metric().fact());
        Assertions.assertEquals(
                codec.canonicalize(fieldSetDefinition),
                codec.canonicalize(codec.parse(codec.canonicalize(fieldSetDefinition))));
        Assertions.assertEquals(
                codec.canonicalize(derivedDefinition),
                codec.canonicalize(codec.parse(codec.canonicalize(derivedDefinition))));
    }

    @Test
    @DisplayName("DSL-T003 DECIMAL 精度与计算分支严格校验")
    void testRejectInvalidScaleAndCalculationUnion() {
        String invalidScale = """
                {
                  "schemaVersion": 1,
                  "metric": {
                    "code": "VCC_TOTAL_AMOUNT",
                    "valueShape": "SCALAR",
                    "fact": "VccTransaction",
                    "subject": {"type": "GLOBAL"},
                    "time": {"field": "authTime"},
                    "dimensions": [],
                    "value": {
                      "valueType": "DECIMAL",
                      "scale": 3,
                      "roundingMode": "HALF_UP",
                      "measure": {"aggregation": "SUM", "field": "amount"}
                    }
                  }
                }
                """;
        String calculationUnion = """
                {
                  "schemaVersion": 1,
                  "metric": {
                    "code": "VCC_APPROVED_TOTAL",
                    "valueShape": "SCALAR",
                    "fact": "VccTransaction",
                    "subject": {"type": "GLOBAL"},
                    "time": {"field": "authTime"},
                    "dimensions": [],
                    "value": {
                      "valueType": "LONG",
                      "measure": {"aggregation": "COUNT"},
                      "expression": {"type": "SPEL", "value": "1"}
                    }
                  }
                }
                """;

        MetricValidationException scaleException = Assertions.assertThrows(
                MetricValidationException.class, () -> codec.parse(invalidScale));
        MetricValidationException unionException = Assertions.assertThrows(
                MetricValidationException.class, () -> codec.parse(calculationUnion));

        Assertions.assertEquals("/metric/value/scale", scaleException.fieldPath());
        Assertions.assertEquals(MetricErrorCode.DSL_VALUE_BRANCH_INVALID, unionException.errorCode());
        Assertions.assertEquals("/metric/value", unionException.fieldPath());
    }

    @Test
    void testRejectMetricReferenceAliasConflictingWithOutputField() {
        String source = """
                {
                  "schemaVersion": 1,
                  "metric": {
                    "code": "VCC_DERIVED_SUMMARY",
                    "valueShape": "FIELD_SET",
                    "subject": {"type": "CUSTOMER"},
                    "dimensions": [],
                    "metricRefs": {
                      "approvedTotal": {"metricCode": "VCC_APPROVED_TOTAL", "valueField": "value"}
                    },
                    "fields": {
                      "approvedTotal": {
                        "valueType": "LONG",
                        "expression": {"type": "SPEL", "value": "metric('approvedTotal')"}
                      }
                    }
                  }
                }
                """;

        MetricValidationException exception = Assertions.assertThrows(
                MetricValidationException.class,
                () -> codec.parse(source));

        Assertions.assertEquals(MetricErrorCode.DSL_VALUE_INVALID, exception.errorCode());
        Assertions.assertEquals("/metric/fields/approvedTotal", exception.fieldPath());
    }

    @Test
    void testRejectUnsupportedSchemaVersion() {
        MetricValidationException exception = Assertions.assertThrows(
                MetricValidationException.class,
                () -> codec.parse("{\"schemaVersion\":2,\"metric\":{},\"futureField\":true}"));

        Assertions.assertEquals(MetricErrorCode.DSL_SCHEMA_VERSION_UNSUPPORTED, exception.errorCode());
        Assertions.assertEquals("/schemaVersion", exception.fieldPath());
    }

    @Test
    void testRejectFieldPathAndIdentifierConflicts() {
        String nestedSubjectField = """
                {
                  "schemaVersion": 1,
                  "metric": {
                    "code": "VCC_APPROVED_TOTAL",
                    "valueShape": "SCALAR",
                    "fact": "VccTransaction",
                    "subject": {"type": "CUSTOMER", "field": "customer.id"},
                    "time": {"field": "authTime"},
                    "dimensions": [],
                    "value": {"valueType": "LONG", "measure": {"aggregation": "COUNT"}}
                  }
                }
                """;
        String factAliasConflict = """
                {
                  "schemaVersion": 1,
                  "metric": {
                    "code": "VCC_APPROVED_TOTAL_BY_REGION",
                    "valueShape": "SCALAR",
                    "fact": "VccTransaction",
                    "joins": [{
                      "alias": "VccTransaction",
                      "fact": "Merchant",
                      "joinType": "LEFT",
                      "cardinality": "MANY_TO_ONE",
                      "on": [{"primaryField": "merchantId", "joinField": "id"}]
                    }],
                    "subject": {"type": "CUSTOMER", "field": "customerId"},
                    "time": {"field": "authTime"},
                    "dimensions": ["VccTransaction.region"],
                    "value": {"valueType": "LONG", "measure": {"aggregation": "COUNT"}}
                  }
                }
                """;
        String scalarValueAliasConflict = """
                {
                  "schemaVersion": 1,
                  "metric": {
                    "code": "VCC_APPROVAL_RATE",
                    "valueShape": "SCALAR",
                    "subject": {"type": "CUSTOMER"},
                    "dimensions": [],
                    "metricRefs": {
                      "value": {"metricCode": "VCC_APPROVED_TOTAL", "valueField": "value"}
                    },
                    "value": {
                      "valueType": "LONG",
                      "expression": {"type": "SPEL", "value": "metric('value')"}
                    }
                  }
                }
                """;

        MetricValidationException fieldException = Assertions.assertThrows(
                MetricValidationException.class, () -> codec.parse(nestedSubjectField));
        MetricValidationException joinException = Assertions.assertThrows(
                MetricValidationException.class, () -> codec.parse(factAliasConflict));
        MetricValidationException referenceException = Assertions.assertThrows(
                MetricValidationException.class, () -> codec.parse(scalarValueAliasConflict));

        Assertions.assertEquals("/metric/subject/field", fieldException.fieldPath());
        Assertions.assertEquals("/metric/joins/0/alias", joinException.fieldPath());
        Assertions.assertEquals("/metric/metricRefs/value", referenceException.fieldPath());
    }

    private String derivedDefinitionWithOrElse(String valueType, String value) {
        return """
                {
                  "schemaVersion": 1,
                  "metric": {
                    "code": "VCC_DERIVED_TOTAL",
                    "valueShape": "SCALAR",
                    "subject": {"type": "CUSTOMER"},
                    "dimensions": [],
                    "metricRefs": {
                      "baseTotal": {"metricCode": "VCC_BASE_TOTAL", "valueField": "value"}
                    },
                    "value": {
                      "valueType": "%s",
                      "expression": {"type": "SPEL", "value": "metric('baseTotal')"},
                      "orElse": {"mode": "VALUE", "value": %s}
                    }
                  }
                }
                """.formatted(valueType, value);
    }

    private String realtimeCountDefinition(String additionalMetricField, String additionalMeasureField) {
        return """
                {
                  "schemaVersion": 1,
                  "metric": {
                    "code": "VCC_APPROVED_TOTAL",
                    "valueShape": "SCALAR",
                    "fact": "VccTransaction",
                    %s
                    "subject": {"type": "GLOBAL"},
                    "time": {"field": "authTime"},
                    "dimensions": [],
                    "value": {
                      "valueType": "LONG",
                      "measure": {"aggregation": "COUNT"%s}
                    }
                  }
                }
                """.formatted(additionalMetricField, additionalMeasureField);
    }
}
