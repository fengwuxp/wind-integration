package com.wind.integration.metrics.dsl;

import com.wind.integration.metrics.MetricValidationException;
import com.wind.integration.metrics.enums.MetricErrorCode;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 指标 Definition DSL 参数化行选择的公共契约测试。
 *
 * @author wuxp
 * @date 2026-07-23 11:10
 */
class MetricDefinitionDslRowSelectionCodecTests {

    private final MetricDefinitionDslCodec codec = new MetricDefinitionDslCodec();

    @Test
    @DisplayName("DSL-T104 v1 使用单个 metric.rowSelection 表达最早 N 条事实")
    void testDslT104CanonicalizeRowSelectionDefinition() {
        String canonical = codec.canonicalize(codec.parse(rowSelectionDefinition(
                "\"limit\": {\"parameter\": \"entryLimit\"}", "")));

        Assertions.assertEquals(
                "{\"schemaVersion\":1,\"metric\":{\"code\":\"VCC_REFUND_FIRST_N_SUMMARY\","
                        + "\"valueShape\":\"FIELD_SET\",\"fact\":\"VccRefundTransaction\","
                        + "\"subject\":{\"type\":\"VCC\",\"field\":\"vccId\"},"
                        + "\"time\":{\"field\":\"authorizationTime\"},\"dimensions\":[],"
                        + "\"parameters\":{\"entryLimit\":{\"valueType\":\"INTEGER\","
                        + "\"minimum\":1,\"maximum\":100}},"
                        + "\"rowSelection\":{\"filter\":{\"and\":[{\"eq\":{\"businessScene\":\"VCC_REFUND\"}},"
                        + "{\"eq\":{\"category\":\"REFUND\"}}]},"
                        + "\"orderBy\":[{\"field\":\"authorizationTime\",\"direction\":\"ASC\"},"
                        + "{\"field\":\"transactionId\",\"direction\":\"ASC\"}],"
                        + "\"limit\":{\"parameter\":\"entryLimit\"}},"
                        + "\"fields\":{\"refundAmount\":{\"valueType\":\"DECIMAL\",\"scale\":4,"
                        + "\"roundingMode\":\"HALF_UP\",\"measure\":{\"aggregation\":\"SUM\","
                        + "\"field\":\"paymentAmount\"},\"orElse\":{\"mode\":\"ZERO\"}},"
                        + "\"refundCount\":{\"valueType\":\"LONG\",\"measure\":{\"aggregation\":\"COUNT\"},"
                        + "\"orElse\":{\"mode\":\"ZERO\"}}}}}",
                canonical);
        Assertions.assertEquals(canonical, codec.canonicalize(codec.parse(canonical)));
    }

    @Test
    @DisplayName("DSL-T105 参数和 rowSelection 结构失败关闭")
    void testDslT105RejectInvalidParameterAndRowSelection() {
        MetricValidationException unused = Assertions.assertThrows(
                MetricValidationException.class,
                () -> codec.parse(rowSelectionDefinition("\"limit\": {\"value\": 2}", "")));
        MetricValidationException unknownReference = Assertions.assertThrows(
                MetricValidationException.class,
                () -> codec.parse(rowSelectionDefinition(
                        "\"limit\": {\"parameter\": \"unknownLimit\"}", "")));
        MetricValidationException duplicateOrder = Assertions.assertThrows(
                MetricValidationException.class,
                () -> codec.parse(rowSelectionDefinition(
                        "\"limit\": {\"parameter\": \"entryLimit\"}",
                        ", {\"field\": \"transactionId\", \"direction\": \"DESC\"}")));

        Assertions.assertEquals(MetricErrorCode.METRIC_PARAMETER_UNUSED, unused.errorCode());
        Assertions.assertEquals("/metric/parameters/entryLimit", unused.fieldPath());
        Assertions.assertEquals("METRIC_ROW_SELECTION_INVALID", unknownReference.errorCode().name());
        Assertions.assertEquals("/metric/rowSelection/limit/parameter", unknownReference.fieldPath());
        Assertions.assertEquals("METRIC_ROW_SELECTION_INVALID", duplicateOrder.errorCode().name());
        Assertions.assertEquals("/metric/rowSelection/orderBy", duplicateOrder.fieldPath());
    }

    @Test
    @DisplayName("DSL-T006 只支持最新 v1 字段位置")
    void testDslT006RejectRemovedInputPipelineAndFutureVersion() {
        String legacyInput = rowSelectionDefinition(
                "\"limit\": {\"parameter\": \"entryLimit\"}", "")
                .replace("\"rowSelection\": {", "\"input\": {");
        String measureInput = rowSelectionDefinition(
                "\"limit\": {\"parameter\": \"entryLimit\"}", "")
                .replace(
                        "\"aggregation\": \"COUNT\"",
                        "\"aggregation\": \"COUNT\", \"input\": {\"transforms\": []}");
        String futureVersion = rowSelectionDefinition(
                "\"limit\": {\"parameter\": \"entryLimit\"}", "")
                .replace("\"schemaVersion\": 1", "\"schemaVersion\": 2");

        MetricValidationException inputException = Assertions.assertThrows(
                MetricValidationException.class, () -> codec.parse(legacyInput));
        MetricValidationException measureException = Assertions.assertThrows(
                MetricValidationException.class, () -> codec.parse(measureInput));
        MetricValidationException versionException = Assertions.assertThrows(
                MetricValidationException.class, () -> codec.parse(futureVersion));

        Assertions.assertEquals(MetricErrorCode.DSL_FIELD_UNKNOWN, inputException.errorCode());
        Assertions.assertEquals("/metric/input", inputException.fieldPath());
        Assertions.assertEquals(MetricErrorCode.DSL_FIELD_UNKNOWN, measureException.errorCode());
        Assertions.assertEquals("/metric/fields/refundCount/measure/input", measureException.fieldPath());
        Assertions.assertEquals(MetricErrorCode.DSL_SCHEMA_VERSION_UNSUPPORTED, versionException.errorCode());
        Assertions.assertEquals("/schemaVersion", versionException.fieldPath());
    }

    private String rowSelectionDefinition(String limitField, String additionalOrder) {
        return """
                {
                  "schemaVersion": 1,
                  "metric": {
                    "code": "VCC_REFUND_FIRST_N_SUMMARY",
                    "valueShape": "FIELD_SET",
                    "fact": "VccRefundTransaction",
                    "subject": {"type": "VCC", "field": "vccId"},
                    "time": {"field": "authorizationTime"},
                    "dimensions": [],
                    "parameters": {
                      "entryLimit": {"valueType": "INTEGER", "minimum": 1, "maximum": 100}
                    },
                    "rowSelection": {
                      "filter": {
                        "and": [
                          {"eq": {"category": "REFUND"}},
                          {"eq": {"businessScene": "VCC_REFUND"}}
                        ]
                      },
                      "orderBy": [
                        {"field": "authorizationTime", "direction": "ASC"},
                        {"field": "transactionId", "direction": "ASC"}%s
                      ],
                      %s
                    },
                    "fields": {
                      "refundAmount": {
                        "valueType": "DECIMAL",
                        "scale": 4,
                        "roundingMode": "HALF_UP",
                        "measure": {"aggregation": "SUM", "field": "paymentAmount"},
                        "orElse": {"mode": "ZERO"}
                      },
                      "refundCount": {
                        "valueType": "LONG",
                        "measure": {"aggregation": "COUNT"},
                        "orElse": {"mode": "ZERO"}
                      }
                    }
                  }
                }
                """.formatted(additionalOrder, limitField);
    }
}
