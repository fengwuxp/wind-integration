package com.wind.mybatis.plugin;

import com.wind.mybatis.isolation.DataIsolationStrategy.QueryContext;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.ExpressionVisitorAdapter;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.ExistsExpression;
import net.sf.jsqlparser.expression.operators.relational.ParenthesedExpressionList;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.ParenthesedSelect;
import net.sf.jsqlparser.statement.select.PlainSelect;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * 仅校验可信策略生成的单层正向相关 EXISTS，不改变原始业务 SQL 的支持范围。
 * 每个内表的隔离语义仍由适用策略声明，此处只验证结构、别名和相关列。
 *
 * @author wuxp
 * @since 2026-11-01
 */
final class RelationIsolationPredicate {

    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private final QueryContext outer;

    private final Function<Table, QueryContext> tableResolver;

    private final List<Relation> relations = new ArrayList<>();

    private final Set<ParenthesedSelect> allowed = new HashSet<>();

    RelationIsolationPredicate(QueryContext outer, Function<Table, QueryContext> tableResolver) {
        this.outer = outer;
        this.tableResolver = tableResolver;
    }

    List<Relation> validate(Expression expression, Set<ParenthesedSelect> subqueries) {
        collectPositiveExists(expression);
        /*
         * 未从正向 AND/OR 位置取得的子查询一律拒绝，包括 CASE、比较、函数及内部嵌套。
         */
        if (!allowed.equals(subqueries)) {
            throw unsupported();
        }
        return List.copyOf(relations);
    }

    private void collectPositiveExists(Expression expression) {
        if (expression instanceof ParenthesedExpressionList<?> parenthesis && parenthesis.size() == 1) {
            collectPositiveExists(parenthesis.getFirst());
        } else if (expression instanceof AndExpression and) {
            collectPositiveExists(and.getLeftExpression());
            collectPositiveExists(and.getRightExpression());
        } else if (expression instanceof OrExpression or) {
            collectPositiveExists(or.getLeftExpression());
            collectPositiveExists(or.getRightExpression());
        } else if (expression instanceof ExistsExpression exists) {
            validateExists(exists);
        }
    }

    private void validateExists(ExistsExpression exists) {
        if (exists.isNot() || !(exists.getRightExpression() instanceof ParenthesedSelect subquery)
                || !(subquery.getSelect() instanceof PlainSelect query)
                || !(query.getFromItem() instanceof Table table)) {
            throw unsupported();
        }
        validateSelect(subquery, query);
        Map<String, QueryContext> names = new LinkedHashMap<>();
        names.put(normalize(outer.qualifier()), outer);
        List<QueryContext> innerTables = new ArrayList<>();
        innerTables.add(register(table, names));
        List<Join> joins = query.getJoins();
        if (joins != null && !joins.isEmpty()) {
            if (joins.size() != 1) {
                throw unsupported();
            }
            Join join = joins.getFirst();
            if (join.isSimple() || join.isOuter() || join.isLeft() || join.isRight() || join.isFull()
                    || join.isCross() || join.isNatural() || join.isSemi() || join.isApply()
                    || join.isStraight() || join.isWindowJoin() || !join.getUsingColumns().isEmpty()
                    || join.getOnExpressions().size() != 1 || !(join.getRightItem() instanceof Table joined)) {
                throw unsupported();
            }
            innerTables.add(register(joined, names));
            join.getOnExpressions().forEach(expression -> validateColumns(expression, names));
        }
        validateColumns(query.getWhere(), names);
        if (!hasMandatoryCorrelation(query.getWhere(), names)) {
            throw unsupported();
        }
        allowed.add(subquery);
        relations.add(new Relation(query, List.copyOf(innerTables)));
    }

    private void validateSelect(ParenthesedSelect subquery, PlainSelect query) {
        if (subquery.getASTNode() == null || subquery.getWithItemsList() != null && !subquery.getWithItemsList().isEmpty()
                || query.getWhere() == null || query.getSelectItems().size() != 1
                || !(query.getSelectItems().getFirst().getExpression() instanceof LongValue value) || value.getValue() != 1
                || query.getDistinct() != null || query.getLimit() != null || query.getOffset() != null
                || query.getFetch() != null || query.getTop() != null || query.getSkip() != null
                || query.getFirst() != null || query.getHaving() != null || query.getGroupBy() != null
                || query.getOrderByElements() != null || query.getIntoTables() != null
                || query.getOracleHierarchical() != null || query.getKsqlWindow() != null
                || query.getForMode() != null || query.getForUpdateTable() != null
                || query.getWait() != null || query.isNoWait()
                || SelectIsolationRewriter.hasUnsupportedClauses(query)) {
            throw unsupported();
        }
    }

    private QueryContext register(Table table, Map<String, QueryContext> names) {
        if (table.getAlias() == null || !IDENTIFIER_PATTERN.matcher(table.getAlias().getName()).matches()) {
            throw unsupported();
        }
        QueryContext context = tableResolver.apply(table);
        if (names.putIfAbsent(normalize(context.qualifier()), context) != null) {
            throw unsupported();
        }
        return context;
    }

    private void validateColumns(Expression expression, Map<String, QueryContext> names) {
        expression.accept(new ExpressionVisitorAdapter<Void>() {
            @Override
            public <S> Void visit(Column column, S state) {
                if (column.getTable() == null || column.getTable().getFullyQualifiedName() == null) {
                    throw unsupported();
                }
                QueryContext context = names.get(normalize(column.getTable().getFullyQualifiedName()));
                if (context == null || context.entity().propertyColumns().values().stream()
                        .noneMatch(name -> normalize(name).equals(normalize(column.getColumnName())))) {
                    throw unsupported();
                }
                return null;
            }

            @Override
            public <S> Void visit(ParenthesedSelect subquery, S state) {
                throw unsupported();
            }
        });
    }

    private boolean hasMandatoryCorrelation(Expression expression, Map<String, QueryContext> names) {
        if (expression instanceof ParenthesedExpressionList<?> parenthesis && parenthesis.size() == 1) {
            return hasMandatoryCorrelation(parenthesis.getFirst(), names);
        }
        if (expression instanceof AndExpression and) {
            return hasMandatoryCorrelation(and.getLeftExpression(), names)
                    || hasMandatoryCorrelation(and.getRightExpression(), names);
        }
        if (!(expression instanceof EqualsTo equals) || !(equals.getLeftExpression() instanceof Column left)
                || !(equals.getRightExpression() instanceof Column right)) {
            return false;
        }
        QueryContext leftTable = names.get(normalize(left.getTable().getFullyQualifiedName()));
        QueryContext rightTable = names.get(normalize(right.getTable().getFullyQualifiedName()));
        return leftTable == outer && rightTable != outer || rightTable == outer && leftTable != outer;
    }

    private static String normalize(String name) {
        return name.replace("`", "").replace("\"", "").replace("[", "").replace("]", "").toLowerCase(Locale.ROOT);
    }

    private IllegalArgumentException unsupported() {
        return new IllegalArgumentException("Unsupported isolation relation predicate: " + outer.statementId());
    }

    record Relation(PlainSelect query, List<QueryContext> tables) {
    }
}
