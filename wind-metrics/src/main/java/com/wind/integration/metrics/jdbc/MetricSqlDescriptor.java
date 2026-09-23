package com.wind.integration.metrics.jdbc;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 指标 SQL 生成结果，供 JDBC 执行端消费。
 *
 * <p>DSL 编译路线包含参数化 SQL、有序参数绑定及 measure 投影；SQL 模板路线包含插值后的
 * SQL 文本，bindings 与 projections 均为空。本对象不执行 SQL，也不持有发布状态。</p>
 *
 * @param sql         参数化或受信模板插值后的目标 SQL
 * @param bindings    按 SQL 占位符次序排列的 {@link MetricJdbcParameterBinding}；模板路线为空
 * @param projections SQL 别名到指标输出字段的映射，仅包含 measure；模板路线为空
 * @author wuxp
 */
public record MetricSqlDescriptor(String sql, List<MetricJdbcParameterBinding> bindings, Map<String, String> projections) {

    public MetricSqlDescriptor {
        Objects.requireNonNull(sql, "sql must not be null");
        bindings = List.copyOf(bindings);
        projections = Collections.unmodifiableMap(new LinkedHashMap<>(projections));
    }
}
