package com.wind.integration.metrics;

import java.util.Collections;
import java.util.Map;

/**
 * 指标统计规则
 * 根据 {@link MetricsCalculateExpression#eval} 计算的结果，对指标增量或全量的更新
 *
 * @author wuxp
 * @date 2025-06-17 14:46
 **/
public interface MetricsStatisticsRules {

    /**
     * 根据业务对象统计指标
     *
     * @param businessObject 业务对象
     * @param variables      变量
     */
    void statistics(Object businessObject, Map<String, Object> variables);

    default void statistics(Object businessObject) {
        statistics(businessObject, Collections.emptyMap());
    }
}
