package com.wind.mybatis.plugin;

import com.wind.mybatis.isolation.DataIsolationStrategy;
import com.wind.mybatis.isolation.FieldDataIsolationStrategy;
import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ResultMap;
import org.apache.ibatis.mapping.ResultMapping;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 在 MyBatis 执行层实现单表读隔离：在生成查询缓存键前绑定范围，不拦截 INSERT/UPDATE/DELETE。
 * 从当前工厂的 BaseMapper 实体元数据自动发现表和隔离字段。
 * 受管单表支持限定形式的 WHERE EXISTS；其他关联、子查询、union、CTE 及嵌套查询映射遇到时拒绝执行。
 *
 * @author wuxp
 * @since 2026-10-16
 */
@Intercepts({
        @Signature(type = Executor.class, method = "query", args = {
                MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class
        }),
        @Signature(type = Executor.class, method = "query", args = {
                MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class, CacheKey.class, BoundSql.class
        }),
        @Signature(type = Executor.class, method = "queryCursor", args = {
                MappedStatement.class, Object.class, RowBounds.class
        })
})
public final class DataIsolationQueryInterceptor implements Interceptor {

    private final List<DataIsolationStrategy> strategies;

    private final Map<Configuration, SelectIsolationRewriter> rewriters = new ConcurrentHashMap<>();

    /**
     * 使用默认字段策略，按实体接口和当前上下文启用租户、工作空间、环境和测试数据隔离。
     */
    public DataIsolationQueryInterceptor() {
        this(List.of(new FieldDataIsolationStrategy()));
    }

    /**
     * 使用完整策略集合，不隐式追加默认策略。按优先级升序执行，同值保持输入顺序，各策略条件取 AND。
     *
     * @param strategies 非空的隔离策略集合
     */
    public DataIsolationQueryInterceptor(List<? extends DataIsolationStrategy> strategies) {
        if (strategies.isEmpty()) {
            throw new IllegalArgumentException("Isolation strategies must not be empty");
        }
        List<DataIsolationStrategy> ordered = new ArrayList<>(List.copyOf(strategies));
        ordered.sort(Comparator.comparingInt(DataIsolationStrategy::getOrder));
        this.strategies = List.copyOf(ordered);
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        Object[] args = invocation.getArgs();
        MappedStatement statement = (MappedStatement) args[0];
        if (statement.getSqlCommandType() != SqlCommandType.SELECT) {
            return invocation.proceed();
        }
        rejectNestedQueries(statement, statement.getResultMaps(), new HashSet<>());
        Object parameter = args[1];
        BoundSql original = args.length == 6 ? (BoundSql) args[5] : statement.getBoundSql(parameter);
        BoundSql isolated = rewriterFor(statement.getConfiguration()).rewrite(statement, original);
        if (isolated == original) {
            if (args.length != 6) {
                // 固定已检查的 SQL，避免动态 SqlSource 在执行时生成不同的查询。
                args[0] = copyStatement(statement, original);
            }
            return invocation.proceed();
        }
        MappedStatement copy = copyStatement(statement, isolated);
        Object isolatedParameter = isolated.getParameterObject();
        args[0] = copy;
        args[1] = isolatedParameter;
        if (args.length == 6) {
            Executor executor = (Executor) invocation.getTarget();
            RowBounds rowBounds = (RowBounds) args[2];
            args[4] = executor.createCacheKey(copy, isolatedParameter, rowBounds, isolated);
            args[5] = isolated;
        }
        return invocation.proceed();
    }

    private SelectIsolationRewriter rewriterFor(Configuration configuration) {
        int mapperCount = configuration.getMapperRegistry().getMappers().size();
        SelectIsolationRewriter current = rewriters.get(configuration);
        if (current != null && current.mapperCount() == mapperCount) {
            return current;
        }
        return rewriters.compute(configuration, (key, previous) -> {
            if (previous != null && previous.mapperCount() == mapperCount) {
                return previous;
            }
            return new SelectIsolationRewriter(key, strategies.toArray(DataIsolationStrategy[]::new));
        });
    }

    private void rejectNestedQueries(MappedStatement statement, List<ResultMap> resultMaps, Set<String> visited) {
        for (ResultMap resultMap : resultMaps) {
            if (!visited.add(resultMap.getId())) {
                continue;
            }
            if (resultMap.getDiscriminator() != null) {
                for (String target : resultMap.getDiscriminator().getDiscriminatorMap().values()) {
                    rejectNestedQueries(statement, List.of(statement.getConfiguration().getResultMap(target)), visited);
                }
            }
            for (ResultMapping mapping : resultMap.getResultMappings()) {
                if (mapping.getNestedQueryId() != null) {
                    throw new IllegalStateException("Nested selects/lazy loading are not supported by read isolation: " + statement.getId());
                }
                if (mapping.getNestedResultMapId() != null) {
                    ResultMap nested = statement.getConfiguration().getResultMap(mapping.getNestedResultMapId());
                    rejectNestedQueries(statement, List.of(nested), visited);
                }
            }
        }
    }

    private MappedStatement copyStatement(MappedStatement source, BoundSql sql) {
        MappedStatement.Builder builder = new MappedStatement.Builder(source.getConfiguration(), source.getId(),
                ignored -> sql, source.getSqlCommandType())
                .resource(source.getResource())
                .fetchSize(source.getFetchSize())
                .timeout(source.getTimeout())
                .statementType(source.getStatementType())
                .resultSetType(source.getResultSetType())
                .parameterMap(source.getParameterMap())
                .resultMaps(source.getResultMaps())
                .cache(source.getCache())
                .flushCacheRequired(source.isFlushCacheRequired())
                .useCache(source.isUseCache())
                .resultOrdered(source.isResultOrdered())
                .keyGenerator(source.getKeyGenerator())
                .databaseId(source.getDatabaseId())
                .lang(source.getLang())
                .dirtySelect(source.isDirtySelect());
        if (source.getKeyProperties() != null) {
            builder.keyProperty(String.join(",", source.getKeyProperties()));
        }
        if (source.getKeyColumns() != null) {
            builder.keyColumn(String.join(",", source.getKeyColumns()));
        }
        if (source.getResultSets() != null) {
            builder.resultSets(String.join(",", source.getResultSets()));
        }
        return builder.build();
    }

}
