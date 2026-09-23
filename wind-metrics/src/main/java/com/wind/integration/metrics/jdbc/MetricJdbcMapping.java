package com.wind.integration.metrics.jdbc;

import org.jspecify.annotations.Nullable;

/**
 * 宿主提供给 Wind SQL 编译器的、已经验证并冻结的事实物理映射端口。
 *
 * <p>它把 DSL 的逻辑引用转换为受控的表、列、Java 类型、JDBC 类型和字段 codec。Wind 编译器
 * 只消费本端口，不发现实体、不查询 Registry、不读取数据库元数据，也不选择指标 revision。
 * 实例对应本次已选定定义的物理校验结果，并在一次查询期间保持映射和 codec 一致。
 * 字段引用使用 DSL 名称，例如 {@code amount}、{@code customer.region}。</p>
 *
 * <h2>责任与不变量</h2>
 * <ul>
 *   <li>表名、列名和 alias 必须是宿主已通过权限和元数据校验的裸标识符，不含 SQL 片段。</li>
 *   <li>{@link #javaType(String)} 与 {@link #jdbcType(String)} 必须描述同一物理字段的配对类型。</li>
 *   <li>{@link #toJdbcValue(String, Object)} 只做确定性编码，不执行 IO、不改变查询条件、不复用可变容器。</li>
 *   <li>正常 {@code null} 仍须带有字段对应的 JDBC 类型，由编译结果交给执行端绑定。</li>
 * </ul>
 *
 * <h2>使用流程</h2>
 * <p>宿主先完成定义、字段、权限和 codec 校验，再将本次冻结的映射直接传给
 * {@link MetricJdbcSqlCompiler#compile}。编译器按主体、时间、维度、measure filter 和 literal
 * 读取映射，生成 {@link MetricSqlDescriptor}。这条路径不依赖预先注册，也不修改编译器的映射缓存。</p>
 *
 * <p>只有使用 {@link MetricJdbcSqlCompiler#generate} 时，才需要先通过
 * {@link MetricJdbcSqlCompiler#registerBinding} 注册对应定义修订的映射。本端口不管理注册生命周期，
 * 不执行 SQL，也不负责读取结果；一个占位符的值和 JDBC 类型由 {@link MetricJdbcParameterBinding} 承载。</p>
 *
 * @author wuxp
 */
public interface MetricJdbcMapping {

    /**
     * 返回裸物理表名。主事实源使用空字符串，关联事实源使用 {@link com.wind.integration.metrics.dsl.definition.MetricJoinDsl#alias()}
     * 指定的 DSL alias。
     *
     * @param factReference 事实在定义内的引用，不等同于事实编码
     * @return 已通过权限和元数据校验的物理表名，不包含引号或 SQL 片段
     * @throws IllegalArgumentException 未知事实引用或物理标识不合法
     */
    String tableName(String factReference);

    /**
     * 返回已验证字段的裸物理列名，不包含表别名、引号或 SQL 片段。
     *
     * @param fieldReference DSL 逻辑字段引用
     * @return 物理列名
     * @throws IllegalArgumentException 未知字段引用或物理标识不合法
     */
    String columnName(String fieldReference);

    /**
     * 返回实际 Java 字段类型，用于主体、时间、维度和 literal 的逻辑值归一。
     *
     * @param fieldReference DSL 逻辑字段引用
     * @return Java 字段类型；不能返回 null
     * @throws IllegalArgumentException 未知字段引用
     */
    Class<?> javaType(String fieldReference);

    /**
     * 返回宿主通过数据库元数据确认的 JDBC 存储类型。
     *
     * @param fieldReference DSL 逻辑字段引用
     * @return {@link java.sql.Types} 类型；与 {@link #javaType(String)} 对应
     * @throws IllegalArgumentException 未知字段引用
     */
    int jdbcType(String fieldReference);

    /**
     * 将已归一的查询条件值编码为实际 JDBC 值；每个字段参数调用一次。
     *
     * <p>枚举存储代码、自定义字段编码归宿主；回调不执行 IO。实现应固定使用本次映射的 codec，
     * 枚举输入可能是对应枚举实例或已校验的枚举名称，整型输入按 Integer 或 Long 归一；
     * codec 根据字段实际存储类型完成编码及范围校验。不再次解释主体文本或时间窗，
     * 不返回共享的可变业务容器。返回 {@code null} 时，{@link #jdbcType(String)} 仍决定绑定类型。</p>
     *
     * @param fieldReference DSL 逻辑字段引用
     * @param normalizedValue 按字段 Java 类型和编译器时区归一后的逻辑值
     * @return JDBC 参数值；允许 codec 明确认可的 null
     * @throws IllegalArgumentException 值无法按物理存储合同编码时抛出
     */
    @Nullable Object toJdbcValue(String fieldReference, Object normalizedValue);
}
