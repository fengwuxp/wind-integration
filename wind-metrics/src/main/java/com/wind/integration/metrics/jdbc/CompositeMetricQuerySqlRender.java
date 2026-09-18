package com.wind.integration.metrics.jdbc;

import com.wind.integration.metrics.query.MetricQuery;
import com.wind.integration.metrics.spec.MetricDSLDefinition;
import com.wind.integration.metrics.spec.MetricDefinitionObject;
import com.wind.integration.metrics.spec.MetricSqlDefinition;

import java.util.Objects;

/**
 * 聚合 DSL 与 SQL 两种 {@link MetricQuerySqlRender} 实现的统一入口。
 *
 * <p>按 {@link MetricDefinitionObject} 的 sealed 类型判断分发到对应实现，不持有额外状态、
 * 不选择修订、不查询数据库。</p>
 *
 * @author wuxp
 */
public final class CompositeMetricQuerySqlRender implements MetricQuerySqlRender {

    private final MetricQuerySqlRender dsl;

    private final MetricQuerySqlRender sql;

    public CompositeMetricQuerySqlRender(MetricQuerySqlRender dsl, MetricQuerySqlRender sql) {
        this.dsl = Objects.requireNonNull(dsl, "dsl must not be null");
        this.sql = Objects.requireNonNull(sql, "sql must not be null");
    }

    @Override
    public MetricSqlDescriptor render(MetricDefinitionObject definition, MetricQuery query) {
        Objects.requireNonNull(definition, "definition must not be null");
        Objects.requireNonNull(query, "query must not be null");
        return switch (definition) {
            case MetricDSLDefinition ignored -> dsl.render(definition, query);
            case MetricSqlDefinition ignored -> sql.render(definition, query);
        };
    }
}
