package com.wind.integration.metrics.jdbc;

import com.wind.integration.metrics.query.MetricQuery;
import com.wind.integration.metrics.spec.MetricDSLDefinition;
import com.wind.integration.metrics.spec.MetricDefinitionObject;
import com.wind.integration.metrics.spec.MetricSqlDefinition;

import java.util.Objects;

/**
 * 按定义类型编排 DSL 与 SQL 两种 {@link MetricQuerySqlRender} 实现的统一入口。
 *
 * <p>它只做一次类型分派和空值校验：DSL 交给编译器，SQL 交给模板渲染器；不持有额外业务状态、
 * 不选择修订、不查询数据库，也不把两种 SQL 语义合并成一条执行路径。</p>
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
