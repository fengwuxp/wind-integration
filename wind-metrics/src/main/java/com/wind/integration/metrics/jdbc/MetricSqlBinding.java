package com.wind.integration.metrics.jdbc;

import org.jspecify.annotations.Nullable;

import java.sql.Timestamp;
import java.util.Date;

/**
 * 单个有序 JDBC 参数，保留精确数值和正常 null。
 *
 * <p>构造与读取时复制 Timestamp、Date 和 byte[]；其他值要求由 codec 提供不可变值， 不保证任意业务对象深度不可变。
 *
 * @param value 实际 JDBC 参数值
 * @param jdbcType {@link java.sql.Types} 类型
 * @author wuxp
 */
public record MetricSqlBinding(@Nullable Object value, int jdbcType) {

    public MetricSqlBinding {
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
