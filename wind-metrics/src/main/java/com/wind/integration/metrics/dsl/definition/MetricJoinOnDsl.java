package com.wind.integration.metrics.dsl.definition;

import java.util.Objects;

/**
 * 事实关联中的一组等值字段。
 *
 * @param primaryField 主事实源字段名
 * @param joinField 关联事实源字段名
 *
 * @author wuxp
 * @date 2026-07-21 17:51
 */
public record MetricJoinOnDsl(String primaryField, String joinField) {

    public MetricJoinOnDsl {
        Objects.requireNonNull(primaryField, "primaryField must not be null");
        Objects.requireNonNull(joinField, "joinField must not be null");
    }
}
