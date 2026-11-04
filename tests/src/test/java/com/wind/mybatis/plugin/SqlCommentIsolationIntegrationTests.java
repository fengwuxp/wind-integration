package com.wind.mybatis.plugin;

import com.mybatisflex.core.BaseMapper;
import com.wind.integration.core.context.DataIsolationQueryIgnoreHolder;
import com.wind.mybatis.isolation.DataIsolationScope;
import com.wind.mybatis.isolation.DataIsolationStrategy;
import com.wind.mybatis.isolation.DataIsolationStrategy.FieldRule;
import com.wind.mybatis.isolation.FieldDataIsolationStrategy;
import org.apache.ibatis.annotations.SelectProvider;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.exceptions.PersistenceException;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static com.wind.integration.core.enums.DataIsolationDimensions.TENANT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 经真实 Mapper 验证注释拒绝边界及普通查询的租户可见结果。
 * H2 仅承载结果与执行时机，不证明 MySQL 或 MariaDB 对可执行注释的解释。
 *
 * @author wuxp
 * @since 2026-11-04
 */
class SqlCommentIsolationIntegrationTests {

    private static final AtomicInteger DATABASE_SEQUENCE = new AtomicInteger();

    private static final String MANAGED_SQL = "SELECT id FROM coverage_tenant_record ORDER BY id";

    private static final String PUBLIC_SQL = "SELECT id FROM coverage_plain_record ORDER BY id";

    private UnpooledDataSource dataSource;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new UnpooledDataSource("org.h2.Driver", "jdbc:h2:mem:sql_comments_"
                + DATABASE_SEQUENCE.incrementAndGet() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        try (var connection = dataSource.getConnection(); var sql = connection.createStatement()) {
            sql.execute("CREATE TABLE coverage_tenant_record(id BIGINT PRIMARY KEY, tenant_id BIGINT)");
            sql.execute("INSERT INTO coverage_tenant_record VALUES(1,10),(2,20),(3,10)");
            sql.execute("CREATE TABLE coverage_plain_record(id BIGINT PRIMARY KEY, tenant_id BIGINT, owner_id BIGINT)");
            sql.execute("INSERT INTO coverage_plain_record VALUES(11,10,7),(12,20,7),(13,10,8)");
        }
    }

    @Test
    void testManagedQueryRejectsMysqlExecutableCommentBeforeJdbc() {
        PrepareAudit audit = new PrepareAudit();
        SqlSessionFactory factory = factory(false, audit, tenantFields());

        assertRejected(factory, audit, MANAGED_SQL + " /*!50000 LIMIT 0 */");
    }

