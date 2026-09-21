package com.wind.integration.metrics.query;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 指标取值、求值和对象聚合共用的条件，不携带指标编码或定义修订。
 *
 * <p>通用计算可以使用集合主体、可空时间及任意业务变量。执行入口依据所选定义和执行模式校验条件。
 * 构造时仅对条件容器做一次只读浅复制，Date/Timestamp 和业务对象均保留原引用，
 * 其生命周期由调用方维护；读取条件不再复制。</p>
 *
 * @param subjectId       单主体标识或主体集合；全局查询为空
 * @param subjectType     主体类型；省略时由已选指标定义确定
 * @param startTime       时间下界，可空；是否包含由具体计算合同决定，DSL 为包含
 * @param endTime         时间上界，可空；是否包含由具体计算合同决定，DSL 为不包含
 * @param dimensionValues 具名维度值，与业务变量保持独立
 * @param parameterValues 业务变量或声明参数；DSL 仅允许 Integer
 * @author wuxp
 * @since 2026-09-15
 */
@Schema(description = "通用指标查询条件，不包含指标编码和定义修订")
public record MetricQuery(
        @Nullable @Schema(description = "单主体或主体集合，全局为空") Object subjectId,
        @Nullable @Schema(description = "主体类型；省略时由定义确定") String subjectType,
        @Nullable @Schema(description = "时间下界；DSL 必填且包含") LocalDateTime startTime,
        @Nullable @Schema(description = "时间上界；DSL 必填且不包含") LocalDateTime endTime,
        @Nullable @Schema(description = "独立具名维度") Map<String, Object> dimensionValues,
        @Nullable @Schema(description = "业务变量；DSL 仅允许声明的整数参数") Map<String, Object> parameterValues) {

    public MetricQuery {
        if (subjectId instanceof Collection<?> subjects) {
            subjectId = copyCollection(subjects);
        }
        dimensionValues = copyValues(dimensionValues);
        parameterValues = copyValues(parameterValues);
    }

    /**
     * 创建不额外指定主体类型的条件，仍由执行入口决定适用性。
     *
     * @param subjectId       单主体或主体集合
     * @param startTime       时间下界
     * @param endTime         时间上界
     * @param dimensionValues 具名维度值
     * @param parameterValues 业务变量或声明参数
     */
    public MetricQuery(@Nullable Object subjectId, @Nullable LocalDateTime startTime,
                       @Nullable LocalDateTime endTime, @Nullable Map<String, Object> dimensionValues,
                       @Nullable Map<String, Object> parameterValues) {
        this(subjectId, null, startTime, endTime, dimensionValues, parameterValues);
    }

    /**
     * 拒绝模型之外的 JSON 条件，避免宽松 ObjectMapper 静默丢弃已退役或拼错的过滤字段。
     * 本方法不保存任何附加字段，也不参与序列化。
     */
    @JsonAnySetter
    private void rejectUnknownProperty(String name, @Nullable Object value) {
        throw new IllegalArgumentException("Unknown MetricQuery property: " + name);
    }

    private static @Nullable Map<String, Object> copyValues(@Nullable Map<String, Object> source) {
        return source == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }

    private static <T> Collection<T> copyCollection(Collection<T> source) {
        return source instanceof Set<?> ? Collections.unmodifiableSet(new LinkedHashSet<>(source)) : List.copyOf(source);
    }

    /**
     * 创建 MetricQuery 构建器。
     *
     * @return 新的构建器实例
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * MetricQuery 构建器，提供流式 API 构建查询条件。
     */
    public static class Builder {
        private Object subjectId;

        private LocalDateTime startTime;

        private LocalDateTime endTime;

        private Map<String, Object> dimensionValues = new HashMap<>();

        private Map<String, Object> parameterValues = new HashMap<>();

        private String subjectType;

        private Builder() {
        }

        /**
         * 设置单个主体标识。
         *
         * @param subjectId 主体标识
         * @return this
         */
        public Builder subjectId(@NonNull Object subjectId) {
            this.subjectId = subjectId;
            return this;
        }

        /**
         * 设置主体集合。
         *
         * @param subjectIds 主体集合
         * @return this
         */
        public Builder subjectIds(@NonNull Collection<?> subjectIds) {
            this.subjectId = subjectIds;
            return this;
        }

        /**
         * 设置时间下界。
         *
         * @param startTime 开始时间
         * @return this
         */
        public Builder startTime(@NonNull LocalDateTime startTime) {
            this.startTime = startTime;
            return this;
        }

        /**
         * 设置时间上界。
         *
         * @param endTime 结束时间
         * @return this
         */
        public Builder endTime(@NonNull LocalDateTime endTime) {
            this.endTime = endTime;
            return this;
        }

        /**
         * 设置时间范围。
         *
         * @param startTime 开始时间
         * @param endTime   结束时间
         * @return this
         */
        public Builder timeRange(@NonNull LocalDateTime startTime, @Nullable LocalDateTime endTime) {
            this.startTime = startTime;
            this.endTime = endTime;
            return this;
        }

        /**
         * 设置单个维度值。
         *
         * @param name  维度名称
         * @param value 维度值
         * @return this
         */
        public Builder dimension(@NonNull String name, Object value) {
            this.dimensionValues.put(name, value);
            return this;
        }

        /**
         * 批量设置维度值。
         *
         * @param dimensions 维度映射
         * @return this
         */
        public Builder dimensions(@NonNull Map<String, Object> dimensions) {
            this.dimensionValues.putAll(dimensions);
            return this;
        }

        /**
         * 设置单个参数值。
         *
         * @param name  参数名称
         * @param value 参数值
         * @return this
         */
        public Builder parameter(@NonNull String name, Object value) {
            this.parameterValues.put(name, value);
            return this;
        }

        /**
         * 批量设置参数值。
         *
         * @param parameters 参数映射
         * @return this
         */
        public Builder parameters(@NonNull Map<String, Object> parameters) {
            this.parameterValues.putAll(parameters);
            return this;
        }

        /**
         * 设置主体类型。
         *
         * @param subjectType 主体类型
         * @return this
         */
        public Builder subjectType(@NonNull String subjectType) {
            this.subjectType = subjectType;
            return this;
        }

        /**
         * 构建 MetricQuery 实例。
         *
         * @return 新的 MetricQuery 实例
         */
        public MetricQuery build() {
            return new MetricQuery(
                    subjectId,
                    subjectType,
                    startTime,
                    endTime,
                    dimensionValues,
                    parameterValues
            );
        }
    }
}
