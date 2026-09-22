package com.wind.integration.metrics.query;

import com.wind.integration.metrics.enums.MetricQueryMode;
import com.wind.integration.metrics.enums.MetricValueShape;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 默认批量查询的顺序、重复项、共同条件及失败边界。
 *
 * @author wuxp
 * @since 2026-09-22
 */
class MetricValueQueryServiceTests {

    private static final LocalDateTime START = LocalDateTime.of(2026, 9, 1, 0, 0);

    private static final MetricQuery QUERY = new MetricQuery("customer-1", START, START.plusDays(1), Map.of(), Map.of("limit", 3));

    /** 重复指标分别执行单查，顺序不变，带业务参数的条件原样透传。 */
    @Test
    void testBatchPreservesOrderDuplicatesAndParameters() {
        List<String> calls = new ArrayList<>();
        MetricValueQueryService service = singleQuery((code, query) -> {
            calls.add(code);
            assertSame(QUERY, query);
            return result(code, calls.size());
        });

        List<MetricResult> results = service.batchQuery(List.of("second", "first", "second"), QUERY);

        assertEquals(List.of("second", "first", "second"), calls);
        assertEquals(List.of("second", "first", "second"), results.stream().map(MetricResult::metricCode).toList());
        assertEquals(List.of(1L, 2L, 3L), results.stream().map(MetricResult::getValue).toList());
    }

    /** 空批次返回空结果，不进入单查。 */
    @Test
    void testEmptyBatchDoesNotQuery() {
        MetricValueQueryService service = singleQuery((code, query) -> {
            throw new AssertionError("Empty batch must not query a metric");
        });

        assertTrue(service.batchQuery(List.of(), QUERY).isEmpty());
    }

    /** 第二项编码由其单查拒绝；第一项已经执行，第三项不执行，异常原样传播。 */
    @Test
    void testSingleQueryFailureStopsLaterItemsWithoutPreflight() {
        List<String> calls = new ArrayList<>();
        IllegalArgumentException failure = new IllegalArgumentException("Invalid metric code");
        MetricValueQueryService service = singleQuery((code, query) -> {
            calls.add(code);
            if (code.isBlank()) {
                throw failure;
            }
            return result(code, 1L);
        });

        assertSame(failure, assertThrows(IllegalArgumentException.class,
                () -> service.batchQuery(List.of("first", " ", "last"), QUERY)));
        assertEquals(List.of("first", " "), calls);
    }

    /** 仅提供单查结果以验证接口默认方法，不模拟查询服务实现。 */
    private static MetricValueQueryService singleQuery(BiFunction<String, MetricQuery, MetricResult> queryFunction) {
        return new MetricValueQueryService() {
            @Override
            public MetricResult query(String metricCode, MetricQuery query) {
                return queryFunction.apply(metricCode, query);
            }

            @Override
            public MetricResult query(String metricCode, Integer definitionRevision, MetricQuery query) {
                throw new AssertionError("Batch must query the effective definition");
            }
        };
    }

    private static MetricResult result(String code, long value) {
        return MetricResult.builder()
                .metricCode(code)
                .definitionRevision(1)
                .executionMode(MetricQueryMode.REALTIME)
                .valueShape(MetricValueShape.SCALAR)
                .value(value)
                .subjectId("customer-1")
                .startTime(START)
                .endTime(START.plusDays(1))
                .build();
    }
}
