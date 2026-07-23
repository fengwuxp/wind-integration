package com.wind.integration.metrics.dsl;

import com.wind.integration.metrics.MetricValidationException;
import com.wind.integration.metrics.enums.MetricErrorCode;
import com.wind.integration.metrics.query.MetricBatchQuery;
import com.wind.integration.metrics.query.MetricQuery;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

/**
 * 指标正式查询 JSON 关闭世界合同测试。
 *
 * @author wuxp
 * @date 2026-07-22 16:01
 */
class MetricQueryJsonCodecTests {

    private static final String QUERY_JSON = """
            {
              "metricCode": "VCC_AUTH_SUMMARY",
              "subjectId": "cust_001",
              "startTime": "2026-03-01T00:00:00",
              "endTime": "2026-07-15T00:00:00",
              "dimensionValues": {
                "currency": "USD"
              }
            }
            """;

    private static final String BATCH_QUERY_JSON = """
            {
              "metricCodes": ["VCC_APPROVED_TOTAL", "VCC_TOTAL_AMOUNT"],
              "subjectId": "cust_001",
              "startTime": "2026-03-01T00:00:00",
              "endTime": "2026-07-15T00:00:00",
              "dimensionValues": {
                "currency": "USD"
              }
            }
            """;

    private final MetricQueryJsonCodec codec = new MetricQueryJsonCodec();

    @Test
    @DisplayName("DSL-T005 canonical 单查与批查映射公共 Query")
    void testDslT005ParseCanonicalSingleAndBatchQueries() {
        MetricQuery query = codec.parse(QUERY_JSON);
        MetricBatchQuery batchQuery = codec.parseBatch(BATCH_QUERY_JSON);

        Assertions.assertEquals("VCC_AUTH_SUMMARY", query.metricCode());
        Assertions.assertEquals("USD", query.dimensionValues().get("currency"));
        Assertions.assertEquals(
                List.of("VCC_APPROVED_TOTAL", "VCC_TOTAL_AMOUNT"), batchQuery.metricCodes());
    }

    @Test
    @DisplayName("DSL-T005 正式单查拒绝全部服务端字段")
    void testDslT005RejectAllForbiddenSingleQueryFields() {
        List<String> forbiddenFields = List.of(
                "executionMode",
                "definitionRevision",
                "tableName",
                "dimensionKey",
                "dimensionSignature",
                "filter",
                "tenantIds");

        for (String field : forbiddenFields) {
            MetricValidationException exception = Assertions.assertThrows(
                    MetricValidationException.class,
                    () -> codec.parse(withExtraField(QUERY_JSON, field)));

            Assertions.assertEquals(MetricErrorCode.QUERY_INVALID, exception.errorCode(), field);
            Assertions.assertEquals("/" + field, exception.fieldPath(), field);
        }
    }

    @Test
    @DisplayName("DSL-T005 正式批查拒绝服务端字段")
    void testDslT005RejectForbiddenBatchQueryField() {
        MetricValidationException exception = Assertions.assertThrows(
                MetricValidationException.class,
                () -> codec.parseBatch(withExtraField(BATCH_QUERY_JSON, "executionMode")));

        Assertions.assertEquals(MetricErrorCode.QUERY_INVALID, exception.errorCode());
        Assertions.assertEquals("/executionMode", exception.fieldPath());
    }

    @Test
    @DisplayName("DSL-T106 单查接受整数 parameterValues，批查仍拒绝")
    void testDslT106ParseSingleQueryParametersAndRejectBatchParameters() {
        MetricQuery query = codec.parse(withExtraField(QUERY_JSON, "parameterValues", "{\"entryLimit\": 2}"));
        Map<String, Object> parameterValues = query.parameterValues();
        MetricValidationException batchException = Assertions.assertThrows(
                MetricValidationException.class,
                () -> codec.parseBatch(withExtraField(
                        BATCH_QUERY_JSON, "parameterValues", "{\"entryLimit\": 2}")));

        Assertions.assertEquals(Map.of("entryLimit", 2), parameterValues);
        Assertions.assertThrows(UnsupportedOperationException.class, () -> parameterValues.put("entryLimit", 3));
        Assertions.assertEquals(MetricErrorCode.QUERY_INVALID, batchException.errorCode());
        Assertions.assertEquals("/parameterValues", batchException.fieldPath());
    }

    @Test
    @DisplayName("DSL-T105 parameterValues 只允许非空整数值")
    void testDslT105RejectInvalidQueryParameterValues() {
        for (String invalidValue : List.of(
                "null", "\"2\"", "2.0", "2147483648", "-2147483649", "{}", "[]")) {
            MetricValidationException exception = Assertions.assertThrows(
                    MetricValidationException.class,
                    () -> codec.parse(withExtraField(
                            QUERY_JSON, "parameterValues", "{\"entryLimit\": " + invalidValue + "}")),
                    invalidValue);

            Assertions.assertEquals("METRIC_PARAMETER_TYPE_MISMATCH", exception.errorCode().name());
            Assertions.assertEquals("/parameterValues/entryLimit", exception.fieldPath());
        }

        MetricValidationException blankName = Assertions.assertThrows(
                MetricValidationException.class,
                () -> codec.parse(withExtraField(QUERY_JSON, "parameterValues", "{\"\": 2}")));
        Assertions.assertEquals(MetricErrorCode.METRIC_PARAMETER_TYPE_MISMATCH, blankName.errorCode());
        Assertions.assertEquals("/parameterValues", blankName.fieldPath());

        MetricValidationException nullParameters = Assertions.assertThrows(
                MetricValidationException.class,
                () -> codec.parse(withExtraField(QUERY_JSON, "parameterValues", "null")));
        Assertions.assertEquals(MetricErrorCode.METRIC_PARAMETER_TYPE_MISMATCH, nullParameters.errorCode());
        Assertions.assertEquals("/parameterValues", nullParameters.fieldPath());
    }

    private static String withExtraField(String source, String field) {
        return withExtraField(source, field, "\"forbidden\"");
    }

    private static String withExtraField(String source, String field, String value) {
        int objectEnd = source.lastIndexOf('}');
        return source.substring(0, objectEnd) + ",\n\"" + field + "\": " + value + "\n}";
    }
}
