package com.wind.mybatis.isolation;

import com.wind.integration.core.context.DataIsolationContextHolder;
import com.wind.integration.core.context.DataIsolationQueryIgnoreHolder;
import com.wind.integration.core.enums.DataIsolationDimensions;
import org.jspecify.annotations.NullMarked;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 基于实体持久属性生成行级读取隔离条件，基础规则取 AND，完整分组内取 AND、组间取 OR。
 * 构造方法仅使用传入规则；withDefaults 显式选择四个内置维度约定。
 * 规则按实体类型或其实现的接口匹配，仅实现隔离接口不会自动启用规则。
 * 本策略不计算授权、不控制返回列，也不负责写入归属校验。
 *
 * @author wuxp
 * @since 2026-10-30
 */
@NullMarked
public final class FieldDataIsolationStrategy implements DataIsolationStrategy {

    private static final ConversionService PARAMETER_CONVERSIONS = DefaultConversionService.getSharedInstance();

    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private final List<FieldRule> defaults;

    private final List<FieldRule> rules;

    private final int order;

    private final Map<Class<?>, EntityRules> groupedRules;

    /**
     * 缓存当前策略对实体元数据的静态绑定；键包含实体类型和列映射，不缓存请求范围。
     */
    private final Map<EntityMetadata, Map<FieldRule, String>> entityBindings = new ConcurrentHashMap<>();

    public FieldDataIsolationStrategy(List<FieldRule> rules) {
        this(rules, 0);
    }

    public FieldDataIsolationStrategy(List<FieldRule> rules, int order) {
        this(List.of(), rules, order);
    }

    private FieldDataIsolationStrategy(List<FieldRule> defaults, List<FieldRule> rules, int order) {
        this(defaults, rules, List.of(), order);
    }

    private FieldDataIsolationStrategy(List<FieldRule> defaults, List<FieldRule> rules,
                                       List<EntityRules> registrations, int order) {
        Map<Class<?>, Set<DataIsolationDimensions>> dimensions = new LinkedHashMap<>();
        Map<Class<?>, Set<String>> fields = new LinkedHashMap<>();
        for (FieldRule rule : rules) {
            if (rule.dimension() != null) {
                Set<DataIsolationDimensions> entityDimensions = dimensions.computeIfAbsent(rule.entityType(),
                        ignored -> EnumSet.noneOf(DataIsolationDimensions.class));
                if (!entityDimensions.add(rule.dimension())) {
                    throw new IllegalArgumentException("Duplicate isolation rule: " + rule.entityType().getName() + "." + rule.dimension());
                }
            }
            if (!fields.computeIfAbsent(rule.entityType(), ignored -> new HashSet<>()).add(rule.property())) {
                throw new IllegalArgumentException("Duplicate isolation field: " + rule.entityType().getName() + "." + rule.property());
            }
        }
        this.defaults = List.copyOf(defaults);
        this.rules = List.copyOf(rules);
        this.order = order;
        Map<Class<?>, EntityRules> grouped = new LinkedHashMap<>();
        for (EntityRules registration : registrations) {
            if (grouped.putIfAbsent(registration.entityType(), registration) != null) {
                throw new IllegalArgumentException("Duplicate grouped isolation entity: " + registration.entityType().getName());
            }
        }
        groupedRules = Map.copyOf(grouped);
    }

    /**
     * 显式启用租户、工作空间、环境和测试数据四个内置维度。
     * 每个实体优先使用最具体的自定义维度规则；普通字段规则作为额外约束加入。
     * 只需部分维度时应使用显式规则构造方法。
     */
    public static FieldDataIsolationStrategy withDefaults(List<FieldRule> overrides) {
        return new FieldDataIsolationStrategy(defaultRules(), overrides, 0);
    }

    /**
     * 创建基础字段 AND 完整分组的策略，不隐式添加内置隔离维度。
     *
     * @param baseRules 独立基础字段规则
     * @param registrations 按实体精确匹配的完整分组登记
     * @return 默认顺序的字段分组策略
     */
    public static FieldDataIsolationStrategy grouped(List<FieldRule> baseRules, List<EntityRules> registrations) {
        return grouped(baseRules, registrations, 0);
    }

