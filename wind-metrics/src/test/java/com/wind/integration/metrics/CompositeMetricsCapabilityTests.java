package com.wind.integration.metrics;

import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证嵌套组合的支持性、结果和错误传播，避免无条件支持抢占后续实现。
 *
 * @author wuxp
 * @since 2026-09-15
 */
class CompositeMetricsCapabilityTests {

    @Test
    void testNestedResolverDoesNotClaimUnsupportedValueTypes() {
        CompositeMetricsValueResolver text = new CompositeMetricsValueResolver(List.of(resolver(String.class, "text")));
        CompositeMetricsValueResolver composite = new CompositeMetricsValueResolver(List.of(text, resolver(Long.class, 12L)));
        assertEquals(12L, composite.resolve(1L, Long.class));
        assertEquals("text", composite.resolve(List.of(1L, 2L), String.class));
        assertFalse(new CompositeMetricsValueResolver(List.of()).supports(Object.class));
    }

    @Test
    void testSelectedResolverFailureIsNotReplacedWithAnotherResult() {
        IllegalStateException failure = new IllegalStateException("coverage missing");
        WindMetricsValueResolver<Object> failing = new WindMetricsValueResolver<>() {
            @Override
            public Object resolve(Collection<? extends Serializable> ids, Class<?> type) {
                throw failure;
            }

            @Override
            public boolean supports(Class<Object> type) {
                return true;
            }
        };
        CompositeMetricsValueResolver composite = new CompositeMetricsValueResolver(List.of(failing, resolver(Long.class, 12L)));
        assertSame(failure, assertThrows(IllegalStateException.class, () -> composite.resolve(1L, Long.class)));
    }

    @Test
    void testStatisticsCompositionRetainsAllMatchingExecutors() {
        List<String> results = new ArrayList<>();
        CompositeMetricsStatisticsExecutor nested = new CompositeMetricsStatisticsExecutor(List.of(executor("first", results)));
        CompositeMetricsStatisticsExecutor composite = new CompositeMetricsStatisticsExecutor(List.of(nested, executor("second", results)));
        assertTrue(composite.supports(String.class));
        assertFalse(composite.supports(Long.class));
        composite.execute("event");
        composite.execute(1L);
        assertEquals(List.of("first:event", "second:event"), results);
    }

    private static WindMetricsValueResolver<Object> resolver(Class<?> supported, Object result) {
        return new WindMetricsValueResolver<>() {
            @Override
            public Object resolve(Collection<? extends Serializable> ids, Class<?> type) {
                return result;
            }

            @Override
            public boolean supports(Class<Object> type) {
                return supported.equals(type);
            }
        };
    }

    private static WindMetricsStatisticsExecutor<Object> executor(String name, List<String> results) {
        return new WindMetricsStatisticsExecutor<>() {
            @Override
            public void execute(Object businessObject, Map<String, Object> variables) {
                results.add(name + ":" + businessObject);
            }

            @Override
            public boolean supports(Class<?> type) {
                return type == String.class;
            }
        };
    }
}
