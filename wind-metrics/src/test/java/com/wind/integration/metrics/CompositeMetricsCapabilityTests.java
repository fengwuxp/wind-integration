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

    /**
     * 场景：嵌套解析器按结果类型选择真正支持的实现。
     * 输入：内层仅支持 String，外层另有返回12的 Long 解析器。
     * 流程：分别解析 Long、String，并检查空组合。
     * 预期：得到12及 text；空组合不宣称支持 Object。
     */
    @Test
    void testNestedResolverDoesNotClaimUnsupportedValueTypes() {
        CompositeMetricsValueResolver text = new CompositeMetricsValueResolver(List.of(resolver(String.class, "text")));
        CompositeMetricsValueResolver composite = new CompositeMetricsValueResolver(List.of(text, resolver(Long.class, 12L)));
        assertEquals(12L, composite.resolve(1L, Long.class));
        assertEquals("text", composite.resolve(List.of(1L, 2L), String.class));
        assertFalse(new CompositeMetricsValueResolver(List.of()).supports(Object.class));
    }

    /**
     * 场景：已选实现失败时不能悄悄切换为其他结果。
     * 输入：首个解析器声明支持但抛 coverage missing，后续可返回12。
     * 流程：通过组合解析 Long。
     * 预期：原异常按同一对象传播，不返回后续实现的12。
     */
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

    /**
     * 场景：统计组合需要执行所有匹配项。
     * 输入：嵌套 first 与外层 second 均支持 String，不支持 Long。
     * 流程：执行字符串 event 后再执行1L。
     * 预期：记录 first:event、second:event 两项，Long 不产生记录。
     */
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
