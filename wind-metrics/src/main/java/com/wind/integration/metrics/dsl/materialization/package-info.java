/**
 * 指标快照与分段执行模式的逻辑物化计划契约。
 *
 * <p>计划持有精确成员、保存目标和近到远的时间规则；执行器按远到近推进实际范围。
 * 计划不复制指标计算口径，不选择版本，不保存物理表信息，也不代表某个主体已经完成快照。</p>
 */
@NullMarked
package com.wind.integration.metrics.dsl.materialization;

import org.jspecify.annotations.NullMarked;