    /**
     * 注释位于语句边界、关键字间或连续注释中时，普通与严格模式均须在 JDBC 前拒绝。
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "/*!50000 SELECT 99 */ SELECT id FROM coverage_tenant_record ORDER BY id",
            "SELECT /*!50000 SQL_NO_CACHE */ id FROM coverage_tenant_record ORDER BY id",
            "SELECT id FROM coverage_tenant_record ORDER BY id /*! LIMIT 0 */",
            "SELECT id FROM coverage_tenant_record ORDER BY id /*M!100100 LIMIT 0 */",
            "/*M! SELECT 99 */ SELECT id FROM coverage_tenant_record ORDER BY id",
            "/* ordinary */ /*!50000 SELECT 99 */ /* ordinary */ SELECT id FROM coverage_tenant_record ORDER BY id",
            "SELECT id FROM coverage_tenant_record ORDER BY id /* ordinary */ /*!50000 LIMIT 0 */ /* ordinary */",
            "SELECT id FROM coverage_tenant_record WHERE id = 1--1\nORDER BY id",
            "--ambiguous\nSELECT id FROM coverage_tenant_record ORDER BY id",
            "SELECT id FROM coverage_tenant_record ORDER BY id --ambiguous",
            "SELECT id FROM coverage_tenant_record ORDER BY id /* outer /* inner */ outer */",
            "/* outer /* inner */ outer */ SELECT id FROM coverage_tenant_record ORDER BY id",
            "// ordinary\nSELECT id FROM coverage_tenant_record ORDER BY id",
            "SELECT id FROM coverage_tenant_record // ordinary\nORDER BY id",
            "SELECT id FROM coverage_tenant_record ORDER BY id // ordinary",
            "SELECT id FROM coverage_tenant_record ORDER BY id; /*!50000 SELECT 2 */",
            "SELECT id FROM coverage_tenant_record ORDER BY id; /*M!100100 SELECT 2 */",
            "SELECT id FROM coverage_tenant_record ORDER BY id; /* first */ /*!50000 SELECT 2 */ /* last */",
            "SELECT id FROM coverage_tenant_record ORDER BY id; /* first */ /* second */ // ordinary"
    })
    void testUnsupportedCommentsAreRejectedWithBothModes(String sql) {
        for (boolean strict : List.of(false, true)) {
            PrepareAudit audit = new PrepareAudit();
            assertRejected(factory(strict, audit, tenantFields()), audit, sql);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/*!50000 LIMIT 0 */", "/*M!100100 LIMIT 0 */", "--ambiguous", "/* outer /* inner */ outer */", "// ordinary",
            "; /*!50000 SELECT 2 */", "; /* first */ /*M!100100 SELECT 2 */ /* last */"
    })
    void testPublicAndLiteralQueriesStillRejectUnsupportedComments(String comment) {
        for (boolean strict : List.of(false, true)) {
            PrepareAudit audit = new PrepareAudit();
            SqlSessionFactory factory = factory(strict, audit, tenantFields());

            assertRejected(factory, audit, PUBLIC_SQL + " " + comment);
            assertRejected(factory, audit, "SELECT 1 " + comment);
        }
    }

    /**
     * 已缓存的公共查询须按本次 SQL 重新检查，失败后正常查询仍可读取原有结果。
     */
    @Test
    void testChangedSqlOnSameStatementCannotReusePublicQueryDecision() {
        for (boolean strict : List.of(false, true)) {
            PrepareAudit audit = new PrepareAudit();
            SqlSessionFactory factory = factory(strict, audit, tenantFields());
            assertRows(factory, PUBLIC_SQL, List.of(11L, 12L, 13L));
            assertRows(factory, PUBLIC_SQL, List.of(11L, 12L, 13L));

            assertRejected(factory, audit, PUBLIC_SQL + " /*!50000 LIMIT 0 */");
            assertRejected(factory, audit, PUBLIC_SQL + " --ambiguous");

            assertRows(factory, PUBLIC_SQL, List.of(11L, 12L, 13L));
            assertRows(factory, MANAGED_SQL, List.of(1L, 3L));
        }
    }

    /**
     * 分号后的第二条语句不能被单语句解析或原 SQL 返回路径遗漏。
     */
    @ParameterizedTest
    @ValueSource(strings = {"; SELECT 2", "; /* ordinary */ SELECT 2", "; SELECT 2 /* trailing */"})
    void testMultipleStatementsAreRejectedBeforeJdbc(String suffix) {
        for (boolean strict : List.of(false, true)) {
            PrepareAudit audit = new PrepareAudit();
            SqlSessionFactory factory = factory(strict, audit, tenantFields());

            assertRejected(factory, audit, MANAGED_SQL + suffix, "Unsupported SQL statement");
            assertRejected(factory, audit, PUBLIC_SQL + suffix, "Unsupported SQL statement");
            assertRejected(factory, audit, "SELECT 1" + suffix, "Unsupported SQL statement");
        }
    }

    /**
     * 显式不限范围仍需校验 SQL 注释，正常查询的原 SQL 与全部可见行保持不变。
     */
    @Test
    void testUnrestrictedScopeChecksCommentsBeforeReturningOriginalSql() {
        FieldDataIsolationStrategy unrestricted = new FieldDataIsolationStrategy(List.of(
                new FieldRule(CoverageTenantRecord.class, "tenantId", DataIsolationScope::unrestrictedScope)));
        for (boolean strict : List.of(false, true)) {
            PrepareAudit audit = new PrepareAudit();
            SqlSessionFactory factory = factory(strict, audit, unrestricted);
            String sql = MANAGED_SQL + " /* ordinary */";

            assertRows(factory, sql, List.of(1L, 2L, 3L));
            assertEquals(sql, audit.sql.getLast());
            assertRejected(factory, audit, MANAGED_SQL + " /*!50000 LIMIT 0 */");
        }
    }

    /**
     * 忽略租户维度只取消该维度过滤，不允许可执行注释绕过语法边界。
     */
    @Test
    void testIgnoredDimensionChecksCommentsBeforeReturningOriginalSql() {
        FieldDataIsolationStrategy dimensions = new FieldDataIsolationStrategy(List.of(
                FieldRule.forDimension(TENANT, "tenantId", () -> DataIsolationScope.of(10L))));
        for (boolean strict : List.of(false, true)) {
            PrepareAudit audit = new PrepareAudit();
            SqlSessionFactory factory = factory(strict, audit, dimensions);

            DataIsolationQueryIgnoreHolder.runIgnoring(() -> {
                String sql = MANAGED_SQL + " /* ordinary */";
                assertRows(factory, sql, List.of(1L, 2L, 3L));
                assertEquals(sql, audit.sql.getLast());
                assertRejected(factory, audit, MANAGED_SQL + " /*M!100100 LIMIT 0 */");
            }, TENANT);

            assertRows(factory, MANAGED_SQL, List.of(1L, 3L));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/* ordinary */ SELECT id FROM coverage_tenant_record ORDER BY id",
            "SELECT /* ordinary */ id FROM coverage_tenant_record ORDER BY id /* trailing */",
            "/* first */ /* second */ SELECT id FROM coverage_tenant_record ORDER BY id /* third */ /* fourth */",
            "-- ordinary\nSELECT id FROM coverage_tenant_record ORDER BY id",
            "SELECT id FROM coverage_tenant_record -- ordinary\nORDER BY id",
            "SELECT id FROM coverage_tenant_record ORDER BY id -- ordinary",
            "--\nSELECT id FROM coverage_tenant_record ORDER BY id",
            "-- ordinary\r\nSELECT id FROM coverage_tenant_record ORDER BY id",
            "SELECT id FROM coverage_tenant_record ORDER BY id --\tordinary",
            "SELECT id FROM coverage_tenant_record ORDER BY id -- ordinary /* outer /* nested marker */ /*!50000 ignored */",
            "SELECT /*+ INDEX(coverage_tenant_record) */ id FROM coverage_tenant_record ORDER BY id",
            "SELECT id FROM coverage_tenant_record ORDER BY id /* ! ordinary */",
            "SELECT id FROM coverage_tenant_record ORDER BY id; /* ordinary */",
            "SELECT id FROM coverage_tenant_record ORDER BY id; /* first */ /* last */",
            "SELECT id FROM coverage_tenant_record ORDER BY id; -- ordinary"
    })
    void testOrdinaryCommentsKeepTenantVisibleRows(String sql) {
        for (boolean strict : List.of(false, true)) {
            PrepareAudit audit = new PrepareAudit();
            assertRows(factory(strict, audit, tenantFields()), sql, List.of(1L, 3L));
            assertEquals(1, audit.sql.size());
        }
    }

    /**
     * 井号已被解析成普通 token 或标识符时也须拒绝，不能在追加条件后被数据库解释为行注释。
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "SELECT id FROM coverage_tenant_record #tail",
            "SELECT id FROM coverage_tenant_record alias#comment",
            "SELECT id FROM coverage_plain_record #tail",
            "SELECT 1 # tail"
    })
    void testHashTokensAreRejectedBeforeJdbc(String sql) {
        for (boolean strict : List.of(false, true)) {
            PrepareAudit audit = new PrepareAudit();
            assertRejected(factory(strict, audit, tenantFields()), audit, sql);
        }
    }

    /**
     * 位于语句开头且不能形成 SELECT 的井号保持已有解析失败语义，仍不得触达 JDBC。
     */
    @Test
    void testLeadingHashKeepsParseFailureBeforeJdbc() {
        for (boolean strict : List.of(false, true)) {
            PrepareAudit audit = new PrepareAudit();
            SqlSessionFactory factory = factory(strict, audit, tenantFields());

            assertRejected(factory, audit, "# ordinary\n" + MANAGED_SQL, "Cannot parse query for read isolation");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/*!50000 LIMIT 0 */", "/*M!100100 LIMIT 0 */", "--ambiguous", "/* outer /* inner */ outer */", "# ordinary", "// ordinary"
    })
    void testQuotedTextIsNotTreatedAsComment(String text) {
        for (boolean strict : List.of(false, true)) {
            PrepareAudit audit = new PrepareAudit();
            SqlSessionFactory factory = factory(strict, audit, tenantFields());

            assertRows(factory, "SELECT id FROM coverage_tenant_record WHERE '" + text + "' = '" + text + "' ORDER BY id",
                    List.of(1L, 3L));
            assertRows(factory, "SELECT id AS `" + text + "` FROM coverage_tenant_record ORDER BY id", List.of(1L, 3L));
            assertRows(factory, "SELECT id AS \"" + text + "\" FROM coverage_tenant_record ORDER BY id", List.of(1L, 3L));
        }
    }

    @Test
    void testEscapedQuotesKeepCommentMarkersInsideLiteral() {
        PrepareAudit audit = new PrepareAudit();
        SqlSessionFactory factory = factory(false, audit, tenantFields());
        String sql = "SELECT id FROM coverage_tenant_record WHERE 'a''/*!50000 text */' = 'a''/*!50000 text */' ORDER BY id";

        assertRows(factory, sql, List.of(1L, 3L));
        assertRows(factory, "SELECT id AS `a``/*M! text */` FROM coverage_tenant_record ORDER BY id", List.of(1L, 3L));
        assertRows(factory, "SELECT id AS \"a\"\"--text\" FROM coverage_tenant_record ORDER BY id", List.of(1L, 3L));
    }

    /**
     * 单、双引号前的奇数反斜杠会随数据库模式改变结束位置，公共查询缓存也不能放行变更后的 SQL。
     */
    @ParameterizedTest
    @ValueSource(strings = {"'text\\'", "\"text\\\"", "\"text\\\"\"tail\""})
    void testAmbiguousQuotedTokensAreRejectedBeforeJdbc(String quoted) {
        for (boolean strict : List.of(false, true)) {
            PrepareAudit audit = new PrepareAudit();
            SqlSessionFactory factory = factory(strict, audit, tenantFields());
            assertRows(factory, PUBLIC_SQL, List.of(11L, 12L, 13L));

            assertRejected(factory, audit, "SELECT " + quoted + " AS quoted_value FROM coverage_tenant_record",
                    "Unsupported SQL quoted literal");
            assertRejected(factory, audit, "SELECT " + quoted + " AS quoted_value FROM coverage_plain_record",
                    "Unsupported SQL quoted literal");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"'text\\'", "\"text\\\"", "\"text\\\"\"tail\""})
    void testAmbiguousQuotedTokensInConditionAreRejectedBeforeJdbc(String quoted) {
        String condition = quoted + " IS NOT NULL AND coverage_tenant_record.tenant_id = ?";
        for (boolean strict : List.of(false, true)) {
            PrepareAudit audit = new PrepareAudit();
            SqlSessionFactory factory = factory(strict, audit, conditionStrategy(condition));

            IllegalStateException rejection = assertRejected(factory, audit, MANAGED_SQL, "Unsupported SQL quoted literal");
            assertFalse(rejection.getMessage().contains(condition));
        }
    }

    /**
     * 单引号双写前的奇数反斜杠由解析器提前拒绝，查询和谓词均保持已有异常且不得触达 JDBC。
     */
    @Test
    void testBackslashBeforeDoubledSingleQuoteKeepsParseFailureBeforeJdbc() {
        String quoted = "'text\\''tail'";
        String condition = quoted + " IS NOT NULL AND coverage_tenant_record.tenant_id = ?";
        for (boolean strict : List.of(false, true)) {
            PrepareAudit audit = new PrepareAudit();
            SqlSessionFactory factory = factory(strict, audit, tenantFields());

            assertRejected(factory, audit, "SELECT " + quoted + " AS quoted_value FROM coverage_tenant_record",
                    "Cannot parse query for read isolation");
            assertRejected(factory, audit, "SELECT " + quoted + " AS quoted_value FROM coverage_plain_record",
                    "Cannot parse query for read isolation");

            SqlSessionFactory conditionFactory = factory(strict, audit, conditionStrategy(condition));
            try (var session = conditionFactory.openSession()) {
                PersistenceException failure = assertThrows(PersistenceException.class,
                        () -> session.getMapper(ReadMapper.class).ids(MANAGED_SQL));
                IllegalArgumentException rejection = assertInstanceOf(IllegalArgumentException.class, failure.getCause());
                assertEquals("Cannot parse isolation condition", rejection.getMessage());
                assertEquals(0, audit.sql.size(), "解析失败的查询和谓词不得进入 JDBC prepare");
            }
        }
    }

    /**
     * 普通路径反斜杠、闭合引号前的偶数反斜杠和标准双写引号仍保持真实读取结果。
     */
    @ParameterizedTest
    @ValueSource(strings = {"'path\\segment'", "'text\\\\'", "'text''tail'"})
    void testUnambiguousQuotedLiteralsKeepVisibleRows(String quoted) {
        for (boolean strict : List.of(false, true)) {
            PrepareAudit audit = new PrepareAudit();
            SqlSessionFactory factory = factory(strict, audit, tenantFields());

            assertRows(factory, "SELECT id FROM coverage_tenant_record WHERE " + quoted + " IS NOT NULL ORDER BY id",
                    List.of(1L, 3L));
            assertRows(factory, "SELECT id FROM coverage_plain_record WHERE " + quoted + " IS NOT NULL ORDER BY id",
                    List.of(11L, 12L, 13L));

            String condition = quoted + " IS NOT NULL AND coverage_tenant_record.tenant_id = ?";
            SqlSessionFactory conditionFactory = factory(strict, audit, conditionStrategy(condition));
            assertRows(conditionFactory, MANAGED_SQL, List.of(1L, 3L));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"path\\segment\"", "\"text\\\\\"", "\"text\"\"tail\""})
    void testUnambiguousQuotedIdentifiersKeepVisibleRows(String quoted) {
        for (boolean strict : List.of(false, true)) {
            PrepareAudit audit = new PrepareAudit();
            SqlSessionFactory factory = factory(strict, audit, tenantFields());

            assertRows(factory, "SELECT id AS " + quoted + " FROM coverage_tenant_record ORDER BY id", List.of(1L, 3L));
            assertRows(factory, "SELECT id AS " + quoted + " FROM coverage_plain_record ORDER BY id", List.of(11L, 12L, 13L));
        }
    }

    @Test
    void testOrdinaryPublicCommentsKeepOriginalSqlAndVisibleRows() {
        for (boolean strict : List.of(false, true)) {
            PrepareAudit audit = new PrepareAudit();
            SqlSessionFactory factory = factory(strict, audit, tenantFields());
            for (String sql : List.of("/* ordinary */ " + PUBLIC_SQL, PUBLIC_SQL + " /* trailing */", PUBLIC_SQL + " -- ordinary",
                    PUBLIC_SQL + "; /* ordinary */", PUBLIC_SQL + "; /* first */ /* last */", PUBLIC_SQL + "; -- ordinary")) {
                assertRows(factory, sql, List.of(11L, 12L, 13L));
                assertEquals(sql, audit.sql.getLast());
            }
            assertRows(factory, "SELECT 1 /* ordinary */", List.of(1L));
            assertRows(factory, "SELECT 1; /* ordinary */", List.of(1L));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/*!50000 1 = 0 AND */ coverage_tenant_record.tenant_id = ?",
            "coverage_tenant_record.tenant_id = ? /*!50000 OR 1 = 1 */",
            "coverage_tenant_record.tenant_id = ? /*M!100100 OR 1 = 1 */",
            "coverage_tenant_record.tenant_id = ? --ambiguous",
            "coverage_tenant_record.tenant_id = ? /* outer /* inner */ outer */",
            "coverage_tenant_record.tenant_id = ? // ordinary"
    })
    void testTrustedStrategyConditionStillRejectsUnsupportedComments(String condition) {
        for (boolean strict : List.of(false, true)) {
            PrepareAudit audit = new PrepareAudit();
            IllegalStateException rejection = assertRejected(factory(strict, audit, conditionStrategy(condition)), audit, MANAGED_SQL);
            assertFalse(rejection.getMessage().contains(condition));
        }
    }

    /**
     * 井号谓词无论被当前解析器识别成运算符还是直接解析失败，都必须在执行前拒绝。
     */
    @Test
    void testHashInStrategyConditionIsRejectedBeforeJdbc() {
        String condition = "coverage_tenant_record.tenant_id = ? # ordinary";
        for (boolean strict : List.of(false, true)) {
            PrepareAudit audit = new PrepareAudit();
            SqlSessionFactory factory = factory(strict, audit, conditionStrategy(condition));
            try (var session = factory.openSession()) {
                PersistenceException failure = assertThrows(PersistenceException.class,
                        () -> session.getMapper(ReadMapper.class).ids(MANAGED_SQL));
                Throwable cause = failure.getCause();
                if (cause instanceof IllegalStateException rejection) {
                    assertTrue(rejection.getMessage().contains("Unsupported SQL comment"));
                    assertTrue(rejection.getMessage().contains(ReadMapper.class.getName() + ".ids"));
                    assertFalse(rejection.getMessage().contains(condition));
                } else {
                    IllegalArgumentException rejection = assertInstanceOf(IllegalArgumentException.class, cause);
                    assertEquals("Cannot parse isolation condition", rejection.getMessage());
                }
                assertEquals(0, audit.sql.size(), "井号谓词不得进入 JDBC prepare");
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/* ordinary */ coverage_tenant_record.tenant_id = ? /* trailing */",
            "coverage_tenant_record.tenant_id = ? -- ordinary",
            "'/*!50000 text */ --text #text //text' = '/*!50000 text */ --text #text //text' AND coverage_tenant_record.tenant_id = ?"
    })
    void testOrdinaryCommentsInStrategyConditionKeepTenantVisibleRows(String condition) {
        for (boolean strict : List.of(false, true)) {
            PrepareAudit audit = new PrepareAudit();
            assertRows(factory(strict, audit, conditionStrategy(condition)), MANAGED_SQL, List.of(1L, 3L));
        }
    }

    private DataIsolationStrategy conditionStrategy(String condition) {
        return new DataIsolationStrategy() {
            @Override
            public boolean supports(EntityMetadata entity) {
                return entity.type() == CoverageTenantRecord.class;
            }

            @Override
            public List<Condition> resolve(QueryContext context) {
                return List.of(Condition.of(condition, 10L));
            }
        };
    }

    private void assertRows(SqlSessionFactory factory, String sql, List<Long> expected) {
        try (var session = factory.openSession()) {
            assertEquals(expected, session.getMapper(ReadMapper.class).ids(sql));
        }
    }

    private FieldDataIsolationStrategy tenantFields() {
        return new FieldDataIsolationStrategy(List.of(
                new FieldRule(CoverageTenantRecord.class, "tenantId", () -> DataIsolationScope.of(10L))));
    }

    private SqlSessionFactory factory(boolean strict, PrepareAudit audit, DataIsolationStrategy strategy) {
        Configuration configuration = new Configuration(new Environment("sql-comments", new JdbcTransactionFactory(), dataSource));
        configuration.addMapper(TenantMapper.class);
        configuration.addMapper(PlainMapper.class);
        configuration.addMapper(ReadMapper.class);
        DataIsolationQueryInterceptor interceptor = new DataIsolationQueryInterceptor(List.of(strategy));
        if (strict) {
            interceptor = DataIsolationQueryInterceptor.strict(List.of(strategy), Set.of(CoveragePlainRecord.class));
        }
        configuration.addInterceptor(interceptor);
        configuration.addInterceptor(audit);
        return new SqlSessionFactoryBuilder().build(configuration);
    }

    private IllegalStateException assertRejected(SqlSessionFactory factory, PrepareAudit audit, String sql) {
        return assertRejected(factory, audit, sql, "Unsupported SQL comment");
    }

    private IllegalStateException assertRejected(SqlSessionFactory factory, PrepareAudit audit, String sql, String expectedReason) {
        int preparedBefore = audit.sql.size();
        try (var session = factory.openSession()) {
            PersistenceException failure = assertThrows(PersistenceException.class,
                    () -> session.getMapper(ReadMapper.class).ids(sql));
            Throwable cause = failure;
            while (!(cause instanceof IllegalStateException) && cause.getCause() != null) {
                cause = cause.getCause();
            }
            IllegalStateException rejection = assertInstanceOf(IllegalStateException.class, cause);
            assertTrue(rejection.getMessage().contains(expectedReason));
            assertTrue(rejection.getMessage().contains(ReadMapper.class.getName() + ".ids"));
            assertFalse(rejection.getMessage().contains(sql));
            assertEquals(preparedBefore, audit.sql.size(), "拒绝查询不得进入 JDBC prepare");
            return rejection;
        }
    }

    /**
     * 为租户表登记真实实体元数据。
     *
     * @author wuxp
     * @since 2026-11-04
     */
    interface TenantMapper extends BaseMapper<CoverageTenantRecord> {
    }

    /**
     * 为公共表登记真实实体元数据。
     *
     * @author wuxp
     * @since 2026-11-04
     */
    interface PlainMapper extends BaseMapper<CoveragePlainRecord> {
    }

    /**
     * 同一 Mapper 语句接收不同测试 SQL，覆盖动态 SQL 与缓存边界。
     *
     * @author wuxp
     * @since 2026-11-04
     */
    interface ReadMapper {

        @SelectProvider(type = SqlProvider.class, method = "query")
        List<Long> ids(String sql);
    }

    /**
     * 将测试给定的 SQL 交给真实 MyBatis 执行路径。
     *
     * @author wuxp
     * @since 2026-11-04
     */
    public static class SqlProvider {

        public static String query(String sql) {
            return sql;
        }
    }

    /**
     * 只观察 JDBC prepare 边界，避免把 H2 忽略注释误判成框架拒绝。
     *
     * @author wuxp
     * @since 2026-11-04
     */
    @Intercepts(@Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class}))
    public static class PrepareAudit implements Interceptor {

        private final List<String> sql = new ArrayList<>();

        @Override
        public Object intercept(Invocation invocation) throws Throwable {
            sql.add(((StatementHandler) invocation.getTarget()).getBoundSql().getSql());
            return invocation.proceed();
        }
    }
}
