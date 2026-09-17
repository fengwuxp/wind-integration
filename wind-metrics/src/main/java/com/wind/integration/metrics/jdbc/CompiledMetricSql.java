package com.wind.integration.metrics.jdbc;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 指标参数化 SQL 编译结果，不执行 JDBC 或持有发布状态。
 *
 * @param sql 使用受控标识符和问号占位符的目标方言 SQL
 * @param bindings 按 SQL 占位符次序排列的实际参数
 * @param projections SQL 别名到指标输出字段的映射，仅包含 measure
 * @author wuxp
 */
public record CompiledMetricSql(
        String sql, List<MetricSqlBinding> bindings, Map<String, String> projections) {

    public CompiledMetricSql {
        Objects.requireNonNull(sql, "sql must not be null");
        bindings = List.copyOf(bindings);
        projections = Collections.unmodifiableMap(new LinkedHashMap<>(projections));
    }
}
