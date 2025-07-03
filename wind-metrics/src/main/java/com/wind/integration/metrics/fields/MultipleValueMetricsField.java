package com.wind.integration.metrics.fields;

import com.alibaba.fastjson2.JSON;
import com.wind.integration.metrics.WindMetricsEvaluator;
import com.wind.integration.metrics.WindMetricsValue;

import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 聚合了多个值的指标字段
 *
 * @author wuxp
 * @date 2025-06-17 14:16
 **/
public interface MultipleValueMetricsField<M> extends WindMetricsValue<M> , WindMetricsEvaluator<M> {

    /**
     * 获取所有子指标
     *
     * @return 子指标
     */
    List<WindMetricsValue<Object>> getMetricsFields();

    /**
     * 获取所有子指标的键值对表示，其中 key 通常为子指标的 name，value 为对应数值。
     * 与 {@link #getValue()} 的列表顺序无关。
     *
     * @return 子指标的键值对表示
     */
    @NotNull
    @SuppressWarnings("unchecked")
    default Map<String, Object> asValues() {
        M value = getValue();
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return (Map<String, Object>) JSON.toJSON(value);
    }

    /**
     * 通过名称获取子指标
     *
     * @param name 子指标名称
     * @return 子指标值
     */
    @SuppressWarnings("unchecked")
    default <V> Optional<WindMetricsValue<V>> findByName(String name) {
        return getMetricsFields()
                .stream()
                .filter(v -> v.getName().equals(name))
                .findFirst()
                .map(value -> (WindMetricsValue<V>) value);
    }
}
