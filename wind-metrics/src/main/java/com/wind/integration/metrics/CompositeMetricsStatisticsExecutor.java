package com.wind.integration.metrics;

import com.wind.common.exception.AssertUtils;
import lombok.AllArgsConstructor;

import java.util.Collection;
import java.util.Map;

/**
 * 顺序执行所有支持业务对象类型的统计器，异常直接传播。
 *
 * <p>无匹配统计器时不执行；本组合不提供跨委托事务或自动重试，
 * 业务统计实现及调用方负责相应一致性。</p>
 *
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
        return delegates.stream().anyMatch(delegate -> delegate.supports(businessObjectType));
    }
}
