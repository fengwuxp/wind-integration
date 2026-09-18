package com.wind.integration.metrics.jdbc;

import com.wind.integration.metrics.query.MetricQuery;
import com.wind.integration.metrics.spec.MetricDefinitionObject;

/**
 * 指标查询 SQL 渲染的统一抽象，由 DSL 与 SQL 两种实现分别承载。
 *
 * <p>DSL 模式由 {@link MetricJdbcSqlCompiler} 实现，产出参数化 SQL（{@code ?} 占位符）与有序绑定；
 * SQL 模式由 {@link MetricSqlTemplateRenderer} 实现，按内部配置信任模型用 Freemarker {@code ${...}}
 * 直接插值，产出无绑定的 SQL 文本。两者统一归一为 {@link MetricSqlDescriptor}：SQL 模式的
 * {@code bindings} 与 {@code projections} 为空。</p>
 *
 * <p>DSL 模式所需的冻结物理映射 {@link MetricJdbcBinding} 由 {@link MetricJdbcSqlCompiler} 内部
 * 按 (code, revision) 注册缓存并获取，不进入本方法签名。</p>
 *
 * @author wuxp
 */
public interface MetricQuerySqlRender {

    /**
     * 渲染对应模式的查询 SQL。
     *
     * @param definition DSL 或 SQL 指标定义
     * @param query 查询条件
     * @return 参数化或插值后的查询 SQL
     */
    MetricSqlDescriptor render(MetricDefinitionObject definition, MetricQuery query);
}
