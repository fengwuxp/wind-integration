package com.wind.integration.metrics.dsl.definition;

import com.wind.integration.metrics.enums.MetricValueType;
import org.jspecify.annotations.Nullable;

import java.math.RoundingMode;
import java.util.Objects;

/**
 * 单个指标值字段的类型、精度、计算来源和空结果规则。
 *
 * <p>{@code measure} 与 {@code expression} 必须且只能提供一个。十进制值最终使用
 * 4 至 6 位 {@code scale} 和 {@link RoundingMode#HALF_UP}；JSON 未填写时分别默认为
 * {@code 4} 和 {@code HALF_UP}。</p>
 *
 * @param valueType 指标值类型
 * @param scale 十进制结果保留位数；非十进制类型为空
 * @param roundingMode 十进制舍入方式；非十进制类型为空
 * @param measure 事实聚合定义；派生计算时为空
 * @param expression 派生表达式；事实聚合时为空
 * @param orElse SQL 正常空结果处理规则
 *
 * @author wuxp
 * @date 2026-07-21 17:51
 */
public record MetricValueDsl(MetricValueType valueType,
                             @Nullable Integer scale,
                             @Nullable RoundingMode roundingMode,
                             @Nullable MetricMeasureDsl measure,
                             @Nullable MetricExpressionDsl expression,
                             MetricOrElseDsl orElse) {

    public MetricValueDsl {
        Objects.requireNonNull(valueType, "valueType must not be null");
        Objects.requireNonNull(orElse, "orElse must not be null");
    }
}
