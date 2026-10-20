package com.wind.mybatis.plugin;

import com.mybatisflex.core.BaseMapper;
import com.mybatisflex.core.table.TableInfo;
import com.mybatisflex.core.table.TableInfoFactory;
import com.wind.integration.core.context.DataIsolationQueryIgnoreHolder;
import com.wind.mybatis.flex.MybatisFlexReadParameters;
import com.wind.mybatis.isolation.DataIsolationScope;
import com.wind.mybatis.plugin.DataIsolationQueryInterceptor.Rule;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.ExpressionVisitorAdapter;
import net.sf.jsqlparser.expression.JdbcParameter;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.Parenthesis;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.expression.operators.relational.InExpression;
import net.sf.jsqlparser.parser.CCJSqlParser;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.parser.Node;
import net.sf.jsqlparser.parser.SimpleNode;
import net.sf.jsqlparser.parser.Token;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.select.FromItem;
import net.sf.jsqlparser.statement.select.ParenthesisFromItem;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SubJoin;
import net.sf.jsqlparser.util.deparser.ExpressionDeParser;
import net.sf.jsqlparser.util.deparser.SelectDeParser;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.session.Configuration;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static net.sf.jsqlparser.parser.CCJSqlParserConstants.K_TABLE;
import static net.sf.jsqlparser.parser.CCJSqlParserTreeConstants.JJTSUBSELECT;
import static net.sf.jsqlparser.parser.CCJSqlParserTreeConstants.JJTWITHITEM;

/**
 * 单表 SELECT 的内部改写器，保留原参数身份并按改写后的占位符顺序重新绑定。
 *
 * @author wuxp
 * @since 2026-10-16
 */
final class SelectIsolationRewriter {

    private final Map<String, List<ColumnRule>> tables;

    private final int mapperCount;

    SelectIsolationRewriter(Configuration configuration, List<Rule> rules) {
        List<Class<?>> mappers = List.copyOf(configuration.getMapperRegistry().getMappers());
        mapperCount = mappers.size();
        Map<String, List<ColumnRule>> registered = new HashMap<>();
        Set<Class<?>> entities = new HashSet<>();
        for (Class<?> mapper : mappers) {
            if (!BaseMapper.class.isAssignableFrom(mapper)) {
                continue;
            }
            TableInfo table = TableInfoFactory.ofMapperClass(mapper);
            if (table == null) {
                throw new IllegalArgumentException("Cannot resolve mapper entity for isolation: " + mapper.getName());
            }
            if (!entities.add(table.getEntityClass())) {
                continue;
            }
            List<ColumnRule> bindings = new ArrayList<>();
            for (Rule rule : rules) {
                if (!rule.dimension().isolationType().isAssignableFrom(table.getEntityClass())) {
                    continue;
                }
                String column = table.getPropertyColumnMapping().get(rule.property());
                if (column == null || !column.matches("[A-Za-z_][A-Za-z0-9_]*")) {
                    throw new IllegalArgumentException("Missing or unsupported isolation column mapping: "
                            + table.getEntityClass().getName() + "." + rule.property());
                }
                bindings.add(new ColumnRule(rule, column));
            }
            String name = simpleName(table.getTableName());
            List<ColumnRule> previous = registered.putIfAbsent(name, List.copyOf(bindings));
            if (previous != null && !previous.equals(bindings)) {
                throw new IllegalArgumentException("Conflicting isolation mappings for table: " + name);
            }
        }
        tables = Map.copyOf(registered);
    }

    int mapperCount() {
        return mapperCount;
    }

