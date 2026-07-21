package com.wind.integration.metrics.dsl;

import java.util.List;
import java.util.Objects;

/** 受控事实关联。 */
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
