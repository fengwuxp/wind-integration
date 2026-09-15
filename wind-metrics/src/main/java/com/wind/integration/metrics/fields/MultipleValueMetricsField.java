package com.wind.integration.metrics.fields;

import com.wind.integration.metrics.WindMetricsEvaluator;
import com.wind.integration.metrics.WindMetricsValueSet;
import com.wind.jackson.WindJson;

import jakarta.validation.constraints.NotNull;
import java.util.Map;

/**
 * 可按条件重新求值的多字段指标，复用公共只读多字段值能力。
 *
 * <p>仅需读取固定结果的实现使用 {@link WindMetricsValueSet}；本接口额外承担条件求值。
 * Map 或业务对象到字段映射的转换保留在本接口，不要求公共值能力依赖 JSON。</p>
 *
 * @author wuxp
 * @date 2025-06-17 14:16
 **/
public interface MultipleValueMetricsField<M> extends WindMetricsValueSet<M>, WindMetricsEvaluator<M> {

    /**
     * 获取所有子指标的键值对表示，其中 key 通常为子指标的 name，value 为对应数值。
     * 与 {@link #getValue()} 的列表顺序无关。
     *
     * @return 子指标的键值对表示
     */
    @NotNull
    @Override
    @SuppressWarnings("unchecked")
    default Map<String, Object> asValues() {
        M value = getValue();
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return (Map<String, Object>) WindJson.convertValue(value, Map.class);
    }

}
