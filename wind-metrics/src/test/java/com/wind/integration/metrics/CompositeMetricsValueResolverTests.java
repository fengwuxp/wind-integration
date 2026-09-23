package com.wind.integration.metrics;

import com.wind.common.exception.BaseException;
import com.wind.integration.metrics.query.MetricQuery;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 原生条件路由、完整业务对象返回及旧 Resolver 调用兼容；不模拟数据库实现。 */
class CompositeMetricsValueResolverTests {

    private static final LocalDateTime START = LocalDateTime.of(2026, 9, 1, 0, 0);

    private static final MetricQuery QUERY = new MetricQuery("u-1", "USER", START, START.plusDays(1),
            Map.of("currency", "USD"), Map.of("settled", 1));

    /** 完整条件和完整对象直接穿过组合器，正常 NULL 字段及身份/版本/水位不被重建或丢弃。 */
    @Test
    void testNativeResolverReceivesOriginalConditionsAndReturnsCompleteObject() {
        UserMetrics stored = new UserMetrics("u-1", null, 7L, START.plusDays(1));
        CompositeMetricsValueResolver resolver = new CompositeMetricsValueResolver(List.of(nativeResolver(UserMetrics.class, query -> {
            assertSame(QUERY, query);
            return stored;
        })));

        UserMetrics result = resolver.resolve(QUERY, UserMetrics.class);

        assertSame(stored, result);
    }

