package com.wind.integration.metrics;

import com.wind.integration.metrics.query.MetricQuery;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 按条件求值、延迟读取与旧求值器适配的使用合同。 */
@SuppressWarnings("deprecation")
class WindMetricsValueEvaluatorTests {

    private static final MetricQuery QUERY = new MetricQuery("customer-1", "CUSTOMER", null, null, Map.of(), Map.of());

    /** 原生 lambda 接收原条件，维度与同名参数保持独立。 */
    @Test
    void testNativeEvaluatorReceivesCompleteQuery() {
        LocalDateTime start = LocalDateTime.of(2026, 9, 1, 0, 0);
        MetricQuery query = new MetricQuery("customer-1", start, start.plusDays(1),
                Map.of("currency", "USD"), Map.of("currency", "EUR"));
        WindMetricsValueEvaluator<List<Object>> evaluator = input -> {
            assertSame(query, input);
            return List.of(input.subjectId(), input.startTime(), input.endTime(),
                    input.dimensionValues().get("currency"), input.parameterValues().get("currency"));
        };

        assertEquals(List.of("customer-1", start, start.plusDays(1), "USD", "EUR"), evaluator.evaluate(query));
    }

    /** 同一 CUSTOMER 主维度内按本次主体标识读取，不切换主维度或跨主体复用结果。 */
    @Test
    void testSameEvaluatorReadsDifferentSubjectIdsWithinOneDimension() {
        Map<String, BigDecimal> amounts = Map.of("customer-1", new BigDecimal("12.50"), "customer-2", new BigDecimal("20.75"));
        WindMetricsValueEvaluator<BigDecimal> evaluator = query -> {
            if (!"CUSTOMER".equals(query.subjectType())) {
                throw new IllegalArgumentException("Metric definition requires CUSTOMER dimension");
            }
            return amounts.get(query.subjectId());
        };
        MetricQuery second = new MetricQuery("customer-2", "CUSTOMER", null, null, Map.of(), Map.of());

        assertEquals(new BigDecimal("12.50"), evaluator.evaluate(QUERY));
        assertEquals(new BigDecimal("20.75"), evaluator.evaluate(second));
        assertEquals(new BigDecimal("12.50"), evaluator.evaluate(QUERY));
        MetricQuery differentDimension = new MetricQuery("merchant-1", "MERCHANT", null, null, Map.of(), Map.of());
        assertThrows(IllegalArgumentException.class, () -> evaluator.evaluate(differentDimension));
    }

    /** 值对象创建时不求值；每次读取重新求值，固定值对象保留先前取得的结果。 */
    @Test
    void testValueCanDeferEvaluationAndReadAgain() {
        AtomicReference<BigDecimal> amount = new AtomicReference<>(new BigDecimal("12.50"));
        AtomicInteger evaluations = new AtomicInteger();
        WindMetricsValueEvaluator<BigDecimal> evaluator = query -> {
            evaluations.incrementAndGet();
            return amount.get();
        };
        WindMetricsValue<BigDecimal> value = new WindMetricsValue<>() {
            @Override
            public String getName() {
                return "amount";
            }

            @Override
            public BigDecimal getValue() {
                return evaluator.evaluate(QUERY);
            }
        };

        assertEquals(0, evaluations.get());
        WindMetricsValue<BigDecimal> fixed = WindMetricsValue.of("amount", value.getValue());
        assertEquals(new BigDecimal("12.50"), fixed.getValue());
        assertEquals(1, evaluations.get());
        amount.set(new BigDecimal("20.75"));
        assertEquals(new BigDecimal("20.75"), value.getValue());
        assertEquals(new BigDecimal("12.50"), fixed.getValue());
        assertEquals(2, evaluations.get());
    }

    /** 非空条件允许得到正常空值；求值异常原样传播，不转换为正常空值。 */
    @Test
    void testNullAndFailureFollowEvaluatorContract() {
        WindMetricsValueEvaluator<BigDecimal> empty = query -> {
            assertSame(QUERY, query);
            return null;
        };
        IllegalStateException failure = new IllegalStateException("Metric source unavailable");
        WindMetricsValueEvaluator<BigDecimal> unavailable = query -> {
            throw failure;
        };

        assertNull(empty.evaluate(QUERY));
        assertSame(failure, assertThrows(IllegalStateException.class, () -> unavailable.evaluate(QUERY)));
    }

    /** 非空条件显式转换后复用旧实现，保留参数，不丢弃旧条件无法表达的维度。 */
    @Test
    void testLegacyAdapterPreservesValuesAndRejectsLossyConditions() {
        WindMetricsEvaluator<Object> legacy = query -> {
            return query.getQueryVariables().get("amount");
        };
        WindMetricsValueEvaluator<Object> evaluator = query -> legacy.evaluate(WindMetricsAggregationQuery.fromQuery(query));
        BigDecimal amount = new BigDecimal("12.5000");
        MetricQuery query = new MetricQuery("customer-1", null, null, Map.of(), Map.of("amount", amount));

        assertSame(amount, evaluator.evaluate(query));
        MetricQuery dimensioned = new MetricQuery("customer-1", null, null, Map.of("currency", "USD"), Map.of());
        assertThrows(IllegalArgumentException.class, () -> evaluator.evaluate(dimensioned));
    }
}
