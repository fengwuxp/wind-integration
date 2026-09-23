package com.wind.integration.metrics.runtime;

import com.wind.integration.metrics.query.MetricQuery;
import com.wind.integration.metrics.spec.MetricDefinitionObject;
import org.jspecify.annotations.NonNull;

/**
 * 按精确指标定义与查询条件读取实时数据，由宿主实现实际数据访问。
 *
 * <p>只接收 RAW 定义。返回对象的具体形态由宿主决定：DSL 可以返回原始 measure
 * 对象，SQL 可以返回已经计算的运行时对象；合并、最终类型归一化、舍入、表达式和缺省值
 * 仍由上层执行。</p>
 *
 * <p>数据源、冻结物理绑定及超时由实现装配，不作为查询参数传递。实现复用宿主当前事务，
 * 不另开依赖查询事务，不选择指标修订，不读取快照或执行物化。</p>
 *
 * <p>SQL SCALAR 要求一行一列，逻辑字段为 value；FIELD_SET 要求一行、至少一列，
 * 采用非空且唯一的列标签。列类型由 JDBC 元数据确定，NULL 也须保留明确类型；
 * 无法确定或不支持的类型、零行、多行、重复列均明确失败，不伪装成空结果。
 * DSL 字段集合须与编译投影中的 measure 一致。</p>
 *
 * @author wuxp
 * @param <T> 宿主使用的完整实时结果对象类型
 * @since 2026-09-23
 */
@FunctionalInterface
public interface MetricRuntimeRepository<T> {

    /**
     * 读取一个实际实时范围的具名值。
     *
     * @param definition 已选定精确编码与修订的 RAW DSL 或 SQL 定义
     * @param query 主体、维度、实际时间范围及业务参数；条件语义由宿主依定义校验
     * @return 完整实时结果对象；对象本身不得为 null，内部字段的空值语义由 T 的契约定义
     * @throws RuntimeException 定义或条件不支持、结果结构/类型不合法，或数据访问失败
     */
    @NonNull T query(@NonNull MetricDefinitionObject definition, @NonNull MetricQuery query);
}
