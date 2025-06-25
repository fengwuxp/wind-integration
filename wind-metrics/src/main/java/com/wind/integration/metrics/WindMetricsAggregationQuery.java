package com.wind.integration.metrics;

import com.wind.common.enums.DescriptiveEnum;
import com.wind.common.exception.AssertUtils;
import com.wind.integration.tag.WindTag;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/**
 * 聚合查询参数
 * @author wuxp
 * @date 2024-09-12 16:55
 **/
@AllArgsConstructor
@Getter
@EqualsAndHashCode
@ToString
public final class WindMetricsAggregationQuery {

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
     * 查询标签
     */
    private final Collection<WindTag> searchTags;

    /**
     * 查询变量
     */
    private final Map<String, Object> queryVariables;

    /**
     * 搜索到最小创建时间
     */
    private final LocalDateTime minGmtCreate;

    /**
     * 搜索到最大创建时间
     */
    private final LocalDateTime maxGmtCreate;

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