    /** 嵌套组合保持原生条件入口，由业务实现校验固定 USER 维度并分别处理不同用户 ID。 */
    @Test
    void testNestedNativeResolverKeepsSubjectDimensionAndIdentity() {
        CompositeMetricsValueResolver nested = new CompositeMetricsValueResolver(List.of(nativeResolver(UserMetrics.class, query -> {
            if (!"USER".equals(query.subjectType())) {
                throw new IllegalArgumentException("User metrics require USER subjectType");
            }
            return new UserMetrics((String) query.subjectId(), BigDecimal.ONE, 1L, query.endTime());
        })));
        CompositeMetricsValueResolver resolver = new CompositeMetricsValueResolver(List.of(nested, nativeResolver(String.class, query -> "text")));
        MetricQuery second = new MetricQuery("u-2", "USER", START, START.plusDays(1), QUERY.dimensionValues(), QUERY.parameterValues());
        MetricQuery wrongDimension = new MetricQuery("card-1", "VCC", START, START.plusDays(1), QUERY.dimensionValues(), QUERY.parameterValues());

        assertEquals("u-1", resolver.resolve(QUERY, UserMetrics.class).subjectId());
        assertEquals("u-2", resolver.resolve(second, UserMetrics.class).subjectId());
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve(wrongDimension, UserMetrics.class));
    }

    /** 旧实现没有新能力时明确失败，不能自动把有时间/维度/参数的条件降成 ID 集合。 */
    @Test
    void testLegacyResolverIsNotAutomaticallyProjectedToIds() {
        WindMetricsValueResolver<Object> legacy = new WindMetricsValueResolver<>() {
            @Override
            public Object resolve(Collection<? extends Serializable> ids, Class<?> resultType) {
                throw new AssertionError("MetricQuery must not be projected to legacy IDs");
            }

            @Override
            public boolean supports(Class<Object> resultType) {
                return resultType.equals(UserMetrics.class);
            }
        };
        CompositeMetricsValueResolver resolver = new CompositeMetricsValueResolver(List.of(legacy));

        assertThrows(UnsupportedOperationException.class, () -> resolver.resolve(QUERY, UserMetrics.class));
    }

    /** 保留 nobe 单卡专用入口：单 ID 直接委托单项方法，集合方法仍可明确拒绝。 */
    @Test
    void testLegacySingleAndCollectionDispatchRemainDistinct() {
        WindMetricsValueResolver<Object> singleOnly = new WindMetricsValueResolver<>() {
            @Override
            public Object resolve(Serializable id, Class<?> resultType) {
                return id;
            }

            @Override
            public Object resolve(Collection<? extends Serializable> ids, Class<?> resultType) {
                throw new UnsupportedOperationException("Single subject only");
            }

            @Override
            public boolean supports(Class<Object> resultType) {
                return resultType.equals(Long.class);
            }
        };
        CompositeMetricsValueResolver resolver = new CompositeMetricsValueResolver(List.of(singleOnly));

        assertEquals(17L, resolver.resolve(17L, Long.class));
        assertThrows(UnsupportedOperationException.class, () -> resolver.resolve(List.of(17L), Long.class));
    }

    /** 新入口拒绝重复视图注册，旧 ID 入口继续选择第一个匹配，兼容原有装配顺序。 */
    @Test
    void testNativeAmbiguityFailsWhileLegacyFirstMatchRemains() {
        CompositeMetricsValueResolver resolver = new CompositeMetricsValueResolver(List.of(legacyText("first"), legacyText("second")));

        assertEquals("first", resolver.resolve(1L, String.class));
        assertEquals("first", resolver.resolve(List.of(1L, 2L), String.class));
        assertThrows(BaseException.class, () -> resolver.resolve(QUERY, String.class));
    }

    /** 未注册的业务视图不能静默变成空对象或任意 Map。 */
    @Test
    void testNativeMissingViewFails() {
        CompositeMetricsValueResolver resolver = new CompositeMetricsValueResolver(List.of());

        assertThrows(BaseException.class, () -> resolver.resolve(QUERY, UserMetrics.class));
    }

    /** 已选业务实现失败时原样传播，不能被空对象或另一个视图的结果掩盖。 */
    @Test
    void testNativeFailurePropagatesUnchanged() {
        IllegalStateException failure = new IllegalStateException("Snapshot coverage unavailable");
        CompositeMetricsValueResolver resolver = new CompositeMetricsValueResolver(List.of(nativeResolver(UserMetrics.class, query -> {
            throw failure;
        })));

        assertSame(failure, assertThrows(IllegalStateException.class, () -> resolver.resolve(QUERY, UserMetrics.class)));
    }

    /** 即使调用者只接收 Object，错误实现也不能返回与所请求视图类型不符的对象。 */
    @Test
    void testNativeResultTypeIsCheckedAtCompositionBoundary() {
        WindMetricsValueResolver<Object> wrongType = new WindMetricsValueResolver<>() {
            @Override
            public Object resolve(Collection<? extends Serializable> ids, Class<?> resultType) {
                throw new AssertionError("Legacy method must not be used");
            }

            @Override
            @SuppressWarnings("unchecked")
            public <T> T resolve(MetricQuery query, Class<T> resultType) {
                return (T) "wrong result type";
            }

            @Override
            public boolean supports(Class<Object> resultType) {
                return resultType.equals(UserMetrics.class);
            }
        };
        CompositeMetricsValueResolver resolver = new CompositeMetricsValueResolver(List.of(wrongType));

        assertThrows(ClassCastException.class, () -> {
            Object ignored = resolver.resolve(QUERY, UserMetrics.class);
        });
    }

    /** 新入口要求明确非空查询、主体类型和结果类型，在分派前拒绝非法输入。 */
    @Test
    void testNativeArgumentsAreCheckedBeforeDispatch() {
        CompositeMetricsValueResolver resolver = new CompositeMetricsValueResolver(List.of(nativeResolver(UserMetrics.class, query -> {
            throw new AssertionError("Invalid input must not be dispatched");
        })));
        MetricQuery missingType = new MetricQuery("u-1", null, START, START.plusDays(1), Map.of(), Map.of());
        MetricQuery blankType = new MetricQuery("u-1", " ", START, START.plusDays(1), Map.of(), Map.of());

        assertThrows(RuntimeException.class, () -> resolver.resolve((MetricQuery) null, UserMetrics.class));
        assertThrows(RuntimeException.class, () -> resolver.resolve(QUERY, null));
        assertThrows(RuntimeException.class, () -> resolver.resolve(missingType, UserMetrics.class));
        assertThrows(RuntimeException.class, () -> resolver.resolve(blankType, UserMetrics.class));
    }

    /** 允许字段正常 NULL，但业务视图对象本身缺失时不能伪装成成功。 */
    @Test
    void testNativeNullObjectFails() {
        CompositeMetricsValueResolver resolver = new CompositeMetricsValueResolver(List.of(nativeResolver(UserMetrics.class, query -> null)));

        assertThrows(RuntimeException.class, () -> resolver.resolve(QUERY, UserMetrics.class));
    }

    /** 原生业务端口替身仅用于组合器合同测试，不宣称真实存储或业务聚合已接入。 */
    private static WindMetricsValueResolver<Object> nativeResolver(Class<?> viewType, Function<MetricQuery, ?> resolve) {
        return new WindMetricsValueResolver<>() {
            @Override
            public Object resolve(Collection<? extends Serializable> ids, Class<?> resultType) {
                throw new AssertionError("Native query must not use the legacy method");
            }

            @Override
            public <T> T resolve(MetricQuery query, Class<T> resultType) {
                return resultType.cast(resolve.apply(query));
            }

            @Override
            public boolean supports(Class<Object> resultType) {
                return resultType.equals(viewType);
            }
        };
    }

    private static WindMetricsValueResolver<Object> legacyText(String text) {
        return new WindMetricsValueResolver<>() {
            @Override
            public Object resolve(Collection<? extends Serializable> ids, Class<?> resultType) {
                return text;
            }

            @Override
            public boolean supports(Class<Object> resultType) {
                return resultType.equals(String.class);
            }
        };
    }

    private record UserMetrics(String subjectId, BigDecimal amount, Long version, LocalDateTime endTime) {
    }
}
