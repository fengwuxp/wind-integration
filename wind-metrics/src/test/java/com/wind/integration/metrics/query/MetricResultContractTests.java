package com.wind.integration.metrics.query;

import com.wind.integration.metrics.MetricSegmentValue;
import com.wind.integration.metrics.WindMetricsValue;
import com.wind.integration.metrics.enums.MetricQueryMode;
import com.wind.integration.metrics.enums.MetricValueShape;
import com.wind.integration.metrics.enums.MetricValueType;
import com.wind.jackson.WindJson;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 用户结果模型的原始值、分段值能力及 JSON 接线。
 *
 * @author wuxp
 * @since 2026-09-22
 */
class MetricResultContractTests {

    @Test
    void testResultItselfProvidesCodeValueAndOpenBounds() {
        BigDecimal amount = new BigDecimal("12.5000");
        MetricResult result = scalar(amount);
        MetricSegmentValue<Object> value = result;

        assertEquals("TOTAL", value.getCode());
        assertEquals("TOTAL", value.getName());
        assertSame(amount, value.getValue());
        assertSame(amount, result.value());
        assertNull(value.startTime());
        assertNull(value.endTime());
    }

    @Test
    void testRawJsonDoesNotExposeLegacyGetterProperties() {
        MetricResult result = scalar(new BigDecimal("12.5000"));
        Map<?, ?> json = WindJson.parseObject(WindJson.toJsonString(result), Map.class);

        assertEquals(Set.of("metricCode", "definitionRevision", "executionMode", "valueShape", "value", "fields",
                "subjectId", "startTime", "endTime"), json.keySet());
        assertInstanceOf(Number.class, json.get("value"));
        assertFalse(json.containsKey("sources"));
        assertFalse(json.containsKey("segments"));
    }

    @Test
    void testRawDecimalAndLargeIntegerKeepPrecision() {
        BigDecimal decimal = new BigDecimal("12345678901234567890.123456789012345678901");
        BigInteger integer = new BigInteger("18446744073709551615");
        MetricResult decimalResult = roundTrip(scalar(decimal));
        MetricResult integerResult = roundTrip(scalar(integer));

        assertEquals(decimal, assertInstanceOf(BigDecimal.class, decimalResult.getValue()));
        assertEquals(integer, assertInstanceOf(BigInteger.class, integerResult.getValue()));
    }

    @Test
    void testRawContainersPreserveNullStringsAndNestedDecimals() {
        BigDecimal amount = new BigDecimal("0.000000000000000000000000000001");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", "00123");
        payload.put("valueType", "business-data");
        payload.put("missing", null);
        payload.put("items", List.of(Map.of("amount", amount), true));

        Map<?, ?> restored = assertInstanceOf(Map.class, roundTrip(scalar(payload)).getValue());

        assertEquals("00123", restored.get("name"));
        assertEquals("business-data", restored.get("valueType"));
        assertTrue(restored.containsKey("missing"));
        assertNull(restored.get("missing"));
        List<?> items = assertInstanceOf(List.class, restored.get("items"));
        assertEquals(amount, ((Map<?, ?>) items.getFirst()).get("amount"));
        assertEquals(true, items.get(1));
    }

    @Test
    void testRawStringIsNotGuessedAsNumberOrTimestamp() {
        assertEquals("00123", roundTrip(scalar("00123")).getValue());
        String time = "2026-09-22T12:30:01.123456789";
        assertEquals(time, roundTrip(scalar(time)).getValue());
        assertEquals(time, roundTrip(scalar(LocalDateTime.parse(time))).getValue());
    }

    @Test
    void testNullPayloadAndFieldsAreNotAutoFilled() {
        MetricResult result = roundTrip(scalar(null));

        assertNull(result.getValue());
        assertNull(result.fields());
        assertNull(result.segmentValues());
    }

    @Test
    void testFieldSetKeepsExplicitTypedFieldsWithoutDerivingValue() {
        Map<String, MetricFieldValue> fields = Map.of(
                "amount", new MetricFieldValue(WindMetricsValue.of("amount", MetricValueType.DECIMAL, new BigDecimal("0.00000000000000000001"))),
                "label", new MetricFieldValue(WindMetricsValue.of("label", MetricValueType.STRING, null)));
        MetricResult result = MetricResult.builder().metricCode("DETAIL").definitionRevision(2)
                .executionMode(MetricQueryMode.REALTIME).valueShape(MetricValueShape.FIELD_SET).fields(fields).build();
        MetricResult restored = roundTrip(result);

        assertNull(result.getValue());
        assertNull(restored.getValue());
        assertEquals(fields, restored.fields());
        assertEquals(MetricValueType.STRING, restored.fields().get("label").value().getValueType());
        assertNull(restored.fields().get("label").value().getValue());
    }

    private static MetricResult scalar(Object value) {
        return MetricResult.builder().metricCode("TOTAL").definitionRevision(1)
                .executionMode(MetricQueryMode.REALTIME).valueShape(MetricValueShape.SCALAR).value(value).build();
    }

    private static MetricResult roundTrip(MetricResult result) {
        return WindJson.parseObject(WindJson.toJsonString(result), MetricResult.class);
    }
}
