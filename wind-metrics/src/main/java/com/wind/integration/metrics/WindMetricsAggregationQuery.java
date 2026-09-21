package com.wind.integration.metrics;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.wind.common.enums.DescriptiveEnum;
import com.wind.common.exception.AssertUtils;
import com.wind.integration.metrics.query.MetricQuery;
import com.wind.integration.tag.WindTag;
import org.jspecify.annotations.Nullable;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import tools.jackson.databind.annotation.JsonSerialize;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 聚合查询的兼容外观，旧模板属性、构造器和 builder 保持原语义。
 *
 * @deprecated 新计算入口使用 {@link MetricQuery}；旧调用通过 {@link #asQuery()} 迁移
 * @author wuxp
 * @date 2024-09-12 16:55
 **/
@AllArgsConstructor
@Getter
@EqualsAndHashCode
@ToString
@Schema(description = "指标聚合查询参数")
@Deprecated
public final class WindMetricsAggregationQuery {

    /**
     * 聚合维度 (业务对象)
     */
    @Schema(description = "聚合维度（业务对象）")
    @NotNull
    private final String dimensions;

    /**
     * 聚合维度的标识
     */
    @Schema(description = "聚合维度标识")
    @NotNull
    private final Object dimensionsId;

    /**
     * 查询标签；JSON 仅承载 name/value，不要求打标来源。
     */
    @Schema(description = "查询标签")
    @JsonSerialize(contentAs = WindTag.class)
    @JsonIgnoreProperties({"source", "sourceId"})
    private final Collection<WindTag> searchTags;

    /**
     * 查询变量
     */
    @Schema(description = "查询变量")
    private final Map<String, Object> queryVariables;

    /**
     * 搜索到最小创建时间
     */
    @Schema(description = "创建时间下界")
    private final LocalDateTime minGmtCreate;

    /**
     * 搜索到最大创建时间
     */
    @Schema(description = "创建时间上界")
    private final LocalDateTime maxGmtCreate;

    /**
     * 提取通用条件，保持主体对象、可空时间及任意业务变量；有标签时不可转换。
     *
     * <p>旧 queryVariables 没有声明参数与维度的区分，全部作为通用变量承接，
     * 不根据名称或数值类型猜测 DSL 维度。DSL 使用方必须另做定义级绑定。</p>
     *
     * @return 不带指标身份的条件
     * @throws IllegalArgumentException 旧查询包含新条件模型无法表达的标签
     */
    public MetricQuery asQuery() {
        if (searchTags != null && !searchTags.isEmpty()) {
            throw new IllegalArgumentException("MetricQuery cannot represent legacy searchTags; use the legacy query entry");
        }
        return new MetricQuery(dimensionsId, dimensions, minGmtCreate, maxGmtCreate,
                Map.of(), queryVariables);
    }

    /**
     * 为既有聚合实现提供兼容视图，不合并具名维度与业务变量。
     *
     * @param metricQuery 通用条件；null 保持既有默认查询语义
     * @return 使用原属性名的查询对象；输入为空时返回空
     * @throws IllegalArgumentException 条件包含旧查询无法表达的独立维度，需要实现方原生接入 criteria
     */
    public static @Nullable WindMetricsAggregationQuery fromQuery(@Nullable MetricQuery metricQuery) {
        if (metricQuery == null) {
            return null;
        }
        Map<String, Object> values = metricQuery.dimensionValues();
        if (values == null || !values.isEmpty()) {
            throw new IllegalArgumentException("Legacy aggregation cannot represent dimensionValues; implement the criteria entry");
        }
        return new WindMetricsAggregationQuery(metricQuery.subjectType(), metricQuery.subjectId(), Set.of(),
                metricQuery.parameterValues(), metricQuery.startTime(), metricQuery.endTime());
    }

    public static MetricsAggregationQueryBuilder newBuilder(@NotNull String dimensions, @NotNull Object dimensionsId) {
        return new MetricsAggregationQueryBuilder(dimensions, dimensionsId);
    }

    public static MetricsAggregationQueryBuilder newBuilder(@NotNull DescriptiveEnum dimensions, @NotNull Object dimensionsId) {
        return newBuilder(dimensions.name(), dimensionsId);
    }

    public static WindMetricsAggregationQuery of(@NotNull DescriptiveEnum dimensions, @NotNull Object dimensionsId) {
        return  of(dimensions.name(), dimensionsId);
    }

    public static WindMetricsAggregationQuery of(@NotBlank String dimensions, @NotNull Object dimensionsId) {
        return new MetricsAggregationQueryBuilder(dimensions, dimensionsId).build();
    }

    public static class MetricsAggregationQueryBuilder {

        /**
         * 聚合维度 (业务对象)
         */
        @NotNull
        private final String dimensions;

        /**
         * 聚合维度的标识
         */
        @NotNull
        private final Object dimensionsId;

        /**
         * 多个标签按照 and 逻辑连接
         */
        private final Collection<WindTag> searchTags;

        /**
         * 查询变量
         */
        private final Map<String, Object> queryVariables;

        /**
         * 搜索到最小创建时间
         */
        private LocalDateTime minGmtCreate;

        /**
         * 搜索到最大创建时间
         */
        private LocalDateTime maxGmtCreate;

        public MetricsAggregationQueryBuilder(String dimensions, Object dimensionsId) {
            AssertUtils.hasText(dimensions, "argument dimensions must not empty");
            AssertUtils.notNull(dimensionsId, "argument dimensionsId must not null");
            this.dimensions = dimensions;
            this.dimensionsId = dimensionsId;
            this.searchTags = new HashSet<>();
            this.queryVariables = new HashMap<>();
        }

        public MetricsAggregationQueryBuilder tag(WindTag tag) {
            this.searchTags.add(tag);
            return this;
        }

        public MetricsAggregationQueryBuilder tag(Collection<WindTag> tags) {
            this.searchTags.addAll(tags);
            return this;
        }

        public MetricsAggregationQueryBuilder tag(String... keyValues) {
            this.searchTags.addAll(WindTag.tags(keyValues));
            return this;
        }

        public MetricsAggregationQueryBuilder queryVariable(String name, String value) {
            this.queryVariables.put(name, value);
            return this;
        }

        public MetricsAggregationQueryBuilder queryVariable(Map<String, Object> vars) {
            this.queryVariables.putAll(vars);
            return this;
        }

        public MetricsAggregationQueryBuilder minGmtCreate(LocalDateTime minGmtCreate) {
            this.minGmtCreate = minGmtCreate;
            return this;
        }

        public MetricsAggregationQueryBuilder maxGmtCreate(LocalDateTime maxGmtCreate) {
            this.maxGmtCreate = maxGmtCreate;
            return this;
        }

        public WindMetricsAggregationQuery build() {
            return new WindMetricsAggregationQuery(dimensions, dimensionsId, searchTags, this.queryVariables, minGmtCreate, maxGmtCreate);
        }
    }

}
