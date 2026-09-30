package com.wind.integration.metrics.spec;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.wind.integration.metrics.MetricValidationException;
import com.wind.integration.metrics.dsl.definition.MetricJoinDsl;
import com.wind.integration.metrics.dsl.definition.MetricTimeDsl;
import com.wind.integration.metrics.dsl.definition.selection.MetricRowSelectionDsl;
import com.wind.integration.metrics.enums.MetricDefinitionType;
import com.wind.integration.metrics.enums.MetricErrorCode;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * 声明事实源如何提供指标计算输入；共同值口径由 {@link MetricDefinition} 保存。
 *
 * <p>本对象只声明事实、关联、时间字段和聚合前行选择，不执行查询、不选择读取路线。
 * 编译器结合共同主体、维度、参数及 measure 生成 SQL；表达式在取值和合并后计算。</p>
 *
 * @param fact 主事实源编码
 * @param joins 主事实源关联定义
 * @param time 主事实源时间字段
 * @param rowSelection 聚合前有限行集；存在时不支持分段合并或物化
 * @author wuxp
 */
public record MetricDSLDefinition(String fact, List<MetricJoinDsl> joins, MetricTimeDsl time,
                                  @Nullable MetricRowSelectionDsl rowSelection) implements MetricValueQueryDefinition {

    public MetricDSLDefinition {
        Objects.requireNonNull(fact, "fact must not be null");
        Objects.requireNonNull(time, "time must not be null");
        joins = List.copyOf(joins);
    }

    @Override
    public MetricDefinitionType type() {
        return MetricDefinitionType.DSL;
    }
    // 拒绝错层规则，避免保存时静默丢失共同口径或读取策略。
    @SuppressWarnings({"PMD.UnusedPrivateMethod", "PMD.UnusedFormalParameter"})
    @JsonAnySetter
    private void rejectUnknownProperty(String name, @Nullable Object ignored) {
        throw new MetricValidationException(MetricErrorCode.DSL_VALUE_INVALID,
                "/metric/valueQuery/" + name.replace("~", "~0").replace("/", "~1"), "Unknown value query property");
    }

}
