package com.wind.integration.metrics;

import java.util.Collections;
import java.util.Map;

/**
 * 指标计算表达式，一般用于增量统计。
 *
 * @author wuxp
 * @date 2025-06-17 14:25
 **/
public interface WindMetricsCalculateExpression<M> {

    /**
     * 指标计算规则
     *
     * @param o         待计算的对象
     * @param variables 计算变量
     * @return 计算结果
     */
    WindMetricsValue<M> eval(Object o, Map<String, Object> variables);

    default WindMetricsValue<M> eval(Object o) {
        return eval(o, Collections.emptyMap());
    }
}