    BoundSql rewrite(MappedStatement statement, BoundSql original) {
        CCJSqlParser parser = CCJSqlParserUtil.newParser(original.getSql()).withAllowComplexParsing(
                CCJSqlParserUtil.getNestingDepth(original.getSql()) <= CCJSqlParserUtil.ALLOWED_NESTING_DEPTH);
        Select select;
        try {
            if (!(CCJSqlParserUtil.parseStatement(parser) instanceof Select parsed)) {
                throw new IllegalStateException("Read isolation requires a SELECT statement: " + statement.getId());
            }
            select = parsed;
        } catch (JSQLParserException exception) {
            throw new IllegalStateException("Cannot parse query for read isolation: " + statement.getId(), exception);
        }
        Node root = parser.getASTRoot();
        rejectTableQueryShorthand(statement, root);
        QueryShape shape = new QueryShape(root);
        boolean managed = shape.tableNames.stream().map(SelectIsolationRewriter::simpleName)
                .map(tables::get).anyMatch(rules -> rules != null && !rules.isEmpty());
        if (!managed) {
            return original;
        }
        if (shape.nested || !(select.getSelectBody() instanceof PlainSelect query)) {
            throw unsupported(statement);
        }
        if (!(query.getFromItem() instanceof Table table) || query.getJoins() != null && !query.getJoins().isEmpty()) {
            throw unsupported(statement);
        }
        if (query.getIntoTables() != null || query.getTop() != null || query.getSkip() != null || query.getFirst() != null
                || query.getFetch() != null || query.getOracleHierarchical() != null || query.getKsqlWindow() != null) {
            throw unsupported(statement);
        }
        if (query.getDistinct() != null && query.getDistinct().getOnSelectItems() != null) {
            throw unsupported(statement);
        }
        List<ColumnRule> bindingsForTable = tables.get(simpleName(table.getName()));
        if (bindingsForTable == null) {
            throw unsupported(statement);
        }
        original = MybatisFlexReadParameters.adapt(statement, original);
        List<ParameterMapping> bindings = new ArrayList<>(original.getParameterMappings());
        Map<String, Object> additional = new LinkedHashMap<>(original.getAdditionalParameters());
        Expression condition = null;
        for (ColumnRule binding : bindingsForTable) {
            Rule rule = binding.rule();
            if (DataIsolationQueryIgnoreHolder.isIgnored(rule.dimension())) {
                continue;
            }
            DataIsolationScope scope = rule.scopeSupplier().get();
            if (scope == null) {
                throw new IllegalStateException("Missing isolation scope: " + rule.dimension().isolationType().getName());
            }
            if (scope.unrestricted()) {
                continue;
            }
            Expression fieldCondition = predicate(statement, original, table, binding.column(), scope, bindings, additional);
            condition = condition == null ? fieldCondition : new AndExpression(condition, fieldCondition);
        }
        if (condition == null) {
            return original;
        }
        if (query.getWhere() != null) {
            condition = new AndExpression(new Parenthesis(query.getWhere()), new Parenthesis(condition));
        }
        query.setWhere(condition);

        StringBuilder sql = new StringBuilder();
        List<ParameterMapping> ordered = new ArrayList<>();
        Set<Integer> visited = new HashSet<>();
        ExpressionDeParser expressions = new ExpressionDeParser() {
            @Override
            public void visit(JdbcParameter parameter) {
                appendBinding(parameter, bindings, ordered, visited);
                getBuffer().append('?');
            }
        };
        SelectDeParser selections = new SelectDeParser(expressions, sql);
        expressions.setBuffer(sql);
        expressions.setSelectVisitor(selections);
        query.accept(selections);
        // 4.3 的 LIMIT/OFFSET 通过 toString 输出，不经过 ExpressionDeParser。
        ExpressionVisitorAdapter trailing = new ExpressionVisitorAdapter() {
            @Override
            public void visit(JdbcParameter parameter) {
                appendBinding(parameter, bindings, ordered, visited);
            }
        };
        if (query.getLimit() != null) {
            if (query.getLimit().getOffset() != null) {
                query.getLimit().getOffset().accept(trailing);
            }
            if (query.getLimit().getRowCount() != null) {
                query.getLimit().getRowCount().accept(trailing);
            }
        }
        if (query.getOffset() != null) {
            query.getOffset().getOffset().accept(trailing);
        }
        if (ordered.size() != bindings.size()) {
            throw new IllegalStateException("Unsupported parameter placement in isolation query: " + statement.getId());
        }
        BoundSql result = new BoundSql(statement.getConfiguration(), sql.toString(), ordered, original.getParameterObject());
        additional.forEach(result::setAdditionalParameter);
        return result;
    }

