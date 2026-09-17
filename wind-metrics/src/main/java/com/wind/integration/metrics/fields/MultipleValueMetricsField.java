package com.wind.integration.metrics.fields;

import com.wind.integration.metrics.WindMetricsEvaluator;
import com.wind.integration.metrics.WindMetricsValue;
import com.wind.integration.metrics.WindStructuredMetricsValue;
import com.wind.jackson.WindJson;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 历史多字段组合接口，同时声明读取、条件求值和对象转换。
 *
 * <p>读取型消费者使用 {@link WindStructuredMetricsValue}，重新求值使用 {@link WindMetricsEvaluator}。
 * Map 或业务对象到字段映射的转换由具体实现承担；本接口的默认转换仅保留历史兼容，
 * 不要求公共值能力依赖 JSON。</p>
 *
 * @author wuxp
 * @date 2025-06-17 14:16
 * @deprecated 按实际能力依赖多字段值或求值接口；公共消费者迁移完成前保留历史签名与默认转换。
 **/
@Deprecated(since = "4.0.0", forRemoval = false)
public interface MultipleValueMetricsField<M> extends WindMetricsValue<M>, WindMetricsEvaluator<M> {

    /**
     * 获取所有子指标
     *
     * @return 子指标
     */
    List<WindMetricsValue<Object>> getMetricsFields();

    /**
     * 将整个指标的值转换为字段映射，键为所属指标内的输出字段名，值为实际字段值。
     * 字段名称与 {@link #getName()} 返回的指标名称属于不同层级。
     *
     * @return 字段名称与实际值
     */
    @NotNull
    @SuppressWarnings("unchecked")
    default Map<String, Object> asValues() {
        {
            M value = getValue();
            if (value instanceof Map) {
                return (Map<String, Object>) value;
            }
            return (Map<String, Object>) WindJson.convertValue(value, Map.class);
        }
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
