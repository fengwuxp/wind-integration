package com.wind.mybatis.isolation;

import com.wind.integration.core.context.DataIsolationContextHolder;
import com.wind.integration.core.context.DataIsolationQueryIgnoreHolder;
import com.wind.integration.core.enums.DataIsolationDimensions;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 基于实体持久属性的允许值集合生成行级读取隔离条件，各规则以 AND 组合。
 * 无参启用四个内置维度；显式列表只使用传入规则，withDefaults 保留默认约定。
 * 本策略不计算授权、不控制返回列，也不负责写入归属校验。
 *
 * @author wuxp
 * @since 2026-10-30
 */
@NullMarked
public final class FieldDataIsolationStrategy implements DataIsolationStrategy {

    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private final List<FieldRule> defaults;

    private final List<FieldRule> rules;

    private final int order;

    /**
     * 缓存当前策略对实体元数据的静态绑定；键包含实体类型和列映射，不缓存请求范围。
     */
    private final Map<EntityMetadata, Map<FieldRule, String>> entityBindings = new ConcurrentHashMap<>();

    public FieldDataIsolationStrategy() {
        this(defaultRules(), List.of(), 0);
    }

    public FieldDataIsolationStrategy(List<FieldRule> rules) {
        this(rules, 0);
    }

    public FieldDataIsolationStrategy(List<FieldRule> rules, int order) {
        this(List.of(), rules, order);
    }

    private FieldDataIsolationStrategy(List<FieldRule> defaults, List<FieldRule> rules, int order) {
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
    }

    /**
     * 保留默认维度，每个实体优先使用最具体的自定义维度规则；普通字段规则作为额外约束加入。
     */
    public static FieldDataIsolationStrategy withDefaults(List<FieldRule> overrides) {
        return new FieldDataIsolationStrategy(defaultRules(), overrides, 0);
    }

    @Override
    public boolean supports(EntityMetadata entity) {
        return !bindings(entity).isEmpty();
    }

    /**
     * 静态字段绑定，用于同一工厂中同名表映射的一致性检查；不读取范围供应者。
     */
    public Map<FieldRule, String> bindings(EntityMetadata entity) {
        return entityBindings.computeIfAbsent(entity, this::resolveBindings);
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

    @Override
    public int getOrder() {
        return order;
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
}
