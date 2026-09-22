package com.wind.integration.metrics.query;

import com.wind.integration.metrics.MetricValidationException;
import com.wind.integration.metrics.WindMetricsValue;
import com.wind.integration.metrics.enums.MetricQueryMode;
import com.wind.integration.metrics.enums.MetricValueShape;
import com.wind.integration.metrics.enums.MetricValueType;
import com.wind.integration.metrics.enums.MetricSnapshotGranularity;
import com.wind.jackson.WindJson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.core.JacksonException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 完整来源结果复用合同，验证宿主可以交付真实计算输入而非另造来源摘要。
 *
 * @author wuxp
 * @since 2026-09-21
 */
class MetricResultSourcesContractTests {

    private static final LocalDateTime START = LocalDateTime.of(2026, 9, 1, 0, 0);

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    /**
     * 场景：派生总额依赖一个实时高精度金额和一个快照汇总字段。
     * 输入：A@2 的金额，B@4 的 amount/count/正常空 average，根值为两金额之和。
     * 流程：构造 RAW 结果集合、构造派生结果、清空输入集合，再做 JSON 往返。
     * 预期：根模式为空；来源的真实值、字段类型、修订、计算时间和覆盖均保留，集合不可修改。
     */
    @Test
    void testFullSourceValuesAndTypesSurviveMixedResultJson() {
        BigDecimal amount = new BigDecimal("12345678901234567890.12345678901234567890");
        MetricResult realtime = leaf("A", 2, "customer", START, ZONE,
                WindMetricsValue.of("A", MetricValueType.DECIMAL, amount));
        MetricResult snapshot = new MetricResult("B", 4, MetricQueryMode.SNAPSHOT, MetricValueShape.FIELD_SET,
                null, Map.of("amount", new MetricFieldValue(WindMetricsValue.of("amount", MetricValueType.DECIMAL,
                        new BigDecimal("0.00000000000000000001"))),
                        "count", new MetricFieldValue(WindMetricsValue.of("count", MetricValueType.LONG, 3)),
                        "average", new MetricFieldValue(WindMetricsValue.of("average", MetricValueType.DECIMAL, null))),
                "customer", START, START.plusDays(1), START.plusDays(1).plusSeconds(2), ZONE,
                MetricSnapshotGranularity.DAY, START, START.plusDays(1), null, List.of(), List.of());
        List<MetricResult> sources = new ArrayList<>(List.of(realtime, snapshot));
        MetricResult result = new MetricResult("TOTAL", 3, null, MetricValueShape.SCALAR,
                WindMetricsValue.of("TOTAL", MetricValueType.DECIMAL,
                        amount.add((BigDecimal) snapshot.fields().get("amount").value().getValue())),
                Map.of(), "customer", START, START.plusDays(1), START.plusDays(1).plusSeconds(3), ZONE,
                null, null, null, null, List.of(), sources);
        sources.clear();

        MetricResult restored = WindJson.parseObject(WindJson.toJsonString(result), MetricResult.class);

        assertEquals(result, restored);
        assertNull(restored.executionMode());
        assertEquals(List.of(realtime, snapshot), restored.sources());
        assertEquals(amount, restored.sources().getFirst().value().getValue());
        assertEquals(MetricValueType.DECIMAL,
                restored.sources().get(1).fields().get("average").value().getValueType());
        assertNull(restored.sources().get(1).fields().get("average").value().getValue());
        assertThrows(UnsupportedOperationException.class, () -> restored.sources().clear());
        assertTrue(restored.sources().stream().allMatch(source -> source.sources().isEmpty()));
    }

    /**
     * 场景：来源列表不可混入另一主体、窗口或时区的结果。
     * 输入：分别只改变 subject、window、zone 的来源。
     * 流程：将来源加入 customer 的固定查询结果。
     * 预期：在 sources 对应位置拒绝上下文不一致，不能靠数值相同蒙混过关。
     */
    @ParameterizedTest
    @ValueSource(strings = {"subject", "window", "zone"})
    void testRejectSourceFromDifferentQueryContext(String changed) {
        MetricResult source = leaf("RAW", 1, changed.equals("subject") ? "other" : "customer",
                changed.equals("window") ? START.plusHours(1) : START,
                changed.equals("zone") ? ZoneId.of("UTC") : ZONE,
                WindMetricsValue.of("RAW", MetricValueType.LONG, 3L));

        MetricValidationException failure = assertThrows(MetricValidationException.class,
                () -> derived(List.of(source)));

        assertTrue(failure.fieldPath().startsWith("/sources/0"));
    }

