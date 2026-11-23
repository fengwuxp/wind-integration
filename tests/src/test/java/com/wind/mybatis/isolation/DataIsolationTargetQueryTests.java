package com.wind.mybatis.isolation;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.BaseMapper;
import com.mybatisflex.core.datasource.FlexDataSource;
import com.mybatisflex.core.mybatis.FlexConfiguration;
import com.mybatisflex.core.mybatis.FlexSqlSessionFactoryBuilder;
import com.wind.mybatis.isolation.DataIsolationStrategy.Condition;
import com.wind.mybatis.isolation.FieldDataIsolationStrategy.ConditionGroup;
import com.wind.mybatis.isolation.FieldDataIsolationStrategy.EntityRules;
import com.wind.mybatis.isolation.FieldDataIsolationStrategy.FieldCondition;
import com.wind.mybatis.plugin.DataIsolationQueryInterceptor;
import com.wind.mybatis.plugin.DataIsolationTargetQuery;
import lombok.Getter;
import lombok.Setter;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.exceptions.PersistenceException;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 用真实工厂、H2 和参数处理器验证有限目标准备，读取与内部匹配共用条件执行语义。
 *
 * @author wuxp
 * @since 2026-11-23
 */
class DataIsolationTargetQueryTests {

    private FlexConfiguration configuration;
    private SqlSessionFactory factory;
    private JdbcTemplate jdbc;
    private DataIsolationTargetQuery targets;

