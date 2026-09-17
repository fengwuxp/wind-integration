package com.wind.integration.metrics.jdbc;

import com.wind.integration.metrics.MetricValidationException;
import com.wind.integration.metrics.dsl.definition.MetricDslSpec;
import com.wind.integration.metrics.dsl.definition.MetricJoinDsl;
import com.wind.integration.metrics.dsl.definition.MetricJoinOnDsl;
import com.wind.integration.metrics.dsl.definition.MetricMeasureDsl;
import com.wind.integration.metrics.dsl.definition.MetricQueryParameterDefinitionDsl;
import com.wind.integration.metrics.dsl.definition.MetricSubjectDsl;
import com.wind.integration.metrics.dsl.definition.MetricValueDsl;
import com.wind.integration.metrics.dsl.definition.selection.MetricLimitDsl;
import com.wind.integration.metrics.dsl.definition.selection.MetricRowSelectionDsl;
import com.wind.integration.metrics.dsl.filter.ComparisonMetricFilterDsl;
import com.wind.integration.metrics.dsl.filter.LogicalMetricFilterDsl;
import com.wind.integration.metrics.dsl.filter.MetricFilterDsl;
import com.wind.integration.metrics.dsl.filter.NullMetricFilterDsl;
import com.wind.integration.metrics.dsl.filter.SetMetricFilterDsl;
import com.wind.integration.metrics.enums.MetricAggregation;
import com.wind.integration.metrics.enums.MetricErrorCode;
import com.wind.integration.metrics.enums.MetricJoinType;
import com.wind.integration.metrics.enums.MetricValueShape;
import com.wind.integration.metrics.enums.MetricSortDirection;
import com.wind.integration.metrics.query.MetricQuery;
import com.wind.integration.metrics.query.MetricQueryValidator;

import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.Param;
import org.jooq.QueryPart;
import org.jooq.SQLDialect;
import org.jooq.SelectQuery;
import org.jooq.SortField;
import org.jooq.Table;
import org.jooq.VisitListener;
import org.jooq.conf.ParamCastMode;
import org.jooq.conf.RenderKeywordCase;
import org.jooq.conf.Settings;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;

import java.sql.Types;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * 将事实指标原 DSL、单次查询条件和冻结物理映射编译为指定方言的参数化 SQL。
 *
 * <p>宿主负责基础 DSL、字段兼容、JOIN 唯一性和稳定排序校验；本类不发现实体、不查询数据库，
 * 也不选择发布修订。实例只持有固定方言、时区与行数上限，单次编译状态均为局部变量。
 *
 * @author wuxp
 */
public final class MetricJdbcSqlCompiler {

    private static final int DEFAULT_MAX_ROW_SELECTION_LIMIT = 1000;

    private static final Pattern PHYSICAL_IDENTIFIER = Pattern.compile("[a-z][a-z0-9_]{0,63}");

