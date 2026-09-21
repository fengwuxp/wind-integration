package com.wind.integration.metrics.query;

import com.wind.integration.metrics.WindMetricsValue;
import com.wind.integration.metrics.enums.MetricValueType;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;

/**
 * 多字段指标结果中的单个字段值。
 *
 * <p>字段 Map 的 key 是字段身份；所属结果会绑定值对象的 code。值类型由 WindMetricsValue 持有。
 * TIMESTAMP payload 使用 LocalDateTime，按所属结果的 timeZone 解释。</p>
 *
 * @param value     字段具名值，构造后必不为空；正常空结果由其 payload 为 null 表示
 * @author wuxp
 * @date 2026-07-21 17:51
 */
@Schema(description = "多字段指标结果中的单个字段值")
public record MetricFieldValue(
        @NonNull
        @JsonSerialize(using = MetricValueJsonSerializer.class)
        @JsonDeserialize(using = MetricValueJsonDeserializer.class)
        @Schema(description = "字段具名值，包含类型及可空 payload") WindMetricsValue<?> value) {

    public MetricFieldValue {
        value = MetricQueryValueSupport.normalizeMetricValue(value == null ? "value" : value.getCode(), value);
    }

    /**
     * 兼容历史 Number 构造调用的源码；新调用传入 WindMetricsValue。升级 jar 后需重新编译消费者。
     * 接受已计算 payload 或 WindMetricsValue；类型只保存在 value 中。
     */
    public MetricFieldValue(MetricValueType valueType, @Nullable Object legacyValue) {
        this(WindMetricsValue.of(legacyValue instanceof WindMetricsValue<?> named ? named.getCode() : "value",
                valueType, legacyValue instanceof WindMetricsValue<?> named ? named.getValue() : legacyValue));
    }

    /**
     * 将字段值绑定到所属字段名；字段 Map 的 key 才是稳定字段身份。
     */
    MetricFieldValue withCode(String fieldCode) {
        return new MetricFieldValue(WindMetricsValue.of(fieldCode, value.getValueType(), value.getValue()));
    }

}
