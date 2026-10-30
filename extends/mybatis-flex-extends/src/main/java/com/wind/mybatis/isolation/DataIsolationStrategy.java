package com.wind.mybatis.isolation;

import com.wind.integration.core.enums.DataIsolationDimensions;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.core.Ordered;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * 为已登记实体的读取提供隔离条件；策略之间取 AND，不按优先级覆盖结果。
 * 配置应不可变且实现须线程安全，请求范围仅在 resolve 中读取。
 *
 * @author wuxp
 * @since 2026-10-30
 */
@NullMarked
public interface DataIsolationStrategy extends Ordered {

    /**
     * 仅依据实体元数据判断适用性，不读取请求上下文；结果用于元数据缓存与 SQL 支持边界检查。
     */
    boolean supports(EntityMetadata entity);

    /**
     * 为本次受支持的单表查询提供条件，空列表表示本策略不追加条件，异常表示拒绝执行。
     * 仅返回可信代码构造的谓词；值使用占位符绑定，不能修改原 SQL 或参数。
     */
    List<Condition> resolve(QueryContext context);

    @Override
    default int getOrder() {
        return 0;
    }

    /**
     * 实体字段的读取范围。property 是 Java 持久化属性名，物理列由实体元数据解析。
     * dimension 是可选的内置维度标签；组合方式和是否允许忽略由消费策略决定。
     *
     * @param entityType    适用实体或接口类型
     * @param property      Java 持久化属性名
     * @param scopeSupplier 当前查询的允许值范围
     * @param dimension     可选的内置维度标签
     * @author wuxp
     * @since 2026-10-30
     */
    record FieldRule(Class<?> entityType, String property, Supplier<@Nullable DataIsolationScope> scopeSupplier,
                     @Nullable DataIsolationDimensions dimension) {

        private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

        public FieldRule {
            if (!IDENTIFIER_PATTERN.matcher(property).matches()) {
                throw new IllegalArgumentException("Isolation property must be a simple identifier");
            }
            if (dimension != null && !dimension.isolationType().isAssignableFrom(entityType)) {
                throw new IllegalArgumentException("Isolation entity must implement " + dimension.isolationType().getName());
            }
        }

        public FieldRule(Class<?> entityType, String property, Supplier<@Nullable DataIsolationScope> scopeSupplier) {
            this(entityType, property, scopeSupplier, null);
        }

        public static FieldRule forDimension(DataIsolationDimensions dimension, String property,
                                             Supplier<@Nullable DataIsolationScope> scopeSupplier) {
            return new FieldRule(dimension.isolationType(), property, scopeSupplier, dimension);
        }
    }

    /**
     * 当前工厂登记的实体与 Java 属性到物理列的映射。
     *
     * @param type            实体类型
     * @param propertyColumns Java 属性到物理列的映射
     * @author wuxp
     * @since 2026-10-30
     */
    record EntityMetadata(Class<?> type, Map<String, String> propertyColumns) {
        public EntityMetadata {
            propertyColumns = Map.copyOf(propertyColumns);
        }
    }

    /**
     * 本次查询目标；仅覆盖当前重写器支持的根表查询，不承诺任意关联或嵌套查询。
     *
     * @param statementId MyBatis 语句标识
     * @param tableName   查询的物理表名
     * @param qualifier   当前表别名或限定表名
     * @param entity      实体元数据
     * @author wuxp
     * @since 2026-10-30
     */
    record QueryContext(String statementId, String tableName, String qualifier, EntityMetadata entity) {
        public String column(String property) {
            String column = entity.propertyColumns().get(property);
            if (column == null || !column.matches("[A-Za-z_][A-Za-z0-9_]*")) {
                throw new IllegalArgumentException("Missing or unsupported isolation column mapping: "
                        + entity.type().getName() + "." + property);
            }
            return qualifier + "." + column;
        }
    }

    /**
     * 参数化谓词及按占位符顺序排列的值；允许 null，由 MyBatis 默认类型处理器绑定。
     * 新增参数按运行时 Java 类型选择处理器，原查询的 JDBC 类型与 TypeHandler 保持不变。
     *
     * @param sql    参数化 SQL 谓词
     * @param values 按占位符顺序排列的参数值
     * @author wuxp
     * @since 2026-10-30
     */
    record Condition(String sql, List<?> values) {
        public Condition {
            if (sql.isBlank()) {
                throw new IllegalArgumentException("Isolation condition must not be blank");
            }
            values = Collections.unmodifiableList(new ArrayList<>(values));
        }

        public static Condition of(String sql, Object... values) {
            return new Condition(sql, Arrays.asList(values));
        }
    }
}
