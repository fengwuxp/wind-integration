package com.wind.integration.metrics;

import com.wind.common.exception.AssertUtils;
import lombok.AllArgsConstructor;

import java.util.Collection;
import java.util.Map;

/**
 * @author wuxp
 * @date 2025-06-25 09:57
 **/
@AllArgsConstructor
public class CompositeMetricsStatisticsExecutor implements WindMetricsStatisticsExecutor<Object> {

    private final Collection<WindMetricsStatisticsExecutor<?>> delegates;

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void execute(Object businessObject, Map<String, Object> variables) {
        AssertUtils.notNull(businessObject, "argument businessObject must not null");
        for (WindMetricsStatisticsExecutor delegate : delegates) {
            if (delegate.supports(businessObject.getClass())) {
                delegate.execute(businessObject, variables);
            }
        }
    }

    @Override
    public boolean supports(Class<?> businessObjectType) {
        return true;
    }
}
