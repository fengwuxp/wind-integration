package com.wind.integration.metrics.query;

import com.wind.integration.metrics.MetricValidationException;
import com.wind.integration.metrics.WindMetricsValue;
import com.wind.integration.metrics.WindStructuredMetricsValue;
import com.wind.integration.metrics.enums.MetricErrorCode;
import com.wind.integration.metrics.enums.MetricQueryMode;
import com.wind.integration.metrics.enums.MetricValueShape;
import com.wind.integration.metrics.enums.MetricValueType;
import com.wind.jackson.WindJson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.core.JacksonException;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 查询结果值对象的类型、固定结果和 JSON 协议验证；不代表宿主已支持非数值 DSL 计算。
 *
 * @author wuxp
 * @since 2026-09-20
 */
class MetricTypedValueContractTests {

    private static final LocalDateTime START = LocalDateTime.of(2026, 9, 1, 0, 0);

    /**
     * 场景：公开结果可以承载每一种已声明的值类型。
     * 输入：整数边界、高精度与零 scale 小数、数字外观字符串、纳秒级本地时间。
     * 流程：由具名值构造标量结果，再用 WindJson 序列化和还原。
     * 预期：code 绑定到结果编码，值及 Java 类型保持一致，JSON value 自带类型信息。
     */
    @ParameterizedTest
    @MethodSource("supportedValues")
    void testTypedScalarJsonRoundTrip(MetricValueType type, Object payload) {
        MetricResult result = scalar(type, WindMetricsValue.of("source", payload));

        String json = WindJson.toJsonString(result);
        MetricResult restored = WindJson.parseObject(json, MetricResult.class);

        assertEquals("TYPED", result.value().getCode());
        assertEquals("TYPED", restored.value().getCode());
        assertEquals(payload, restored.value().getValue());
        assertEquals(payload.getClass(), restored.value().getValue().getClass());
        assertTrue(json.contains("\"valueType\":\"" + type.name() + "\""));
        assertEquals(type, restored.value().getValueType());
        assertEquals(payload, restored.toMetricsValue().getValue());
    }

    /**
     * 场景：字段集同时包含数值、描述值、时间与正常空值。
     * 输入：count=3、grade=A、lastSeen=纳秒时间、average=null。
     * 流程：构造字段集、JSON 往返并读取结构化值视图。
     * 预期：每个字段按自身 valueType 还原；字段编码、值和显式 null 均保留。
     */
    @Test
    void testMixedFieldSetJsonRoundTrip() {
        LocalDateTime lastSeen = START.plusNanos(123456789);
        Map<String, MetricFieldValue> fields = new LinkedHashMap<>();
        fields.put("count", new MetricFieldValue(MetricValueType.LONG, WindMetricsValue.of("old", 3L)));
        fields.put("grade", new MetricFieldValue(MetricValueType.STRING, WindMetricsValue.of("old", "A")));
        fields.put("lastSeen", new MetricFieldValue(MetricValueType.TIMESTAMP,
                WindMetricsValue.of("old", lastSeen)));
        fields.put("average", new MetricFieldValue(MetricValueType.DECIMAL, WindMetricsValue.of("old", null)));
        MetricResult result = new MetricResult("TYPED", 1, MetricQueryMode.REALTIME, MetricValueShape.FIELD_SET, null, fields, null, START,
                START.plusDays(1), START.plusDays(1), ZoneId.of("Asia/Shanghai"), null, null, null, null,
                List.of(), List.of());

        MetricResult restored = WindJson.parseObject(WindJson.toJsonString(result), MetricResult.class);
        WindStructuredMetricsValue<?> view = assertInstanceOf(WindStructuredMetricsValue.class,
                restored.value());

        assertSame(view, restored.toMetricsValue());
        assertEquals(result, restored);
        assertEquals(result.hashCode(), restored.hashCode());
        assertEquals(3L, view.asFieldValues().get("count"));
        assertEquals("A", view.asFieldValues().get("grade"));
        assertEquals(lastSeen, view.asFieldValues().get("lastSeen"));
        assertTrue(view.asFieldValues().containsKey("average"));
        assertNull(view.asFieldValues().get("average"));
        assertEquals("lastSeen", restored.fields().get("lastSeen").value().getCode());
        assertEquals("average", restored.fields().get("average").value().getCode());
        assertNull(restored.fields().get("average").value().getValue());
    }

