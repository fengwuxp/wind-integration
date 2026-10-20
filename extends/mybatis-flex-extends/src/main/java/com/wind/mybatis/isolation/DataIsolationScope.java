package com.wind.mybatis.isolation;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * 单个隔离维度的读取范围；空值集合表示不可见，不限制须显式声明。
 *
 * @param unrestricted 是否不限制当前维度
 * @param values       可见值，不含 null
 * @author wuxp
 * @since 2026-10-16
 */
@NullMarked
public record DataIsolationScope(boolean unrestricted, List<?> values) {

    public DataIsolationScope {
        values = List.copyOf(values);
        if (unrestricted && !values.isEmpty()) {
            throw new IllegalArgumentException("Unrestricted isolation scope must not contain values");
        }
    }

    public static DataIsolationScope of(@Nullable Object value) {
        List<Object> values = value == null ? List.of() : List.of(value);
        return new DataIsolationScope(false, values);
    }

    public static DataIsolationScope ofValues(Collection<?> values) {
        return new DataIsolationScope(false, List.copyOf(values));
    }

    public static DataIsolationScope unrestrictedScope() {
        return new DataIsolationScope(true, List.of());
    }
}