    private Expression predicate(MappedStatement statement, BoundSql original, Table table, String columnName,
                                 DataIsolationScope scope, List<ParameterMapping> bindings, Map<String, Object> additional) {
        if (scope.values().isEmpty()) {
            return new EqualsTo(new LongValue(1), new LongValue(0));
        }
        String qualifier = table.getAlias() == null ? table.getFullyQualifiedName() : table.getAlias().getName();
        Column column = new Column(new Table(qualifier), columnName);
        List<Expression> parameters = new ArrayList<>();
        for (Object value : scope.values()) {
            String name = "windIsolation" + bindings.size();
            while (additional.containsKey(name) || usesProperty(original.getParameterMappings(), name)) {
                name += "Value";
            }
            additional.put(name, value);
            bindings.add(new ParameterMapping.Builder(statement.getConfiguration(), name, value.getClass()).build());
            parameters.add(new JdbcParameter(bindings.size(), false));
        }
        if (parameters.size() == 1) {
            return new EqualsTo(column, parameters.getFirst());
        }
        return new InExpression(column, new ExpressionList(parameters));
    }

    private boolean usesProperty(List<ParameterMapping> mappings, String name) {
        return mappings.stream().map(ParameterMapping::getProperty)
                .anyMatch(property -> property.equals(name) || property.startsWith(name + ".") || property.startsWith(name + "["));
    }

    private void rejectTableQueryShorthand(MappedStatement statement, Node root) {
        // 4.3 会把部分 TABLE 查询简写当作行构造表达式；按词法 token 拒绝，避免误伤注释和字符串。
        for (Token token = ((SimpleNode) root).jjtGetFirstToken(); token != null; token = token.next) {
            if ("(".equals(token.image) && token.next != null && token.next.kind == K_TABLE) {
                throw unsupported(statement);
            }
        }
    }

    private static void appendBinding(JdbcParameter parameter, List<ParameterMapping> bindings,
                                      List<ParameterMapping> ordered, Set<Integer> visited) {
        Integer index = parameter.getIndex();
        if (index == null || index < 1 || index > bindings.size() || !visited.add(index)) {
            throw new IllegalStateException("Unsupported JDBC parameter in isolation query");
        }
        ordered.add(bindings.get(index - 1));
    }

    private static String simpleName(String name) {
        String unquoted = name.replace("`", "").replace("\"", "").replace("[", "").replace("]", "");
        return unquoted.substring(unquoted.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }

    private IllegalStateException unsupported(MappedStatement statement) {
        return new IllegalStateException("Read isolation supports single-table SELECT only: " + statement.getId());
    }

    /**
     * 将一个隔离维度绑定到当前实体的实际数据库列。
     *
     * @param rule   隔离维度及其范围供应者
     * @param column 实体元数据解析出的物理列
     * @author wuxp
     * @since 2026-10-19
     */
    private record ColumnRule(Rule rule, String column) {
    }

    /**
     * 遍历完整解析树识别子查询，只从 FROM 关系收集表，避免把投影别名当成物理表。
     *
     * @author wuxp
     * @since 2026-10-16
     */
    private static final class QueryShape {

        private final Set<String> tableNames = new HashSet<>();

        private boolean nested;

        private QueryShape(Node root) {
            Deque<Node> pending = new ArrayDeque<>();
            pending.push(root);
            while (!pending.isEmpty()) {
                SimpleNode node = (SimpleNode) pending.pop();
                // 表名 finder 会跳过部分表达式，并将同名 CTE 的物理表一并排除。
                if (node.getId() == JJTSUBSELECT || node.getId() == JJTWITHITEM) {
                    nested = true;
                }
                if (node.jjtGetValue() instanceof PlainSelect query) {
                    collectFromItem(query.getFromItem());
                    if (query.getJoins() != null) {
                        query.getJoins().forEach(join -> collectFromItem(join.getRightItem()));
                    }
                }
                for (int i = 0; i < node.jjtGetNumChildren(); i++) {
                    pending.push(node.jjtGetChild(i));
                }
            }
        }

        private void collectFromItem(@Nullable FromItem item) {
            if (item instanceof Table table) {
                tableNames.add(table.getFullyQualifiedName());
            } else if (item instanceof ParenthesisFromItem parenthesis) {
                collectFromItem(parenthesis.getFromItem());
            } else if (item instanceof SubJoin join) {
                collectFromItem(join.getLeft());
                join.getJoinList().forEach(part -> collectFromItem(part.getRightItem()));
            }
        }
    }
}
