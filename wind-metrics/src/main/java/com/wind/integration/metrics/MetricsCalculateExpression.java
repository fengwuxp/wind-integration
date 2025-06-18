package com.wind.integration.metrics;

import java.util.Collections;
import java.util.Map;

/**
 * 指标计算表达式
 *
 * @author wuxp
 * @date 2025-06-17 14:25
 **/
public interface MetricsCalculateExpression<M> {

    /**
     * 指标计算规则
     *
     * @param o         待计算的对象
     * @param variables 计算变量
     * @return 计算结果
     */
    WindMetricsObject<M> eval(Object o, Map<String, Object> variables);

    default WindMetricsObject<M> eval(Object o) {
        return eval(o, Collections.emptyMap());
    }
}
