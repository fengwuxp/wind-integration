/**
 * 指标聚合前有限行集的选择合同。
 *
 * <p>选择树只表达过滤、稳定排序和数量上限。它改变进入聚合的记录集合，不能被当作
 * 可跨分段累计的状态；物化或历史分段应在校验阶段拒绝这类定义。</p>
 */
@NullMarked
package com.wind.integration.metrics.dsl.definition.selection;

import org.jspecify.annotations.NullMarked;
