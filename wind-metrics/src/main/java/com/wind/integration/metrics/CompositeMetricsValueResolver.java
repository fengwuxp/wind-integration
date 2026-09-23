package com.wind.integration.metrics;

import com.wind.common.exception.AssertUtils;
import com.wind.common.exception.BaseException;
import com.wind.integration.metrics.query.MetricQuery;
import lombok.AllArgsConstructor;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

/**
 * 按业务视图类型选择指标解析器，保留历史入口的首个匹配语义。
 *
 * <p>完整条件入口要求目标类型只有一个匹配委托，避免业务视图被重复注册后随顺序变化。
 * 条件原样交给该委托，主体维度与业务口径由其校验。历史单项/集合仍按原顺序选择首个匹配。
 * 支持性与实际委托一致，允许作为其他组合解析器的成员；执行异常直接传播，
 * 不以切换解析器的方式掩盖失败。</p>
 *
 * @author wuxp
 * @date 2025-06-25 09:39
 **/
@AllArgsConstructor
public class CompositeMetricsValueResolver implements WindMetricsValueResolver<Object> {

    private final Collection<WindMetricsValueResolver<?>> delegates;

    @Override
    public <T> T resolve(MetricQuery query, Class<T> resultType) {
        AssertUtils.notNull(query, "argument query must not null");
        AssertUtils.notNull(resultType, "argument resultType must not null");
        AssertUtils.isTrue(query.subjectType() != null && !query.subjectType().isBlank(), "argument query.subjectType must not blank");
        WindMetricsValueResolver<?> delegate = getUniqueDelegate(resultType);
        T result = delegate.resolve(query, resultType);
        AssertUtils.notNull(result, "Resolved business metric object must not null");
        return resultType.cast(result);
    }

    @Override
    public Object resolve(Collection<? extends Serializable> dimensionsIds, Class<?> metricsValueType) {
        return getDelegate(metricsValueType).resolve(dimensionsIds, metricsValueType);
    }

    @Override
    public Object resolve(Serializable dimensionsId, Class<?> metricsValueType) {
        return getDelegate(metricsValueType).resolve(dimensionsId, metricsValueType);
    }

    @Override
    public boolean supports(Class<Object> metricsValueType) {
        return delegates.stream().anyMatch(delegate -> supportsType(delegate, metricsValueType));
    }

    private WindMetricsValueResolver<?> getDelegate(Class<?> metricsValueType) {
        AssertUtils.notNull(metricsValueType, "argument metricsValueType must not null");
        for (WindMetricsValueResolver<?> delegate : delegates) {
            if (supportsType(delegate, metricsValueType)) {
                return delegate;
            }
        }
        throw BaseException.common("not found metricsValueType = " + metricsValueType.getName() + " WindMetricsValueResolver");
    }

    private WindMetricsValueResolver<?> getUniqueDelegate(Class<?> resultType) {
        List<WindMetricsValueResolver<?>> matches = delegates.stream().filter(delegate -> supportsType(delegate, resultType)).toList();
        if (matches.size() != 1) {
            throw BaseException.common("Expected one WindMetricsValueResolver for " + resultType.getName() + ", found " + matches.size());
        }
        return matches.getFirst();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static boolean supportsType(WindMetricsValueResolver delegate, Class<?> valueType) {
        return delegate.supports(valueType);
    }
}
