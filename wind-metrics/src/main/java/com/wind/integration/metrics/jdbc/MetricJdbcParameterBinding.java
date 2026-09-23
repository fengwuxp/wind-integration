package com.wind.integration.metrics.jdbc;

import org.jspecify.annotations.Nullable;

import java.sql.Timestamp;
import java.util.Date;

/**
 * 一个 SQL 占位符对应的 JDBC 参数绑定。
 *
 * <p>绑定同时保存实际 JDBC 值和 {@link java.sql.Types} 类型。编译器按 SQL 占位符出现顺序
 * 将本对象放入 {@link MetricSqlDescriptor#bindings()}，执行端按同一顺序绑定；因此它不是
 * SQL 文本、字段映射或查询结果。构造与读取时复制 Timestamp、Date 和 byte[]；其他值要求
 * 由 codec 提供不可变值，不保证任意业务对象深度不可变。</p>
 *
 * @param value 实际 JDBC 参数值；允许正常 null
 * @param jdbcType {@link java.sql.Types} 类型，即使 value 为 null 也必须明确
 * @author wuxp
 */
public record MetricJdbcParameterBinding(@Nullable Object value, int jdbcType) {

    public MetricJdbcParameterBinding {
        value = copy(value);
    }

    @Override
    public @Nullable Object value() {
        return copy(value);
    }

    private static @Nullable Object copy(@Nullable Object value) {
        if (value instanceof Timestamp timestamp) {
            return timestamp.clone();
        }
        if (value instanceof Date date) {
            return date.clone();
        }
        if (value instanceof byte[] bytes) {
            return bytes.clone();
        }
        return value;
    }
}
