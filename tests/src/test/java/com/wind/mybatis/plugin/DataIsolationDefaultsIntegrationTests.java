package com.wind.mybatis.plugin;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.BaseMapper;
import com.mybatisflex.core.datasource.FlexDataSource;
import com.mybatisflex.core.mybatis.FlexConfiguration;
import com.mybatisflex.core.mybatis.FlexSqlSessionFactoryBuilder;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.query.CPI;
import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryTable;
import com.wind.common.WindConstants;
import com.wind.integration.core.context.DataIsolationContextHolder;
import com.wind.integration.core.model.EnvIsolationObject;
import com.wind.integration.core.model.TestDataIsolationObject;
import com.wind.integration.core.context.DataIsolationQueryIgnoreHolder;
import com.wind.mybatis.isolation.DataIsolationScope;
import com.wind.mybatis.plugin.DataIsolationQueryInterceptor.Rule;
import org.apache.ibatis.datasource.pooled.PooledDataSource;
import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.exceptions.PersistenceException;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import static com.wind.integration.core.enums.DataIsolationDimensions.ENVIRONMENT;
import static com.wind.integration.core.enums.DataIsolationDimensions.TEST_DATA;

class DataIsolationDefaultsIntegrationTests {

    private static final String JDBC_URL = "jdbc:h2:mem:data_isolation;MODE=MySQL;DB_CLOSE_DELAY=-1";

    private final DataSource dataSource = new FlexDataSource(
            "default", new PooledDataSource("org.h2.Driver", JDBC_URL, "sa", "sa"), false);

    private SqlSessionFactory sqlSessionFactory;

    @BeforeEach
    void setUp() throws SQLException {
        createSchema();

        sqlSessionFactory = factory(DataMapper.class, List.of());
    }

    @AfterEach
    void tearDown() {
        DataIsolationQueryIgnoreHolder.clear();
    }

    @Test
    void testRegisteredEntityIsIsolatedWithoutTableConfiguration() throws Exception {
        FlexConfiguration configuration = new FlexConfiguration(new Environment(
                "automatic", new JdbcTransactionFactory(), dataSource));
        configuration.addInterceptor(new DataIsolationQueryInterceptor());
        configuration.addMapper(DataMapper.class);
        sqlSessionFactory = new FlexSqlSessionFactoryBuilder().build(configuration);

        assertEquals(List.of(1L), inContext(true, () -> selectList(QueryWrapper.create()))
                .stream().map(DataEntity::getId).sorted().toList());
        assertEquals(List.of(2L), inContext(false, () -> selectList(QueryWrapper.create()))
                .stream().map(DataEntity::getId).sorted().toList());
    }

    @Test
    void testPrimaryKeyAndCountUseEnvironmentAndTraceDefaults() throws Exception {
        inContext(true, () -> {
            try (SqlSession session = sqlSessionFactory.openSession()) {
                DataMapper mapper = session.getMapper(DataMapper.class);
                assertNull(mapper.selectOneById(2L));
                assertNull(mapper.selectOneById(3L));
                assertEquals(1L, mapper.selectCountByQuery(QueryWrapper.create()));
            }
            return null;
        });
        DataIsolationContextHolder.runWith(Map.of(ENVIRONMENT, WindConstants.SIT), () -> {
            try (SqlSession session = sqlSessionFactory.openSession()) {
                assertEquals(1L, session.getMapper(DataMapper.class).selectCountByQuery(QueryWrapper.create()));
            }
        });
    }

    @Test
    void testWrapperCanBeReusedAcrossContextsWithAlias() throws Exception {
        QueryWrapper query = QueryWrapper.create().from(new QueryTable(null, "t_data_isolation", "d"))
                .where(new QueryColumn("d", "id").gt(0L));
        String originalCondition = CPI.getWhereQueryCondition(query).toString();

        assertEquals(List.of(1L), inContext(true, () -> selectList(query)).stream().map(DataEntity::getId).toList());
        assertEquals(List.of(2L), inContext(false, () -> selectList(query)).stream().map(DataEntity::getId).sorted().toList());
        assertEquals(originalCondition, CPI.getWhereQueryCondition(query).toString());
    }

    @Test
    void testOnlyImplementedDimensionsApply() throws Exception {
        sqlSessionFactory = factory(EnvOnlyMapper.class, List.of());
        List<Long> envIds = inContext(true, () -> {
            try (SqlSession session = sqlSessionFactory.openSession()) {
                return session.getMapper(EnvOnlyMapper.class).selectListByQuery(QueryWrapper.create())
                        .stream().map(DataFields::getId).sorted().toList();
            }
        });
        assertEquals(List.of(1L, 2L), envIds);

        sqlSessionFactory = factory(TestOnlyMapper.class, List.of(
                new Rule(ENVIRONMENT, "env", () -> {
                    throw new AssertionError("测试数据维度不应读取环境范围");
                })));
        List<Long> testIds = inContext(true, () -> {
            try (SqlSession session = sqlSessionFactory.openSession()) {
                return session.getMapper(TestOnlyMapper.class).selectListByQuery(QueryWrapper.create())
                        .stream().map(DataFields::getId).sorted().toList();
            }
        });
        assertEquals(List.of(1L, 3L), testIds);
    }