    @BeforeEach
    void setUp() {
        var raw = new UnpooledDataSource("org.h2.Driver", "jdbc:h2:mem:wind_targets_" + UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        var source = new FlexDataSource("default", raw, false);
        jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE bounded_record(id BIGINT PRIMARY KEY,tenant_id BIGINT,owner_code VARCHAR(40),is_deleted INT)");
        jdbc.execute("INSERT INTO bounded_record VALUES (1,10,'encoded:alice',0),(2,20,'encoded:bob',0),(3,10,'encoded:alice',1)");
        jdbc.execute("CREATE TABLE bounded_relation(id BIGINT PRIMARY KEY,record_id BIGINT,tenant_id BIGINT,tag VARCHAR(40))");
        jdbc.execute("INSERT INTO bounded_relation VALUES (11,1,10,'encoded:A'),(12,1,20,'encoded:B'),(13,2,10,'encoded:B')");
        configuration = new FlexConfiguration(new Environment("bounded-target", new JdbcTransactionFactory(), source));
        configuration.addMapper(RecordMapper.class);
        configuration.addMapper(RelationMapper.class);
        configuration.getTypeHandlerRegistry().register(String.class, new EncodedStringHandler());
        factory = new FlexSqlSessionFactoryBuilder().build(configuration);
        targets = new DataIsolationTargetQuery(configuration, Record.class, Set.of("id", "tenantId", "owner"));
    }

    @Test
    void testPreparedQueryUsesActualTypeHandlersAndFiniteIds() {
        List<ConditionGroup> groups = List.of(group(FieldCondition.ofValues("owner", List.of("alice"))));
        assertEquals(List.of(1L), execute(targets.prepare(Set.of("1", "2", "3"), groups)));
        assertEquals(List.of(), execute(targets.prepare(Set.of("2"), groups)));
        assertEquals(List.of(), execute(targets.prepare(Set.of(), groups)));
    }

    @Test
    void testRepeatedCallsKeepRequestInputsSeparateAndRespectLogicalDelete() {
        assertEquals(List.of(1L), execute(targets.prepare(Set.of("1"), List.of(ConditionGroup.unrestricted()))));
        assertEquals(List.of(2L), execute(targets.prepare(Set.of("2"), List.of(ConditionGroup.unrestricted()))));
        assertEquals(List.of(), execute(targets.prepare(Set.of("3"), List.of(ConditionGroup.unrestricted()))));
        assertEquals(List.of(), execute(targets.prepare(Set.of("1"), List.of())));
    }

    @Test
    void testCorrelatedRelationUsesSameWitnessAndFactoryBindingAsList() {
        List<ConditionGroup> groups = List.of(new ConditionGroup(
                List.of(FieldCondition.ofValues("owner", List.of("bob"))),
                List.of(query -> Condition.of("EXISTS (SELECT 1 FROM bounded_relation relation_row WHERE relation_row.record_id = "
                        + query.column("id") + " AND relation_row.tenant_id = ? AND relation_row.tag = ?)", 10L, "B"))));
        installListGroups(groups);
        assertEquals(List.of(2L), execute(targets.prepare(Set.of("1", "2"), groups)));
        try (var session = factory.openSession()) {
            assertEquals(List.of(2L), session.getMapper(RecordMapper.class).ids());
        }
        assertEquals(List.of(), execute(targets.prepare(Set.of("1"), groups)));
        List<ConditionGroup> sameRow = List.of(new ConditionGroup(List.of(),
                List.of(query -> Condition.of("EXISTS (SELECT 1 FROM bounded_relation relation_row WHERE relation_row.record_id = "
                        + query.column("id") + " AND relation_row.tenant_id = ? AND relation_row.tag = ?)", 10L, "B"))));
        assertEquals(List.of(), execute(targets.prepare(Set.of("1"), sameRow)));
    }

    @Test
    void testUncorrelatedAndShadowedRelationsRejectLikeList() {
        List<ConditionGroup> uncorrelated = List.of(new ConditionGroup(List.of(), List.of(query ->
                Condition.of("EXISTS (SELECT 1 FROM bounded_relation relation_row WHERE relation_row.tenant_id = ?)", 10L))));
        installListGroups(uncorrelated);
        assertThrows(IllegalArgumentException.class, () -> targets.prepare(Set.of("1"), uncorrelated));
        try (var session = factory.openSession()) {
            assertThrows(PersistenceException.class, () -> session.getMapper(RecordMapper.class).ids());
        }
        List<ConditionGroup> shadowed = List.of(new ConditionGroup(List.of(), List.of(query ->
                Condition.of("EXISTS (SELECT 1 FROM bounded_relation " + query.qualifier() + " WHERE "
                        + query.qualifier() + ".record_id = " + query.column("id") + ")"))));
        assertThrows(IllegalArgumentException.class, () -> targets.prepare(Set.of("1"), shadowed));
    }

    @Test
    void testInstalledRequestSupplierIsNotUsedForTargetPreparation() {
        FieldDataIsolationStrategy normal = FieldDataIsolationStrategy.grouped(List.of(), List.of(
                new EntityRules(Record.class, Set.of("id"), () -> {
                    throw new IllegalStateException("Request DATA has not been prepared");
                })));
        configuration.addInterceptor(new DataIsolationQueryInterceptor(List.of(normal)));
        assertEquals(List.of(1L), execute(targets.prepare(Set.of("1"), List.of(ConditionGroup.unrestricted()))));
        try (var session = factory.openSession()) {
            assertThrows(PersistenceException.class, () -> session.getMapper(RecordMapper.class).ids());
        }
    }

    @Test
    void testCompleteGroupsAndRepeatedPropertiesRemainBounded() {
        List<ConditionGroup> groups = List.of(group(FieldCondition.ofValues("tenantId", List.of(10L)),
                        FieldCondition.ofValues("owner", List.of("bob"))),
                group(FieldCondition.ofValues("tenantId", List.of(20L)), FieldCondition.ofValues("owner", List.of("alice"))));
        assertEquals(List.of(), execute(targets.prepare(Set.of("1", "2"), groups)));
        List<ConditionGroup> intersection = List.of(group(FieldCondition.ofValues("id", List.of(1L, 2L)),
                        FieldCondition.ofValues("id", List.of(2L))), group(FieldCondition.ofValues("id", List.of(1L))));
        assertEquals(List.of(2L), execute(targets.prepare(Set.of("2"), intersection)));
    }

    @Test
    void testEmptyIdsCannotHideInvalidGroupsOrReferences() {
        List<ConditionGroup> unknown = List.of(ConditionGroup.unrestricted(), group(FieldCondition.ofValues("unknown", List.of(1L))));
        assertThrows(IllegalArgumentException.class, () -> targets.prepare(Set.of(), unknown));
        assertThrows(IllegalArgumentException.class, () -> targets.prepare(Set.of("001"), List.of(ConditionGroup.unrestricted())));
        List<ConditionGroup> broken = List.of(new ConditionGroup(List.of(), List.of(query -> Condition.of(query.column("id") + " = ?"))));
        assertThrows(IllegalArgumentException.class, () -> targets.prepare(Set.of(), broken));
        List<ConditionGroup> absent = List.of(new ConditionGroup(List.of(), List.of(query -> null)));
        assertThrows(IllegalStateException.class, () -> targets.prepare(Set.of(), absent));
    }

    @Test
    void testMissingRelationRegistrationRejectsBeforeExecution() {
        var limited = new FlexConfiguration(configuration.getEnvironment());
        limited.addMapper(RecordMapper.class);
        new FlexSqlSessionFactoryBuilder().build(limited);
        var prepared = new DataIsolationTargetQuery(limited, Record.class, Set.of("id"));
        List<ConditionGroup> groups = List.of(new ConditionGroup(List.of(), List.of(query ->
                Condition.of("EXISTS (SELECT 1 FROM bounded_relation relation_row WHERE relation_row.record_id = " + query.column("id") + ")"))));
        assertThrows(IllegalArgumentException.class, () -> prepared.prepare(Set.of("1"), groups));
    }

    @Test
    void testUnknownFieldsAndMissingRootRegistrationRejectAtCreation() {
        assertThrows(IllegalArgumentException.class, () -> new DataIsolationTargetQuery(configuration, Record.class, Set.of("missing")));
        var empty = new FlexConfiguration(configuration.getEnvironment());
        new FlexSqlSessionFactoryBuilder().build(empty);
        assertThrows(IllegalStateException.class, () -> new DataIsolationTargetQuery(empty, Record.class, Set.of("id")));
    }

    private void installListGroups(List<ConditionGroup> groups) {
        FieldDataIsolationStrategy normal = FieldDataIsolationStrategy.grouped(List.of(), List.of(
                new EntityRules(Record.class, Set.of("id", "tenantId", "owner"), () -> groups)));
        configuration.addInterceptor(new DataIsolationQueryInterceptor(List.of(normal)));
    }

    private static ConditionGroup group(FieldCondition... fields) {
        return new ConditionGroup(List.of(fields), List.of());
    }

    private List<Long> execute(MappedStatement statement) {
        var sql = statement.getBoundSql(null);
        var parameters = configuration.newParameterHandler(statement, sql.getParameterObject(), sql);
        return jdbc.query(sql.getSql(), parameters::setParameters, rows -> {
            List<Long> result = new ArrayList<>();
            var ids = configuration.getTypeHandlerRegistry().getTypeHandler(Long.class);
            while (rows.next()) {
                result.add(ids.getResult(rows, 1));
            }
            return result;
        });
    }

    /**
     * 含逻辑删除字段的目标实体夹具。
     *
     * @author wuxp
     * @since 2026-11-23
     */
    @Getter
    @Setter
    @Table("bounded_record")
    static class Record {
        @Id
        private Long id;
        private Long tenantId;
        @Column("owner_code")
        private String owner;
        @Column(value = "is_deleted", isLogicDelete = true)
        private Integer deleted;
    }

    /**
     * 关系配对与工厂参数处理夹具。
     *
     * @author wuxp
     * @since 2026-11-23
     */
    @Getter
    @Setter
    @Table("bounded_relation")
    static class Relation {
        @Id
        private Long id;
        private Long recordId;
        private Long tenantId;
        private String tag;
    }

    /**
     * 用实际列表入口对照目标准备结果。
     *
     * @author wuxp
     * @since 2026-11-23
     */
    interface RecordMapper extends BaseMapper<Record> {

        @Select("SELECT r.id FROM bounded_record r WHERE r.is_deleted = 0 ORDER BY r.id")
        List<Long> ids();
    }

    /**
     * 为受控关系登记内表元数据。
     *
     * @author wuxp
     * @since 2026-11-23
     */
    interface RelationMapper extends BaseMapper<Relation> {
    }

    /**
     * 用可观察的数据库编码证明实际工厂处理器参与绑定。
     *
     * @author wuxp
     * @since 2026-11-23
     */
    static class EncodedStringHandler extends BaseTypeHandler<String> {
        @Override
        public void setNonNullParameter(PreparedStatement statement, int index, String value, JdbcType type) throws SQLException {
            statement.setString(index, "encoded:" + value);
        }

        @Override
        public String getNullableResult(ResultSet rows, String column) throws SQLException {
            return rows.getString(column);
        }

        @Override
        public String getNullableResult(ResultSet rows, int column) throws SQLException {
            return rows.getString(column);
        }

        @Override
        public String getNullableResult(CallableStatement statement, int column) throws SQLException {
            return statement.getString(column);
        }
    }
}