    /**
     * 指定顺序创建字段分组策略，顺序不改变基础规则与分组的交集关系。
     *
     * @param baseRules 独立基础字段规则
     * @param registrations 按实体精确匹配的完整分组登记
     * @param order 执行顺序
     * @return 字段分组策略
     */
    public static FieldDataIsolationStrategy grouped(List<FieldRule> baseRules, List<EntityRules> registrations, int order) {
        return new FieldDataIsolationStrategy(List.of(), baseRules, registrations, order);
    }

    @Override
    public boolean supports(EntityMetadata entity) {
        boolean hasBaseRules = !bindings(entity).isEmpty();
        EntityRules registration = groupedRules.get(entity.type());
        if (registration == null) {
            return hasBaseRules;
        }
        for (String property : registration.properties()) {
            validateColumn(entity, property);
            propertyType(entity.type(), property);
        }
        return true;
    }

    /**
     * 静态字段绑定，用于同一工厂中同名表映射的一致性检查；不读取范围供应者。
     */
    public Map<FieldRule, String> bindings(EntityMetadata entity) {
        return entityBindings.computeIfAbsent(entity, this::resolveBindings);
    }

    /**
     * 供工厂核对同表多实体的静态映射；分组按精确实体登记，不承诺不同实体共享同一规则。
     *
     * @param entity 实体元数据
     * @return 可比较的静态签名，不读取分组供应者
     */
    public Object mappingSignature(EntityMetadata entity) {
        return groupedRules.containsKey(entity.type()) ? entity : bindings(entity);
    }

    private Map<FieldRule, String> resolveBindings(EntityMetadata entity) {
        Map<DataIsolationDimensions, List<FieldRule>> candidates = new EnumMap<>(DataIsolationDimensions.class);
        List<FieldRule> ordinary = new ArrayList<>();
        for (FieldRule rule : rules) {
            if (!rule.entityType().isAssignableFrom(entity.type())) {
                continue;
            }
            if (rule.dimension() == null) {
                ordinary.add(rule);
            } else {
                candidates.computeIfAbsent(rule.dimension(), ignored -> new ArrayList<>()).add(rule);
            }
        }
        for (FieldRule rule : defaults) {
            if (rule.entityType().isAssignableFrom(entity.type())) {
                candidates.putIfAbsent(rule.dimension(), List.of(rule));
            }
        }

        List<FieldRule> selected = new ArrayList<>();
        candidates.forEach((dimension, matches) -> selected.add(selectRule(entity, dimension, matches)));
        selected.addAll(ordinary);
        Map<FieldRule, String> result = new LinkedHashMap<>();
        for (FieldRule rule : selected) {
            validateColumn(entity, rule.property());
            result.put(rule, entity.propertyColumns().get(rule.property()));
        }
        return Collections.unmodifiableMap(result);
    }

    private FieldRule selectRule(EntityMetadata entity, DataIsolationDimensions dimension, List<FieldRule> candidates) {
        /*
         * 仅保留未被更具体类型覆盖的规则；互不包含的类型不能靠配置顺序决定优先级。
         */
        List<FieldRule> mostSpecific = candidates.stream()
                .filter(candidate -> candidates.stream().noneMatch(other -> candidate != other
                        && candidate.entityType().isAssignableFrom(other.entityType())))
                .toList();
        if (mostSpecific.size() != 1) {
            throw new IllegalArgumentException("Ambiguous isolation rules: " + entity.type().getName() + "." + dimension);
        }
        return mostSpecific.getFirst();
    }

    @Override
    public List<Condition> resolve(QueryContext context) {
        supports(context.entity());
        List<Condition> result = new ArrayList<>(resolveBaseFields(context));
        EntityRules registration = groupedRules.get(context.entity().type());
        if (registration != null) {
            List<ConditionGroup> supplied = registration.groups().get();
            if (supplied == null) {
                throw new IllegalStateException("Missing isolation groups: " + registration.entityType().getName());
            }
            List<Condition> branches = new ArrayList<>();
            for (ConditionGroup group : List.copyOf(supplied)) {
                List<Condition> conditions = new ArrayList<>();
                for (FieldCondition field : group.fields()) {
                    if (!registration.properties().contains(field.property())) {
                        throw new IllegalArgumentException("Isolation group property is not registered: " + field.property());
                    }
                    conditions.add(resolveField(context, field));
                }
                for (Function<QueryContext, Condition> factory : group.trustedConditions()) {
                    Condition condition = factory.apply(context);
                    if (condition == null) {
                        throw new IllegalStateException("Missing trusted isolation condition");
                    }
                    conditions.add(condition);
                }
                branches.add(combine(conditions, " AND ", "1 = 1"));
            }
            result.add(combine(branches, " OR ", "1 = 0"));
        }
        return List.copyOf(result);
    }

