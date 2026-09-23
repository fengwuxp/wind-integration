/**
 * 指标 DSL 的受限表达式编译、依赖提取与纯数值求值。
 *
 * <p>流程是 {@code MetricExpressionCompiler -> CompiledMetricExpression -> evaluate}：
 * 编译阶段只解析 AST、校验白名单并提取引用；求值阶段只读取宿主传入的本地 measure 和
 * 已选定依赖结果。定义 revision、依赖加载、事务和数据访问由宿主负责。</p>
 *
 * <p>表达式不能调用数据库、Registry、查询服务或任意 Spring 方法；非法语法、缺失值、
 * 类型错误和除零保持失败语义。</p>
 */
package com.wind.integration.metrics.expression;
