package com.wind.integration.metrics.dsl.definition;

import com.wind.integration.metrics.dsl.filter.MetricNumericLiteralDsl;
import com.wind.integration.metrics.enums.MetricOrElseMode;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * SQL 正常返回空结果时的显式取值规则。
 *
 * <p>该规则不处理计算异常或除零错误。</p>
 *
 * @param mode 空结果处理方式
 * @param value {@code VALUE} 模式的回退数值；其他模式必须为空
 *
 * @author wuxp
 * @date 2026-07-21 17:51
 */
public record MetricOrElseDsl(MetricOrElseMode mode, @Nullable MetricNumericLiteralDsl value) {

    public MetricOrElseDsl {
        Objects.requireNonNull(mode, "mode must not be null");
    }
}