    private List<Condition> resolveBaseFields(QueryContext context) {
        List<Condition> result = new ArrayList<>();
        for (Map.Entry<FieldRule, String> binding : bindings(context.entity()).entrySet()) {
            FieldRule rule = binding.getKey();
            if (rule.dimension() != null && DataIsolationQueryIgnoreHolder.isIgnored(rule.dimension())) {
                continue;
            }
            DataIsolationScope scope = rule.scopeSupplier().get();
            if (scope == null) {
                throw new IllegalStateException("Missing isolation scope: " + rule.entityType().getName() + "." + rule.property());
            }
            if (scope.unrestricted()) {
                continue;
            }
            if (scope.values().isEmpty()) {
                result.add(Condition.of("1 = 0"));
            } else {
                String column = context.qualifier() + "." + binding.getValue();
                String sql = scope.values().size() == 1 ? column + " = ?"
                        : column + " IN (" + String.join(", ", Collections.nCopies(scope.values().size(), "?")) + ")";
                result.add(new Condition(sql, scope.values()));
            }
        }
        return List.copyOf(result);
    }

    /**
     * 关系内表只执行基础字段边界及其显式忽略语义，不再次请求独立资源分组或递归关系条件。
     */
    @Override
    public List<Condition> resolveRelation(QueryContext context) {
        return resolveBaseFields(context);
    }

    @Override
    public int getOrder() {
        return order;
    }

    private static Condition resolveField(QueryContext context, FieldCondition field) {
        String column = context.column(field.property());
        if (field.match() == FieldMatch.IS_NOT_NULL) {
            return Condition.of(column + " IS NOT NULL");
        }
        if (field.values().isEmpty()) {
            return Condition.of("1 = 0");
        }
        Class<?> type = propertyType(context.entity().type(), field.property());
        List<Object> parameters = new ArrayList<>();
        for (Object value : field.values()) {
            if (ClassUtils.isAssignableValue(type, value)) {
                parameters.add(value);
            } else if (value instanceof String reference) {
                Object converted = PARAMETER_CONVERSIONS.convert(reference, type);
                if (converted == null || !reference.equals(PARAMETER_CONVERSIONS.convert(converted, String.class))) {
                    throw new IllegalArgumentException("Grouped isolation mapping changes reference: " + field.property());
                }
                parameters.add(converted);
            } else {
                throw new IllegalArgumentException("Grouped isolation value type does not match property: " + field.property());
            }
        }
        String sql = parameters.size() == 1 ? column + " = ?"
                : column + " IN (" + String.join(", ", Collections.nCopies(parameters.size(), "?")) + ")";
        return new Condition(sql, parameters);
    }

    private static Class<?> propertyType(Class<?> entityType, String property) {
        Field field = ReflectionUtils.findField(entityType, property);
        if (field == null) {
            throw new IllegalArgumentException("Missing grouped isolation entity property: " + entityType.getName() + "." + property);
        }
        return field.getType();
    }

    private static Condition combine(List<Condition> conditions, String delimiter, String emptySql) {
        if (conditions.isEmpty()) {
            return Condition.of(emptySql);
        }
        StringJoiner sql = new StringJoiner(delimiter, "(", ")");
        List<Object> parameters = new ArrayList<>();
        for (Condition condition : conditions) {
            sql.add("(" + condition.sql() + ")");
            parameters.addAll(condition.values());
        }
        return new Condition(sql.toString(), parameters);
    }

    private static void validateColumn(EntityMetadata entity, String property) {
        String column = entity.propertyColumns().get(property);
        if (column == null || !IDENTIFIER_PATTERN.matcher(column).matches()) {
            throw new IllegalArgumentException("Missing or unsupported isolation column mapping: " + entity.type().getName() + "." + property);
        }
    }

