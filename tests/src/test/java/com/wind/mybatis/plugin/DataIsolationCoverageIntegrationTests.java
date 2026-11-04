package com.wind.mybatis.plugin;

import com.mybatisflex.core.BaseMapper;
import com.wind.mybatis.isolation.DataIsolationScope;
import com.wind.mybatis.isolation.DataIsolationStrategy.FieldRule;
import com.wind.mybatis.isolation.FieldDataIsolationStrategy;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.exceptions.PersistenceException;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataIsolationCoverageIntegrationTests {

    private static final AtomicInteger DATABASE_SEQUENCE = new AtomicInteger();

    private UnpooledDataSource dataSource;

    private DataIsolationScope tenantScope;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new UnpooledDataSource("org.h2.Driver", "jdbc:h2:mem:coverage_"
                + DATABASE_SEQUENCE.incrementAndGet() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        try (var connection = dataSource.getConnection(); var sql = connection.createStatement()) {
            sql.execute("CREATE TABLE coverage_tenant_record(id BIGINT PRIMARY KEY, tenant_id BIGINT)");
            sql.execute("INSERT INTO coverage_tenant_record VALUES(1,10),(2,20),(3,10)");
            sql.execute("CREATE TABLE coverage_plain_record(id BIGINT PRIMARY KEY, tenant_id BIGINT, owner_id BIGINT)");
            sql.execute("INSERT INTO coverage_plain_record VALUES(11,10,7),(12,20,7),(13,10,8)");
            sql.execute("CREATE TABLE coverage_unknown_record(id BIGINT PRIMARY KEY)");
            sql.execute("INSERT INTO coverage_unknown_record VALUES(91)");
        }
        tenantScope = DataIsolationScope.of(10L);
    }

    /** 启动校验只验证静态绑定；请求范围缺失只在真实受保护查询时失败。 */
    @Test
    void testValidationDoesNotReadRequestScope() {
        FieldDataIsolationStrategy fields = new FieldDataIsolationStrategy(List.of(
                new FieldRule(CoverageTenantRecord.class, "tenantId", () -> {
                    if (tenantScope == null) {
                        throw new IllegalStateException("请求尚未绑定租户范围");
                    }
                    return tenantScope;
                })));
        DataIsolationQueryInterceptor interceptor = DataIsolationQueryInterceptor.strict(List.of(fields), Set.of());
        SqlSessionFactory factory = factory(interceptor, TenantMapper.class);
        tenantScope = null;

        assertDoesNotThrow(() -> interceptor.validate(factory.getConfiguration(), CoverageTenantRecord.class));
        try (var session = factory.openSession()) {
            ReadMapper mapper = session.getMapper(ReadMapper.class);
            PersistenceException failure = assertThrows(PersistenceException.class, mapper::tenantIds);
            assertTrue(failure.getMessage().contains("请求尚未绑定租户范围"));
            tenantScope = DataIsolationScope.of(10L);
            assertEquals(List.of(1L, 3L), mapper.tenantIds());
        }
    }

    /** 另一实例已安装不能替代当前实例的安装校验。 */
    @Test
    void testValidationRequiresTheSameInstalledInstance() {
        DataIsolationQueryInterceptor installed = strict(Set.of());
        DataIsolationQueryInterceptor other = strict(Set.of());
        SqlSessionFactory factory = factory(installed, TenantMapper.class);

        assertDoesNotThrow(() -> installed.validate(factory.getConfiguration(), CoverageTenantRecord.class));
        assertThrows(IllegalStateException.class,
                () -> other.validate(factory.getConfiguration(), CoverageTenantRecord.class));
        Configuration uninstalled = configuration(TenantMapper.class);
        assertThrows(IllegalStateException.class, () -> installed.validate(uninstalled, CoverageTenantRecord.class));
    }

    /** 即使策略能匹配实体，requiredEntities 仍必须存在于当前工厂的 Mapper 登记中。 */
    @Test
    void testRequiredEntityMustHaveRegisteredMapper() {
        DataIsolationQueryInterceptor interceptor = strict(Set.of());
        SqlSessionFactory factory = factory(interceptor);

        assertThrows(IllegalStateException.class,
                () -> interceptor.validate(factory.getConfiguration(), CoverageTenantRecord.class));
    }

    /** 同一表的两个等价实体均已登记且匹配父类规则时，requiredEntities 应认可两者。 */
    @Test
    void testEquivalentTableMappingsBothSatisfyRequiredEntities() {
        DataIsolationQueryInterceptor interceptor = strict(Set.of());
        SqlSessionFactory factory = factory(interceptor, TenantMapper.class, TenantAliasMapper.class);

        assertDoesNotThrow(() -> interceptor.validate(factory.getConfiguration(),
                CoverageTenantRecord.class, CoverageTenantAliasRecord.class));
        try (var session = factory.openSession()) {
            ReadMapper mapper = session.getMapper(ReadMapper.class);
            assertEquals(List.of(1L, 3L), mapper.tenantIds());
            tenantScope = DataIsolationScope.of(20L);
            assertEquals(List.of(2L), mapper.tenantIds());
        }
    }

    /** 公共声明不能满足必须受保护实体的要求。 */
    @Test
    void testRequiredEntityCannotBePublic() {
        DataIsolationQueryInterceptor interceptor = strict(Set.of(CoveragePlainRecord.class));
        SqlSessionFactory factory = factory(interceptor, PlainMapper.class);

        assertDoesNotThrow(() -> interceptor.validate(factory.getConfiguration()));
        assertThrows(IllegalStateException.class,
                () -> interceptor.validate(factory.getConfiguration(), CoveragePlainRecord.class));
    }

    /** 普通实体虽有 tenantId 字段，未声明基础语义时默认策略不得猜测其租户责任。 */
    @Test
    void testStrictCoverageRejectsUnclassifiedEntity() {
        DataIsolationQueryInterceptor interceptor = DataIsolationQueryInterceptor.strict(
                List.of(new FieldDataIsolationStrategy()), Set.of());
        SqlSessionFactory factory = factory(interceptor, PlainMapper.class);

        assertThrows(IllegalStateException.class, () -> interceptor.validate(factory.getConfiguration()));
        try (var session = factory.openSession()) {
            assertThrows(PersistenceException.class, () -> session.getMapper(ReadMapper.class).plainIds());
        }
    }

    /** 显式普通字段规则只承担自己的约束，不为未声明基础语义的实体补租户条件。 */
    @Test
    void testExplicitPlainEntityRuleDoesNotInventTenantIsolation() {
        FieldDataIsolationStrategy fields = FieldDataIsolationStrategy.withDefaults(List.of(
                new FieldRule(CoveragePlainRecord.class, "ownerId", () -> DataIsolationScope.of(7L))));
        DataIsolationQueryInterceptor interceptor = DataIsolationQueryInterceptor.strict(List.of(fields), Set.of());
        SqlSessionFactory factory = factory(interceptor, PlainMapper.class);

        assertDoesNotThrow(() -> interceptor.validate(factory.getConfiguration(), CoveragePlainRecord.class));
        try (var session = factory.openSession()) {
            assertEquals(List.of(11L, 12L), session.getMapper(ReadMapper.class).plainIds());
        }
    }

    /** 已适用保护策略的实体不能再声明为公共实体。 */
    @Test
    void testPublicDeclarationCannotOverrideProtection() {
        DataIsolationQueryInterceptor interceptor = strict(Set.of(CoverageTenantRecord.class));
        SqlSessionFactory factory = factory(interceptor, TenantMapper.class);

        assertThrows(IllegalStateException.class, () -> interceptor.validate(factory.getConfiguration()));
        try (var session = factory.openSession()) {
            assertThrows(PersistenceException.class, () -> session.getMapper(ReadMapper.class).tenantIds());
        }
    }

    /** 公共声明也必须由当前工厂的真实 Mapper 登记支撑。 */
    @Test
    void testPublicDeclarationWithoutMapperFailsValidation() {
        DataIsolationQueryInterceptor interceptor = strict(Set.of(CoveragePlainRecord.class));
        SqlSessionFactory factory = factory(interceptor, TenantMapper.class);

        assertThrows(IllegalStateException.class, () -> interceptor.validate(factory.getConfiguration()));
    }

    /** 已知受管实体通过启动校验，不能使另一张未登记的实际查询表获得放行。 */
    @Test
    void testStrictQueryRejectsUnknownPhysicalTable() {
        DataIsolationQueryInterceptor interceptor = strict(Set.of());
        SqlSessionFactory factory = factory(interceptor, TenantMapper.class);
        assertDoesNotThrow(() -> interceptor.validate(factory.getConfiguration(), CoverageTenantRecord.class));

        try (var session = factory.openSession()) {
            ReadMapper mapper = session.getMapper(ReadMapper.class);
            assertEquals(List.of(1L, 3L), mapper.tenantIds());
            assertThrows(PersistenceException.class, mapper::unknownIds);
        }
    }

    /** 严格模式不支持 schema 限定，即使表的短名恰好已声明公共，也不能据此放行。 */
    @Test
    void testStrictPublicQueryRejectsSchemaQualification() {
        DataIsolationQueryInterceptor interceptor = strict(Set.of(CoveragePlainRecord.class));
        SqlSessionFactory factory = factory(interceptor, PlainMapper.class);
        assertDoesNotThrow(() -> interceptor.validate(factory.getConfiguration()));

        try (var session = factory.openSession()) {
            ReadMapper mapper = session.getMapper(ReadMapper.class);
            assertEquals(List.of(11L, 12L, 13L), mapper.plainIds());
            PersistenceException failure = assertThrows(PersistenceException.class, mapper::schemaQualifiedPlainIds);
            assertInstanceOf(IllegalStateException.class, rootCause(failure));
        }
    }

    /** 与公共表同名的 CTE 仍属于不支持的查询结构，不能命中公共表快速放行。 */
    @Test
    void testStrictPublicQueryRejectsSameNamedCte() {
        DataIsolationQueryInterceptor interceptor = strict(Set.of(CoveragePlainRecord.class));
        SqlSessionFactory factory = factory(interceptor, PlainMapper.class);
        assertDoesNotThrow(() -> interceptor.validate(factory.getConfiguration()));

        try (var session = factory.openSession()) {
            PersistenceException failure = assertThrows(PersistenceException.class,
                    () -> session.getMapper(ReadMapper.class).sameNamedPublicCte());

            assertInstanceOf(IllegalStateException.class, rootCause(failure));
        }
    }

    /** 明确公共实体及纯字面量查询无需请求范围，可以正常读取真实结果。 */
    @Test
    void testPublicAndLiteralQueriesRemainAvailableWithoutScope() {
        DataIsolationQueryInterceptor interceptor = strict(Set.of(CoveragePlainRecord.class));
        SqlSessionFactory factory = factory(interceptor, PlainMapper.class);
        tenantScope = null;
        assertDoesNotThrow(() -> interceptor.validate(factory.getConfiguration()));

        try (var session = factory.openSession()) {
            ReadMapper mapper = session.getMapper(ReadMapper.class);
            assertEquals(List.of(11L, 12L, 13L), mapper.plainIds());
            assertEquals(1L, mapper.literal());
        }
    }

    /** 首次查询后新增未分类 Mapper 必须重新校验，不能沿用旧缓存放行。 */
    @Test
    void testLateMapperRegistrationInvalidatesCoverage() {
        DataIsolationQueryInterceptor interceptor = strict(Set.of());
        SqlSessionFactory factory = factory(interceptor, TenantMapper.class);
        assertDoesNotThrow(() -> interceptor.validate(factory.getConfiguration()));

        try (var session = factory.openSession()) {
            ReadMapper mapper = session.getMapper(ReadMapper.class);
            assertEquals(List.of(1L, 3L), mapper.tenantIds());
            factory.getConfiguration().addMapper(PlainMapper.class);
            assertThrows(IllegalStateException.class, () -> interceptor.validate(factory.getConfiguration()));
            assertThrows(PersistenceException.class, mapper::tenantIds);
        }
    }

    /** 旧构造仍允许未受管实体和未知表，并继续保护已匹配策略的表。 */
    @Test
    void testLegacyConstructorKeepsUnmanagedReadCompatibility() {
        DataIsolationQueryInterceptor interceptor = new DataIsolationQueryInterceptor(List.of(tenantFields()));
        SqlSessionFactory factory = factory(interceptor, TenantMapper.class, PlainMapper.class);

        try (var session = factory.openSession()) {
            ReadMapper mapper = session.getMapper(ReadMapper.class);
            assertEquals(List.of(1L, 3L), mapper.tenantIds());
            assertEquals(List.of(11L, 12L, 13L), mapper.plainIds());
            assertEquals(List.of(91L), mapper.unknownIds());
        }
    }

    /** Spring 启动完成单例创建后校验真实工厂；无需请求范围即可启动，并在请求阶段返回隔离结果。 */
    @Test
    void testSpringStartupValidatesTheInstalledFactory() {
        tenantScope = null;
        try (AnnotationConfigApplicationContext context = startupContext(Set.of(CoveragePlainRecord.class))) {
            assertDoesNotThrow(context::refresh);
            assertTrue(context.isActive());
            tenantScope = DataIsolationScope.of(10L);

            try (var session = context.getBean(SqlSessionFactory.class).openSession()) {
                ReadMapper mapper = session.getMapper(ReadMapper.class);
                assertEquals(List.of(1L, 3L), mapper.tenantIds());
                assertEquals(List.of(11L, 12L, 13L), mapper.plainIds());
                tenantScope = DataIsolationScope.of(20L);
                assertEquals(List.of(2L), mapper.tenantIds());
            }
        }
    }

    /** 公共 Mapper 漏声明时由启动校验终止 refresh，应用不能以未分类实体继续服务。 */
    @Test
    void testSpringStartupRejectsMissingPublicDeclaration() {
        try (AnnotationConfigApplicationContext context = startupContext(Set.of())) {
            RuntimeException failure = assertThrows(RuntimeException.class, context::refresh);
            IllegalStateException cause = assertInstanceOf(IllegalStateException.class, rootCause(failure));

            assertTrue(cause.getMessage().contains(CoveragePlainRecord.class.getName()));
            assertFalse(context.isActive());
        }
    }

    private AnnotationConfigApplicationContext startupContext(Set<Class<?>> publicEntities) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.registerBean(DataIsolationQueryInterceptor.class, () -> strict(publicEntities));
        context.registerBean(SqlSessionFactory.class, () -> factory(context.getBean(DataIsolationQueryInterceptor.class),
                TenantMapper.class, PlainMapper.class));
        context.registerBean(SmartInitializingSingleton.class, () -> () -> {
            SqlSessionFactory factory = context.getBean(SqlSessionFactory.class);
            context.getBean(DataIsolationQueryInterceptor.class).validate(factory.getConfiguration(), CoverageTenantRecord.class);
        });
        return context;
    }

    private static Throwable rootCause(Throwable failure) {
        Throwable cause = failure;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }

    private DataIsolationQueryInterceptor strict(Set<Class<?>> publicEntities) {
        return DataIsolationQueryInterceptor.strict(List.of(tenantFields()), publicEntities);
    }

    private FieldDataIsolationStrategy tenantFields() {
        return new FieldDataIsolationStrategy(List.of(
                new FieldRule(CoverageTenantRecord.class, "tenantId", () -> tenantScope)));
    }

    private SqlSessionFactory factory(DataIsolationQueryInterceptor interceptor, Class<?>... mapperTypes) {
        Configuration configuration = configuration(mapperTypes);
        configuration.addInterceptor(interceptor);
        return new SqlSessionFactoryBuilder().build(configuration);
    }

    private Configuration configuration(Class<?>... mapperTypes) {
        Configuration configuration = new Configuration(new Environment("coverage", new JdbcTransactionFactory(), dataSource));
        configuration.addMapper(ReadMapper.class);
        for (Class<?> mapperType : mapperTypes) {
            configuration.addMapper(mapperType);
        }
        return configuration;
    }

    interface TenantMapper extends BaseMapper<CoverageTenantRecord> {
    }

    interface TenantAliasMapper extends BaseMapper<CoverageTenantAliasRecord> {
    }

    interface PlainMapper extends BaseMapper<CoveragePlainRecord> {
    }

    interface ReadMapper {

        @Select("SELECT id FROM coverage_tenant_record ORDER BY id")
        List<Long> tenantIds();

        @Select("SELECT id FROM coverage_plain_record ORDER BY id")
        List<Long> plainIds();

        @Select("SELECT id FROM public.coverage_plain_record ORDER BY id")
        List<Long> schemaQualifiedPlainIds();

        @Select("WITH coverage_plain_record AS (SELECT id FROM coverage_plain_record) SELECT id FROM coverage_plain_record ORDER BY id")
        List<Long> sameNamedPublicCte();

        @Select("SELECT id FROM coverage_unknown_record ORDER BY id")
        List<Long> unknownIds();

        @Select("SELECT 1")
        long literal();
    }
}
