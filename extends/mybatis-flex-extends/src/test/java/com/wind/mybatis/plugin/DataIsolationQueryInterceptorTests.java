package com.wind.mybatis.plugin;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.BaseMapper;
import com.mybatisflex.core.FlexConsts;
import com.mybatisflex.core.mybatis.FlexConfiguration;
import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryTable;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.query.CPI;
import com.wind.common.WindConstants;
import com.wind.integration.core.model.EnvIsolationObject;
import com.wind.integration.core.model.TestDataIsolationObject;
import com.wind.trace.WindTraceContext;
import com.wind.trace.WindTracer;
import org.apache.ibatis.builder.StaticSqlSource;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ResultMap;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static com.wind.common.WindConstants.TRACE_TEST_DATA_CLASSIFICATION_ATTRIBUTE_NAME;
import static org.mockito.Mockito.mock;

class DataIsolationQueryInterceptorTests {

    private final DataIsolationQueryInterceptor interceptor = new DataIsolationQueryInterceptor();

    @Test
    void testContextAddsEnvAndTestConditionsToCopiedWrapper() throws Throwable {
        QueryWrapper original = QueryWrapper.create();
        Invocation invocation = invocation(parameterMap(original));

        runInEnv(WindConstants.SIT, () -> runInTestContext(() -> {
            try {
                interceptor.intercept(invocation);
            } catch (Throwable e) {
                throw new AssertionError(e);
            }
        }));

        QueryWrapper isolated = (QueryWrapper) ((Map<?, ?>) invocation.getArgs()[1]).get(FlexConsts.QUERY);
        Assertions.assertNotSame(original, isolated);
        Assertions.assertArrayEquals(new Object[]{WindConstants.SIT, Boolean.TRUE}, CPI.getValueArray(isolated));
        Assertions.assertTrue(isolated.toSQL().contains("env"));
        Assertions.assertTrue(isolated.toSQL().contains("is_test"));
        Assertions.assertTrue(isolated.toSQL().contains(WindConstants.SIT));
        Assertions.assertFalse(original.toSQL().contains("env"));
        Assertions.assertFalse(original.toSQL().contains("is_test"));
    }

    @Test
    void directQueryWrapperParameterIsCopiedAndIsolated() throws Throwable {
        QueryWrapper original = QueryWrapper.create();
        Invocation invocation = invocation(TestMapper.class, TestEntity.class, original, SqlCommandType.SELECT);

        runInEnv(WindConstants.SIT, () -> runInTestContext(() -> {
            try {
                interceptor.intercept(invocation);
            } catch (Throwable e) {
                throw new AssertionError(e);
            }
        }));

        QueryWrapper isolated = (QueryWrapper) invocation.getArgs()[1];
        Assertions.assertNotSame(original, isolated);
        Assertions.assertArrayEquals(new Object[]{WindConstants.SIT, Boolean.TRUE}, CPI.getValueArray(isolated));
        Assertions.assertTrue(isolated.toSQL().contains("env"));
        Assertions.assertTrue(isolated.toSQL().contains("is_test"));
        Assertions.assertFalse(original.toSQL().contains("env"));
        Assertions.assertFalse(original.toSQL().contains("is_test"));
    }

    @Test
    void formalContextAddsOnlyEnvCondition() throws Throwable {
        QueryWrapper original = QueryWrapper.create();
        Map<String, Object> parameters = parameterMap(original);
        Invocation invocation = invocation(parameters);

        runInEnv(WindConstants.SIT, () -> {
            try {
                interceptor.intercept(invocation);
            } catch (Throwable e) {
                throw new AssertionError(e);
            }
        });

        QueryWrapper isolated = (QueryWrapper) ((Map<?, ?>) invocation.getArgs()[1]).get(FlexConsts.QUERY);
        Assertions.assertNotSame(parameters, invocation.getArgs()[1]);
        Assertions.assertArrayEquals(new Object[]{WindConstants.SIT}, CPI.getValueArray(isolated));
        Assertions.assertTrue(isolated.toSQL().contains("env"));
        Assertions.assertFalse(isolated.toSQL().contains("is_test"));
        Assertions.assertFalse(original.toSQL().contains("env"));
    }

