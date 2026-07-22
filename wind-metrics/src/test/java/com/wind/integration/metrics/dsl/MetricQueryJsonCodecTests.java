package com.wind.integration.metrics.dsl;

import com.wind.integration.metrics.MetricValidationException;
import com.wind.integration.metrics.enums.MetricErrorCode;
import com.wind.integration.metrics.query.MetricBatchQuery;
import com.wind.integration.metrics.query.MetricQuery;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 指标正式查询 JSON 关闭世界合同测试。
 *
 * @author wuxp
 * @since 2026-07-22
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

    private static String withExtraField(String source, String field) {
        int objectEnd = source.lastIndexOf('}');
        return source.substring(0, objectEnd) + ",\n\"" + field + "\": \"forbidden\"\n}";
    }
}