    private static final Pattern SQL_ALIAS = Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,63}");

    private final SQLDialect dialect;

    private final int maxRowSelectionLimit;

    private final MetricJdbcValueNormalizer normalizer;

    private final MetricJdbcFilterRenderer filterRenderer;

    /**
     * 使用固定指标时区与默认 1000 行上限创建编译器。
     *
     * @param timeZone 当前查询解释使用的指标时区
     */
    public MetricJdbcSqlCompiler(ZoneId timeZone) {
        this(timeZone, DEFAULT_MAX_ROW_SELECTION_LIMIT);
    }

    /**
     * 使用宿主冻结的时区与行选择上限创建编译器。
     *
     * <p>实际 LIMIT 来自 DSL 固定值或查询参数；超过本上限时拒绝编译，不截断查询行数。
     *
     * @param timeZone 指标时区，不能使用查询过程中变化的默认时区
     * @param maxRowSelectionLimit 正数，与宿主定义验证使用的上限一致
     */
    public MetricJdbcSqlCompiler(ZoneId timeZone, int maxRowSelectionLimit) {
        this(timeZone, maxRowSelectionLimit, SQLDialect.MYSQL);
    }

    /**
     * 显式指定 SQL 方言；只构造 SQL，不创建连接、执行查询或持有第三方 AST。
     *
     * @param timeZone 宿主冻结的指标时区
     * @param maxRowSelectionLimit 正数行选择上限
     * @param dialect 目标数据库方言，当前支持 MYSQL、POSTGRES、H2
     */
    public MetricJdbcSqlCompiler(ZoneId timeZone, int maxRowSelectionLimit, SQLDialect dialect) {
        Objects.requireNonNull(dialect, "dialect must not be null");
        if (!Set.of(SQLDialect.MYSQL, SQLDialect.POSTGRES, SQLDialect.H2).contains(dialect)) {
            throw new IllegalArgumentException("Unsupported metric SQL dialect: " + dialect);
        }
        this.dialect = dialect;
        if (maxRowSelectionLimit <= 0) {
            throw new IllegalArgumentException("maxRowSelectionLimit must be positive");
        }
        this.maxRowSelectionLimit = maxRowSelectionLimit;
        normalizer = new MetricJdbcValueNormalizer(Objects.requireNonNull(timeZone, "timeZone must not be null"));
        filterRenderer = new MetricJdbcFilterRenderer(normalizer);
    }

    /**
     * 编译单个事实指标；表达式字段由宿主在 measure 加载后求值，不进入 SQL 投影。
     *
     * @param definition 同次基础和物理校验使用的原声明引用
     * @param query 正式 DSL 查询条件
     * @param binding 同次校验冻结的物理映射，不得执行 IO
     * @return SQL、占位符顺序参数以及 measure 投影
     * @throws MetricValidationException 定义形态、查询条件或字段值不受支持时抛出
     */
    public CompiledMetricSql compile(MetricDslSpec definition, MetricQuery query, MetricJdbcBinding binding) {
        validateQuery(definition, query);
        MetricRowSelectionDsl selection = definition.rowSelection();
        int rowSelectionLimit = selection == null
                ? 0 : resolveRowSelectionLimit(selection.limit(), query.parameterValues());
        Objects.requireNonNull(binding, "binding must not be null");

        List<MetricJoinDsl> orderedJoins = definition.joins().stream()
                .sorted(Comparator.comparing(MetricJoinDsl::alias)).toList();
        Map<String, String> aliases = aliases(orderedJoins);
        Function<String, Field<Object>> columns = field -> column(binding, aliases, field);
        Map<String, MetricMeasureDsl> measures = measures(definition);
        Map<String, String> selectedColumns = selection == null ? Map.of() : selectedFields(selection, measures);
        Function<String, Field<Object>> projectionColumns = selection == null
                ? columns : field -> DSL.field(DSL.name("r", selectedColumns.get(field)));

        Map<String, MetricSqlBinding> parameters = new LinkedHashMap<>();
        Map<String, String> resultProjections = new LinkedHashMap<>();
        List<Field<?>> projections = new ArrayList<>();
        for (Map.Entry<String, MetricMeasureDsl> entry : measures.entrySet()) {
            String name = entry.getKey();
            validateAlias(name);
            projections.add(projection(binding, entry.getValue(), projectionColumns, parameters).as(DSL.name(name)));
            resultProjections.put(name, name);
        }
        Table<?> source = source(orderedJoins, binding, aliases, columns);
        List<Condition> conditions = predicates(definition, query, binding, columns, parameters);
        SelectQuery<?> sql = DSL.using(dialect).selectQuery();
        sql.addSelect(projections);
        if (selection == null) {
            sql.addFrom(source);
            sql.addConditions(conditions);
        } else {
            sql.addFrom(rowSelection(selection, selectedColumns, source, conditions, rowSelectionLimit,
                    binding, columns, parameters));
        }
        List<MetricSqlBinding> bindings = new ArrayList<>();
        String rendered = render(sql, parameters, bindings);
        return new CompiledMetricSql(rendered, bindings, resultProjections);
    }

    /**
     * 为实时查询、有限行集和物化页复用相同的过滤语义，成功后追加参数。
     *
     * <p>columnResolver 由受信宿主代码构建列引用 SQL，例如 p.column 或 r.c0，
     * 不得接收 HTTP/DSL 提供的原始 SQL。过滤需已通过定义与字段校验。
     *
     * @param binding 冻结物理字段映射
     * @param filter 已验证的原过滤 DSL
     * @param bindings 接收按占位符次序编码的参数；失败时不追加半组参数
     * @param columnResolver 逻辑字段引用到受控列引用的转换
     * @return 参数化谓词
     */
    public String renderValidatedFilter(MetricJdbcBinding binding, MetricFilterDsl filter,
                                       List<MetricSqlBinding> bindings, Function<String, String> columnResolver) {
        Objects.requireNonNull(binding, "binding must not be null");
        Objects.requireNonNull(filter, "filter must not be null");
        Objects.requireNonNull(bindings, "bindings must not be null");
        Objects.requireNonNull(columnResolver, "columnResolver must not be null");
        Map<String, MetricSqlBinding> parameters = new LinkedHashMap<>();
        Condition predicate = filterRenderer.render(binding, filter, parameters,
                field -> DSL.field(columnResolver.apply(field)));
        List<MetricSqlBinding> ordered = new ArrayList<>();
        String sql = render(predicate, parameters, ordered);
        bindings.addAll(ordered);
        return sql;
    }

    private Table<?> rowSelection(MetricRowSelectionDsl selection, Map<String, String> selectedColumns,
                                  Table<?> source, List<Condition> predicates, int limit, MetricJdbcBinding binding,
                                  Function<String, Field<Object>> columns, Map<String, MetricSqlBinding> parameters) {
        SelectQuery<?> sql = DSL.using(dialect).selectQuery();
        for (Map.Entry<String, String> entry : selectedColumns.entrySet()) {
            sql.addSelect(columns.apply(entry.getKey()).as(DSL.name(entry.getValue())));
        }
        sql.addFrom(source);
        sql.addConditions(predicates);
        if (selection.filter() != null) {
            sql.addConditions(filterRenderer.render(binding, selection.filter(), parameters, columns));
        }
        List<SortField<?>> orderBy = selection.orderBy().stream().<SortField<?>>map(order -> {
            Field<?> field = columns.apply(order.field());
            return order.direction() == MetricSortDirection.ASC ? field.asc() : field.desc();
        }).toList();
        sql.addOrderBy(orderBy);
        String limitName = "v" + parameters.size();
        parameters.put(limitName, new MetricSqlBinding(limit, Types.INTEGER));
        sql.addLimit(DSL.param(limitName, limit));
        return sql.asTable(DSL.name("r"));
    }

    private Field<?> projection(MetricJdbcBinding binding, MetricMeasureDsl measure,
                                Function<String, Field<Object>> columns, Map<String, MetricSqlBinding> parameters) {
        boolean count = measure.aggregation() == MetricAggregation.COUNT;
        if (count && measure.filter() == null) {
            return DSL.count();
        }
        Field<?> argument = count ? DSL.inline(1) : columns.apply(measure.field());
        if (measure.filter() != null) {
            Condition predicate = filterRenderer.render(binding, measure.filter(), parameters, columns);
            argument = DSL.when(predicate, argument);
        }
        return DSL.aggregate(measure.aggregation().name(), SQLDataType.DECIMAL, argument);
    }

    private List<Condition> predicates(MetricDslSpec definition, MetricQuery query, MetricJdbcBinding binding,
                                      Function<String, Field<Object>> columns,
                                      Map<String, MetricSqlBinding> parameters) {
        List<Condition> result = new ArrayList<>();
        if (!MetricSubjectDsl.GLOBAL.equals(definition.subject().type())) {
            String field = definition.subject().field();
            result.add(columns.apply(field).eq(parameter(parameters,
                    normalizer.subject(binding, field, (String) query.subjectId()))));
        }
        String timeField = definition.time().field();
        result.add(columns.apply(timeField).ge(parameter(parameters,
                normalizer.time(binding, timeField, query.startTime()))));
        result.add(columns.apply(timeField).lt(parameter(parameters,
                normalizer.time(binding, timeField, query.endTime()))));
        Map<String, Object> dimensionValues = query.dimensionValues();
        for (String field : definition.dimensions().stream().sorted().toList()) {
            result.add(columns.apply(field).eq(parameter(parameters,
                    normalizer.dimension(binding, field, dimensionValues.get(field)))));
        }
        return result;
    }

    private static Table<?> source(List<MetricJoinDsl> joins, MetricJdbcBinding binding,
                                   Map<String, String> aliases, Function<String, Field<Object>> columns) {
        Table<?> source = DSL.table(DSL.name(physical(binding.tableName("")))).as(DSL.name("p"));
        for (MetricJoinDsl join : joins) {
            Table<?> table = DSL.table(DSL.name(physical(binding.tableName(join.alias()))))
                    .as(DSL.name(aliases.get(join.alias())));
            List<Condition> conditions = join.on().stream()
                    .sorted(Comparator.comparing(MetricJoinOnDsl::primaryField)
                            .thenComparing(MetricJoinOnDsl::joinField))
                    .map(on -> columns.apply(on.primaryField())
                            .eq(columns.apply(join.alias() + '.' + on.joinField()))).toList();
            source = (join.joinType() == MetricJoinType.INNER ? source.join(table) : source.leftJoin(table))
                    .on(DSL.and(conditions));
        }
        return source;
    }

    /** 绑定身份独立于值相等性，避免等值参数或方言重排丢失 JDBC 类型。 */
    static Field<Object> parameter(Map<String, MetricSqlBinding> parameters, MetricSqlBinding binding) {
        String name = "v" + parameters.size();
        parameters.put(name, binding);
        return DSL.param(name, binding.value());
    }

    private String render(QueryPart part, Map<String, MetricSqlBinding> parameters, List<MetricSqlBinding> ordered) {
        VisitListener listener = VisitListener.onVisitStart(visit -> {
            if (visit.queryPart() instanceof Param<?> parameter && !parameter.isInline()) {
                MetricSqlBinding binding = parameters.get(parameter.getParamName());
                if (binding == null) {
                    throw new IllegalStateException("SQL renderer introduced an unregistered parameter");
                }
                ordered.add(binding);
                // 值已由宿主编码；用固定占位符截断第三方的值转换和 null 参数重访。
                visit.queryPart(DSL.sql("?"));
            }
        });
        Settings settings = new Settings().withRenderKeywordCase(RenderKeywordCase.UPPER)
                .withParamCastMode(ParamCastMode.NEVER);
        return DSL.using(DSL.using(dialect, settings).configuration().derive(listener)).render(part);
    }

    private static Map<String, String> aliases(List<MetricJoinDsl> orderedJoins) {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("", "p");
        for (int index = 0; index < orderedJoins.size(); index++) {
            result.put(orderedJoins.get(index).alias(), "j" + index);
        }
        return result;
    }

    private static Map<String, MetricMeasureDsl> measures(MetricDslSpec definition) {
        Map<String, MetricValueDsl> values = definition.valueShape() == MetricValueShape.SCALAR
                ? Map.of("value", definition.value()) : new TreeMap<>(definition.fields());
        Map<String, MetricMeasureDsl> result = new LinkedHashMap<>();
        for (Map.Entry<String, MetricValueDsl> entry : values.entrySet()) {
            MetricMeasureDsl measure = entry.getValue().measure();
            if (measure != null) {
                result.put(entry.getKey(), measure);
            }
        }
        if (result.isEmpty()) {
            throw error(MetricErrorCode.DSL_VALUE_INVALID, "/metric/value", "Fact metric does not contain measures");
        }
        return result;
    }

    private static Map<String, String> selectedFields(MetricRowSelectionDsl selection,
                                                     Map<String, MetricMeasureDsl> measures) {
        Map<String, String> result = new LinkedHashMap<>();
        Set<String> references = new TreeSet<>();
        for (MetricMeasureDsl measure : measures.values()) {
            if (measure.field() != null) {
                references.add(measure.field());
            }
            if (measure.filter() != null) {
                filterFields(measure.filter(), references);
            }
        }
        if (references.isEmpty()) {
            references.add(selection.orderBy().getFirst().field());
        }
        for (String field : references) {
            result.put(field, "c" + result.size());
        }
        return result;
    }

    private static void filterFields(MetricFilterDsl filter, Set<String> fields) {
        switch (filter) {
            case ComparisonMetricFilterDsl comparison -> fields.add(comparison.fieldRef());
            case SetMetricFilterDsl set -> fields.add(set.fieldRef());
            case NullMetricFilterDsl nullFilter -> fields.add(nullFilter.fieldRef());
            case LogicalMetricFilterDsl logical -> {
                for (MetricFilterDsl operand : logical.operands()) {
                    filterFields(operand, fields);
                }
            }
        }
    }

    private static void validateQuery(MetricDslSpec definition, MetricQuery query) {
        if (definition == null || query == null) {
            throw error(MetricErrorCode.QUERY_INVALID, "", "Metric definition and query must not be null");
        }
        if (definition.fact() == null) {
            throw error(MetricErrorCode.METRIC_EXECUTION_MODE_UNSUPPORTED, "/metric/fact",
                    "Derived metric is not supported by JDBC SQL compiler");
        }
        MetricQueryValidator.validateDsl(query);
        if (query.subjectType() != null && !definition.subject().type().equals(query.subjectType())) {
            throw error(MetricErrorCode.QUERY_INVALID, "/subjectType", "Subject type does not match definition");
        }
        validateParameters(definition.parameters(), query.parameterValues());
        boolean global = MetricSubjectDsl.GLOBAL.equals(definition.subject().type());
        if (global && query.subjectId() != null) {
            throw error(MetricErrorCode.QUERY_INVALID, "/subjectId", "GLOBAL metric forbids subjectId");
        }
        if (!global && query.subjectId() == null) {
            throw error(MetricErrorCode.QUERY_INVALID, "/subjectId", "Subject metric requires subjectId");
        }
        if (!Set.copyOf(definition.dimensions()).equals(query.dimensionValues().keySet())) {
            throw error(MetricErrorCode.QUERY_INVALID, "/dimensionValues",
                    "Dimension keys must exactly match metric definition");
        }
    }

    private static void validateParameters(Map<String, MetricQueryParameterDefinitionDsl> definitions,
                                           Map<String, Object> parameters) {
        for (String name : new TreeSet<>(parameters.keySet())) {
            if (!definitions.containsKey(name)) {
                throw error(MetricErrorCode.METRIC_PARAMETER_UNEXPECTED, "/parameterValues/" + escape(name),
                        "Metric query parameter is not declared");
            }
        }
        for (String name : new TreeSet<>(definitions.keySet())) {
            String path = "/parameterValues/" + escape(name);
            if (!parameters.containsKey(name)) {
                throw error(MetricErrorCode.METRIC_PARAMETER_MISSING, path,
                        "Metric query parameter is required");
            }
            if (!(parameters.get(name) instanceof Integer value)) {
                throw error(MetricErrorCode.METRIC_PARAMETER_TYPE_MISMATCH, path,
                        "Metric query parameter must be an integer");
            }
            MetricQueryParameterDefinitionDsl contract = definitions.get(name);
            if (value < contract.minimum() || value > contract.maximum()) {
                throw error(MetricErrorCode.METRIC_PARAMETER_OUT_OF_RANGE, path,
                        "Metric query parameter is outside the declared range");
            }
        }
    }

    private int resolveRowSelectionLimit(MetricLimitDsl limit, Map<String, Object> parameters) {
        int value = limit.value() == null ? (Integer) parameters.get(limit.parameter()) : limit.value();
        if (value > maxRowSelectionLimit || value <= 0) {
            boolean parameterized = limit.parameter() != null;
            throw error(parameterized ? MetricErrorCode.METRIC_PARAMETER_OUT_OF_RANGE : MetricErrorCode.DSL_VALUE_INVALID,
                    parameterized ? "/parameterValues/" + escape(limit.parameter()) : "/metric/rowSelection/limit/value",
                    "Row selection limit is outside the system range");
        }
        return value;
    }

    private static Field<Object> column(MetricJdbcBinding binding, Map<String, String> aliases, String field) {
        int separator = field.indexOf('.');
        String reference = separator < 0 ? "" : field.substring(0, separator);
        String alias = aliases.get(reference);
        if (alias == null) {
            throw error(MetricErrorCode.DSL_VALUE_INVALID, "/metric", "Field references an unknown fact alias");
        }
        validateAlias(alias);
        return DSL.field(DSL.name(alias, physical(binding.columnName(field))));
    }

    private static String physical(String identifier) {
        if (identifier == null || !PHYSICAL_IDENTIFIER.matcher(identifier).matches()) {
            throw error(MetricErrorCode.DSL_VALUE_INVALID, "/metric", "Invalid physical SQL identifier");
        }
        return identifier;
    }

    private static void validateAlias(String alias) {
        if (alias == null || !SQL_ALIAS.matcher(alias).matches()) {
            throw error(MetricErrorCode.DSL_VALUE_INVALID, "/metric", "Invalid SQL alias");
        }
    }

    private static String escape(String value) {
        return value.replace("~", "~0").replace("/", "~1");
    }

    private static MetricValidationException error(MetricErrorCode code, String path, String message) {
        return new MetricValidationException(code, path, message);
    }
}
