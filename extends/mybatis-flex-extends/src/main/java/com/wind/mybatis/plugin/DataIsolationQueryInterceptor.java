package com.wind.mybatis.plugin;

import com.wind.mybatis.isolation.DataIsolationStrategy;
import lombok.extern.slf4j.Slf4j;
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
 * 在 MyBatis 执行层实现读隔离：在生成查询缓存键前绑定范围，不拦截 INSERT/UPDATE/DELETE。
 * 仅执行显式传入的策略，不自动添加内置维度规则。
 * 从当前工厂的 BaseMapper 实体元数据自动发现表和隔离字段。
 * 支持单表、派生包装、UNION 及限定的 WHERE EXISTS；其他关联、CTE 及嵌套查询映射拒绝执行。
 *
 * @author wuxp
 * @since 2026-10-16
 */
@Slf4j
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

    private final boolean strict;

    private final Set<Class<?>> publicEntities;

    private final Map<Configuration, SelectIsolationRewriter> rewriters = new ConcurrentHashMap<>();

    /**
     * 使用完整策略集合，不隐式追加默认策略。按优先级升序执行，同值保持输入顺序，各策略条件取 AND。
     *
     * @param strategies 非空的隔离策略集合
     */
    public DataIsolationQueryInterceptor(List<? extends DataIsolationStrategy> strategies) {
        this(strategies, false, Set.of());
    }

    private DataIsolationQueryInterceptor(List<? extends DataIsolationStrategy> strategies, boolean strict,
                                          Set<Class<?>> publicEntities) {
        if (strategies.isEmpty()) {
            throw new IllegalArgumentException("Isolation strategies must not be empty");
        }
        List<DataIsolationStrategy> ordered = new ArrayList<>(List.copyOf(strategies));
        ordered.sort(Comparator.comparingInt(DataIsolationStrategy::getOrder));
        this.strategies = List.copyOf(ordered);
        this.strict = strict;
        this.publicEntities = Set.copyOf(publicEntities);
    }

    /**
     * 严格覆盖模式：每个已登记实体须匹配策略或明确声明为公共实体，未知物理表拒绝查询。
     * 公共声明不能覆盖适用策略，所有声明仍须在实际工厂登记 BaseMapper。
     *
     * @param strategies 完整策略集合
     * @param publicEntities 明确无需隔离的实体；不接受表名或请求输入
     * @return 严格覆盖的查询拦截器
     */
    public static DataIsolationQueryInterceptor strict(List<? extends DataIsolationStrategy> strategies,
                                                       Set<Class<?>> publicEntities) {
        return new DataIsolationQueryInterceptor(strategies, true, publicEntities);
    }

    /**
     * 在工厂完成 Mapper 登记后预热元数据，校验安装状态及必须受保护的实体，不读取请求范围。
     * requiredEntities 可从应用已有资源登记提取；此检查不替代策略自身的业务边界定义。
     *
     * @param configuration 实际业务工厂配置
     * @param requiredEntities 必须有实体映射且匹配隔离策略的实体
     */
    public void validate(Configuration configuration, Class<?>... requiredEntities) {
        if (configuration.getInterceptors().stream().noneMatch(interceptor -> interceptor == this)) {
            throw new IllegalStateException("SqlSessionFactory has not installed this isolation interceptor");
        }
        rewriterFor(configuration).validateRequiredEntities(requiredEntities);
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        Object[] args = invocation.getArgs();
        MappedStatement statement = (MappedStatement) args[0];
        if (statement.getSqlCommandType() != SqlCommandType.SELECT) {
            return invocation.proceed();
        }
        boolean debugEnabled = isDebugLoggingEnabled();
        long started = debugEnabled ? System.nanoTime() : 0;
        PreparationStage stage = PreparationStage.RESULT_MAPPING;
        BoundSql original;
        BoundSql isolated;
        try {
            rejectNestedQueries(statement, statement.getResultMaps(), new HashSet<>());
            stage = PreparationStage.BOUND_SQL;
            Object parameter = args[1];
            original = args.length == 6 ? (BoundSql) args[5] : statement.getBoundSql(parameter);
            stage = PreparationStage.METADATA;
            SelectIsolationRewriter rewriter = rewriterFor(statement.getConfiguration());
            stage = PreparationStage.REWRITE;
            isolated = rewriter.rewrite(statement, original);
            stage = PreparationStage.STATEMENT_COPY;
            if (isolated == original) {
                if (args.length != 6) {
                    // 固定已检查的 SQL，避免动态 SqlSource 在执行时生成不同的查询。
                    args[0] = copyStatement(statement, original);
                }
            } else {
                MappedStatement copy = copyStatement(statement, isolated);
                Object isolatedParameter = isolated.getParameterObject();
                args[0] = copy;
                args[1] = isolatedParameter;
                if (args.length == 6) {
                    stage = PreparationStage.CACHE_KEY;
                    Executor executor = (Executor) invocation.getTarget();
                    RowBounds rowBounds = (RowBounds) args[2];
                    args[4] = executor.createCacheKey(copy, isolatedParameter, rowBounds, isolated);
                    args[5] = isolated;
                }
            }
        } catch (RuntimeException failure) {
            logRejectedQuery(statement, stage, failure);
            throw failure;
        }
        logPreparedQuery(statement, original, isolated, debugEnabled, started);
        return invocation.proceed();
    }

    private void logPreparedQuery(MappedStatement statement, BoundSql original, BoundSql isolated, boolean debugEnabled, long started) {
        try {
            boolean traceEnabled = log.isTraceEnabled();
            if (!debugEnabled && !traceEnabled) {
                return;
            }
            long elapsedMicros = debugEnabled ? (System.nanoTime() - started) / 1000 : 0;
            boolean sqlChanged = !original.getSql().equals(isolated.getSql());
            if (debugEnabled) {
                log.debug("Read isolation prepared: statement={}, sqlChanged={}, parameterCount={}, elapsedMicros={}",
                        statement.getId(), sqlChanged, isolated.getParameterMappings().size(), elapsedMicros);
            }
            if (traceEnabled) {
                String before = IsolationSqlDiagnostics.format(original.getSql());
                String after = sqlChanged ? IsolationSqlDiagnostics.format(isolated.getSql()) : before;
                log.trace("Read isolation SQL: statement={}, sqlChanged={}, before={}, after={}",
                        statement.getId(), sqlChanged, before, after);
            }
        } catch (RuntimeException ignored) {
            // 诊断展示或日志提交失败不改变查询结果；避免在失败的日志链上递归记录。
        }
    }

    private boolean isDebugLoggingEnabled() {
        try {
            return log.isDebugEnabled();
        } catch (RuntimeException ignored) {
            // 过滤器故障时关闭本次摘要，不阻止实际查询。
            return false;
        }
    }

    private void logRejectedQuery(MappedStatement statement, PreparationStage stage, RuntimeException failure) {
        try {
            // 异常详情可能含 SQL 或业务值，只记定位摘要，原异常交回调用方。
            StackTraceElement[] stack = failure.getStackTrace();
            log.warn("Read isolation rejected: statement={}, stage={}, failureType={}, failureSite={}",
                    statement.getId(), stage, failure.getClass().getName(), stack.length == 0 ? "unknown" : stack[0]);
        } catch (RuntimeException ignored) {
            // 保留查询原始异常，不让日志提交异常将其覆盖。
        }
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
            return new SelectIsolationRewriter(key, strict, publicEntities, strategies.toArray(DataIsolationStrategy[]::new));
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

    /**
     * 查询进入 Executor 之前的隔离准备阶段，用于定位失败位置。
     *
     * @author wuxp
     * @since 2026-11-09
     */
    private enum PreparationStage {
        RESULT_MAPPING,
        BOUND_SQL,
        METADATA,
        REWRITE,
        STATEMENT_COPY,
        CACHE_KEY
    }
}
