package com.wind.integration.metrics.dsl.definition;

import com.wind.integration.metrics.enums.MetricValueType;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.math.RoundingMode;
import java.util.Objects;

/**
 * 单个指标值字段的类型、精度、计算来源和正常空结果规则。
 *
 * <p>{@code measure} 与 {@code expression} 是互斥分支：前者产生可供合并的原始状态，
 * 后者读取本地 measure 或宿主准备的依赖结果。分支合法性由所属定义校验；本 record 只保存
 * 不可变声明。十进制值最终使用声明的 {@code scale} 和 {@link RoundingMode} 归一。</p>
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
@Schema(description = "单个指标值字段的类型、精度、计算来源和空结果规则")
public record MetricValueDsl(
        @Schema(description = "指标值类型") MetricValueType valueType,
        @Nullable @Schema(description = "十进制结果保留位数；非十进制类型为空") Integer scale,
        @Nullable @Schema(description = "十进制舍入方式；非十进制类型为空") RoundingMode roundingMode,
        @Nullable @Schema(description = "事实聚合定义；派生计算时为空") MetricMeasureDsl measure,
        @Nullable @Schema(description = "派生表达式；事实聚合时为空") MetricExpressionDsl expression,
        @Schema(description = "SQL 正常空结果处理规则") MetricOrElseDsl orElse) {

    public MetricValueDsl {
        Objects.requireNonNull(valueType, "valueType must not be null");
        Objects.requireNonNull(orElse, "orElse must not be null");
    }
}
