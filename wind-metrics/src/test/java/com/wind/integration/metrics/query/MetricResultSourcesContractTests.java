package com.wind.integration.metrics.query;

import com.wind.integration.metrics.enums.MetricQueryMode;
import com.wind.integration.metrics.enums.MetricValueShape;
import com.wind.jackson.WindJson;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 组成结果通过 segmentValues 传输，时间关系由执行流程决定。
 *
 * @author wuxp
 * @since 2026-09-22
 */
class MetricResultSourcesContractTests {

    @Test
    void testSameMetricCanContributeDifferentRanges() {
        LocalDateTime cutover = LocalDateTime.of(2026, 6, 1, 0, 0);
        MetricResult history = part(MetricQueryMode.SNAPSHOT, null, cutover, 10);
        MetricResult recent = part(MetricQueryMode.REALTIME, cutover, null, 2);
        MetricResult result = MetricResult.builder().metricCode("TOTAL").definitionRevision(1)
                .executionMode(MetricQueryMode.SEGMENTED).valueShape(MetricValueShape.SCALAR).value(12)
                .segmentValues(List.of(history, recent)).build();
        MetricResult restored = WindJson.parseObject(WindJson.toJsonString(result), MetricResult.class);

        assertEquals(result, restored);
        assertEquals(2, restored.segmentValues().size());
        assertEquals(history.metricCode(), recent.metricCode());
        assertEquals(cutover, restored.segmentValues().getFirst().endTime());
        assertEquals(cutover, restored.segmentValues().getLast().startTime());
        assertNull(restored.segmentValues().getLast().endTime());
    }

    @Test
    void testNestedContributionsArePreservedWithoutOldSourceRestrictions() {
        MetricResult leaf = part(MetricQueryMode.REALTIME, null, null, 2);
        MetricResult middle = MetricResult.builder().metricCode("SUBTOTAL").definitionRevision(2)
                .valueShape(MetricValueShape.SCALAR).value(2).segmentValues(List.of(leaf)).build();
        MetricResult root = MetricResult.builder().metricCode("TOTAL").definitionRevision(3)
                .valueShape(MetricValueShape.SCALAR).value(4).segmentValues(List.of(middle, leaf)).build();
        String encoded = WindJson.toJsonString(root);
        MetricResult restored = WindJson.parseObject(encoded, MetricResult.class);

        assertEquals(root, restored);
        assertNull(restored.executionMode());
        assertEquals("ITEM", restored.segmentValues().getFirst().segmentValues().getFirst().getCode());
        Map<?, ?> json = WindJson.parseObject(encoded, Map.class);
        assertTrue(json.containsKey("segmentValues"));
        assertFalse(json.containsKey("sources"));
        assertFalse(json.containsKey("segments"));
    }

    private static MetricResult part(MetricQueryMode mode, LocalDateTime start, LocalDateTime end, int value) {
        return MetricResult.builder().metricCode("ITEM").definitionRevision(1).executionMode(mode)
                .valueShape(MetricValueShape.SCALAR).value(value).startTime(start).endTime(end).build();
    }
}
