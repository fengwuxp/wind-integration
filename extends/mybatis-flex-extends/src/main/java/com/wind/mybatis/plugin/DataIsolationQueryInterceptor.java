package com.wind.mybatis.plugin;

import com.wind.integration.core.context.DataIsolationContextHolder;
import com.wind.integration.core.enums.DataIsolationDimensions;
import com.wind.mybatis.isolation.DataIsolationScope;
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
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import static com.wind.integration.core.enums.DataIsolationDimensions.ENVIRONMENT;
import static com.wind.integration.core.enums.DataIsolationDimensions.TENANT;
import static com.wind.integration.core.enums.DataIsolationDimensions.TEST_DATA;
import static com.wind.integration.core.enums.DataIsolationDimensions.WORKSPACE;

/**
 * 在 MyBatis 执行层实现单表读隔离：在生成查询缓存键前绑定范围，不拦截 INSERT/UPDATE/DELETE。
 * 从当前工厂的 BaseMapper 实体元数据自动发现表和隔离字段。
 * 受管表的关联、子查询、union、CTE 以及嵌套查询映射尚不支持，遇到时拒绝执行。
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

    private final List<Rule> rules;

    private final Map<Configuration, SelectIsolationRewriter> rewriters = new ConcurrentHashMap<>();

    /**
     * 自动发现当前工厂已注册 Mapper 的实体，启用租户、工作空间、环境和测试数据隔离；工作空间范围须由业务提供。
     */
    public DataIsolationQueryInterceptor() {
        this(List.of());
    }

    /**
     * 定制内置维度的规则；按维度覆盖默认配置，表名和物理列由实体元数据解析。
     *
     * @param rules 内置维度、实体属性及范围供应者，不需要逐表登记
     */
    public DataIsolationQueryInterceptor(List<Rule> rules) {
        Map<DataIsolationDimensions, Rule> configured = new LinkedHashMap<>();
        // TODO 字段名称应该支持标记或配置
        configured.put(TENANT, new Rule(TENANT, "tenantId", () -> DataIsolationScope.of(DataIsolationContextHolder.getTenantId())));
        configured.put(WORKSPACE, new Rule(WORKSPACE, "workspaceId", () -> DataIsolationScope.of(DataIsolationContextHolder.getWorkspaceId())));
        configured.put(ENVIRONMENT, new Rule(ENVIRONMENT, "env", () -> DataIsolationScope.of(DataIsolationContextHolder.getEnv())));
        configured.put(TEST_DATA, new Rule(TEST_DATA, "testData", () -> DataIsolationScope.of(DataIsolationContextHolder.isTestData())));
        Set<DataIsolationDimensions> overridden = EnumSet.noneOf(DataIsolationDimensions.class);
        for (Rule rule : rules) {
            if (!overridden.add(rule.dimension())) {
                throw new IllegalArgumentException("Duplicate isolation rule: " + rule.dimension());
            }
            configured.put(rule.dimension(), rule);
        }
        this.rules = List.copyOf(configured.values());
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
            return new SelectIsolationRewriter(key, rules);
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
     * 隔离维度与实体属性的绑定；每次查询从范围供应者读取当前允许访问的值。
     *
     * @param dimension     隔离维度，通过其接口匹配实体
     * @param property      实体属性名，物理列从 MyBatis-Flex 元数据读取
     * @param scopeSupplier 当前范围供应者，返回 null 表示缺少必要上下文
     * @author wuxp
     * @since 2026-10-16
     */
    @NullMarked
    public record Rule(DataIsolationDimensions dimension, String property, Supplier<@Nullable DataIsolationScope> scopeSupplier) {

        public Rule {
            if (!property.matches("[A-Za-z_][A-Za-z0-9_]*")) {
                throw new IllegalArgumentException("Isolation property must be a simple identifier");
            }
        }
    }

}
