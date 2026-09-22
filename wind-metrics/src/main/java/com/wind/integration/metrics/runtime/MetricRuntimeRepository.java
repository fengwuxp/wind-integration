package com.wind.integration.metrics.runtime;

import com.wind.integration.metrics.WindMetricsValue;
import com.wind.integration.metrics.query.MetricQuery;
import com.wind.integration.metrics.spec.MetricDefinitionObject;
import org.jspecify.annotations.NonNull;

import java.util.Map;

/**
 * 按精确指标定义与查询条件读取实时数据，由宿主实现实际数据访问。
 *
 * <p>只接收 RAW 定义。DSL 返回基础 measure，保留读取到的数值精度和正常 NULL；
 * 合并、最终类型归一化、舍入、表达式和缺省值由上层执行。SQL 定义仅用于单个实时范围，
 * 返回 SQL 已计算的最终列，不再套用 DSL 合并与计算。</p>
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
 * @since 2026-09-22
 */
@FunctionalInterface
public interface MetricRuntimeRepository {

    /**
     * 读取一个实际实时范围的具名值。
     *
     * @param definition 已选定精确编码与修订的 RAW DSL 或 SQL 定义
     * @param query 主体、维度、实际时间范围及业务参数；条件语义由宿主依定义校验
     * @return 逻辑字段名到非空具名值的映射；值的 code 与键一致，payload 可为 null
     * @throws RuntimeException 定义或条件不支持、结果结构/类型不合法，或数据访问失败
     */
    @NonNull Map<String, WindMetricsValue<?>> query(@NonNull MetricDefinitionObject definition, @NonNull MetricQuery query);
}
