package com.wind.mybatis.plugin;

import com.mybatisflex.core.FlexConsts;
import com.mybatisflex.core.query.CPI;
import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryTable;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.table.TableInfo;
import com.mybatisflex.core.table.TableInfoFactory;
import com.wind.common.WindConstants;
import com.wind.common.util.ServiceInfoUtils;
import com.wind.integration.core.model.EnvIsolationObject;
import com.wind.integration.core.model.TestDataIsolationObject;
import com.wind.trace.WindTraceContext;
import com.wind.trace.WindTracer;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.wind.common.WindConstants.TRACE_TEST_DATA_CLASSIFICATION_ATTRIBUTE_NAME;

/**
 * 环境和测试数据查询隔离拦截器。
 *
 * <p>当前实现基于 MyBatis-Flex QueryWrapper。环境条件始终追加，测试条件仅在测试上下文追加。
 * QueryWrapper 会先复制再追加条件，不改变调用方持有的查询对象。</p>
 */
@Intercepts({
        @Signature(type = Executor.class, method = "query", args = {
                MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class
        }),
        @Signature(type = Executor.class, method = "queryCursor", args = {
                MappedStatement.class, Object.class, RowBounds.class
        })
})
public class DataIsolationQueryInterceptor implements Interceptor {

    private static final String ENV_PROPERTY = "env";
    private static final String TEST_DATA_PROPERTY = "testData";

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        Object[] args = invocation.getArgs();
        MappedStatement statement = (MappedStatement) args[0];
        if (statement.getSqlCommandType() != SqlCommandType.SELECT) {
            return invocation.proceed();
        }

        args[1] = isolateParameter(statement, args[1], isTestContext());
        return invocation.proceed();
    }

    private Object isolateParameter(MappedStatement statement, Object parameter, boolean testContext) {
        TableInfo tableInfo = resolveTableInfo(statement);
        if (tableInfo == null) {
            return parameter;
        }
        boolean testDataEntity = isTestDataEntity(tableInfo);
        boolean envEntity = isEnvEntity(tableInfo);
        if (!testDataEntity && !envEntity) {
            return parameter;
        }
        String env = envEntity ? currentEnv() : null;

        if (parameter instanceof QueryWrapper queryWrapper) {
            return copyWithIsolationConditions(tableInfo, queryWrapper, testContext, env, testDataEntity, envEntity);
        }
        if (parameter instanceof Map<?, ?> parameterMap) {
            Object wrapper = parameterMap.get(FlexConsts.QUERY);
            if (wrapper instanceof QueryWrapper queryWrapper) {
                Map<Object, Object> copy = new LinkedHashMap<>();
                parameterMap.forEach(copy::put);
                copy.put(FlexConsts.QUERY,
                        copyWithIsolationConditions(tableInfo, queryWrapper, testContext, env, testDataEntity, envEntity));
                return copy;
            }
        }

        throw new IllegalStateException("Isolation query must use MyBatis-Flex QueryWrapper: " + statement.getId());
    }

    private QueryWrapper copyWithIsolationConditions(TableInfo tableInfo, QueryWrapper queryWrapper,
                                                     boolean testContext, String env,
                                                     boolean testDataEntity, boolean envEntity) {
        List<?> joins = CPI.getJoins(queryWrapper);
        if (joins != null && !joins.isEmpty()) {
            throw new IllegalStateException("Isolation query with joins is not supported: " + tableInfo.getTableName());
        }
        List<QueryWrapper> childSelects = CPI.getChildSelect(queryWrapper);
        if (childSelects != null && !childSelects.isEmpty()) {
            throw new IllegalStateException("Isolation query with subqueries is not supported: " + tableInfo.getTableName());
        }

        String envColumn = envEntity ? requiredColumn(tableInfo, ENV_PROPERTY) : null;
        String testDataColumn = testDataEntity ? requiredColumn(tableInfo, TEST_DATA_PROPERTY) : null;

        QueryWrapper copy = queryWrapper.clone();
        if (envEntity) {
            copy.and(isolationColumn(tableInfo, copy, envColumn).eq(env));
        }
        if (testContext && testDataEntity) {
            copy.and(isolationColumn(tableInfo, copy, testDataColumn).eq(Boolean.TRUE));
        }
        return copy;
    }

    private String requiredColumn(TableInfo tableInfo, String property) {
        String column = tableInfo.getPropertyColumnMapping().get(property);
        if (column == null) {
            for (Map.Entry<String, String> entry : tableInfo.getPropertyColumnMapping().entrySet()) {
                if (property.equalsIgnoreCase(entry.getKey())) {
                    column = entry.getValue();
                    break;
                }
            }
        }
        if (column == null || column.isBlank()) {
            throw new IllegalStateException("Isolation object requires a " + property + " column: " + tableInfo.getTableName());
        }
        return column;
    }

    private QueryColumn isolationColumn(TableInfo tableInfo, QueryWrapper queryWrapper, String column) {
        List<QueryTable> tables = CPI.getQueryTables(queryWrapper);
        if (tables != null && !tables.isEmpty()) {
            if (tables.size() != 1 || !Objects.equals(tableInfo.getTableName(), tables.getFirst().getName())) {
                throw new IllegalStateException("Isolation query must target the mapper table: " + tableInfo.getTableName());
            }
            QueryTable table = tables.getFirst();
            String alias = table.getAlias();
            if (alias != null && !alias.isBlank()) {
                return new QueryColumn(alias, column);
            }
        }
        return new QueryColumn(column);
    }

    private boolean isTestContext() {
        WindTraceContext context = WindTracer.TRACER.currentContext().orElse(null);
        return context != null
                && Boolean.TRUE.equals(context.getContextVariable(TRACE_TEST_DATA_CLASSIFICATION_ATTRIBUTE_NAME, false));
    }

    private String currentEnv() {
        String env = ServiceInfoUtils.getSpringProfilesActive();
        return env == null ? WindConstants.UNKNOWN : env;
    }

    private boolean isTestDataEntity(TableInfo tableInfo) {
        Class<?> entityClass = tableInfo.getEntityClass();
        return entityClass != null && TestDataIsolationObject.class.isAssignableFrom(entityClass);
    }

    private boolean isEnvEntity(TableInfo tableInfo) {
        Class<?> entityClass = tableInfo.getEntityClass();
        return entityClass != null && EnvIsolationObject.class.isAssignableFrom(entityClass);
    }

    private TableInfo resolveTableInfo(MappedStatement statement) {
        String statementId = statement.getId();
        int separator = statementId.lastIndexOf('.');
        if (separator <= 0) {
            return null;
        }
        String mapperName = statementId.substring(0, separator);
        try {
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            if (classLoader == null) {
                classLoader = DataIsolationQueryInterceptor.class.getClassLoader();
            }
            Class<?> mapperClass = Class.forName(mapperName, false, classLoader);
            TableInfo tableInfo = TableInfoFactory.ofMapperClass(mapperClass);
            if (tableInfo != null) {
                return tableInfo;
            }
        } catch (ClassNotFoundException | RuntimeException ignored) {
            // 自定义 XML namespace 可能不是可加载的 Mapper 类，继续使用 ResultMap 类型回退。
        }
        if (statement.getResultMaps().isEmpty()) {
            return null;
        }
        try {
            return TableInfoFactory.ofEntityClass(statement.getResultMaps().get(0).getType());
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