    /**
     * 场景：具名值中的正常空 payload 必须保留声明类型。
     * 输入：各 valueType 的 null 和带旧 code 的空值对象。
     * 流程：构造标量/字段值并 JSON 往返。
     * 预期：组件始终非空且绑定正确指标编码，只有 payload 为 null；JSON 往返保留声明类型。
     */
    @ParameterizedTest
    @EnumSource(MetricValueType.class)
    void testEmptyPayloadKeepsNamedValue(MetricValueType type) {
        MetricResult empty = scalar(type, null);
        MetricResult wrapped = scalar(type, WindMetricsValue.of("old", null));

        assertNotNull(wrapped.value());
        assertEquals("TYPED", wrapped.value().getCode());
        assertNull(wrapped.value().getValue());
        assertEquals(type, wrapped.value().getValueType());
        MetricFieldValue field = new MetricFieldValue(type, WindMetricsValue.of("field", null));
        assertNotNull(field.value());
        assertEquals("field", field.value().getCode());
        assertNull(field.value().getValue());
        assertEquals(empty, wrapped);
        assertEquals(empty, WindJson.parseObject(WindJson.toJsonString(wrapped), MetricResult.class));
        assertEquals("TYPED", wrapped.toMetricsValue().getCode());
        assertNull(wrapped.toMetricsValue().getValue());
        assertSame(wrapped.value(), wrapped.toMetricsValue());
        assertTrue(WindJson.toJsonString(wrapped).contains("\"value\":null"));
    }

    /**
     * 场景：字段集复制时可携带已生成的值对象，但不允许与字段正文冲突。
     * 输入：count=3 的字段正文与一致或冲突的结构化值。
     * 流程：分别构造字段集；一致结果做 JSON 往返，冲突结果捕获异常。
     * 预期：value 非空且固定，JSON 顶层 value 仍为 null；冲突定位 /value。
     */
    @Test
    void testFieldSetValueMustAgreeWithFields() {
        Map<String, MetricFieldValue> fields = Map.of("count", new MetricFieldValue(MetricValueType.LONG, 3L));
        MetricResult result = fieldSet(WindStructuredMetricsValue.of("old", Map.of("count", 3L)), fields);
        String json = WindJson.toJsonString(result);

        assertEquals("TYPED", result.value().getCode());
        assertEquals(Map.of("count", 3L), result.value().getValue());
        Map<?, ?> jsonResult = WindJson.getJsonMapper().readValue(json, Map.class);
        assertTrue(jsonResult.containsKey("value"));
        assertNull(jsonResult.get("value"));
        assertEquals(result, WindJson.parseObject(json, MetricResult.class));
        MetricValidationException failure = assertThrows(MetricValidationException.class,
                () -> fieldSet(WindStructuredMetricsValue.of("TYPED", Map.of("count", 4L)), fields));
        assertEquals(MetricErrorCode.RESULT_INVALID, failure.errorCode());
        assertEquals("/value", failure.fieldPath());
    }

    /**
     * 场景：值对象不能绕过声明类型和数值精度约束。
     * 输入：数字字符串、整数溢出、Double、小数冒充整数、未声明的布尔和对象。
     * 流程：分别构造标量与字段值。
     * 预期：统一报 RESULT_INVALID /value，不静默转换、不丢精度。
     */
    @ParameterizedTest
    @MethodSource("invalidValues")
    void testRejectMismatchedValueType(MetricValueType type, Object payload) {
        MetricValidationException scalarFailure = assertThrows(MetricValidationException.class,
                () -> scalar(type, WindMetricsValue.of("source", payload)));
        MetricValidationException fieldFailure = assertThrows(MetricValidationException.class,
                () -> new MetricFieldValue(type, WindMetricsValue.of("field", payload)));

        assertEquals(MetricErrorCode.RESULT_INVALID, scalarFailure.errorCode());
        assertEquals("/value", scalarFailure.fieldPath());
        assertEquals(MetricErrorCode.RESULT_INVALID, fieldFailure.errorCode());
        assertEquals("/value", fieldFailure.fieldPath());
    }

