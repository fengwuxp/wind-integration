package com.wind.integration.metrics.spec;

import com.wind.integration.metrics.dsl.definition.MetricQueryParameterDsl;
import com.wind.integration.metrics.enums.MetricDerivationType;
import com.wind.integration.metrics.enums.MetricValueShape;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * 指标定义规格（顶层抽象）
 *
 * <p>sealed interface 允许两种形态：
 * <ul>
 *   <li>{@link MetricDSLDefinition} - 通过 DSL 定义的指标（支持实时查询和快照物化）</li>
 *   <li>{@link MetricSqlDefinition} - 通过 SQL 模板定义的指标（仅支持实时查询）</li>
 * </ul>
 *
 * @author wuxp
 * @date 2026-09-16
 */
public sealed interface MetricDefinitionObject permits MetricDSLDefinition, MetricSqlDefinition {

    /**
     * 指标编码（全局唯一）
     */
    String code();

    /**
     * 定义修订号，与编码共同唯一标识一个定义实例
     */
    int revision();

    /**
     * 由定义结构确定的派生类型，不作为独立的 JSON 配置或持久化字段。
     *
     * <p>DSL 事实分支为 RAW，跨指标表达式分支为 DERIVED；当前 SQL 模板定义为 RAW。
     * 分类不执行编译或合法性校验，调用方仍须校验事实字段和指标引用。</p>
     *
     * @return 定义所属的计算分支
     */
    MetricDerivationType derivationType();

    /**
     * 值形态
     */
    MetricValueShape valueShape();

    /**
     * 主体类型
     */
    String subjectType();

    /**
     * 维度列表
     */
    List<String> dimensions();

    /**
     * 参数定义（用于参数化查询）
     */
    @Nullable
    Map<String, MetricQueryParameterDsl> parameters();
}
