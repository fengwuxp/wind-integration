/**
 * 直接消费指标 DSL、查询条件和宿主冻结物理映射的 JDBC SQL 生成能力。
 *
 * <p>流程是 {@code MetricQuerySqlRender -> CompositeMetricQuerySqlRender ->}
 * DSL compiler 或 SQL template renderer。DSL 路线输出参数化 SQL、JDBC 类型和 measure
 * 投影；SQL 路线输出受信模板的最终文本。两条路线都只生成描述，不访问数据库、不选择 revision、
 * 不读取快照，也不管理查询或物化事务。</p>
 *
 * <p>SQL 模板路线统一使用名称明确的 {@code FreemarkerSqlTemplateRenderer}。</p>
 */
package com.wind.integration.metrics.jdbc;
