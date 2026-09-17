package com.wind.integration.metrics.query;

import com.wind.integration.tag.WindTag;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.annotation.JsonDeserialize;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 指标取值、求值和对象聚合共用的条件，不携带指标编码或定义修订。
 *
 * <p>通用计算可以使用集合主体、可空时间、标签及任意业务变量。正式 DSL 查询必须在
 * 执行入口调用 {@link MetricQueryValidator#validateDsl}，再依据固定定义校验。
 * 条件容器被复制，Map 中的 Date/Timestamp 同时复制；任意业务对象仍由宿主维护其生命周期，
 * 不能据此宣称运行上下文可序列化或已被深度冻结。</p>
 *
 * @param subjectId 单主体标识或主体集合；全局查询为空
 * @param startTime 时间下界，可空；是否包含由具体计算合同决定，DSL 为包含
 * @param endTime 时间上界，可空；是否包含由具体计算合同决定，DSL 为不包含
 * @param dimensionValues 具名维度值，与业务变量保持独立
 * @param parameterValues 业务变量或声明参数；DSL 仅允许 Integer
 * @param subjectType 主体类型；省略时由已选指标定义确定
 * @param searchTags 查询标签；多个标签的组合语义沿计算实现
 * @author wuxp
 * @since 2026-09-15
 */
@Schema(description = "通用指标查询条件，不包含指标编码和定义修订")
@JsonDeserialize(using = MetricQueryJsonParser.QueryDeserializer.class)
public record MetricQuery(
        @Nullable @Schema(description = "单主体或主体集合，全局为空") Object subjectId,
        @Nullable @Schema(description = "时间下界；DSL 必填且包含") LocalDateTime startTime,
        @Nullable @Schema(description = "时间上界；DSL 必填且不包含") LocalDateTime endTime,
        @Nullable @Schema(description = "独立具名维度") Map<String, Object> dimensionValues,
        @Nullable @Schema(description = "业务变量；DSL 仅允许声明的整数参数") Map<String, Object> parameterValues,
        @Nullable @Schema(description = "主体类型；省略时由定义确定") String subjectType,
        @Nullable @Schema(description = "查询标签；正式 DSL 不接受非空标签") Collection<WindTag> searchTags) {

    public MetricQuery {
        if (subjectId instanceof Collection<?> subjects) {
            subjectId = copyCollection(subjects);
        }
        dimensionValues = copyValues(dimensionValues);
        parameterValues = copyValues(parameterValues);
        searchTags = searchTags == null ? null : copyCollection(searchTags);
    }

    /**
     * 创建不额外指定主体类型和标签的条件，仍由执行入口决定适用性。
     *
     * @param subjectId 单主体或主体集合
     * @param startTime 时间下界
     * @param endTime 时间上界
     * @param dimensionValues 具名维度值
     * @param parameterValues 业务变量或声明参数
     */
    public MetricQuery(@Nullable Object subjectId, @Nullable LocalDateTime startTime,
                               @Nullable LocalDateTime endTime, @Nullable Map<String, Object> dimensionValues,
                               @Nullable Map<String, Object> parameterValues) {
        this(subjectId, startTime, endTime, dimensionValues, parameterValues, null, List.of());
    }

    /** @return 隔离可变日期值后的只读维度容器，保留显式 null */
    @Override
    public @Nullable Map<String, Object> dimensionValues() {
        return copyValues(dimensionValues);
    }

    /** @return 隔离可变日期值后的只读变量容器，业务上下文对象保持原引用 */
    @Override
    public @Nullable Map<String, Object> parameterValues() {
        return copyValues(parameterValues);
    }

    private static @Nullable Map<String, Object> copyValues(@Nullable Map<String, Object> source) {
        return source == null ? null : MetricQueryValueSupport.copyDimensions(source);
    }

    private static <T> Collection<T> copyCollection(Collection<T> source) {
        return source instanceof Set<?> ? Collections.unmodifiableSet(new LinkedHashSet<>(source))
                : Collections.unmodifiableList(new ArrayList<>(source));
    }
}