    /**
     * 场景：固定查询结果不能继续引用可变化的值提供者。
     * 输入：构造时为 3L 的外部值提供者，构造后将其改为 9L。
     * 流程：构造标量和字段值，再改变提供者并读取结果/序列化。
     * 预期：结果一直为构造时的 3L。
     */
    @Test
    void testResultCapturesProviderValueAtConstruction() {
        AtomicReference<Long> current = new AtomicReference<>(3L);
        WindMetricsValue<Long> provider = new WindMetricsValue<>() {
            @Override
            public String getName() {
                return "live";
            }

            @Override
            public MetricValueType getValueType() {
                return MetricValueType.LONG;
            }

            @Override
            public Long getValue() {
                return current.get();
            }
        };
        MetricResult result = new MetricResult("TYPED", 1, MetricQueryMode.REALTIME, MetricValueShape.SCALAR,
                provider, Map.of(), null, START, START.plusDays(1), START.plusDays(1),
                ZoneId.of("Asia/Shanghai"), null, null, null, null, List.of(), List.of());
        MetricFieldValue field = new MetricFieldValue(provider);

        current.set(9L);

        assertEquals(3L, result.value().getValue());
        assertEquals(3L, field.value().getValue());
        assertTrue(WindJson.toJsonString(result).contains("\"value\":3"));
    }

    /**
     * 场景：LONG payload 不能悄悄接受其他 token 或嵌套值对象。
     * 输入：LONG 结果的 value 被改为字符串、布尔、数组或 code/value 对象。
     * 流程：用 WindJson 反序列化外部 JSON。
     * 预期：全部拒绝，数字字符串不强制转数值，payload 不接受再次嵌套的包装形状。
     */
    @ParameterizedTest
    @ValueSource(strings = {"\"3\"", "true", "[3]", "{\"code\":\"TYPED\",\"value\":3}"})
    void testRejectInvalidJsonValue(String invalidToken) {
        String json = WindJson.toJsonString(scalar(MetricValueType.LONG, WindMetricsValue.of("source", 3L)));
        assertTrue(json.contains("\"value\":3"));
        String invalidJson = json.replace("\"value\":3", "\"value\":" + invalidToken);

        assertThrows(JacksonException.class,
                () -> WindJson.parseObject(invalidJson, MetricResult.class));
    }

    private static Stream<Arguments> supportedValues() {
        return Stream.of(
                Arguments.of(MetricValueType.INTEGER, Integer.MIN_VALUE),
                Arguments.of(MetricValueType.LONG, Long.MAX_VALUE),
                Arguments.of(MetricValueType.DECIMAL, new BigDecimal("12345678901234567890.12345678901234567890")),
                Arguments.of(MetricValueType.DECIMAL, new BigDecimal("12")),
                Arguments.of(MetricValueType.STRING, "00123"),
                Arguments.of(MetricValueType.TIMESTAMP, START.plusNanos(123456789)));
    }

    private static Stream<Arguments> invalidValues() {
        return Stream.of(
                Arguments.of(MetricValueType.LONG, "3"),
                Arguments.of(MetricValueType.INTEGER, 2147483648L),
                Arguments.of(MetricValueType.LONG, new BigInteger("9223372036854775808")),
                Arguments.of(MetricValueType.DECIMAL, 0.1D),
                Arguments.of(MetricValueType.INTEGER, new BigDecimal("1.1")),
                Arguments.of(MetricValueType.STRING, 3L),
                Arguments.of(MetricValueType.STRING, true),
                Arguments.of(MetricValueType.STRING, Map.of("grade", "A")),
                Arguments.of(MetricValueType.TIMESTAMP, "not-a-timestamp"));
    }

    private static MetricResult scalar(MetricValueType type, WindMetricsValue<?> value) {
        return new MetricResult("TYPED", 1, MetricQueryMode.REALTIME, MetricValueShape.SCALAR,
                WindMetricsValue.of("value", type, value == null ? null : value.getValue()), Map.of(), null,
                START, START.plusDays(1), START.plusDays(1), ZoneId.of("Asia/Shanghai"), null, null, null,
                null, List.of(), List.of());
    }

    private static MetricResult fieldSet(WindMetricsValue<?> value, Map<String, MetricFieldValue> fields) {
        return new MetricResult("TYPED", 1, MetricQueryMode.REALTIME, MetricValueShape.FIELD_SET, value, fields, null, START,
                START.plusDays(1), START.plusDays(1), ZoneId.of("Asia/Shanghai"), null, null, null, null,
                List.of(), List.of());
    }
}
