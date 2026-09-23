/**
 * 指标计算定义 DSL 的结构化契约。
 *
 * <p>{@code MetricDSLDefinition} 组合事实输入源、主体、维度、参数、measure 和表达式；
 * {@code MetricValueDsl} 在同一字段上选择事实聚合或派生表达式。这里描述计算口径，
 * 不描述实时/快照路由和物化进度。</p>
 */
@NullMarked
package com.wind.integration.metrics.dsl.definition;

import org.jspecify.annotations.NullMarked;