    /**
     * 场景：多级派生复用扁平 RAW 结果，不把派生节点重新塞入来源。
     * 输入：RAW@1 作为中间 DERIVED 的来源，再尝试将该中间结果作为来源。
     * 流程：构造两层来源列表。
     * 预期：拒绝非叶子来源；宿主应交付按精确引用去重后的 RAW 集合。
     */
    @Test
    void testRejectNestedSourceTrees() {
        MetricResult raw = leaf("RAW", 1, "customer", START, ZONE,
                WindMetricsValue.of("RAW", MetricValueType.LONG, 3L));
        MetricResult intermediate = derived(List.of(raw));

        MetricValidationException failure = assertThrows(MetricValidationException.class,
                () -> derived(List.of(intermediate)));

        assertEquals("/sources/0/sources", failure.fieldPath());
    }

    /**
     * 场景：默认类型支持正常空值，但不能省略值对象或沿用旧裸值 JSON。
     * 输入：缺少值对象、采用默认类型的空值对象，以及旧裸值 JSON。
     * 流程：分别构造叶子结果或还原 JSON。
     * 预期：默认空值按 DECIMAL 往返；缺少值对象、显式空类型和旧裸值 JSON 均拒绝。
     */
    @Test
    void testDefaultEmptyValueAndRejectedAbsentValueOrOldBareJson() {
        assertThrows(MetricValidationException.class, () -> leaf("A", 1, "customer", START, ZONE, null));
        MetricResult empty = leaf("A", 1, "customer", START, ZONE, WindMetricsValue.of("A", null));
        MetricResult restored = WindJson.parseObject(WindJson.toJsonString(empty), MetricResult.class);
        assertEquals(MetricValueType.DECIMAL, restored.value().getValueType());
        assertNull(restored.value().getValue());
        assertThrows(MetricValidationException.class, () -> WindMetricsValue.of("A", null, null));
        String json = WindJson.toJsonString(leaf("A", 1, "customer", START, ZONE,
                WindMetricsValue.of("A", MetricValueType.LONG, 3L)));
        String oldJson = json.replace("{\"code\":\"A\",\"valueType\":\"LONG\",\"value\":3}", "3");
        assertFalse(json.equals(oldJson));
        assertThrows(JacksonException.class, () -> WindJson.parseObject(oldJson, MetricResult.class));
    }

    /**
     * 场景：JSON 属性顺序由外部消费者决定，不能影响类型归一化。
     * 输入：把高精度 value 放在 valueType 前面，另输入重复 value 属性。
     * 流程：还原单个字段值；再读取带重复属性的字段值。
     * 预期：精确 BigDecimal 可恢复，重复属性拒绝而非静默取最后一次值。
     */
    @Test
    void testTypedValueJsonIsOrderIndependentAndRejectsDuplicateFields() {
        String decimal = "12345678901234567890.12345678901234567890";
        String json = "{\"value\":{\"value\":" + decimal + ",\"code\":\"amount\",\"valueType\":\"DECIMAL\"}}";
        MetricFieldValue field = WindJson.parseObject(json, MetricFieldValue.class);

        assertEquals(new BigDecimal(decimal), field.value().getValue());
        assertEquals(MetricValueType.DECIMAL, field.value().getValueType());
        assertThrows(JacksonException.class,
                () -> WindJson.parseObject(json.replace("\"code\"", "\"value\":1,\"code\""), MetricFieldValue.class));
    }

    private static MetricResult leaf(String code, int revision, String subject, LocalDateTime start, ZoneId zone,
                                     WindMetricsValue<?> value) {
        return new MetricResult(code, revision, MetricQueryMode.REALTIME, MetricValueShape.SCALAR, value,
                Map.of(), subject, start, start.plusDays(1), START.plusDays(1), zone,
                null, null, null, null, List.of(), List.of());
    }

    private static MetricResult derived(List<MetricResult> sources) {
        return new MetricResult("DERIVED", 1, null, MetricValueShape.SCALAR,
                WindMetricsValue.of("DERIVED", MetricValueType.LONG, 3L), Map.of(),
                "customer", START, START.plusDays(1), START.plusDays(1), ZONE,
                null, null, null, null, List.of(), sources);
    }
}
