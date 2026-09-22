package com.wind.integration.metrics;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wind.integration.metrics.enums.MetricValueType;
import com.wind.integration.metrics.json.MetricValuePayloadJsonDeserializer;
import com.wind.integration.metrics.json.MetricValuePayloadJsonSerializer;
import com.wind.integration.metrics.query.MetricValueJsonDeserializer;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;

/**
 * 面向开发者的具名指标值能力，与定义方式、计算引擎和取数策略无关。
 *
 * <p>值可以由代码、SQL 或 DSL 实现提供。调用方无须区分实时、快照或分段查询；
 * 实现负责选择正确的数据来源并传播失败，不得将执行失败伪装成正常空值。
 * 本接口只提供读取能力，不承诺每次读取是否重新计算；固定查询结果可使用 {@link #of}。
 * 多字段结果可进一步实现 {@link WindStructuredMetricsValue}。</p>
 *
 * @param <V> 值类型
 * @author wuxp
 * @date 2025-06-17 14:27
 **/
@Schema(description = "指标编码和值的描述对象")
@JsonDeserialize(using = MetricValueJsonDeserializer.class)
public interface WindMetricsValue<V> {

    /**
     * 获取指标名称
     */
    @NonNull
    @JsonIgnore
    @Deprecated(forRemoval = true)
    String getName();

    /**
     * @return 指标编码
     */
    @NonNull
    default String getCode() {
        return getName();
    }

    /**
     * @return 非空指标值类型，未覆盖时默认为 DECIMAL。
     * 指标或字段有明确声明时实现应返回该类型，即使 payload 为空也保留类型。
     * 结构化值沿用接口默认值，子字段类型分别由字段值承载。
     * 本方法不触发求值，不从每次变化的 payload 推断类型。
     */
    @NonNull
    default MetricValueType getValueType() {
        return MetricValueType.DECIMAL;
    }

    /**
     * @return 指标值；定义允许的正常空结果可以为空
     */
    @Nullable
    @JsonSerialize(using = MetricValuePayloadJsonSerializer.class)
    V getValue();

    /**
     * 将已计算的指标编码和值保存为只读值对象，不触发计算或存储。
     * 已知标量 payload 可推断类型；null 或其他通用对象使用默认 DECIMAL。
     * 查询结果应使用显式类型工厂，以保留定义类型和正常空值的类型。
     *
     * @param code  非空白编码
     * @param value 已计算的值，可以为空；可变值的所有权由调用方管理
     * @param <V>   值类型
     * @return 编码与值引用固定的指标值
     */
    static <V> WindMetricsValue<V> of(@JsonProperty("code") String code, @JsonProperty("value") @Nullable V value) {
        return new ImmutableMetricsValue<>(code, MetricsValueSupport.inferType(value), value);
    }

    /**
     * 固定声明类型和已计算值；精确整数按目标类型转换，拒绝浮点近似和溢出。
     *
     * @param code      指标或字段编码
     * @param valueType 非空标量类型
     * @param value     原始 payload，可以为空；TIMESTAMP 接受 LocalDateTime 或 ISO 本地时间文本
     * @return 已验证、类型与值一同固定的指标值
     */
    @JsonCreator
    static WindMetricsValue<?> of(@JsonProperty("code") String code, @JsonProperty("valueType") MetricValueType valueType,
                                 @JsonProperty("value") @JsonDeserialize(using = MetricValuePayloadJsonDeserializer.class) @Nullable Object value) {
        return new ImmutableMetricsValue<>(code, valueType, MetricsValueSupport.normalize(valueType, value));
    }
}
