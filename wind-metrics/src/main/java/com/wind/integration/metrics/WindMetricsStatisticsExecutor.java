package com.wind.integration.metrics;

import java.util.Collections;
import java.util.Map;

/**
 * 根据业务对象，进行指标统计并保存指标数据到数据库（使用宽表）或缓存中
 * 根据 {@link WindMetricsCalculateExpression#eval} 计算的结果，对指标增量或全量的更新
 *
 * @author wuxp
 * @date 2025-06-17 14:46
 **/
public interface WindMetricsStatisticsExecutor<E> {

    /**
     * 根据业务对象统计、保存指标
     *
     * @param businessObject 业务对象
     * @param variables      上下文变量
     */
    void execute(E businessObject, Map<String, Object> variables);

    /**
     * 根据业务对象统计、保存指标
     *
     * @param businessObject 业务对象
     */
    default void execute(E businessObject) {
        execute(businessObject, Collections.emptyMap());
    }

    /**
     * 判断是否支持
     *
     * @param businessObject 业务对象
     * @return true:支持
     */
    boolean supports(E businessObject);
}
