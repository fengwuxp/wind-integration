/**
 * 指标事实过滤条件的封闭 DSL 语法树。
 *
 * <p>节点只表达条件，不拼接 SQL。JDBC 包将已校验的树翻译为参数化谓词；参数类型和
 * 字段物理映射由宿主冻结的 binding 提供。</p>
 */
@NullMarked
package com.wind.integration.metrics.dsl.filter;

import org.jspecify.annotations.NullMarked;
