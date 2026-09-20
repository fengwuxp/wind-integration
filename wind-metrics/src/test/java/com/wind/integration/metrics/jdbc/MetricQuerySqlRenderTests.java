package com.wind.integration.metrics.jdbc;

import com.wind.integration.metrics.dsl.definition.MetricMeasureDsl;
import com.wind.integration.metrics.dsl.definition.MetricOrElseDsl;
import com.wind.integration.metrics.dsl.definition.MetricSubjectDsl;
import com.wind.integration.metrics.dsl.definition.MetricTimeDsl;
import com.wind.integration.metrics.dsl.definition.MetricValueDsl;
import com.wind.integration.metrics.enums.MetricAggregation;
import com.wind.integration.metrics.enums.MetricOrElseMode;
import com.wind.integration.metrics.enums.MetricValueShape;
import com.wind.integration.metrics.enums.MetricValueType;
import com.wind.integration.metrics.query.MetricQuery;
import com.wind.integration.metrics.spec.MetricDSLDefinition;
import com.wind.integration.metrics.spec.MetricDefinitionObject;
import com.wind.integration.metrics.spec.MetricSqlDefinition;
import org.junit.jupiter.api.Test;

import java.sql.Types;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link MetricQuerySqlRender} 统一入口的契约用例：DSL / SQL 两种实现按 sealed 类型判断各司其职。
 *
 * @author wuxp
 */
class MetricQuerySqlRenderTests {

    private static final ZoneId UTC = ZoneId.of("UTC");

    private static final LocalDateTime START = LocalDateTime.of(2026, 9, 1, 0, 0);

    private static final LocalDateTime END = START.plusDays(1);

    private final MetricJdbcSqlCompiler compiler = new MetricJdbcSqlCompiler(UTC);

    private final MetricQuerySqlRender dsl = compiler;

    private final MetricQuerySqlRender sql = new MetricSqlTemplateRenderer();

    private final MetricQuerySqlRender composite =
            new CompositeMetricQuerySqlRender(compiler, new MetricSqlTemplateRenderer());

    /**
     * 场景：统一 DSL 渲染入口使用已登记的事实映射。
     * 输入：COUNT 定义、UTC 时间窗口及 order_fact 的冻结 binding。
     * 流程：registerBinding 后比较 render 与直接 compile 的结果。
     * 预期：SQL、参数与投影描述整体相等。
     */
    @Test
    void testDslRenderResolvesRegisteredBindingAndDelegatesToCompiler() {
        MetricDSLDefinition definition = dslDefinition();
        MetricJdbcBinding binding = binding();
        compiler.registerBinding(definition, binding);

        assertEquals(compiler.compile(definition, query(), binding), dsl.render(definition, query()));
    }

    /**
     * 场景：SQL 模板渲染保留既有直接插值契约。
     * 输入：tenant_id 模板和 subjectId=tenant-1。
     * 流程：通过 SQL 渲染接口处理定义。
     * 预期：输出带 tenant-1 的 SQL，bindings 与 projections 为空；不证明参数化执行。
     */
    @Test
    void testSqlRenderReturnsInterpolatedSqlWithoutBindings() {
        MetricDefinitionObject definition = new MetricSqlDefinition("code", 1, MetricValueShape.SCALAR,
                "TENANT", List.of(), Map.of(), "SELECT * FROM `t` WHERE `tenant_id` = '${subjectId}'");

        MetricSqlDescriptor result = sql.render(definition, query("tenant-1"));

        assertEquals("SELECT * FROM `t` WHERE `tenant_id` = 'tenant-1'", result.sql());
        assertEquals(List.of(), result.bindings());
        assertEquals(Map.of(), result.projections());
    }

    /**
     * 场景：DSL 专用入口不接受 SQL 模板定义。
     * 输入：SQL 类型的 SELECT 1 定义。
     * 流程：调用 DSL render。
     * 预期：抛出 IllegalArgumentException。
     */
    @Test
    void testDslRenderRejectsSqlDefinition() {
        MetricDefinitionObject definition = new MetricSqlDefinition("code", 1, MetricValueShape.SCALAR,
                "TENANT", List.of(), Map.of(), "SELECT 1");

        assertThrows(IllegalArgumentException.class, () -> dsl.render(definition, query()));
    }

    /**
     * 场景：DSL render 必须先取得事实映射。
     * 输入：合法 DSL COUNT 定义但未注册 binding。
     * 流程：直接调用 render。
     * 预期：抛出 IllegalStateException，不生成缺失映射的 SQL。
     */
    @Test
    void testDslRenderRejectsMissingBinding() {
        assertThrows(IllegalStateException.class, () -> dsl.render(dslDefinition(), query()));
    }

    /**
     * 场景：组合渲染器按定义类型选择对应能力。
     * 输入：已登记 binding 的 DSL COUNT，及读取 subjectId 的 SQL 模板。
     * 流程：分别经 composite.render。
     * 预期：DSL 与直接 compile 相等；SQL 输出 SELECT tenant-1 的字符串字面量。
     */
    @Test
    void testCompositeDispatchesByDefinitionType() {
        MetricDSLDefinition definition = dslDefinition();
        compiler.registerBinding(definition, binding());

        assertEquals(compiler.compile(definition, query(), binding()),
                composite.render(definition, query()));

        MetricDefinitionObject sqlDefinition = new MetricSqlDefinition("code", 1, MetricValueShape.SCALAR,
                "TENANT", List.of(), Map.of(), "SELECT '${subjectId}'");
        assertEquals("SELECT 'tenant-1'", composite.render(sqlDefinition, query("tenant-1")).sql());
    }

    private static MetricDSLDefinition dslDefinition() {
        MetricValueDsl value = new MetricValueDsl(MetricValueType.INTEGER, null, null,
                new MetricMeasureDsl(MetricAggregation.COUNT, null, null), null,
                new MetricOrElseDsl(MetricOrElseMode.NULL, null));
        return new MetricDSLDefinition("code", 1, MetricValueShape.SCALAR, "order_fact", List.of(),
                new MetricSubjectDsl(MetricSubjectDsl.GLOBAL, null), new MetricTimeDsl("created_at"),
                List.of(), Map.of(), null, value, Map.of());
    }

    private static MetricQuery query() {
        return new MetricQuery(null, START, END, Map.of(), Map.of());
    }

    private static MetricQuery query(String subjectId) {
        return new MetricQuery(subjectId, START, END, Map.of(), Map.of());
    }

    private static MetricJdbcBinding binding() {
        return new MetricJdbcBinding() {
            @Override
            public String tableName(String factReference) {
                return "order_fact";
            }

            @Override
            public String columnName(String fieldReference) {
                return "created_at";
            }

            @Override
            public Class<?> javaType(String fieldReference) {
                return Instant.class;
            }

            @Override
            public int jdbcType(String fieldReference) {
                return Types.TIMESTAMP;
            }

            @Override
            public Object toJdbcValue(String fieldReference, Object normalizedValue) {
                return normalizedValue;
            }
        };
    }
}