    @Test
    void envOnlyEntityDoesNotAddTestCondition() throws Throwable {
        QueryWrapper original = QueryWrapper.create();
        Invocation invocation = invocation(EnvMapper.class, EnvOnlyEntity.class, parameterMap(original), SqlCommandType.SELECT);

        runInEnv(WindConstants.SIT, () -> {
            try {
                interceptor.intercept(invocation);
            } catch (Throwable e) {
                throw new AssertionError(e);
            }
        });

        QueryWrapper isolated = (QueryWrapper) ((Map<?, ?>) invocation.getArgs()[1]).get(FlexConsts.QUERY);
        Assertions.assertArrayEquals(new Object[]{WindConstants.SIT}, CPI.getValueArray(isolated));
        Assertions.assertTrue(isolated.toSQL().contains("env"));
        Assertions.assertFalse(isolated.toSQL().contains("is_test"));
    }

    @Test
    void testOnlyEntityDoesNotReadEnvironment() throws Throwable {
        QueryWrapper original = QueryWrapper.create();
        Invocation invocation = invocation(TestOnlyMapper.class, TestOnlyEntity.class, parameterMap(original), SqlCommandType.SELECT);

        runWithoutEnv(() -> runInTestContext(() -> {
            try {
                interceptor.intercept(invocation);
            } catch (Throwable e) {
                throw new AssertionError(e);
            }
        }));

        QueryWrapper isolated = (QueryWrapper) ((Map<?, ?>) invocation.getArgs()[1]).get(FlexConsts.QUERY);
        Assertions.assertArrayEquals(new Object[]{Boolean.TRUE}, CPI.getValueArray(isolated));
        Assertions.assertTrue(isolated.toSQL().contains("is_test"));
        Assertions.assertFalse(isolated.toSQL().contains("env"));
    }

    @Test
    void nonIsolatedEntityPassesThroughWithoutEnvironment() throws Throwable {
        QueryWrapper original = QueryWrapper.create();
        Map<String, Object> parameters = parameterMap(original);
        Invocation invocation = invocation(PlainMapper.class, PlainEntity.class, parameters, SqlCommandType.SELECT);

        runWithoutEnv(() -> {
            try {
                interceptor.intercept(invocation);
            } catch (Throwable e) {
                throw new AssertionError(e);
            }
        });

        Assertions.assertSame(parameters, invocation.getArgs()[1]);
    }

    @Test
    void nonSelectPassesThrough() throws Throwable {
        Map<String, Object> parameters = Map.of("id", 1L);
        Invocation invocation = invocation(TestMapper.class, TestEntity.class, parameters, SqlCommandType.UPDATE);

        runWithoutEnv(() -> {
            try {
                interceptor.intercept(invocation);
            } catch (Throwable e) {
                throw new AssertionError(e);
            }
        });

        Assertions.assertSame(parameters, invocation.getArgs()[1]);
    }

    @Test
    void explicitAliasIsAppliedToIsolationColumns() throws Throwable {
        QueryWrapper query = QueryWrapper.create().from(new QueryTable(null, "t_test_entity", "te"));
        Invocation invocation = invocation(parameterMap(query));

        runInEnv(WindConstants.SIT, () -> runInTestContext(() -> {
            try {
                interceptor.intercept(invocation);
            } catch (Throwable e) {
                throw new AssertionError(e);
            }
        }));

        QueryWrapper isolated = (QueryWrapper) ((Map<?, ?>) invocation.getArgs()[1]).get(FlexConsts.QUERY);
        Assertions.assertTrue(isolated.toSQL().contains("`te`.`env`"));
        Assertions.assertTrue(isolated.toSQL().contains("`te`.`is_test`"));
    }

