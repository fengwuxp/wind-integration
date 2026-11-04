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
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.relational.ExistsExpression;
import net.sf.jsqlparser.expression.operators.relational.ParenthesedExpressionList;
import net.sf.jsqlparser.parser.CCJSqlParser;
import net.sf.jsqlparser.parser.CCJSqlParserConstants;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.parser.CCJSqlParserTreeConstants;
import net.sf.jsqlparser.parser.Node;
import net.sf.jsqlparser.parser.Token;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.select.FromItem;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.ParenthesedFromItem;
import net.sf.jsqlparser.statement.select.ParenthesedSelect;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SetOperationList;
import net.sf.jsqlparser.statement.select.TableStatement;
import net.sf.jsqlparser.statement.select.UnionOp;
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
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static net.sf.jsqlparser.parser.CCJSqlParserConstants.K_TABLE;

/**
 * 对受支持查询的物理表叶节点追加隔离，保留原参数身份并按最终 SQL 顺序重新绑定。
 *
 * @author wuxp
 * @since 2026-10-16
 */
final class SelectIsolationRewriter {

    private static final Set<String> UNION_MODIFIERS = Set.of("", "ALL", "DISTINCT");

    /**
     * 解析是 CPU 工作，按可用处理器限制并发；256 项队列吸收短时突发，满载时明确拒绝。
     * 池随模块共享，不随 Mapper 刷新重建；守护线程不继承请求上下文，闲置一分钟后回收。
     */
    private static final ExecutorService PARSER_EXECUTOR = createParserExecutor();

    private final ExecutorService parserExecutor;

    private final boolean strict;

    private final Map<String, TableBinding> tables;

