package com.wind.integration.metrics.jdbc;

import org.jspecify.annotations.Nullable;

/**
 * 宿主已验证并冻结的事实物理映射。
 *
 * <p>实现只读取同次定义校验产生的映射和 codec，不重新查询 Registry、数据库元数据或发布修订。 字段引用使用 DSL 名称，例如
 * amount、customer.region；本能力不持有指标声明副本。
 *
 * @author wuxp
 */
public interface MetricJdbcBinding {

    /**
     * 返回裸物理表名，主事实引用为空字符串，关联事实引用为 DSL join alias。
     *
     * @param factReference 事实在定义内的引用，不等同于事实编码
     * @return 已通过权限和元数据校验的物理表名，不包含引号或 SQL
     */
    String tableName(String factReference);

    /**
     * 返回已验证字段的裸物理列名，不包含表别名、引号或 SQL。
     *
     * @param fieldReference DSL 逻辑字段引用
     * @return 物理列名
     */
    String columnName(String fieldReference);

    /**
     * 返回实际 Java 字段类型，用于主体、维度和 literal 的逻辑值归一。
     *
     * @param fieldReference DSL 逻辑字段引用
     * @return Java 字段类型
     */
    Class<?> javaType(String fieldReference);

    /**
     * 返回宿主通过数据库元数据确认的 JDBC 存储类型。
     *
     * @param fieldReference DSL 逻辑字段引用
     * @return {@link java.sql.Types} 类型
     */
    int jdbcType(String fieldReference);

    /**
     * 将已归一的 Java 逻辑值编码为实际 JDBC 值；每个字段参数调用一次。
     *
     * <p>枚举存储代码、自定义字段编码归宿主；回调不执行 IO。实现应固定使用本次映射的 codec， 不再次解释主体文本或时间窗，不返回共享的可变业务容器。
     *
     * @param fieldReference DSL 逻辑字段引用
     * @param normalizedValue 按字段 Java 类型和编译器时区归一后的逻辑值
     * @return JDBC 参数值；允许 codec 明确认可的 null
     * @throws IllegalArgumentException 值无法按物理存储合同编码时抛出
     */
    @Nullable Object toJdbcValue(String fieldReference, Object normalizedValue);
}
