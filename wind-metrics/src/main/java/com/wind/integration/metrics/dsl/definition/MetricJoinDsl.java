package com.wind.integration.metrics.dsl.definition;

import com.wind.integration.metrics.enums.MetricJoinCardinality;
import com.wind.integration.metrics.enums.MetricJoinType;

import java.util.List;
import java.util.Objects;

/**
 * 主事实源到关联事实源的受控等值关联。
 *
 * @param alias 关联事实在字段引用中的别名
 * @param fact 关联事实源编码
 * @param joinType 连接类型
 * @param cardinality 关联事实相对主事实的基数
 * @param on 一组等值关联字段，不能为空
 *
 * @author wuxp
 * @date 2026-07-21 17:51
 */
public record MetricJoinDsl(String alias,
                            String fact,
                            MetricJoinType joinType,
                            MetricJoinCardinality cardinality,
                            List<MetricJoinOnDsl> on) {

    public MetricJoinDsl {
        Objects.requireNonNull(alias, "alias must not be null");
        Objects.requireNonNull(fact, "fact must not be null");
        Objects.requireNonNull(joinType, "joinType must not be null");
        Objects.requireNonNull(cardinality, "cardinality must not be null");
        on = List.copyOf(on);
    }
}
