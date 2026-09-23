package com.wind.integration.metrics.jdbc;

import com.wind.integration.metrics.MetricValidationException;
import com.wind.integration.metrics.dsl.definition.MetricJoinDsl;
import com.wind.integration.metrics.dsl.definition.MetricJoinOnDsl;
import com.wind.integration.metrics.dsl.definition.MetricMeasureDsl;
import com.wind.integration.metrics.dsl.definition.MetricQueryParameterDsl;
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
import com.wind.integration.metrics.enums.MetricSortDirection;
import com.wind.integration.metrics.enums.MetricValueShape;
import com.wind.integration.metrics.query.MetricQuery;
import com.wind.integration.metrics.spec.MetricDSLDefinition;
import com.wind.integration.metrics.spec.MetricDefinitionObject;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * 将事实指标 DSL、单次查询条件和冻结物理映射编译为指定方言的参数化 SQL。
 *
 * <p>宿主负责基础 DSL、字段兼容、JOIN 唯一性和稳定排序校验；本类不发现实体、不查询数据库，
 * 也不选择发布修订。实例持有固定方言、时区、行数上限，以及按 (code, revision) 注册的冻结物理映射缓存；
 * 单次编译的其余状态均为局部变量。
 * SQL 生成只返回 {@link MetricSqlDescriptor}，不把 JDBC 执行、结果读取或最终表达式计算混入编译器。
 * 推荐将同次校验得到的映射直接传入 {@link #compile}；只有通过 {@link #generate} 接入时才读取注册缓存。
 * DSL 未声明维度或参数时，查询可省略对应 Map；声明后才要求容器存在并校验其键集合、类型和值域。
 * 未声明内容仍拒绝非空额外键，避免宿主误传查询条件后被静默忽略。
 *
 * @author wuxp
 */
public final class MetricJdbcSqlCompiler implements MetricSqlGenerator {

    /** 单次编译已经固定的声明、绑定和投影上下文；不跨查询缓存。 */
    private record CompilationPlan(
            MetricDSLDefinition definition,
            MetricQuery query,
            MetricJdbcMapping binding,
            MetricRowSelectionDsl rowSelection,
            int rowSelectionLimit,
            List<MetricJoinDsl> joins,
            Map<String, String> aliases,
            Function<String, Field<Object>> columns,
            Map<String, MetricMeasureDsl> measures,
            Map<String, String> selectedColumns,
            Function<String, Field<Object>> projectionColumns) {
    }

    private static final int DEFAULT_MAX_ROW_SELECTION_LIMIT = 1000;

    private static final Pattern PHYSICAL_IDENTIFIER = Pattern.compile("[a-z][a-z0-9_]{0,63}");

    private static final Pattern SQL_ALIAS = Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,63}");

    private final SQLDialect dialect;

    private final int maxRowSelectionLimit;

    private final MetricJdbcValueNormalizer metricJdbcValueNormalizer;

    private final MetricJdbcPredicateBuilder metricJdbcPredicateBuilder;

    private final ConcurrentMap<String, MetricJdbcMapping> bindings = new ConcurrentHashMap<>();

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
     * @param timeZone             指标时区，不能使用查询过程中变化的默认时区
     * @param maxRowSelectionLimit 正数，与宿主定义验证使用的上限一致
     */
    public MetricJdbcSqlCompiler(ZoneId timeZone, int maxRowSelectionLimit) {
        this(timeZone, maxRowSelectionLimit, SQLDialect.MYSQL);
    }

    /**
     * 显式指定 SQL 方言；只构造 SQL，不创建连接、执行查询或持有第三方 AST。
     *
     * @param timeZone             宿主冻结的指标时区
     * @param maxRowSelectionLimit 正数行选择上限
     * @param dialect              目标数据库方言，当前支持 MYSQL、POSTGRES、H2
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
        metricJdbcValueNormalizer = new MetricJdbcValueNormalizer(Objects.requireNonNull(timeZone, "timeZone must not be null"));
        metricJdbcPredicateBuilder = new MetricJdbcPredicateBuilder(metricJdbcValueNormalizer);
    }

    /**
     * 编译具有完整半开时间窗的单个 RAW 事实指标；表达式字段由宿主在 measure 加载后求值，不进入 SQL 投影。
     *
     * <p>编排顺序固定为：校验查询 -> 固定事实/join/measure 投影 -> 构造来源和谓词 ->
     * 渲染参数化 SQL。每次调用的参数、别名和投影均为局部状态。</p>
     *
     * @param definition 同次基础和物理校验使用的原声明引用
     * @param query      正式 DSL 查询条件
     * @param binding    同次校验冻结的物理映射，不得执行 IO
     * @return SQL、占位符顺序参数以及 measure 投影
     * @throws MetricValidationException 定义形态、查询条件或字段值不受支持时抛出
     */
    public MetricSqlDescriptor compile(MetricDSLDefinition definition, MetricQuery query, MetricJdbcMapping binding) {
        CompilationPlan plan = plan(definition, query, binding);

        Map<String, MetricJdbcParameterBinding> parameters = new LinkedHashMap<>();
        Map<String, String> resultProjections = new LinkedHashMap<>();
        List<Field<?>> projections = projections(plan, parameters, resultProjections);
        Table<?> source = buildSource(plan.joins(), plan.binding(), plan.aliases(), plan.columns());
        List<Condition> conditions = buildPredicates(plan.definition(), plan.query(), plan.binding(), plan.columns(), parameters);
        SelectQuery<?> sql = assembleQuery(plan, source, conditions, projections, parameters);
        List<MetricJdbcParameterBinding> bindings = new ArrayList<>();
        String rendered = render(sql, parameters, bindings);
        return new MetricSqlDescriptor(rendered, bindings, resultProjections);
    }

    private CompilationPlan plan(MetricDSLDefinition definition, MetricQuery query, MetricJdbcMapping binding) {
        validateQuery(definition, query);
        Objects.requireNonNull(binding, "binding must not be null");
        MetricRowSelectionDsl selection = definition.rowSelection();
        int limit = selection == null ? 0 : resolveRowSelectionLimit(selection.limit(), query.parameterValues());
        List<MetricJoinDsl> joins = definition.joins().stream()
                .sorted(Comparator.comparing(MetricJoinDsl::alias)).toList();
        Map<String, String> aliases = resolveAliases(joins);
        Function<String, Field<Object>> columns = field -> column(binding, aliases, field);
        Map<String, MetricMeasureDsl> measures = resolveMeasures(definition);
        Map<String, String> selectedColumns = selection == null ? Map.of() : resolveSelectedFields(selection, measures);
        Function<String, Field<Object>> projectionColumns = selection == null
                ? columns : field -> DSL.field(DSL.name("r", selectedColumns.get(field)));
        return new CompilationPlan(definition, query, binding, selection, limit, joins, aliases, columns,
                measures, selectedColumns, projectionColumns);
    }

    private List<Field<?>> projections(CompilationPlan plan, Map<String, MetricJdbcParameterBinding> parameters,
                                       Map<String, String> resultProjections) {
        List<Field<?>> projections = new ArrayList<>();
        for (Map.Entry<String, MetricMeasureDsl> entry : plan.measures().entrySet()) {
            String name = entry.getKey();
            validateAlias(name);
            projections.add(renderAggregate(plan.binding(), entry.getValue(), plan.projectionColumns(), parameters)
                    .as(DSL.name(name)));
            resultProjections.put(name, name);
        }
        return projections;
    }

    private SelectQuery<?> assembleQuery(CompilationPlan plan, Table<?> source, List<Condition> conditions,
                                         List<Field<?>> projections, Map<String, MetricJdbcParameterBinding> parameters) {
        SelectQuery<?> sql = DSL.using(dialect).selectQuery();
        sql.addSelect(projections);
        if (plan.rowSelection() == null) {
            sql.addFrom(source);
            sql.addConditions(conditions);
        } else {
            sql.addFrom(renderRowSelection(plan.rowSelection(), plan.selectedColumns(), source, conditions,
                    plan.rowSelectionLimit(), plan.binding(), plan.columns(), parameters));
        }
        return sql;
    }

    /**
     * 注册某定义修订的冻结物理映射，供 {@link #generate} 内部按 (code, revision) 获取。
     *
     * <p>这是 generate 的可选接入路径；显式传入映射的 {@link #compile} 不读取或修改此缓存。
     * 同一 (code, revision) 再次注册会替换原映射，宿主负责保证其适用范围与生命周期一致。</p>
     *
     * @param definition DSL 定义，提供编码与修订
     * @param binding    该修订冻结的物理映射，不得执行 IO
     */
    public void registerBinding(MetricDSLDefinition definition, MetricJdbcMapping binding) {
        Objects.requireNonNull(definition, "definition must not be null");
        Objects.requireNonNull(binding, "binding must not be null");
        bindings.put(key(definition.code(), definition.revision()), binding);
    }

    /**
     * 实现 {@link MetricSqlGenerator}，按 DSL 模式编译查询，物理映射从内部缓存获取。
     *
     * @param definition 必须是 {@link MetricDSLDefinition}
     * @param query      查询条件
     * @return 参数化 SQL、有序绑定与投影
     * @throws IllegalArgumentException 定义不是 DSL 形态
     * @throws IllegalStateException    未注册对应 (code, revision) 的物理映射
     */
    @Override
    public MetricSqlDescriptor generate(MetricDefinitionObject definition, MetricQuery query) {
        if (!(definition instanceof MetricDSLDefinition dsl)) {
            throw new IllegalArgumentException("DSL compiler requires a MetricDSLDefinition, but was "
                    + definition.getClass().getSimpleName());
        }
        MetricJdbcMapping binding = bindings.get(key(dsl.code(), dsl.revision()));
        if (binding == null) {
            throw new IllegalStateException("No binding registered for metric " + dsl.code() + "@" + dsl.revision());
        }
        return compile(dsl, query, binding);
    }

    private static String key(String code, int revision) {
        return code + "@" + revision;
    }

    /**
     * 为实时查询、有限行集和物化页复用相同的过滤语义，成功后追加参数。
     *
     * <p>columnResolver 由受信宿主代码构建列引用 SQL，例如 p.column 或 r.c0，
     * 不得接收 HTTP/DSL 提供的原始 SQL。过滤需已通过定义与字段校验。
     *
     * @param binding        冻结物理字段映射
     * @param filter         已验证的原过滤 DSL
     * @param bindings       接收按占位符次序编码的参数；失败时不追加半组参数
     * @param columnResolver 逻辑字段引用到受控列引用的转换
     * @return 参数化谓词
     */
    public String renderValidatedFilter(MetricJdbcMapping binding, MetricFilterDsl filter,
                                        List<MetricJdbcParameterBinding> bindings, Function<String, String> columnResolver) {
        Objects.requireNonNull(binding, "binding must not be null");
        Objects.requireNonNull(filter, "filter must not be null");
        Objects.requireNonNull(bindings, "bindings must not be null");
        Objects.requireNonNull(columnResolver, "columnResolver must not be null");
        Map<String, MetricJdbcParameterBinding> parameters = new LinkedHashMap<>();
        Condition predicate = metricJdbcPredicateBuilder.build(binding, filter, parameters,
                field -> DSL.field(columnResolver.apply(field)));
        List<MetricJdbcParameterBinding> ordered = new ArrayList<>();
        String sql = render(predicate, parameters, ordered);
        bindings.addAll(ordered);
        return sql;
    }

    private Table<?> renderRowSelection(MetricRowSelectionDsl selection, Map<String, String> selectedColumns,
                                  Table<?> source, List<Condition> predicates, int limit, MetricJdbcMapping binding,
                                  Function<String, Field<Object>> columns, Map<String, MetricJdbcParameterBinding> parameters) {
        SelectQuery<?> sql = DSL.using(dialect).selectQuery();
        for (Map.Entry<String, String> entry : selectedColumns.entrySet()) {
            sql.addSelect(columns.apply(entry.getKey()).as(DSL.name(entry.getValue())));
        }
        sql.addFrom(source);
        sql.addConditions(predicates);
        if (selection.filter() != null) {
            sql.addConditions(metricJdbcPredicateBuilder.build(binding, selection.filter(), parameters, columns));
        }
        List<SortField<?>> orderBy = selection.orderBy().stream().<SortField<?>>map(order -> {
            Field<?> field = columns.apply(order.field());
            return order.direction() == MetricSortDirection.ASC ? field.asc() : field.desc();
        }).toList();
        sql.addOrderBy(orderBy);
        String limitName = "v" + parameters.size();
        parameters.put(limitName, new MetricJdbcParameterBinding(limit, Types.INTEGER));
        sql.addLimit(DSL.param(limitName, limit));
        return sql.asTable(DSL.name("r"));
    }

    private Field<?> renderAggregate(MetricJdbcMapping binding, MetricMeasureDsl measure,
                                Function<String, Field<Object>> columns, Map<String, MetricJdbcParameterBinding> parameters) {
        boolean count = measure.aggregation() == MetricAggregation.COUNT;
        if (count && measure.filter() == null) {
            return DSL.count();
        }
        Field<?> argument = count ? DSL.inline(1) : columns.apply(measure.field());
        if (measure.filter() != null) {
            Condition predicate = metricJdbcPredicateBuilder.build(binding, measure.filter(), parameters, columns);
            argument = DSL.when(predicate, argument);
        }
        return DSL.aggregate(measure.aggregation().name(), SQLDataType.DECIMAL, argument);
    }

    private List<Condition> buildPredicates(MetricDSLDefinition definition, MetricQuery query, MetricJdbcMapping binding,
                                       Function<String, Field<Object>> columns,
                                       Map<String, MetricJdbcParameterBinding> parameters) {
        List<Condition> result = new ArrayList<>();
        if (!MetricSubjectDsl.GLOBAL.equals(definition.subject().type())) {
            String field = definition.subject().field();
            result.add(columns.apply(field).eq(parameter(parameters,
                    metricJdbcValueNormalizer.subject(binding, field, (String) query.subjectId()))));
        }
        String timeField = definition.time().field();
        result.add(columns.apply(timeField).ge(parameter(parameters,
                metricJdbcValueNormalizer.time(binding, timeField, query.startTime()))));
        result.add(columns.apply(timeField).lt(parameter(parameters,
                metricJdbcValueNormalizer.time(binding, timeField, query.endTime()))));
        Map<String, Object> dimensionValues = query.dimensionValues();
        for (String field : definition.dimensions().stream().sorted().toList()) {
            result.add(columns.apply(field).eq(parameter(parameters,
                    metricJdbcValueNormalizer.dimension(binding, field, dimensionValues.get(field)))));
        }
        return result;
    }

    private static Table<?> buildSource(List<MetricJoinDsl> joins, MetricJdbcMapping binding,
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

    /**
     * 绑定身份独立于值相等性，避免等值参数或方言重排丢失 JDBC 类型。
     */
    static Field<Object> parameter(Map<String, MetricJdbcParameterBinding> parameters, MetricJdbcParameterBinding binding) {
        String name = "v" + parameters.size();
        parameters.put(name, binding);
        return DSL.param(name, binding.value());
    }

    private String render(QueryPart part, Map<String, MetricJdbcParameterBinding> parameters, List<MetricJdbcParameterBinding> ordered) {
        VisitListener listener = VisitListener.onVisitStart(visit -> {
            if (visit.queryPart() instanceof Param<?> parameter && !parameter.isInline()) {
                MetricJdbcParameterBinding binding = parameters.get(parameter.getParamName());
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

    private static Map<String, String> resolveAliases(List<MetricJoinDsl> orderedJoins) {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("", "p");
        for (int index = 0; index < orderedJoins.size(); index++) {
            result.put(orderedJoins.get(index).alias(), "j" + index);
        }
        return result;
    }

    private static Map<String, MetricMeasureDsl> resolveMeasures(MetricDSLDefinition definition) {
        Map<String, MetricValueDsl> values = definition.valueShape() == MetricValueShape.SCALAR ? Map.of("value", definition.value()) : new TreeMap<>(definition.fields());
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

    private static Map<String, String> resolveSelectedFields(MetricRowSelectionDsl selection,
                                                      Map<String, MetricMeasureDsl> measures) {
        Map<String, String> result = new LinkedHashMap<>();
        Set<String> references = new TreeSet<>();
        for (MetricMeasureDsl measure : measures.values()) {
            if (measure.field() != null) {
                references.add(measure.field());
            }
            if (measure.filter() != null) {
                collectFilterFields(measure.filter(), references);
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

    private static void collectFilterFields(MetricFilterDsl filter, Set<String> fields) {
        switch (filter) {
            case ComparisonMetricFilterDsl comparison -> fields.add(comparison.fieldRef());
            case SetMetricFilterDsl set -> fields.add(set.fieldRef());
            case NullMetricFilterDsl nullFilter -> fields.add(nullFilter.fieldRef());
            case LogicalMetricFilterDsl logical -> {
                for (MetricFilterDsl operand : logical.operands()) {
                    collectFilterFields(operand, fields);
                }
            }
        }
    }

    private static void validateQuery(MetricDSLDefinition definition, MetricQuery query) {
        if (definition == null || query == null) {
            throw error(MetricErrorCode.QUERY_INVALID, "", "Metric definition and query must not be null");
        }
        if (definition.derivationType().isDerived()) {
            throw error(MetricErrorCode.METRIC_EXECUTION_MODE_UNSUPPORTED, "/metric/fact",
                    "Derived metric is not supported by JDBC SQL compiler");
        }
        if (query.startTime() == null) {
            throw error(MetricErrorCode.QUERY_INVALID, "/startTime", "startTime must not be null");
        }
        if (query.endTime() == null || !query.startTime().isBefore(query.endTime())) {
            throw error(MetricErrorCode.QUERY_INVALID, "/endTime", "endTime must be after startTime");
        }
        if (query.subjectType() != null && !definition.subject().type().equals(query.subjectType())) {
            throw error(MetricErrorCode.QUERY_INVALID, "/subjectType", "Subject type does not match definition");
        }
        validateParameters(definition.parameters(), query.parameterValues());
        boolean global = MetricSubjectDsl.GLOBAL.equals(definition.subject().type());
        if (global && query.subjectId() != null) {
            throw error(MetricErrorCode.QUERY_INVALID, "/subjectId", "GLOBAL metric forbids subjectId");
        }
        if (!global && (!(query.subjectId() instanceof String id) || id.isBlank())) {
            throw error(MetricErrorCode.QUERY_INVALID, "/subjectId", "Subject metric requires a non-blank string subjectId");
        }
        validateDimensions(definition.dimensions(), query.dimensionValues());
    }

    /**
     * 无声明维度时允许调用方省略容器；一旦声明维度，容器必须存在且键集合必须完全匹配。
     */
    private static void validateDimensions(List<String> definitions, Map<String, Object> dimensions) {
        if (definitions.isEmpty()) {
            if (dimensions != null && !dimensions.isEmpty()) {
                throw error(MetricErrorCode.QUERY_INVALID, "/dimensionValues",
                        "Metric does not declare query dimensions");
            }
            return;
        }
        if (dimensions == null || !Set.copyOf(definitions).equals(dimensions.keySet())) {
            throw error(MetricErrorCode.QUERY_INVALID, "/dimensionValues",
                    "Dimension keys must exactly match metric definition");
        }
    }

    private static void validateParameters(Map<String, MetricQueryParameterDsl> definitions,
                                           Map<String, Object> parameters) {
        if (parameters == null && definitions.isEmpty()) {
            return;
        }
        if (parameters == null || parameters.keySet().stream().anyMatch(name -> name == null || name.isBlank())) {
            throw error(MetricErrorCode.METRIC_PARAMETER_TYPE_MISMATCH, "/parameterValues",
                    "Query parameters must have a container and non-blank names");
        }
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
            MetricQueryParameterDsl contract = definitions.get(name);
            Integer minimum = contract.minimum();
            Integer maximum = contract.maximum();
            if ((minimum != null && value < minimum) || (maximum != null && value > maximum)) {
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

    private static Field<Object> column(MetricJdbcMapping binding, Map<String, String> aliases, String field) {
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