    private final Set<Class<?>> protectedEntities;

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
        this(configuration, false, Set.of(), strategies);
    }

    SelectIsolationRewriter(Configuration configuration, boolean strict, Set<Class<?>> publicEntities,
                            DataIsolationStrategy... strategies) {
        this(configuration, PARSER_EXECUTOR, strict, publicEntities, strategies);
    }

    /**
     * 借用指定解析执行器，由提供者管理关闭；用于独立验证排队、拒绝和取消边界。
     */
    SelectIsolationRewriter(Configuration configuration, ExecutorService parserExecutor, DataIsolationStrategy... strategies) {
        this(configuration, parserExecutor, false, Set.of(), strategies);
    }

    private SelectIsolationRewriter(Configuration configuration, ExecutorService parserExecutor, boolean strict,
                                    Set<Class<?>> publicEntities, DataIsolationStrategy... strategies) {
        this.parserExecutor = Objects.requireNonNull(parserExecutor, "parser executor");
        this.strict = strict;
        List<Class<?>> mappers = List.copyOf(configuration.getMapperRegistry().getMappers());
        mapperCount = mappers.size();
        Map<String, TableBinding> registered = new HashMap<>();
        Set<String> unqualified = new HashSet<>();
        Set<String> qualified = new HashSet<>();
        Set<Class<?>> entities = new HashSet<>();
        Set<Class<?>> protectedTypes = new HashSet<>();
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
            if (strict) {
                boolean declaredPublic = publicEntities.contains(entity.type());
                if (applicable.isEmpty() && !declaredPublic) {
                    throw new IllegalStateException("Entity has no isolation strategy or public declaration: " + entity.type().getName());
                }
                if (!applicable.isEmpty() && declaredPublic) {
                    throw new IllegalStateException("Public entity also matches an isolation strategy: " + entity.type().getName());
                }
            }
            String name = simpleName(table.getTableName());
            if (!applicable.isEmpty()) {
                protectedTypes.add(entity.type());
            }
            if (table.getTableName().contains(".") || table.getSchema() != null && !table.getSchema().isBlank()) {
                if (strict) {
                    throw new IllegalStateException("Strict isolation does not support schema-qualified mappings: " + entity.type().getName());
                }
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
        protectedEntities = Set.copyOf(protectedTypes);
        for (Class<?> publicEntity : publicEntities) {
            if (!entities.contains(publicEntity)) {
                throw new IllegalStateException("Public entity has no mapper in this SqlSessionFactory: " + publicEntity.getName());
            }
        }
        unqualified.removeAll(qualified);
        unqualifiedTables = Set.copyOf(unqualified);
    }

    int mapperCount() {
        return mapperCount;
    }

    void validateRequiredEntities(Class<?>... requiredEntities) {
        for (Class<?> required : requiredEntities) {
            if (!protectedEntities.contains(required)) {
                throw new IllegalStateException("Required entity has no protected mapper in this SqlSessionFactory: " + required.getName());
            }
        }
    }

    BoundSql rewrite(MappedStatement statement, BoundSql original) {
        String sqlText = original.getSql();
        if (sqlText.equals(unisolatedStatements.get(statement.getId()))) {
            return original;
        }
        CCJSqlParser[] parser = new CCJSqlParser[1];
        Select select;
        try {
            if (!(CCJSqlParserUtil.parse(sqlText, parserExecutor, configured -> parser[0] = configured) instanceof Select parsed)) {
                throw new IllegalStateException("Read isolation requires a SELECT statement: " + statement.getId());
            }
            select = parsed;
        } catch (JSQLParserException | RejectedExecutionException exception) {
            throw new IllegalStateException("Cannot parse query for read isolation: " + statement.getId(), exception);
        }
        Node root = parser[0].getASTRoot();
        validateSqlTokens(statement, parser[0]);
        QueryShape shape = new QueryShape(root);
        if (shape.hasUnsupportedQuery || select instanceof TableStatement) {
            throw unsupported(statement);
        }
        if (strict) {
            if (shape.hasWith) {
                throw unsupported(statement);
            }
            for (String name : shape.tableNames) {
                if (name.contains(".") || !tables.containsKey(simpleName(name))) {
                    throw new IllegalStateException("Query table has no isolation mapping: " + name + ", statement=" + statement.getId());
                }
            }
        }
        boolean managed = shape.tableNames.stream().map(SelectIsolationRewriter::simpleName)
                .map(tables::get).anyMatch(binding -> binding != null && !binding.strategies().isEmpty());
        if (!managed) {
            if (!shape.nested && select instanceof PlainSelect query
                    && query.getFromItem() instanceof Table table && (query.getJoins() == null || query.getJoins().isEmpty())) {
                TableBinding binding = tables.get(simpleName(table.getName()));
                if (binding != null && binding.strategies().isEmpty()) {
                    unisolatedStatements.put(statement.getId(), sqlText);
                }
            }
            return original;
        }
        if (shape.hasWith) {
            throw unsupported(statement);
        }
        List<PlainSelect> leaves = new ArrayList<>();
        Set<ParenthesedSelect> allowed = new HashSet<>();
        collectQueryLeaves(statement, select, false, leaves, allowed);
        for (PlainSelect leaf : leaves) {
            allowed.addAll(validateExistsSubqueries(statement, leaf));
        }
        // 原始解析树中的每个子查询都必须属于结构包装或已验证的 EXISTS。
        if (!allowed.equals(shape.subqueries)) {
            throw unsupported(statement);
        }
        original = MybatisFlexReadParameters.adapt(statement, original);
        List<ParameterMapping> bindings = new ArrayList<>(original.getParameterMappings());
        Map<String, Object> additional = new LinkedHashMap<>(original.getAdditionalParameters());
        Set<String> reservedProperties = parameterRoots(original.getParameterMappings());
        boolean changed = false;
        for (PlainSelect leaf : leaves) {
            changed |= isolateLeaf(statement, leaf, reservedProperties, bindings, additional);
        }
        if (!changed) {
            return original;
        }

        StringBuilder sql = new StringBuilder();
        List<ParameterMapping> ordered = new ArrayList<>();
        Set<Integer> visited = new HashSet<>();
        ExpressionDeParser expressions = new ExpressionDeParser() {
            @Override
            public <S> StringBuilder visit(JdbcParameter parameter, S context) {
                appendBinding(parameter, bindings, ordered, visited);
                return getBuilder().append('?');
            }
        };
        SelectDeParser selections = new SelectDeParser(expressions, sql);
        expressions.setBuilder(sql);
        expressions.setSelectVisitor(selections);
        select.accept(selections);
        if (ordered.size() != bindings.size()) {
            throw new IllegalStateException("Unsupported parameter placement in isolation query: " + statement.getId());
        }
        BoundSql result = new BoundSql(statement.getConfiguration(), sql.toString(), ordered, original.getParameterObject());
        additional.forEach(result::setAdditionalParameter);
        return result;
    }

    /**
     * 派生表外层保留原有计数、过滤和分页，仅向实际读取物理表的叶查询追加隔离。
     * UNION 的各分支独立处理，不把某个分支的实体或范围借给其他分支。
     */
    private void collectQueryLeaves(MappedStatement statement, Select select, boolean derived,
                                    List<PlainSelect> leaves, Set<ParenthesedSelect> allowed) {
        if (select instanceof ParenthesedSelect wrapper) {
            if (wrapper.getClass() != ParenthesedSelect.class
                    || wrapper.getOrderByElements() != null || wrapper.getLimit() != null || wrapper.getOffset() != null
                    || wrapper.getFetch() != null || wrapper.getLimitBy() != null || wrapper.getForMode() != null
                    || !derived && wrapper.getAlias() != null || wrapper.getPivot() != null || wrapper.getUnPivot() != null
                    || wrapper.getSampleClause() != null || wrapper.getWithItemsList() != null && !wrapper.getWithItemsList().isEmpty()) {
                throw unsupported(statement);
            }
            allowed.add(wrapper);
            collectQueryLeaves(statement, wrapper.getSelect(), false, leaves, allowed);
            return;
        }
        if (select instanceof SetOperationList union) {
            if (union.getOperations().stream().anyMatch(operation -> !(operation instanceof UnionOp)
                    || !UNION_MODIFIERS.contains(operation.getModifier().trim().toUpperCase(Locale.ROOT)))
                    || union.getFetch() != null || union.getLimitBy() != null || union.getForMode() != null
                    || union.getSampleClause() != null) {
                throw unsupported(statement);
            }
            for (Select branch : union.getSelects()) {
                collectQueryLeaves(statement, branch, false, leaves, allowed);
            }
            return;
        }
        if (!(select instanceof PlainSelect query)) {
            throw unsupported(statement);
        }
        if (query.getJoins() != null && !query.getJoins().isEmpty()
                || query.getIntoTables() != null || query.getTop() != null || query.getSkip() != null || query.getFirst() != null
                || query.getFetch() != null || query.getOracleHierarchical() != null || query.getKsqlWindow() != null
                || hasUnsupportedClauses(query)) {
            throw unsupported(statement);
        }
        if (query.getDistinct() != null && query.getDistinct().getOnSelectItems() != null) {
            throw unsupported(statement);
        }
        if (query.getFromItem() instanceof ParenthesedSelect source) {
            if (source.getAlias() == null) {
                throw unsupported(statement);
            }
            collectQueryLeaves(statement, source, true, leaves, allowed);
            return;
        }
        if (!(query.getFromItem() instanceof Table table) || !tables.containsKey(simpleName(table.getName()))) {
            throw unsupported(statement);
        }
        leaves.add(query);
    }

    private boolean isolateLeaf(MappedStatement statement, PlainSelect query, Set<String> reservedProperties,
                                List<ParameterMapping> bindings, Map<String, Object> additional) {
        Table table = (Table) query.getFromItem();
        TableBinding binding = tables.get(simpleName(table.getName()));
        String qualifier = table.getAlias() == null ? table.getFullyQualifiedName() : table.getAlias().getName();
        QueryContext context = new QueryContext(statement.getId(), table.getFullyQualifiedName(), qualifier, binding.entity());
        Expression condition = null;
        for (DataIsolationStrategy strategy : binding.strategies()) {
            List<Condition> conditions = Objects.requireNonNull(strategy.resolve(context), "isolation conditions");
            for (Condition resolved : conditions) {
                Expression predicate = predicate(statement, reservedProperties, resolved, bindings, additional, context);
                Expression grouped = new ParenthesedExpressionList<>(predicate);
                condition = condition == null ? grouped : new AndExpression(condition, grouped);
            }
        }
        if (condition == null) {
            return false;
        }
        if (query.getWhere() != null) {
            condition = new AndExpression(new ParenthesedExpressionList<>(query.getWhere()), new ParenthesedExpressionList<>(condition));
        }
        query.setWhere(condition);
        return true;
    }

    private Expression predicate(MappedStatement statement, Set<String> reservedProperties, Condition condition,
                                 List<ParameterMapping> bindings, Map<String, Object> additional,
                                 @Nullable QueryContext relationRoot) {
        Expression expression;
        CCJSqlParser[] parsed = new CCJSqlParser[1];
        try {
            expression = CCJSqlParserUtil.parseCondExpression(condition.sql(), false, parser -> parsed[0] = parser);
        } catch (JSQLParserException exception) {
            throw new IllegalArgumentException("Cannot parse isolation condition", exception);
        }
        validateSqlTokens(statement, parsed[0]);
        QueryShape shape = new QueryShape(parsed[0].getASTRoot());
        List<RelationIsolationPredicate.Relation> relations = List.of();
        if (shape.nested || !shape.tableNames.isEmpty()) {
            if (relationRoot == null || shape.hasWith || shape.hasUnsupportedQuery) {
                throw new IllegalArgumentException("Isolation relation constraints must not contain subqueries");
            }
            relations = new RelationIsolationPredicate(relationRoot, table -> relationContext(statement, table))
                    .validate(expression, shape.subqueries);
        }
        List<JdbcParameter> parameters = new ArrayList<>();
        /*
         * 使用与最终 SQL 输出一致的遍历器，避免普通 Visitor 漏掉 ESCAPE、FROM/FOR 等位置的参数。
         */
        StringBuilder buffer = new StringBuilder();
        ExpressionDeParser parameterCollector = new ExpressionDeParser(null, buffer) {
            @Override
            public <S> StringBuilder visit(JdbcNamedParameter parameter, S context) {
                throw new IllegalArgumentException("Isolation condition must use positional placeholders");
            }

            @Override
            public <S> StringBuilder visit(JdbcParameter parameter, S context) {
                if (parameter.isUseFixedIndex()) {
                    throw new IllegalArgumentException("Isolation condition must use positional placeholders");
                }
                parameters.add(parameter);
                return super.visit(parameter, context);
            }
        };
        parameterCollector.setSelectVisitor(new SelectDeParser(parameterCollector, buffer));
        expression.accept(parameterCollector);
        if (parameters.size() != condition.values().size()) {
            throw new IllegalArgumentException("Isolation condition parameter count does not match values");
        }
        for (int i = 0; i < parameters.size(); i++) {
            Object value = condition.values().get(i);
            String name = "windIsolation" + bindings.size();
            while (additional.containsKey(name) || reservedProperties.contains(name)) {
                name += "Value";
            }
            additional.put(name, value);
            Class<?> javaType = value == null ? Object.class : value.getClass();
            bindings.add(new ParameterMapping.Builder(statement.getConfiguration(), name, javaType).build());
            parameters.get(i).setIndex(bindings.size());
        }
        /*
         * 原策略参数先取得身份，再追加各内表约束；最终输出统一按 SQL 位置重排。
         * 只调用显式关系入口，不递归套用内表的独立资源权限。
         */
        for (RelationIsolationPredicate.Relation relation : relations) {
            for (QueryContext table : relation.tables()) {
                TableBinding binding = tables.get(simpleName(table.tableName()));
                for (DataIsolationStrategy strategy : binding.strategies()) {
                    List<Condition> constraints = Objects.requireNonNull(strategy.resolveRelation(table), "isolation relation conditions");
                    for (Condition constraint : constraints) {
                        Expression inner = predicate(statement, reservedProperties, constraint, bindings, additional, null);
                        relation.query().setWhere(new AndExpression(new ParenthesedExpressionList<>(relation.query().getWhere()), new ParenthesedExpressionList<>(inner)));
                    }
                }
            }
        }
        return expression;
    }

    private QueryContext relationContext(MappedStatement statement, Table table) {
        TableBinding binding = tables.get(simpleName(table.getName()));
        if (binding == null || !unqualifiedTables.contains(simpleName(table.getName()))
                || table.getName().contains(".") || !table.getFullyQualifiedName().equals(table.getName())
                || table.getPivot() != null || table.getUnPivot() != null) {
            throw new IllegalArgumentException("Isolation relation requires an unqualified registered table: " + statement.getId());
        }
        return new QueryContext(statement.getId(), table.getFullyQualifiedName(), table.getAlias().getName(), binding.entity());
    }

    private static Set<String> parameterRoots(List<ParameterMapping> mappings) {
        Set<String> result = new HashSet<>();
        for (ParameterMapping mapping : mappings) {
            String property = mapping.getProperty();
            int end = property.length();
            int dot = property.indexOf('.');
            int bracket = property.indexOf('[');
            if (dot >= 0) {
                end = dot;
            }
            if (bracket >= 0 && bracket < end) {
                end = bracket;
            }
            result.add(property.substring(0, end));
        }
        return result;
    }

    private static ExecutorService createParserExecutor() {
        int workers = Math.max(1, Runtime.getRuntime().availableProcessors());
        ThreadPoolExecutor executor = new ThreadPoolExecutor(workers, workers, 60, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(256),
                Thread.ofPlatform().daemon(true).inheritInheritableThreadLocals(false).name("wind-isolation-parser-", 0).factory(),
                new ThreadPoolExecutor.AbortPolicy());
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

    private Set<ParenthesedSelect> validateExistsSubqueries(MappedStatement statement, PlainSelect query) {
        if (query.getWhere() == null) {
            return Set.of();
        }
        Table outer = (Table) query.getFromItem();
        String outerName = simpleName(outer.getAlias() == null ? outer.getName() : outer.getAlias().getName());
        Set<ParenthesedSelect> allowed = new HashSet<>();
        boolean[] hasNot = {false};
        query.getWhere().accept(new ExpressionVisitorAdapter<Void>() {
            @Override
            public <S> Void visit(NotExpression expression, S context) {
                hasNot[0] = true;
                return super.visit(expression, context);
            }

            @Override
            public <S> Void visit(ExistsExpression expression, S context) {
                if (expression.isNot() || !(expression.getRightExpression() instanceof ParenthesedSelect subquery)
                        || !(subquery.getSelect() instanceof PlainSelect inner)
                        || !(inner.getFromItem() instanceof Table table)
                        || subquery.getWithItemsList() != null && !subquery.getWithItemsList().isEmpty()
                        || inner.getSelectItems().size() != 1
                        || !(inner.getSelectItems().getFirst().getExpression() instanceof LongValue value) || value.getValue() != 1
                        || inner.getLimit() != null || inner.getOffset() != null || inner.getFetch() != null
                        || inner.getTop() != null || inner.getSkip() != null || inner.getFirst() != null
                        || inner.getHaving() != null || inner.getGroupBy() != null || inner.getOrderByElements() != null
                        || inner.getIntoTables() != null || inner.getOracleHierarchical() != null
                        || inner.getKsqlWindow() != null || inner.getForMode() != null || inner.getForUpdateTable() != null
                        || inner.getWait() != null || inner.isNoWait() || hasUnsupportedClauses(inner)) {
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
                allowed.add(subquery);
                return null;
            }
        });
        if (hasNot[0] && !allowed.isEmpty()) {
            throw unsupported(statement);
        }
        return allowed;
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

    /**
     * 检查解析器实际消费的完整 token 链，避免被当作注释的内容在数据库中执行。
     * Statement 可在分号处结束，须先取得 EOF，才能覆盖分号后的注释；不接受第二条语句。
     */
    private void validateSqlTokens(MappedStatement statement, CCJSqlParser parser) {
        if (parser.getToken(0).kind != CCJSqlParserConstants.EOF
                && parser.getNextToken().kind != CCJSqlParserConstants.EOF) {
            throw new IllegalStateException("Unsupported SQL statement tail: " + statement.getId());
        }
        for (Token token = parser.getASTRoot().jjtGetFirstToken(); token != null; token = token.next) {
            for (Token comment = token.specialToken; comment != null; comment = comment.specialToken) {
                validateSqlComment(statement, comment.image);
            }
            if (token.kind == CCJSqlParserConstants.S_CHAR_LITERAL || token.kind == CCJSqlParserConstants.S_QUOTED_IDENTIFIER) {
                validateQuotedToken(statement, token.image);
                continue;
            }
            // 默认词法配置把 # 当操作符或标识符，MySQL 却会从此处忽略行尾，可能吞掉追加条件。
            if (token.kind != CCJSqlParserConstants.EOF && token.image.indexOf('#') >= 0) {
                throw new IllegalStateException("Unsupported SQL comment syntax: " + statement.getId());
            }
            if ("(".equals(token.image) && token.next != null && token.next.kind == K_TABLE) {
                throw unsupported(statement);
            }
        }
    }

    /**
     * 不按数据库 SQL mode 猜测反斜杠是否转义引号；标准双写引号不受影响。
     */
    private void validateQuotedToken(MappedStatement statement, String text) {
        char delimiter = text.charAt(text.length() - 1);
        if (delimiter != '\'' && delimiter != '"') {
            return;
        }
        int backslashes = 0;
        for (int i = 0; i < text.length(); i++) {
            char current = text.charAt(i);
            if (current == '\\') {
                backslashes++;
                continue;
            }
            if (current == delimiter && backslashes % 2 != 0) {
                throw new IllegalStateException("Unsupported SQL quoted literal: " + statement.getId());
            }
            backslashes = 0;
        }
    }

    private void validateSqlComment(MappedStatement statement, String comment) {
        if (comment.startsWith("/*")) {
            if (comment.startsWith("/*!") || comment.startsWith("/*M!") || comment.startsWith("/*m!")
                    || comment.indexOf("/*", 2) >= 0) {
                throw new IllegalStateException("Unsupported SQL comment syntax: " + statement.getId());
            }
            return;
        }
        if (comment.startsWith("--")) {
            // 长度为 2 的 token 也可能来自 -- 后直接换行；它没有可被隐藏的注释正文。
            if (comment.length() > 2 && comment.charAt(2) > ' ' && comment.charAt(2) != 127) {
                throw new IllegalStateException("Unsupported SQL comment syntax: " + statement.getId());
            }
            return;
        }
        throw new IllegalStateException("Unsupported SQL comment syntax: " + statement.getId());
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
        return new IllegalStateException("Read isolation supports single-table SELECT only, optionally wrapped or combined by UNION: " + statement.getId());
    }

    /**
     * 升级解析器不扩大隔离查询契约；额外过滤、分页与 FROM 扩展需单独验证后才能支持。
     */
    static boolean hasUnsupportedClauses(PlainSelect query) {
        return query.getPreWhere() != null || query.getQualify() != null || query.getLimitBy() != null
                || query.getLateralViews() != null && !query.getLateralViews().isEmpty()
                || query.getIntoTempTable() != null || query.getMySqlSelectIntoClause() != null
                || query.getSampleClause() != null || query.getSettings() != null;
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

        private boolean hasUnsupportedQuery;

        private final Set<ParenthesedSelect> subqueries = new HashSet<>();

        private QueryShape(Node root) {
            Deque<Node> pending = new ArrayDeque<>();
            pending.push(root);
            while (!pending.isEmpty()) {
                Node node = pending.pop();
                if (node.getId() == CCJSqlParserTreeConstants.JJTFROMQUERY || node.getId() == CCJSqlParserTreeConstants.JJTPARENTHESEDINSERT
                        || node.getId() == CCJSqlParserTreeConstants.JJTPARENTHESEDUPDATE || node.getId() == CCJSqlParserTreeConstants.JJTPARENTHESEDDELETE) {
                    hasUnsupportedQuery = true;
                    nested = true;
                }
                // 表名 finder 会跳过部分表达式，并将同名 CTE 的物理表一并排除。
                if (node.getId() == CCJSqlParserTreeConstants.JJTPARENTHESEDSELECT || node.getId() == CCJSqlParserTreeConstants.JJTWITHITEM) {
                    nested = true;
                }
                if (node.getId() == CCJSqlParserTreeConstants.JJTPARENTHESEDSELECT) {
                    // 5.4 的模型 AST 指针可能指向 PrimaryExpression，按同一子查询对象核对覆盖。
                    subqueries.add((ParenthesedSelect) node.jjtGetValue());
                } else if (node.getId() == CCJSqlParserTreeConstants.JJTWITHITEM) {
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
            } else if (item instanceof ParenthesedFromItem parenthesis) {
                collectFromItem(parenthesis.getFromItem());
                if (parenthesis.getJoins() != null) {
                    parenthesis.getJoins().forEach(join -> collectFromItem(join.getRightItem()));
                }
            }
        }
    }
}