    @Test
    void testMissingPropertyMappingFailsInsteadOfReturningUnscopedRows() {
        sqlSessionFactory = factory(DataMapper.class, List.of(
                new Rule(TEST_DATA, "missingProperty", () -> DataIsolationScope.of(true))));

        assertThrows(PersistenceException.class, () -> selectList(QueryWrapper.create()));
    }

    private SqlSessionFactory factory(Class<?> mapperType, List<Rule> rules) {
        FlexConfiguration configuration = new FlexConfiguration(new Environment(
                "test", new JdbcTransactionFactory(), dataSource));
        configuration.addInterceptor(new DataIsolationQueryInterceptor(rules));
        configuration.addMapper(mapperType);
        return new FlexSqlSessionFactoryBuilder().build(configuration);
    }

    /** 列表查询同时遵守 SIT 环境与测试数据上下文，离开上下文后恢复正式查询范围。 */
    @Test
    void testBaseMapperQueryUsesEnvAndTestContextConditions() throws Exception {
        List<DataEntity> records = inContext(true, () -> selectList(QueryWrapper.create()));

        assertEquals(List.of(1L), records.stream().map(DataEntity::getId).sorted().toList());

        List<DataEntity> formalRecords = inContext(false, () -> selectList(QueryWrapper.create()));
        assertEquals(List.of(2L), formalRecords.stream().map(DataEntity::getId).sorted().toList());
    }

    /** 游标读取同样过滤其他环境及非测试记录，不能绕过列表查询的隔离条件。 */
    @Test
    void testBaseMapperCursorUsesIsolationConditions() throws Exception {
        List<Long> ids = inContext(true, () -> {
            try (SqlSession session = sqlSessionFactory.openSession()) {
                Cursor<DataEntity> cursor = session.getMapper(DataMapper.class)
                        .selectCursorByQuery(QueryWrapper.create());
                return StreamSupport.stream(cursor.spliterator(), false).map(DataEntity::getId).sorted().toList();
            }
        });

        assertEquals(List.of(1L), ids);
    }

    @Test
    void testMissingEnvironmentReturnsNoRows() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            assertEquals(List.of(), session.getMapper(DataMapper.class).selectListByQuery(QueryWrapper.create()));
            assertEquals(0L, session.getMapper(DataMapper.class).selectCountByQuery(QueryWrapper.create()));
        }
    }

    private List<DataEntity> selectList(QueryWrapper queryWrapper) {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            return session.getMapper(DataMapper.class).selectListByQuery(queryWrapper);
        }
    }

    private <T> T inContext(boolean testData, CheckedSupplier<T> supplier) throws Exception {
        return DataIsolationContextHolder.callWith(Map.of(ENVIRONMENT, WindConstants.SIT, TEST_DATA, testData), supplier::get);
    }

    private void createSchema() throws SQLException {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS t_data_isolation");
            statement.execute("CREATE TABLE t_data_isolation (id BIGINT PRIMARY KEY, env VARCHAR(32) NOT NULL, is_test BOOLEAN NOT NULL)");
            statement.execute("INSERT INTO t_data_isolation VALUES (1, 'sit', TRUE), (2, 'sit', FALSE), (3, 'prod', TRUE)");
        }
    }

    @FunctionalInterface
    private interface CheckedSupplier<T> {
        T get() throws Exception;
    }

    static class DataFields {

        @Id
        private Long id;

        @Column("env")
        private String env;

        @Column("is_test")
        private boolean testData;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getEnv() {
            return env;
        }

        public void setEnv(String env) {
            this.env = env;
        }

        public void setTestData(boolean testData) {
            this.testData = testData;
        }
    }

    @Table("t_data_isolation")
    static class DataEntity extends DataFields implements EnvIsolationObject, TestDataIsolationObject {
    }

    @Table("t_data_isolation")
    static class EnvOnlyEntity extends DataFields implements EnvIsolationObject {
    }

    @Table("t_data_isolation")
    static class TestOnlyEntity extends DataFields implements TestDataIsolationObject {
    }

    interface EnvOnlyMapper extends BaseMapper<EnvOnlyEntity> {
    }

    interface TestOnlyMapper extends BaseMapper<TestOnlyEntity> {
    }

    interface DataMapper extends BaseMapper<DataEntity> {
    }
}
