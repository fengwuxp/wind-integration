package com.wind.integration.metrics.fields;

import com.wind.integration.metrics.WindMetricsObject;

import java.util.List;

/**
 * 聚合了多个指标的指标字段
 *
 * @author wuxp
 * @date 2025-06-17 14:16
 **/
public interface MultipleMetricsFields<M extends Number> extends WindMetricsObject<List<SingleMetricsField<M>>> {

    void setFields(List<SingleMetricsField<M>> fields);
}
