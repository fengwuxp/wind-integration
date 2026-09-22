package com.wind.integration.metrics.query;

import com.wind.integration.metrics.MetricValidationException;
import com.wind.integration.metrics.WindMetricsValue;
import com.wind.integration.metrics.enums.MetricValueType;
import com.wind.jackson.WindJson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import tools.jackson.core.type.TypeReference;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Runtime具名映射与字段值保留类型、NULL及原始精度；结果Object不承担这层类型声明。
 *
 * @author wuxp
 * @since 2026-09-22
 */
class MetricTypedValueContractTests {

    @ParameterizedTest
    @EnumSource(MetricValueType.class)
    void testTypedNullSurvivesCreatorAndFieldCodec(MetricValueType type) {
        WindMetricsValue<?> value = WindMetricsValue.of("value", type, null);
        WindMetricsValue<?> restored = WindJson.parseObject(WindJson.toJsonString(value), WindMetricsValue.class);
        MetricFieldValue field = new MetricFieldValue(value);
        MetricFieldValue restoredField = WindJson.parseObject(WindJson.toJsonString(field), MetricFieldValue.class);

        assertEquals(type, restored.getValueType());
        assertNull(restored.getValue());
        assertEquals(field, restoredField);
    }

    @ParameterizedTest
    @MethodSource("typedValues")
    void testTypedPayloadSurvivesCreatorAndFieldCodec(MetricValueType type, Object payload) {
        WindMetricsValue<?> value = WindMetricsValue.of("field", type, payload);
        WindMetricsValue<?> restored = WindJson.parseObject(WindJson.toJsonString(value), WindMetricsValue.class);
        MetricFieldValue field = new MetricFieldValue(value);
        MetricFieldValue restoredField = WindJson.parseObject(WindJson.toJsonString(field), MetricFieldValue.class);

        assertEquals(payload, restored.getValue());
        assertEquals(type, restored.getValueType());
        assertEquals(payload.getClass(), restored.getValue().getClass());
        assertEquals(field, restoredField);
    }

    @Test
    void testRuntimeMapPreservesExplicitNullTypeAndUnsignedIntegerPrecision() {
        BigDecimal amount = new BigDecimal("12345678901234567890.12345678901234567890");
        BigInteger unsigned = new BigInteger("18446744073709551615");
        Map<String, WindMetricsValue<?>> values = new LinkedHashMap<>();
        values.put("amount", WindMetricsValue.of("amount", MetricValueType.DECIMAL, amount));
        values.put("unsigned", WindMetricsValue.of("unsigned", MetricValueType.DECIMAL, unsigned));
        values.put("label", WindMetricsValue.of("label", MetricValueType.STRING, null));

        Map<String, WindMetricsValue<?>> restored = WindJson.parseObject(WindJson.toJsonString(values), new TypeReference<>() { });

        assertEquals(amount, restored.get("amount").getValue());
        assertEquals(new BigDecimal(unsigned), restored.get("unsigned").getValue());
        assertEquals(MetricValueType.STRING, restored.get("label").getValueType());
        assertNull(restored.get("label").getValue());
        restored.forEach((name, value) -> assertEquals(name, value.getCode()));
    }

    @Test
    void testExplicitCreatorRequiresTypeAndJavaInferenceFactoryStillWorks() {
        assertThrows(RuntimeException.class, () -> WindJson.parseObject("{\"code\":\"value\",\"value\":3}", WindMetricsValue.class));
        assertEquals(MetricValueType.LONG, WindMetricsValue.of("value", 3L).getValueType());
        assertEquals(MetricValueType.DECIMAL, WindMetricsValue.of("value", null).getValueType());
    }

    @Test
    void testTypedFactoryStillRejectsFloatingPointAndOverflow() {
        assertThrows(MetricValidationException.class, () -> WindMetricsValue.of("value", MetricValueType.DECIMAL, 0.1D));
        assertThrows(MetricValidationException.class, () -> WindMetricsValue.of("value", MetricValueType.INTEGER, 2147483648L));
        assertThrows(MetricValidationException.class, () -> WindMetricsValue.of("value", MetricValueType.STRING, 3));
    }

    private static Stream<Arguments> typedValues() {
        return Stream.of(
                Arguments.of(MetricValueType.INTEGER, 3),
                Arguments.of(MetricValueType.LONG, 3L),
                Arguments.of(MetricValueType.LONG, Long.MAX_VALUE),
                Arguments.of(MetricValueType.DECIMAL, new BigDecimal("12345678901234567890.12345678901234567890")),
                Arguments.of(MetricValueType.DECIMAL, new BigDecimal("0.00000000000000000001")),
                Arguments.of(MetricValueType.STRING, "00123"),
                Arguments.of(MetricValueType.TIMESTAMP, LocalDateTime.of(2026, 9, 22, 12, 30, 0, 123456789)));
    }
}
