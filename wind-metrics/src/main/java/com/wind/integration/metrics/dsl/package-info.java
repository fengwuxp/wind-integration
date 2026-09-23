/**
 * 指标声明 DSL 的模型、校验和纯计算能力。
 *
 * <p>本包的职责是把 JSON/配置表达成不可变的定义对象，并在不访问数据源的前提下完成
 * 过滤树、字面量、分段规则和原始 measure 的确定性校验或计算。典型流程是：
 * {@code definition} 描述口径，{@code expression} 编译表达式，{@code jdbc} 生成 SQL，
 * 宿主再负责版本、物理绑定、事务和实际数据读取。</p>
 *
 * <p>本包不选择指标 revision、不读取快照、不执行 JDBC，也不把查询服务编排塞进 DSL 模型。</p>
 */
@NullMarked
package com.wind.integration.metrics.dsl;

import org.jspecify.annotations.NullMarked;
