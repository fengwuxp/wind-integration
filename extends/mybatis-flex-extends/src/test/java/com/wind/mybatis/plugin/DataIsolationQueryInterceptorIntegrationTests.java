package com.wind.mybatis.plugin;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.BaseMapper;
import com.mybatisflex.core.datasource.FlexDataSource;
import com.mybatisflex.core.mybatis.FlexConfiguration;
import com.mybatisflex.core.mybatis.FlexSqlSessionFactoryBuilder;
import com.mybatisflex.core.query.QueryWrapper;
import com.wind.common.WindConstants;
import com.wind.integration.core.model.EnvIsolationObject;
import com.wind.integration.core.model.TestDataIsolationObject;
import com.wind.trace.WindTraceContext;
import com.wind.trace.WindTracer;
import org.apache.ibatis.datasource.pooled.PooledDataSource;
import org.apache.ibatis.cursor.Cursor;
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

class DataIsolationQueryInterceptorIntegrationTests {

    private static final String JDBC_URL = "jdbc:h2:mem:data_isolation;MODE=MySQL;DB_CLOSE_DELAY=-1";

    private final DataSource dataSource = new FlexDataSource(
            "default", new PooledDataSource("org.h2.Driver", JDBC_URL, "sa", "sa"), false);

    private SqlSessionFactory sqlSessionFactory;

    private String previousEnv;

    @BeforeEach
    void setUp() throws SQLException {
        previousEnv = System.getProperty(WindConstants.SPRING_PROFILES_ACTIVE);
        System.setProperty(WindConstants.SPRING_PROFILES_ACTIVE, WindConstants.SIT);
        createSchema();

        FlexConfiguration configuration = new FlexConfiguration(new Environment(
                "test", new JdbcTransactionFactory(), dataSource));
        configuration.addInterceptor(new DataIsolationQueryInterceptor());
        configuration.addMapper(DataMapper.class);
        sqlSessionFactory = new FlexSqlSessionFactoryBuilder().build(configuration);
    }

    @AfterEach
    void tearDown() {
        if (previousEnv == null) {
            System.clearProperty(WindConstants.SPRING_PROFILES_ACTIVE);
        } else {
            System.setProperty(WindConstants.SPRING_PROFILES_ACTIVE, previousEnv);
        }
    }

    @Test
    void baseMapperQueryUsesEnvAndTestContextConditions() throws Exception {
        List<DataEntity> records = inTestContext(() -> selectList(QueryWrapper.create()));

        assertEquals(List.of(1L), records.stream().map(DataEntity::getId).sorted().toList());

        List<DataEntity> formalRecords = selectList(QueryWrapper.create());
        assertEquals(List.of(1L, 2L), formalRecords.stream().map(DataEntity::getId).sorted().toList());
    }

    @Test
    void baseMapperCursorUsesIsolationConditions() throws Exception {
        List<Long> ids = inTestContext(() -> {
            try (SqlSession session = sqlSessionFactory.openSession()) {
                Cursor<DataEntity> cursor = session.getMapper(DataMapper.class)
                        .selectCursorByQuery(QueryWrapper.create());
                return StreamSupport.stream(cursor.spliterator(), false).map(DataEntity::getId).sorted().toList();
            }
        });

        assertEquals(List.of(1L), ids);
    }

    private List<DataEntity> selectList(QueryWrapper queryWrapper) {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            return session.getMapper(DataMapper.class).selectListByQuery(queryWrapper);
        }
    }

    private <T> T inTestContext(CheckedSupplier<T> supplier) throws Exception {
        return WindTracer.TRACER.callWithContext(
                WindTraceContext.root(Map.of(WindConstants.TRACE_TEST_DATA_CLASSIFICATION_ATTRIBUTE_NAME, true)), supplier::get);
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

    @Table("t_data_isolation")
    static class DataEntity implements EnvIsolationObject, TestDataIsolationObject {

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

        @Override
        public String getEnv() {
            return env;
        }

        @Override
        public void setEnv(String env) {
            this.env = env;
        }

        @Override
        public void setTestData(boolean testData) {
            this.testData = testData;
        }
    }

    interface DataMapper extends BaseMapper<DataEntity> {
    }
}
