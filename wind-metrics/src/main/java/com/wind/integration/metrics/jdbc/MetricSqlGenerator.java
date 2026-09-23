package com.wind.integration.metrics.jdbc;

import com.wind.integration.metrics.query.MetricQuery;
import com.wind.integration.metrics.spec.MetricDefinitionObject;

/**
 * 根据已选定的指标定义和查询条件生成 SQL 描述的公共能力。
 *
 * <h2>职责</h2>
 * <p>本接口只负责 SQL 描述生成，不执行 SQL、不读取结果、不选择 revision、不读取快照、不展开
 * 派生依赖，也不决定分段边界。调用者必须先确定定义、revision 和实际时间范围。</p>
 *
 * <h2>两条实现路线</h2>
 * <ul>
 *   <li>DSL：{@link MetricJdbcSqlCompiler} 使用宿主冻结的 {@link MetricJdbcMapping}，生成参数化 SQL、
 *       有序 {@link MetricJdbcParameterBinding} 和 measure projection。</li>
 *   <li>SQL：{@link FreemarkerMetricSqlRenderer} 渲染受信模板，生成 SQL 文本；其 bindings 和
 *       projections 为空，不能把模板最终列自动当成可累计原始量。</li>
 * </ul>
 *
 * <h2>使用流程</h2>
 * <p>调用者确定实际实时区间后调用 {@link #generate}，得到 {@link MetricSqlDescriptor}，再交给
 * {@code MetricRealtimeRepository} 或宿主 JDBC 适配器执行。实时查询、分段查询的实时尾段和物化
 * 增量读取可以复用本端口；快照读取、revision 选择、依赖展开和结果合并不属于本端口。</p>
 *
 * <p>SQL 模板插值只适用于宿主已信任和发布校验的模板。面向不可信输入的业务筛选应使用 DSL
 * 参数化路线，不能把 {@code MetricSqlGenerator} 当作任意 SQL 拼接接口。</p>
 *
 * @author wuxp
 */
public interface MetricSqlGenerator {

    /**
     * 生成对应定义分支的 SQL 描述。
     *
     * @param definition 已选定的 DSL 或 SQL 指标定义；实现不得替换其 revision
     * @param query 已确定实际窗口、主体、维度和参数的查询条件
     * @return 参数化或插值后的 SQL 描述，不代表 SQL 已执行
     * @throws IllegalArgumentException 定义分支、条件或模板不支持
     * @throws IllegalStateException 冻结物理映射缺失或 SQL 描述无法成立
     */
    MetricSqlDescriptor generate(MetricDefinitionObject definition, MetricQuery query);
}
