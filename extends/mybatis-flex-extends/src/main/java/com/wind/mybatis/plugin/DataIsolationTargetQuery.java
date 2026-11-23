package com.wind.mybatis.plugin;

import com.mybatisflex.core.FlexGlobalConfig;
import com.mybatisflex.core.dialect.DbType;
import com.mybatisflex.core.dialect.DialectFactory;
import com.mybatisflex.core.logicdelete.LogicDeleteManager;
import com.mybatisflex.core.table.IdInfo;
import com.mybatisflex.core.table.TableInfo;
import com.mybatisflex.core.table.TableInfoFactory;
import com.wind.mybatis.isolation.DataIsolationStrategy.Condition;
import com.wind.mybatis.isolation.FieldDataIsolationStrategy;
import com.wind.mybatis.isolation.FieldDataIsolationStrategy.ConditionGroup;
import com.wind.mybatis.isolation.FieldDataIsolationStrategy.EntityRules;
import com.wind.mybatis.isolation.FieldDataIsolationStrategy.FieldCondition;
import org.apache.ibatis.builder.StaticSqlSource;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ResultMap;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.session.Configuration;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 准备固定实体的有限主键查询，共用普通读取的关系校验、占位符排序与工厂参数映射。
 * 在工厂完成 Mapper 登记后创建并复用；实例只持静态元数据，不保存目标 ID、分组或授权结果。
 * 不执行数据库查询，不调用已安装策略的请求供应者，不追加宿主的独立基础隔离条件。
 * 调用方须使用该 Configuration 的 ParameterHandler 和主键 TypeHandler，在所属数据源上执行。
 *
 * @author wuxp
 * @since 2026-11-23
 */
@NullMarked
public final class DataIsolationTargetQuery {

    private final Configuration configuration;

    private final TableInfo table;

    private final IdInfo key;

    private final Set<String> properties;

    private final SelectIsolationRewriter rewriter;

    /**
     * 固定实体、真实工厂及可使用的根字段；单主键自动进入字段白名单。
     * 关系内表仍必须在本工厂登记 Mapper，复用 Wind 支持的正向相关 EXISTS 语法。
     *
     * @param configuration 所属工厂的实际配置
     * @param entityType 目标实体类型
     * @param properties 可信分组使用的根属性集合
     * @throws IllegalArgumentException 实体、字段、主键或同表映射不合法
     * @throws IllegalStateException 本工厂尚未登记目标实体的 Mapper
     */
    public DataIsolationTargetQuery(Configuration configuration, Class<?> entityType, Set<String> properties) {
        this.configuration = configuration;
        table = TableInfoFactory.ofEntityClass(entityType);
        if (table.getPrimaryKeyList().size() != 1) {
            throw new IllegalArgumentException("Bounded target queries require a single primary key");
        }
        boolean hasSchema = table.getSchema() != null && !table.getSchema().isBlank();
        if (hasSchema || !table.getTableName().matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("Bounded target queries require an unqualified table");
        }
        key = table.getPrimaryKeyList().getFirst();
        Set<String> fields = new HashSet<>(properties);
        fields.add(key.getProperty());
        this.properties = Set.copyOf(fields);
        FieldDataIsolationStrategy metadata = FieldDataIsolationStrategy.grouped(List.of(), List.of(
                new EntityRules(entityType, this.properties, List::of)));
        rewriter = new SelectIsolationRewriter(configuration, metadata);
        rewriter.validateRequiredEntities(entityType);
    }

    /**
     * 生成「有限主键 AND 有效记录条件 AND 完整分组并集」的固定 SELECT，只投影真实主键。
     * 空 ID 或空分组为假，但仍检查本次全部条件；显式 unrestricted 分组不取消 ID 和存续条件。
     * 返回语句不登记进全局 Configuration，也不缓存跨调用结果。
     *
     * @param targetIds 本次有限主键集合，字符串引用须符合主键规范
     * @param groups 本次完整分组，组内 AND、组间 OR
     * @return 固定 BoundSql 的无结果缓存语句，参数和结果使用实际工厂的处理器
     * @throws IllegalStateException Mapper 登记在实例创建后发生变化
     * @throws IllegalArgumentException 字段、引用、关系或 SQL 参数不合法
     */
    public MappedStatement prepare(Set<?> targetIds, List<ConditionGroup> groups) {
        if (configuration.getMapperRegistry().getMappers().size() != rewriter.mapperCount()) {
            throw new IllegalStateException("Mapper registrations changed; recreate the bounded target query");
        }
        List<?> ids = List.copyOf(targetIds);
        List<ConditionGroup> completeGroups = List.copyOf(groups);
        FieldDataIsolationStrategy selected = FieldDataIsolationStrategy.grouped(List.of(), List.of(
                new EntityRules(table.getEntityClass(), properties, () -> completeGroups)));
        FieldDataIsolationStrategy targets = FieldDataIsolationStrategy.grouped(List.of(), List.of(
                new EntityRules(table.getEntityClass(), Set.of(key.getProperty()), () -> List.of(new ConditionGroup(
                        List.of(FieldCondition.ofValues(key.getProperty(), ids)), List.of())))));
        String sql = "SELECT data_target." + key.getColumn() + " FROM " + table.getTableName() + " data_target";
        String logicColumn = table.getLogicDeleteColumn();
        if (logicColumn != null && !logicColumn.isBlank()) {
            sql += " WHERE " + logicNormalCondition(logicColumn);
        }
        MappedStatement original = statement(new StaticSqlSource(configuration, sql));
        BoundSql prepared = rewriter.rewrite(original, original.getBoundSql(Map.of()), query -> {
            List<Condition> conditions = new ArrayList<>(targets.resolve(query));
            conditions.addAll(selected.resolve(query));
            return List.copyOf(conditions);
        });
        return statement(ignored -> prepared);
    }

    private MappedStatement statement(SqlSource source) {
        String id = DataIsolationTargetQuery.class.getName() + "." + table.getEntityClass().getName();
        ResultMap result = new ResultMap.Builder(configuration, id + ".primaryKey", key.getPropertyType(), List.of()).build();
        return new MappedStatement.Builder(configuration, id, source, SqlCommandType.SELECT)
                .useCache(false).timeout(configuration.getDefaultStatementTimeout()).resultMaps(List.of(result)).build();
    }

    private String logicNormalCondition(String column) {
        DbType previous = DialectFactory.getHintDbType();
        try {
            DialectFactory.setHintDbType(FlexGlobalConfig.getConfig(configuration).getDbType());
            return LogicDeleteManager.getProcessor().buildLogicNormalCondition(column, table, DialectFactory.getDialect());
        } finally {
            if (previous == null) {
                DialectFactory.clearHintDbType();
            } else {
                DialectFactory.setHintDbType(previous);
            }
        }
    }
}
