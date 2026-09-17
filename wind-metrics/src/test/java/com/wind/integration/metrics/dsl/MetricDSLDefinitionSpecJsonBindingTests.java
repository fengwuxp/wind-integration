package com.wind.integration.metrics.dsl;

import com.wind.integration.metrics.MetricValidationException;
import com.wind.integration.metrics.json.MetricDefinitionDslCodec;
import com.wind.integration.metrics.spec.MetricDefinitionSpec.MetricDSLDefinitionSpec;
import com.wind.integration.metrics.dsl.definition.selection.MetricRowSelectionDsl;
import com.wind.integration.metrics.dsl.filter.LogicalMetricFilterDsl;
import com.wind.integration.metrics.dsl.filter.SetMetricFilterDsl;
import com.wind.integration.metrics.dsl.literal.StringMetricLiteralDsl;
import com.wind.integration.metrics.enums.MetricDefinitionType;
import com.wind.integration.metrics.enums.MetricErrorCode;
import com.wind.integration.metrics.enums.MetricFilterOperator;
import com.wind.jackson.WindJson;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

/**
 * 指标 Definition DSL 的 Jackson HTTP JSON 契约测试。
 *
 * @author wuxp
 * @date 2026-09-02 16:20
 */
class MetricDSLDefinitionSpecJsonBindingTests {

    private static final String DEFINITION_JSON = """
            {
              "schemaVersion": 1,
              "metric": {
                "code": "VCC_REFUND_FIRST_N_COUNT",
                "valueShape": "SCALAR",
                "fact": "VccRefundTransaction",
                "subject": {"type": "VCC", "field": "vccId"},
                "time": {"field": "authorizationTime"},
                "dimensions": [],
                "rowSelection": {
                  "filter": {"in": {"category": ["REFUND", "REVERSAL"]}},
                  "orderBy": [{"field": "authorizationTime", "direction": "ASC"}],
                  "limit": {"value": 2}
                },
                "value": {
                  "valueType": "LONG",
                  "measure": {
                    "aggregation": "COUNT",
                    "filter": {
                      "and": [
                        {"eq": {"currency": "USD"}},
                        {"isNotNull": "paymentAmount"}
                      ]
                    }
                  },
                  "orElse": {"mode": "ZERO"}
                }
              }
            }
            """;

    private final MetricDefinitionDslCodec codec = new MetricDefinitionDslCodec();

    private final JsonMapper jsonMapper = WindJson.getJsonMapper();

    @Test
    void testDeserializeNestedDefinitionWithRowSelectionSetFilter() {
        DefinitionRequest request = jsonMapper.readValue(
                "{\"definition\":" + DEFINITION_JSON + "}", DefinitionRequest.class);

        MetricRowSelectionDsl rowSelection = request.definition().definition().rowSelection();
        Assertions.assertNotNull(rowSelection);
        SetMetricFilterDsl filter = Assertions.assertInstanceOf(SetMetricFilterDsl.class, rowSelection.filter());
        Assertions.assertEquals(MetricFilterOperator.IN, filter.operator());
        Assertions.assertEquals("category", filter.fieldRef());
        Assertions.assertEquals(
                List.of(new StringMetricLiteralDsl("REFUND"), new StringMetricLiteralDsl("REVERSAL")),
                filter.values());
        Assertions.assertInstanceOf(
                LogicalMetricFilterDsl.class, request.definition().definition().value().measure().filter());
    }

    @Test
    void testSerializeNestedDefinitionAsCanonicalDslJson() {
        MetricDSLDefinitionSpec definition = codec.parse(DEFINITION_JSON);

        String json = jsonMapper.writeValueAsString(new DefinitionRequest(definition));

        Assertions.assertEquals("{\"definition\":" + codec.canonicalize(definition) + "}", json);
    }

    @Test
    void testRejectExplicitNullDefinition() {
        DatabindException exception = Assertions.assertThrows(
                DatabindException.class,
                () -> jsonMapper.readValue("{\"definition\":null}", DefinitionRequest.class));
        MetricValidationException cause = Assertions.assertInstanceOf(
                MetricValidationException.class, exception.getCause());

        Assertions.assertEquals(MetricErrorCode.DSL_ROOT_NOT_OBJECT, cause.errorCode());
        Assertions.assertEquals("", cause.fieldPath());
    }

    @Test
    void testRejectMalformedNestedDefinitionAsDslJsonError() {
        DatabindException exception = Assertions.assertThrows(
                DatabindException.class,
                () -> jsonMapper.readValue(
                        "{\"definition\":{\"schemaVersion\":1,\"metric\":]}}", DefinitionRequest.class));
        MetricValidationException cause = Assertions.assertInstanceOf(
                MetricValidationException.class, exception.getCause());

        Assertions.assertEquals(MetricErrorCode.DSL_JSON_INVALID, cause.errorCode());
        Assertions.assertEquals("", cause.fieldPath());
    }

    /** definitionType 由实现类型派生，不得进入规范 JSON，否则已发布定义的内容指纹会失效。 */
    @Test
    void testDefinitionTypeIsDerivedAndAbsentFromCanonicalJson() {
        MetricDSLDefinitionSpec definition = codec.parse(DEFINITION_JSON);

        Assertions.assertEquals(MetricDefinitionType.DSL, definition.definitionType());
        Assertions.assertFalse(codec.canonicalize(definition).contains("definitionType"));
    }

    private record DefinitionRequest(MetricDSLDefinitionSpec definition) {
    }
}