    @Test
    void testContextRejectsNonWrapperQueryForIsolatedEntity() throws NoSuchMethodException {
        Invocation invocation = invocation(Map.of("id", 1L));

        Assertions.assertThrows(IllegalStateException.class, () -> runInEnv(WindConstants.UNKNOWN, () -> runInTestContext(() -> {
            try {
                interceptor.intercept(invocation);
            } catch (IllegalStateException e) {
                throw e;
            } catch (Throwable e) {
                throw new AssertionError(e);
            }
        })));
    }

    @Test
    void testContextRejectsJoinQuery() throws NoSuchMethodException {
        QueryWrapper query = QueryWrapper.create()
                .from("t_test_entity")
                .leftJoin("t_other_entity")
                .on("t_test_entity.id = t_other_entity.id");
        Invocation invocation = invocation(parameterMap(query));

        Assertions.assertThrows(IllegalStateException.class, () -> runInEnv(WindConstants.SIT, () -> runInTestContext(() -> {
            try {
                interceptor.intercept(invocation);
            } catch (IllegalStateException e) {
                throw e;
            } catch (Throwable e) {
                throw new AssertionError(e);
            }
        })));
    }

    @Test
    void testContextRejectsSubquery() throws NoSuchMethodException {
        QueryWrapper subquery = QueryWrapper.create().from("t_other_entity");
        QueryWrapper query = QueryWrapper.create().where(new QueryColumn("id").in(subquery));
        Invocation invocation = invocation(parameterMap(query));

        Assertions.assertThrows(IllegalStateException.class, () -> runInEnv(WindConstants.SIT, () -> runInTestContext(() -> {
            try {
                interceptor.intercept(invocation);
            } catch (IllegalStateException e) {
                throw e;
            } catch (Throwable e) {
                throw new AssertionError(e);
            }
        })));
    }

    @Test
    void testContextRejectsWrongExplicitTable() throws NoSuchMethodException {
        QueryWrapper query = QueryWrapper.create().from("t_other_entity");
        Invocation invocation = invocation(parameterMap(query));

        Assertions.assertThrows(IllegalStateException.class, () -> runInEnv(WindConstants.SIT, () -> runInTestContext(() -> {
            try {
                interceptor.intercept(invocation);
            } catch (IllegalStateException e) {
                throw e;
            } catch (Throwable e) {
                throw new AssertionError(e);
            }
        })));
    }

    @Test
    void customXmlResultMapFallsBackToEntityType() throws Throwable {
        Invocation invocation = invocation("custom.namespace.select", TestEntity.class,
                parameterMap(QueryWrapper.create()), SqlCommandType.SELECT);

        runInEnv(WindConstants.SIT, () -> runInTestContext(() -> {
            try {
                interceptor.intercept(invocation);
            } catch (Throwable e) {
                throw new AssertionError(e);
            }
        }));

        QueryWrapper isolated = (QueryWrapper) ((Map<?, ?>) invocation.getArgs()[1]).get(FlexConsts.QUERY);
        Assertions.assertArrayEquals(new Object[]{WindConstants.SIT, Boolean.TRUE}, CPI.getValueArray(isolated));
    }

    @Test
    void missingIsolationColumnIsRejected() throws NoSuchMethodException {
        Invocation invocation = invocation(BrokenMapper.class, BrokenEntity.class,
                parameterMap(QueryWrapper.create()), SqlCommandType.SELECT);

        Assertions.assertThrows(IllegalStateException.class, () -> runInEnv(WindConstants.SIT, () -> runInTestContext(() -> {
            try {
                interceptor.intercept(invocation);
            } catch (IllegalStateException e) {
                throw e;
            } catch (Throwable e) {
                throw new AssertionError(e);
            }
        })));
    }

    private Invocation invocation(Object parameter) throws NoSuchMethodException {
        return invocation(TestMapper.class, TestEntity.class, parameter, SqlCommandType.SELECT);
    }

