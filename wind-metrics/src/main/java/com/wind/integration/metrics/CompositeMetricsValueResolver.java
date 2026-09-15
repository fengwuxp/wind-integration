package com.wind.integration.metrics;

import com.wind.common.exception.AssertUtils;
import com.wind.common.exception.BaseException;
import lombok.AllArgsConstructor;

import java.io.Serializable;
import java.util.Collection;

/**
 * 按委托顺序选择首个支持目标类型的指标值解析器。
 *
 * <p>支持性与实际委托一致，允许作为其他组合解析器的成员；执行异常直接传播，
 * 不以切换解析器的方式掩盖失败。</p>
 *
 * @author wuxp
 * @date 2025-06-25 09:39
 **/
@AllArgsConstructor
public class CompositeMetricsValueResolver implements WindMetricsValueResolver<Object> {

    private final Collection<WindMetricsValueResolver<?>> delegates;

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

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static boolean supportsType(WindMetricsValueResolver delegate, Class<?> valueType) {
        return delegate.supports(valueType);
    }
}
