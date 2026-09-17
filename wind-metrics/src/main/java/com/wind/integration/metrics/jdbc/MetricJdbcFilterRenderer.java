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
 * 实时、有限行集和物化共用的参数化过滤翻译，不解释宿主路由。
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
        if (filter instanceof ComparisonMetricFilterDsl comparison) {
            Field<Object> value = MetricJdbcSqlCompiler.parameter(parameters,
                    normalizer.literal(binding, comparison.fieldRef(), comparison.value()));
            Field<Object> field = columns.apply(comparison.fieldRef());
            return switch (comparison.operator()) {
                case EQ -> field.eq(value);
                case NE -> field.ne(value);
                case GT -> field.gt(value);
                case GE -> field.ge(value);
                case LT -> field.lt(value);
                case LE -> field.le(value);
                default -> throw new IllegalArgumentException("Unsupported comparison operator");
            };
        }
        if (filter instanceof SetMetricFilterDsl set) {
            List<Field<?>> values = new ArrayList<>();
            for (MetricLiteralDsl literal : set.values()) {
                values.add(MetricJdbcSqlCompiler.parameter(parameters,
                        normalizer.literal(binding, set.fieldRef(), literal)));
            }
            Field<?> field = columns.apply(set.fieldRef());
            return set.operator() == MetricFilterOperator.IN ? field.in(values) : field.notIn(values);
        }
        if (filter instanceof NullMetricFilterDsl nullFilter) {
            Field<?> field = columns.apply(nullFilter.fieldRef());
            return nullFilter.operator() == MetricFilterOperator.IS_NULL ? field.isNull() : field.isNotNull();
        }
        LogicalMetricFilterDsl logical = (LogicalMetricFilterDsl) filter;
        List<Condition> operands = new ArrayList<>();
        for (MetricFilterDsl operand : logical.operands()) {
            operands.add(render(binding, operand, parameters, columns));
        }
        return logical.operator() == MetricFilterOperator.AND ? DSL.and(operands) : DSL.or(operands);
    }
}