    private Invocation invocation(Class<?> mapperType, Class<?> resultType, Object parameter,
                                  SqlCommandType commandType) throws NoSuchMethodException {
        return invocation(mapperType.getName() + ".selectListByQuery", resultType, parameter, commandType);
    }

    private Invocation invocation(String statementId, Class<?> resultType, Object parameter,
                                  SqlCommandType commandType) throws NoSuchMethodException {
        Method method = Executor.class.getMethod("query", MappedStatement.class, Object.class,
                RowBounds.class, ResultHandler.class);
        FlexConfiguration configuration = new FlexConfiguration();
        MappedStatement statement = new MappedStatement.Builder(
                configuration,
                statementId,
                new StaticSqlSource(configuration, "SELECT 1"),
                commandType)
                .resultMaps(List.of(new ResultMap.Builder(configuration, "test", resultType, List.of()).build()))
                .build();
        return new Invocation(mock(Executor.class), method,
                new Object[]{statement, parameter, RowBounds.DEFAULT, Executor.NO_RESULT_HANDLER});
    }

    private Map<String, Object> parameterMap(QueryWrapper wrapper) {
        return Map.of(FlexConsts.QUERY, wrapper);
    }

    private void runInTestContext(Runnable runnable) {
        WindTracer.TRACER.runWithContext(
                WindTraceContext.root(Map.of(TRACE_TEST_DATA_CLASSIFICATION_ATTRIBUTE_NAME, true)), runnable);
    }

    private void runInEnv(String env, Runnable runnable) {
        String previous = System.getProperty(WindConstants.SPRING_PROFILES_ACTIVE);
        try {
            System.setProperty(WindConstants.SPRING_PROFILES_ACTIVE, env);
            runnable.run();
        } finally {
            if (previous == null) {
                System.clearProperty(WindConstants.SPRING_PROFILES_ACTIVE);
            } else {
                System.setProperty(WindConstants.SPRING_PROFILES_ACTIVE, previous);
            }
        }
    }

    private void runWithoutEnv(Runnable runnable) {
        String previous = System.getProperty(WindConstants.SPRING_PROFILES_ACTIVE);
        try {
            System.clearProperty(WindConstants.SPRING_PROFILES_ACTIVE);
            runnable.run();
        } finally {
            if (previous == null) {
                System.clearProperty(WindConstants.SPRING_PROFILES_ACTIVE);
            } else {
                System.setProperty(WindConstants.SPRING_PROFILES_ACTIVE, previous);
            }
        }
    }

    @Table("t_test_entity")
    static class TestEntity implements TestDataIsolationObject, EnvIsolationObject {

        @Column("is_test")
        private boolean testData;

        @Column("env")
        private String env;

        @Override
        public void setTestData(boolean testData) {
            this.testData = testData;
        }

        @Override
        public String getEnv() {
            return env;
        }

        @Override
        public void setEnv(String env) {
            this.env = env;
        }
    }

    interface TestMapper extends BaseMapper<TestEntity> {
    }

    @Table("t_env_only_entity")
    static class EnvOnlyEntity implements EnvIsolationObject {

        @Column("env")
        private String env;

        @Override
        public String getEnv() {
            return env;
        }

        @Override
        public void setEnv(String env) {
            this.env = env;
        }
    }

    interface EnvMapper extends BaseMapper<EnvOnlyEntity> {
    }

    @Table("t_test_only_entity")
    static class TestOnlyEntity implements TestDataIsolationObject {

        @Column("is_test")
        private boolean testData;

        @Override
        public void setTestData(boolean testData) {
            this.testData = testData;
        }
    }

    interface TestOnlyMapper extends BaseMapper<TestOnlyEntity> {
    }

    @Table("t_plain_entity")
    static class PlainEntity {
    }

    interface PlainMapper extends BaseMapper<PlainEntity> {
    }

    @Table("t_broken_entity")
    static class BrokenEntity implements TestDataIsolationObject {

        @Override
        public void setTestData(boolean testData) {
        }
    }

    interface BrokenMapper extends BaseMapper<BrokenEntity> {
    }
}
