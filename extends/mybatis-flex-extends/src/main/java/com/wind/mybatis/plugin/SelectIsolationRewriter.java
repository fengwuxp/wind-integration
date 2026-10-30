package com.wind.mybatis.plugin;

import com.mybatisflex.core.BaseMapper;
import com.mybatisflex.core.table.TableInfo;
import com.mybatisflex.core.table.TableInfoFactory;
import com.wind.mybatis.flex.MybatisFlexReadParameters;
import com.wind.mybatis.isolation.DataIsolationStrategy;
import com.wind.mybatis.isolation.DataIsolationStrategy.Condition;
import com.wind.mybatis.isolation.DataIsolationStrategy.EntityMetadata;
import com.wind.mybatis.isolation.DataIsolationStrategy.QueryContext;
import com.wind.mybatis.isolation.FieldDataIsolationStrategy;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.ExpressionVisitorAdapter;
import net.sf.jsqlparser.expression.JdbcNamedParameter;
import net.sf.jsqlparser.expression.JdbcParameter;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.NotExpression;
import net.sf.jsqlparser.expression.Parenthesis;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.relational.ExistsExpression;
import net.sf.jsqlparser.parser.CCJSqlParser;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.parser.Node;
import net.sf.jsqlparser.parser.SimpleNode;
import net.sf.jsqlparser.parser.Token;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.select.FromItem;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.ParenthesisFromItem;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectExpressionItem;
import net.sf.jsqlparser.statement.select.SubJoin;
import net.sf.jsqlparser.statement.select.SubSelect;
import net.sf.jsqlparser.util.deparser.ExpressionDeParser;
import net.sf.jsqlparser.util.deparser.SelectDeParser;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.session.Configuration;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
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

    private final Map<String, TableBinding> tables;

    private final Set<String> unqualifiedTables;

    private final int mapperCount;

    /**
     * 已确认无需隔离的单表 SQL 缓存。
     * key 为 MyBatis 语句 ID，value 为该语句最近一次确认的 SQL；仅 SQL 完全一致时跳过解析。
     * 缓存由多线程共享，按最近访问顺序最多保留 256 条；Mapper 元数据刷新时随重写器重建。
     */
    private final Map<String, String> unisolatedStatements = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                    return size() > 256;
                }
            });

    SelectIsolationRewriter(Configuration configuration, DataIsolationStrategy... strategies) {
        List<Class<?>> mappers = List.copyOf(configuration.getMapperRegistry().getMappers());
        mapperCount = mappers.size();
        Map<String, TableBinding> registered = new HashMap<>();
        Set<String> unqualified = new HashSet<>();
        Set<String> qualified = new HashSet<>();
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
            EntityMetadata entity = new EntityMetadata(table.getEntityClass(), table.getPropertyColumnMapping());
            List<DataIsolationStrategy> applicable = new ArrayList<>();
            List<Object> signatures = new ArrayList<>();
            for (DataIsolationStrategy strategy : strategies) {
                if (!strategy.supports(entity)) {
                    continue;
                }
                applicable.add(strategy);
                Object signature = strategy instanceof FieldDataIsolationStrategy fields ? fields.bindings(entity) : entity;
                signatures.add(List.of(strategy, signature));
            }
            String name = simpleName(table.getTableName());
            if (table.getTableName().contains(".") || table.getSchema() != null && !table.getSchema().isBlank()) {
                qualified.add(name);
            } else {
                unqualified.add(name);
            }
            TableBinding binding = new TableBinding(entity, List.copyOf(applicable), List.copyOf(signatures));
            TableBinding previous = registered.putIfAbsent(name, binding);
            if (previous != null && !previous.signatures().equals(binding.signatures())) {
                throw new IllegalArgumentException("Conflicting isolation mappings for table: " + name);
            }
        }
        tables = Map.copyOf(registered);
        unqualified.removeAll(qualified);
        unqualifiedTables = Set.copyOf(unqualified);
    }

    int mapperCount() {
        return mapperCount;
    }

    BoundSql rewrite(MappedStatement statement, BoundSql original) {
        String sqlText = original.getSql();
        if (sqlText.equals(unisolatedStatements.get(statement.getId()))) {
            return original;
        }
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
                .map(tables::get).anyMatch(binding -> binding != null && !binding.strategies().isEmpty());
        if (!managed) {
            if (!shape.nested && select.getSelectBody() instanceof PlainSelect query
                    && query.getFromItem() instanceof Table table && (query.getJoins() == null || query.getJoins().isEmpty())) {
                TableBinding binding = tables.get(simpleName(table.getName()));
                if (binding != null && binding.strategies().isEmpty()) {
                    unisolatedStatements.put(statement.getId(), sqlText);
                }
            }
            return original;
        }
        if (!(select.getSelectBody() instanceof PlainSelect query)) {
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
        TableBinding bindingsForTable = tables.get(simpleName(table.getName()));
        if (bindingsForTable == null) {
            throw unsupported(statement);
        }
        if (shape.nested) {
            validateExistsSubqueries(statement, query, shape);
        }
        original = MybatisFlexReadParameters.adapt(statement, original);
        List<ParameterMapping> bindings = new ArrayList<>(original.getParameterMappings());
        Map<String, Object> additional = new LinkedHashMap<>(original.getAdditionalParameters());
        Expression condition = null;
        String qualifier = table.getAlias() == null ? table.getFullyQualifiedName() : table.getAlias().getName();
        QueryContext context = new QueryContext(statement.getId(), table.getFullyQualifiedName(), qualifier, bindingsForTable.entity());
        for (DataIsolationStrategy strategy : bindingsForTable.strategies()) {
            List<Condition> conditions = Objects.requireNonNull(strategy.resolve(context), "isolation conditions");
            for (Condition resolved : conditions) {
                Expression predicate = predicate(statement, original, resolved, bindings, additional);
                Expression grouped = new Parenthesis(predicate);
                condition = condition == null ? grouped : new AndExpression(condition, grouped);
            }
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

    private Expression predicate(MappedStatement statement, BoundSql original, Condition condition,
                                 List<ParameterMapping> bindings, Map<String, Object> additional) {
        Expression expression;
        CCJSqlParser[] parsed = new CCJSqlParser[1];
        try {
            expression = CCJSqlParserUtil.parseCondExpression(condition.sql(), false, parser -> parsed[0] = parser);
        } catch (JSQLParserException exception) {
            throw new IllegalArgumentException("Cannot parse isolation condition", exception);
        }
        rejectTableQueryShorthand(statement, parsed[0].getASTRoot());
        QueryShape shape = new QueryShape(parsed[0].getASTRoot());
        if (shape.nested || !shape.tableNames.isEmpty()) {
            throw new IllegalArgumentException("Isolation strategy conditions do not support subqueries");
        }
        List<JdbcParameter> parameters = new ArrayList<>();
        /*
         * 使用与最终 SQL 输出一致的遍历器，避免普通 Visitor 漏掉 ESCAPE、FROM/FOR 等位置的参数。
         */
        expression.accept(new ExpressionDeParser(null, new StringBuilder()) {
            @Override
            public void visit(JdbcNamedParameter parameter) {
                throw new IllegalArgumentException("Isolation condition must use positional placeholders");
            }

            @Override
            public void visit(JdbcParameter parameter) {
                if (parameter.isUseFixedIndex()) {
                    throw new IllegalArgumentException("Isolation condition must use positional placeholders");
                }
                parameters.add(parameter);
                super.visit(parameter);
            }
        });
        if (parameters.size() != condition.values().size()) {
            throw new IllegalArgumentException("Isolation condition parameter count does not match values");
        }
        for (int i = 0; i < parameters.size(); i++) {
            Object value = condition.values().get(i);
            String name = "windIsolation" + bindings.size();
            while (additional.containsKey(name) || usesProperty(original.getParameterMappings(), name)) {
                name += "Value";
            }
            additional.put(name, value);
            Class<?> javaType = value == null ? Object.class : value.getClass();
            bindings.add(new ParameterMapping.Builder(statement.getConfiguration(), name, javaType).build());
            parameters.get(i).setIndex(bindings.size());
        }
        return expression;
    }

    private boolean usesProperty(List<ParameterMapping> mappings, String name) {
        return mappings.stream().map(ParameterMapping::getProperty)
                .anyMatch(property -> property.equals(name) || property.startsWith(name + ".") || property.startsWith(name + "["));
    }

    private void validateExistsSubqueries(MappedStatement statement, PlainSelect query, QueryShape shape) {
        if (shape.hasWith || query.getWhere() == null) {
            throw unsupported(statement);
        }
        Table outer = (Table) query.getFromItem();
        String outerName = simpleName(outer.getAlias() == null ? outer.getName() : outer.getAlias().getName());
        Set<Node> allowed = new HashSet<>();
        query.getWhere().accept(new ExpressionVisitorAdapter() {
            @Override
            public void visit(NotExpression expression) {
                throw unsupported(statement);
            }

            @Override
            public void visit(ExistsExpression expression) {
                if (expression.isNot() || !(expression.getRightExpression() instanceof SubSelect subquery)
                        || !(subquery.getSelectBody() instanceof PlainSelect inner)
                        || !(inner.getFromItem() instanceof Table table)
                        || subquery.getWithItemsList() != null && !subquery.getWithItemsList().isEmpty()
                        || inner.getSelectItems().size() != 1
                        || !(inner.getSelectItems().getFirst() instanceof SelectExpressionItem item)
                        || !(item.getExpression() instanceof LongValue value) || value.getValue() != 1
                        || inner.getLimit() != null || inner.getOffset() != null || inner.getFetch() != null
                        || inner.getTop() != null || inner.getSkip() != null || inner.getFirst() != null
                        || inner.getHaving() != null || inner.getGroupBy() != null || inner.getOrderByElements() != null
                        || inner.getIntoTables() != null || inner.getOracleHierarchical() != null
                        || inner.getKsqlWindow() != null || inner.isForUpdate() || inner.getForUpdateTable() != null
                        || inner.getWait() != null || inner.isNoWait()) {
                    throw unsupported(statement);
                }
                if (subquery.getASTNode() == null) {
                    throw unsupported(statement);
                }
                Set<String> names = new HashSet<>();
                names.add(outerName);
                validateUnisolatedTable(statement, table, names);
                List<Join> joins = inner.getJoins();
                if (joins != null && !joins.isEmpty()) {
                    if (joins.size() != 1) {
                        throw unsupported(statement);
                    }
                    Join join = joins.getFirst();
                    if (join.isSimple() || join.isOuter() || join.isLeft() || join.isRight() || join.isFull()
                            || join.isCross() || join.isNatural() || join.isSemi() || join.isApply()
                            || join.isStraight() || join.isWindowJoin() || !join.getUsingColumns().isEmpty()
                            || join.getOnExpressions().size() != 1 || !(join.getRightItem() instanceof Table joined)) {
                        throw unsupported(statement);
                    }
                    validateUnisolatedTable(statement, joined, names);
                }
                allowed.add(subquery.getASTNode());
            }
        });
        // 逐个核对子查询节点，避免遗漏投影、排序及 EXISTS 内部的嵌套查询。
        if (!allowed.equals(shape.subselectNodes)) {
            throw unsupported(statement);
        }
    }

    private void validateUnisolatedTable(MappedStatement statement, Table table, Set<String> names) {
        TableBinding binding = tables.get(simpleName(table.getName()));
        String name = simpleName(table.getAlias() == null ? table.getName() : table.getAlias().getName());
        if (binding == null || !binding.strategies().isEmpty() || !unqualifiedTables.contains(simpleName(table.getName()))
                || table.getName().contains(".") || !table.getFullyQualifiedName().equals(table.getName())
                || table.getPivot() != null || table.getUnPivot() != null || !names.add(name)) {
            throw unsupported(statement);
        }
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
     * 当前工厂中实体与适用策略的静态绑定；签名用于验证同名表的规则一致性。
     *
     * @param entity     实体元数据
     * @param strategies 适用策略
     * @param signatures 同名表映射的一致性签名
     * @author wuxp
     * @since 2026-10-30
     */
    private record TableBinding(EntityMetadata entity, List<DataIsolationStrategy> strategies, List<Object> signatures) {
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

        private boolean hasWith;

        private final Set<Node> subselectNodes = new HashSet<>();

        private QueryShape(Node root) {
            Deque<Node> pending = new ArrayDeque<>();
            pending.push(root);
            while (!pending.isEmpty()) {
                SimpleNode node = (SimpleNode) pending.pop();
                // 表名 finder 会跳过部分表达式，并将同名 CTE 的物理表一并排除。
                if (node.getId() == JJTSUBSELECT || node.getId() == JJTWITHITEM) {
                    nested = true;
                }
                if (node.getId() == JJTSUBSELECT) {
                    subselectNodes.add(node);
                } else if (node.getId() == JJTWITHITEM) {
                    hasWith = true;
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
