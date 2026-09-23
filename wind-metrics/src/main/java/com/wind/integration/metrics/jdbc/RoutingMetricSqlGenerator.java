package com.wind.integration.metrics.jdbc;

import com.wind.integration.metrics.query.MetricQuery;
import com.wind.integration.metrics.spec.MetricDSLDefinition;
import com.wind.integration.metrics.spec.MetricDefinitionObject;
import com.wind.integration.metrics.spec.MetricSqlDefinition;

import java.util.Objects;

/**
 * 按定义类型选择 {@link MetricSqlGenerator} 实现的统一 SQL 生成入口。
 *
 * <p>它只做一次类型分派和空值校验：DSL 交给编译器，SQL 交给模板渲染器；不持有额外业务状态、
 * 不选择修订、不查询数据库，也不把两种 SQL 语义合并成一条执行路径。</p>
 *
 * @author wuxp
 */
public final class RoutingMetricSqlGenerator implements MetricSqlGenerator {

    private final MetricSqlGenerator dsl;

    private final MetricSqlGenerator sql;

    public RoutingMetricSqlGenerator(MetricSqlGenerator dsl, MetricSqlGenerator sql) {
        this.dsl = Objects.requireNonNull(dsl, "dsl must not be null");
        this.sql = Objects.requireNonNull(sql, "sql must not be null");
    }

    @Override
    public MetricSqlDescriptor generate(MetricDefinitionObject definition, MetricQuery query) {
        Objects.requireNonNull(definition, "definition must not be null");
        Objects.requireNonNull(query, "query must not be null");
        return switch (definition) {
            case MetricDSLDefinition ignored -> dsl.generate(definition, query);
            case MetricSqlDefinition ignored -> sql.generate(definition, query);
        };
    }
}
