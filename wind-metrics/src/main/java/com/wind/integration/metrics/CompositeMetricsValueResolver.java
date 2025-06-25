package com.wind.integration.metrics;

import com.wind.common.exception.AssertUtils;
import com.wind.common.exception.BaseException;
import lombok.AllArgsConstructor;

import java.io.Serializable;
import java.util.Collection;

/**
 * 组合历史指标解析器
 *
 * @author wuxp
 * @date 2025-06-25 09:39
 **/
@AllArgsConstructor
public class CompositeMetricsValueResolver implements WindMetricsValueResolver<Object> {

    private final Collection<WindMetricsValueResolver<?>> delegates;

    @Override
    @SuppressWarnings({"unchecked"})
    public Object resolve(Collection<? extends Serializable> dimensionsIds, Class<?> metricsValueType) {
        return getDelegate(metricsValueType).resolve(dimensionsIds, metricsValueType);
    }

    @Override
    @SuppressWarnings({"unchecked"})
    public Object resolve(Serializable dimensionsId, Class<?> metricsValueType) {
        return getDelegate(metricsValueType).resolve(dimensionsId, metricsValueType);
    }

    @Override
    public boolean supports(Class<Object> metricsValueType) {
        return true;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private WindMetricsValueResolver getDelegate(Class<?> metricsValueType) {
        AssertUtils.notNull(metricsValueType, "argument metricsValueType must not null");
        for (WindMetricsValueResolver delegate : delegates) {
            if (delegate.supports(metricsValueType)) {
                return delegate;
            }
        }
        throw BaseException.common("not found metricsValueType = " + metricsValueType.getName() + " WindHistoricalMetricsResolver");
    }
}
