package com.wind.integration.metrics.jdbc;

import com.wind.integration.metrics.dsl.filter.ComparisonMetricFilterDsl;
import com.wind.integration.metrics.dsl.filter.LogicalMetricFilterDsl;
import com.wind.integration.metrics.dsl.filter.MetricFilterDsl;
import com.wind.integration.metrics.dsl.filter.NullMetricFilterDsl;
import com.wind.integration.metrics.dsl.filter.SetMetricFilterDsl;
import com.wind.integration.metrics.dsl.literal.MetricLiteralDsl;
import com.wind.integration.metrics.enums.MetricFilterOperator;

import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.impl.DSL;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 指标 JDBC 过滤条件渲染器，将指标 DSL 过滤条件翻译为 jOOQ SQL 条件表达式。
 *
 * <h2>核心职责</h2>
 * <ul>
 *   <li>过滤翻译：将 {@link MetricFilterDsl} 递归翻译为 jOOQ {@link Condition}</li>
 *   <li>参数绑定：所有字面量值转换为参数化占位符，防止 SQL 注入</li>
 *   <li>值规范化：委托 {@link MetricJdbcValueNormalizer} 将 DSL 字面量转换为字段期望的 Java 类型</li>
 * </ul>
 *
 * <h2>支持的过滤运算</h2>
 * <ul>
 *   <li><b>比较运算</b>：EQ, NE, GT, GE, LT, LE</li>
 *   <li><b>集合运算</b>：IN, NOT_IN</li>
 *   <li><b>空值判断</b>：IS_NULL, IS_NOT_NULL</li>
 *   <li><b>逻辑运算</b>：AND, OR（支持任意深度嵌套）</li>
 * </ul>
 *
 * <h2>使用场景</h2>
 * <p>被以下三种查询共用：
 * <ul>
 *   <li>实时查询：直接从数据源表查询</li>
 *   <li>有限行集查询：先过滤再聚合的查询</li>
 *   <li>物化查询：从快照表查询</li>
 * </ul>
 *
 * <h2>设计约束</h2>
 * <p><b>不解释宿主路由：</b>仅负责纯过滤逻辑翻译，不处理表路由、数据源选择、分库分表等宿主层面的逻辑。
 *
 * @author wuxp
 */
final class MetricJdbcFilterRenderer {

    private final MetricJdbcValueNormalizer normalizer;

    MetricJdbcFilterRenderer(MetricJdbcValueNormalizer normalizer) {
        this.normalizer = normalizer;
    }

    Condition render(MetricJdbcBinding binding, MetricFilterDsl filter,
                     Map<String, MetricSqlBinding> parameters, Function<String, Field<Object>> columns) {
        if (filter instanceof ComparisonMetricFilterDsl(MetricFilterOperator operator, String fieldRef, MetricLiteralDsl value1)) {
            Field<Object> value = MetricJdbcSqlCompiler.parameter(parameters,
                    normalizer.literal(binding, fieldRef, value1));
            Field<Object> field = columns.apply(fieldRef);
            return switch (operator) {
                case EQ -> field.eq(value);
                case NE -> field.ne(value);
                case GT -> field.gt(value);
                case GE -> field.ge(value);
                case LT -> field.lt(value);
                case LE -> field.le(value);
                default -> throw new IllegalArgumentException("Unsupported comparison operator");
            };
        }

        if (filter instanceof SetMetricFilterDsl(MetricFilterOperator operator, String fieldRef, List<MetricLiteralDsl> values1)) {
            List<Field<?>> values = new ArrayList<>();
            for (MetricLiteralDsl literal : values1) {
                values.add(MetricJdbcSqlCompiler.parameter(parameters, normalizer.literal(binding, fieldRef, literal)));
            }
            Field<?> field = columns.apply(fieldRef);
            return operator == MetricFilterOperator.IN ? field.in(values) : field.notIn(values);
        }

        if (filter instanceof NullMetricFilterDsl(MetricFilterOperator operator, String fieldRef)) {
            Field<?> field = columns.apply(fieldRef);
            return operator == MetricFilterOperator.IS_NULL ? field.isNull() : field.isNotNull();
        }
        LogicalMetricFilterDsl logical = (LogicalMetricFilterDsl) filter;
        List<Condition> operands = new ArrayList<>();
        for (MetricFilterDsl operand : logical.operands()) {
            operands.add(render(binding, operand, parameters, columns));
        }
        return logical.operator() == MetricFilterOperator.AND ? DSL.and(operands) : DSL.or(operands);
    }
}
