package com.wind.integration.metrics.query;

import com.wind.integration.metrics.dsl.MetricDslErrorCode;
import com.wind.integration.metrics.dsl.MetricDslValidationException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 指标正式查询模型的公共合同测试。
 */
class MetricQueryContractTests {

    private static final LocalDateTime START_TIME = LocalDateTime.of(2026, 3, 1, 0, 0);

    private static final LocalDateTime END_TIME = LocalDateTime.of(2026, 7, 15, 0, 0);

    @Test
    void testQueryKeepsImmutableScalarDimensionValues() {
        Map<String, Object> dimensions = new LinkedHashMap<>();
        dimensions.put("currency", "USD");
        dimensions.put("amount", new BigDecimal("12.30"));

        MetricQuery query = new MetricQuery(
                "VCC_APPROVED_TOTAL", "cust_001", START_TIME, END_TIME, dimensions);
        dimensions.put("currency", "EUR");

        Assertions.assertEquals("USD", query.dimensionValues().get("currency"));
        Assertions.assertThrows(
                UnsupportedOperationException.class,
                () -> query.dimensionValues().put("currency", "EUR"));
    }

    @Test
    void testRejectUnsupportedDimensionValue() {
        MetricDslValidationException exception = Assertions.assertThrows(
                MetricDslValidationException.class,
                () -> new MetricQuery(
                        "VCC_APPROVED_TOTAL", "cust_001", START_TIME, END_TIME, Map.of("ratio", 0.5D)));

        Assertions.assertEquals(MetricDslErrorCode.QUERY_INVALID, exception.errorCode());
        Assertions.assertEquals("/dimensionValues/ratio", exception.fieldPath());
    }

    @Test
    void testBatchRejectsDuplicateMetricCodes() {
        List<String> metricCodes = new ArrayList<>(List.of("VCC_APPROVED_TOTAL", "VCC_APPROVED_TOTAL"));

        MetricDslValidationException exception = Assertions.assertThrows(
                MetricDslValidationException.class,
                () -> new MetricBatchQuery(metricCodes, "cust_001", START_TIME, END_TIME, Map.of()));

        Assertions.assertEquals(MetricDslErrorCode.QUERY_INVALID, exception.errorCode());
        Assertions.assertEquals("/metricCodes", exception.fieldPath());
    }

    @Test
    void testQueryDefensivelyCopiesMutableTemporalValues() {
        Date requestedDate = new Date(1_720_000_000_000L);
        Timestamp requestedTimestamp = new Timestamp(1_720_000_000_000L);
        requestedTimestamp.setNanos(123_456_789);
        MetricQuery query = new MetricQuery(
                "VCC_APPROVED_TOTAL",
                "cust_001",
                START_TIME,
                END_TIME,
                Map.of("requestedDate", requestedDate, "requestedTimestamp", requestedTimestamp));

        requestedDate.setTime(0L);
        requestedTimestamp.setTime(0L);
        ((Date) query.dimensionValues().get("requestedDate")).setTime(1L);
        ((Timestamp) query.dimensionValues().get("requestedTimestamp")).setTime(1L);

        Assertions.assertEquals(1_720_000_000_000L, ((Date) query.dimensionValues().get("requestedDate")).getTime());
        Timestamp actualTimestamp = (Timestamp) query.dimensionValues().get("requestedTimestamp");
        Assertions.assertEquals(1_720_000_000_123L, actualTimestamp.getTime());
        Assertions.assertEquals(123_456_789, actualTimestamp.getNanos());
    }
}