    private static List<FieldRule> defaultRules() {
        return List.of(
                FieldRule.forDimension(DataIsolationDimensions.TENANT, "tenantId", () -> DataIsolationScope.of(DataIsolationContextHolder.getTenantId())),
                FieldRule.forDimension(DataIsolationDimensions.WORKSPACE, "workspaceId", () -> DataIsolationScope.of(DataIsolationContextHolder.getWorkspaceId())),
                FieldRule.forDimension(DataIsolationDimensions.ENVIRONMENT, "env", () -> DataIsolationScope.of(DataIsolationContextHolder.getEnv())),
                FieldRule.forDimension(DataIsolationDimensions.TEST_DATA, "testData", () -> DataIsolationScope.of(DataIsolationContextHolder.isTestData())));
    }

    /**
     * 分组字段仅提供集合匹配和存在性，不承担权限用途或业务操作符。
     *
     * @author wuxp
     * @since 2026-11-20
     */
    public enum FieldMatch {
        VALUES, IS_NOT_NULL
    }

    /**
     * 一条字段条件，VALUES 空集合为假；重复属性允许在同组内分别成立。
     * 新分组的字符串引用按实体字段类型转换，并要求往返文本不变；已匹配类型直接绑定。
     *
     * @param property 简单 Java 持久化属性名
     * @param match 集合匹配或字段非空
     * @param values 非空元素的不可变值列表；IS_NOT_NULL 必须为空列表
     * @author wuxp
     * @since 2026-11-20
     */
    public record FieldCondition(String property, FieldMatch match, List<?> values) {
        public FieldCondition {
            if (!IDENTIFIER_PATTERN.matcher(property).matches()) {
                throw new IllegalArgumentException("Isolation property must be a simple identifier");
            }
            values = List.copyOf(values);
            if (match == FieldMatch.IS_NOT_NULL && !values.isEmpty()) {
                throw new IllegalArgumentException("IS_NOT_NULL isolation field must not contain values");
            }
        }

        /**
         * @param property 已登记属性
         * @param values 当前完整允许值，空集合表示不可见
         * @return 参数化集合匹配条件
         */
        public static FieldCondition ofValues(String property, List<?> values) {
            return new FieldCondition(property, FieldMatch.VALUES, values);
        }

        /**
         * @param property 非空能够证明所需关联存在的已登记属性
         * @return 字段非空条件
         */
        public static FieldCondition isNotNull(String property) {
            return new FieldCondition(property, FieldMatch.IS_NOT_NULL, List.of());
        }
    }

    /**
     * 一条完整 AND 分支，字段与可信条件均须成立；显式空组为真，不等于没有分组。
     * 关系函数必须保留同一关系行上的完整约束与真实外内关联，不执行 I/O。
     *
     * @param fields 字段条件，可重复相同属性以保留交集
     * @param trustedConditions 可信参数化条件，按当前查询别名构造，不能返回 null
     * @author wuxp
     * @since 2026-11-20
     */
    public record ConditionGroup(List<FieldCondition> fields, List<Function<QueryContext, Condition>> trustedConditions) {
        public ConditionGroup {
            fields = List.copyOf(fields);
            trustedConditions = List.copyOf(trustedConditions);
        }

        /**
         * @return 显式恒真分支；基础字段约束仍独立生效
         */
        public static ConditionGroup unrestricted() {
            return new ConditionGroup(List.of(), List.of());
        }
    }

    /**
     * 按精确实体登记分组，静态属性与运行时允许值分离；只缓存静态绑定。
     * 每个 SQL 叶只读取一次 groups，同一请求的多叶查询应读取同一已准备快照。
     *
     * @param entityType 精确匹配的实体类型
     * @param properties 字段叶的静态属性白名单及可信条件所需根属性
     * @param groups 本次完整 OR 分组，空列表为假，null 或异常表示来源失败
     * @author wuxp
     * @since 2026-11-20
     */
    public record EntityRules(Class<?> entityType, Set<String> properties, Supplier<List<ConditionGroup>> groups) {
        public EntityRules {
            properties = Set.copyOf(properties);
            for (String property : properties) {
                if (!IDENTIFIER_PATTERN.matcher(property).matches()) {
                    throw new IllegalArgumentException("Isolation property must be a simple identifier");
                }
            }
        }
    }

}
