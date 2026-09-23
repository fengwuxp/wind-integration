/**
 * 直接消费指标 DSL、查询条件和宿主冻结物理映射的 JDBC SQL 生成能力。
 *
 * <p>流程是 {@code MetricSqlGenerator -> RoutingMetricSqlGenerator ->}
 * DSL compiler 或 SQL template renderer。DSL 路线输出参数化 SQL、JDBC 类型和 measure
 * 投影；SQL 路线输出受信模板的最终文本。两条路线都只生成描述，不访问数据库、不选择 revision、
 * 不读取快照，也不管理查询或物化事务。</p>
 *
 * <p>{@code MetricJdbcMapping} 描述宿主冻结的物理映射和字段编码规则；
 * {@code MetricJdbcParameterBinding} 承载一个占位符的实际值和 JDBC 类型。
 * 包内的 {@code MetricJdbcPredicateBuilder} 构造条件对象，{@code MetricJdbcValueNormalizer}
 * 归一查询输入，最终 SQL 文本和参数顺序由编译器生成。</p>
 */
package com.wind.integration.metrics.jdbc;
