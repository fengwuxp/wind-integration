package com.wind.integration.metrics.jdbc;

import com.wind.integration.metrics.MetricValidationException;
import com.wind.integration.metrics.dsl.definition.MetricExpressionDsl;
import com.wind.integration.metrics.dsl.definition.MetricReferenceDsl;
import com.wind.integration.metrics.dsl.definition.MetricJoinDsl;
import com.wind.integration.metrics.dsl.definition.MetricJoinOnDsl;
import com.wind.integration.metrics.dsl.definition.MetricMeasureDsl;
import com.wind.integration.metrics.dsl.definition.MetricOrElseDsl;
import com.wind.integration.metrics.dsl.definition.MetricQueryParameterDsl;
import com.wind.integration.metrics.dsl.definition.MetricSubjectDsl;
import com.wind.integration.metrics.dsl.definition.MetricTimeDsl;
import com.wind.integration.metrics.dsl.definition.MetricValueDsl;
import com.wind.integration.metrics.dsl.definition.selection.MetricLimitDsl;
import com.wind.integration.metrics.dsl.definition.selection.MetricOrderByDsl;
import com.wind.integration.metrics.dsl.definition.selection.MetricRowSelectionDsl;
import com.wind.integration.metrics.dsl.filter.ComparisonMetricFilterDsl;
import com.wind.integration.metrics.dsl.filter.LogicalMetricFilterDsl;
import com.wind.integration.metrics.dsl.filter.MetricFilterDsl;
import com.wind.integration.metrics.dsl.filter.NullMetricFilterDsl;
import com.wind.integration.metrics.dsl.filter.SetMetricFilterDsl;
import com.wind.integration.metrics.dsl.literal.IntegralMetricLiteralDsl;
import com.wind.integration.metrics.dsl.literal.StringMetricLiteralDsl;
import com.wind.integration.metrics.enums.MetricAggregation;
import com.wind.integration.metrics.enums.MetricErrorCode;
import com.wind.integration.metrics.enums.MetricExpressionType;
import com.wind.integration.metrics.enums.MetricFilterOperator;
import com.wind.integration.metrics.enums.MetricJoinCardinality;
import com.wind.integration.metrics.enums.MetricJoinType;
import com.wind.integration.metrics.enums.MetricOrElseMode;
import com.wind.integration.metrics.enums.MetricSortDirection;
import com.wind.integration.metrics.enums.MetricValueShape;
import com.wind.integration.metrics.enums.MetricValueType;
import com.wind.integration.metrics.query.MetricQuery;
import com.wind.integration.metrics.spec.MetricDSLDefinition;
import org.jooq.SQLDialect;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link MetricJdbcSqlCompiler} 公开契约的 TDD 用例。
 *
 * <p>只观察 {@code compile} 与 {@code renderValidatedFilter} 的 SQL、有序参数、投影和校验异常，
 * 不感知渲染内部实现。所有标识符均受控，参数统一以 {@code ?} 占位符表达。</p>
 *
 * @author wuxp
 */
class MetricJdbcSqlCompilerTests {

    private static final ZoneId UTC = ZoneId.of("UTC");

    private static final LocalDateTime START = LocalDateTime.of(2026, 9, 1, 0, 0);

    private static final LocalDateTime END = START.plusDays(1);

    private static final Instant START_INSTANT = START.atZone(UTC).toInstant();

    private static final Instant END_INSTANT = END.atZone(UTC).toInstant();

    // 构造器契约

    /**
     * 场景：编译器必须显式确定 SQL 方言。
     * 输入：UTC、行数上限1000、dialect=null。
     * 流程：构造 MetricJdbcSqlCompiler。
     * 预期：抛出 NullPointerException。
     */
    @Test
    void testConstructorRejectsNullDialect() {
        assertThrows(NullPointerException.class,
                () -> new MetricJdbcSqlCompiler(UTC, 1000, null));
    }

    /**
     * 场景：时间参数转换必须有明确时区。
     * 输入：timeZone=null。
     * 流程：构造 MetricJdbcSqlCompiler。
     * 预期：抛出 NullPointerException。
     */
    @Test
    void testConstructorRejectsNullTimeZone() {
        assertThrows(NullPointerException.class,
                () -> new MetricJdbcSqlCompiler(null));
    }

    /**
     * 场景：编译器不能接受尚未支持的方言。
     * 输入：UTC、上限1000、SQLITE。
     * 流程：构造 MetricJdbcSqlCompiler。
     * 预期：抛出 IllegalArgumentException。
     */
    @Test
    void testConstructorRejectsUnsupportedDialect() {
        assertThrows(IllegalArgumentException.class,
                () -> new MetricJdbcSqlCompiler(UTC, 1000, SQLDialect.SQLITE));
    }

    /**
     * 场景：行选择的全局上限必须为正数。
     * 输入：上限0与-1。
     * 流程：分别构造编译器。
     * 预期：均抛出 IllegalArgumentException。
     */
    @Test
    void testConstructorRejectsNonPositiveRowSelectionLimit() {
        for (int limit : List.of(0, -1)) {
            assertThrows(IllegalArgumentException.class,
                    () -> new MetricJdbcSqlCompiler(UTC, limit));
        }
    }

    /**
     * 场景：声明支持的方言可以正常初始化。
     * 输入：MYSQL、POSTGRES、H2，UTC 与上限1000。
     * 流程：逐一构造编译器。
     * 预期：均成功；此用例仅验证初始化，不连接数据库执行 SQL。
     */
    @Test
    void testConstructorAcceptsSupportedDialects() {
        for (SQLDialect dialect : List.of(SQLDialect.MYSQL, SQLDialect.POSTGRES, SQLDialect.H2)) {
            assertDoesNotThrow(() -> new MetricJdbcSqlCompiler(UTC, 1000, dialect));
        }
    }

    // 实时查询：投影、谓词、关联与有限行集

    /**
     * 场景：全局 COUNT 只按窗口过滤。
     * 输入：全局 order_fact 指标，2026-09-01至09-02 UTC，无主体。
     * 流程：编译定义和冻结 mapping。
     * 预期：生成 count(*)、value 投影及半开窗两个 TIMESTAMP 绑定，无主体谓词。
     */
    @Test
    void testGlobalCountRendersWithoutSubjectPredicate() {
        MetricSqlDescriptor result = compiler().compile(definition().build(), query(), binding());

        assertEquals("SELECT count(*) AS `value` FROM `order_fact` AS `p`"
                + " WHERE (`p`.`created_at` >= ? AND `p`.`created_at` < ?)", result.sql());
        assertEquals(Map.of("value", "value"), result.projections());
        assertBindings(result.bindings(),
                START_INSTANT, Types.TIMESTAMP,
                END_INSTANT, Types.TIMESTAMP);
    }

    /**
     * 场景：没有声明维度或参数的全局指标，调用方省略两个可选容器。
     * 输入：null dimensionValues 与 null parameterValues，完整半开时间窗。
     * 流程：使用冻结 JDBC mapping 编译 COUNT SQL。
     * 预期：编译成功，只产生时间绑定；不把缺省容器误判为合同错误。
     */
    @Test
    void testCompileAllowsOmittedOptionalQueryContainers() {
        MetricQuery query = new MetricQuery(null, START, END, null, null);
        MetricSqlDescriptor result = compiler().compile(definition().build(), query, binding());

        assertEquals("SELECT count(*) AS `value` FROM `order_fact` AS `p`"
                + " WHERE (`p`.`created_at` >= ? AND `p`.`created_at` < ?)", result.sql());
        assertBindings(result.bindings(), START_INSTANT, Types.TIMESTAMP, END_INSTANT, Types.TIMESTAMP);
    }

    /**
     * 场景：度量条件只影响对应汇总值。
     * 输入：SUM(amount)，条件 amount 大于100，完整窗口。
     * 流程：编译带 measure.filter 的定义。
     * 预期：生成 CASE WHEN 条件 SUM，依次绑定 DECIMAL 100 和起止时间。
     */
    @Test
    void testSumMeasureWithFilterRendersConditionalAggregate() {
        MetricMeasureDsl measure = measure(MetricAggregation.SUM, "amount",
                new ComparisonMetricFilterDsl(MetricFilterOperator.GT, "amount",
                        new IntegralMetricLiteralDsl(BigInteger.valueOf(100))));
        MetricDSLDefinition definition = definition().value(value(measure)).build();

        MetricSqlDescriptor result = compiler().compile(definition, query(), binding());

        assertEquals("SELECT SUM(CASE WHEN `p`.`amount` > ? THEN `p`.`amount` END) AS `value`"
                + " FROM `order_fact` AS `p` WHERE (`p`.`created_at` >= ? AND `p`.`created_at` < ?)",
                result.sql());
        assertBindings(result.bindings(),
                new BigDecimal("100"), Types.DECIMAL,
                START_INSTANT, Types.TIMESTAMP,
                END_INSTANT, Types.TIMESTAMP);
    }

    /**
     * 场景：主体指标必须把主体限制编入 SQL。
     * 输入：USER 指标，subjectId=user-1，完整窗口。
     * 流程：编译 COUNT。
     * 预期：WHERE 包含 user_id 与半开窗，按主体、起点、终点顺序绑定。
     */
    @Test
    void testSubjectMetricFiltersBySubjectId() {
        MetricDSLDefinition definition = definition()
                .subject(new MetricSubjectDsl("USER", "user_id"))
                .build();

        MetricSqlDescriptor result = compiler().compile(definition, query("user-1"), binding());

        assertEquals("SELECT count(*) AS `value` FROM `order_fact` AS `p`"
                + " WHERE (`p`.`user_id` = ? AND `p`.`created_at` >= ? AND `p`.`created_at` < ?)",
                result.sql());
        assertBindings(result.bindings(),
                "user-1", Types.VARCHAR,
                START_INSTANT, Types.TIMESTAMP,
                END_INSTANT, Types.TIMESTAMP);
    }

    /**
     * 场景：维度过滤顺序稳定，不依赖输入 Map 顺序。
     * 输入：声明 region/channel，输入 CN/APP。
     * 流程：编译 COUNT。
     * 预期：等值条件按 channel、region 排序，时间参数后绑定 APP、CN。
     */
    @Test
    void testDimensionsRenderAsEqualityInSortedFieldOrder() {
        MetricDSLDefinition definition = definition().dimensions(List.of("region", "channel")).build();

        MetricSqlDescriptor result = compiler().compile(definition,
                query(Map.of("region", "CN", "channel", "APP")), binding());

        assertEquals("SELECT count(*) AS `value` FROM `order_fact` AS `p`"
                + " WHERE (`p`.`created_at` >= ? AND `p`.`created_at` < ?"
                + " AND `p`.`channel` = ? AND `p`.`region` = ?)", result.sql());
        assertBindings(result.bindings(),
                START_INSTANT, Types.TIMESTAMP,
                END_INSTANT, Types.TIMESTAMP,
                "APP", Types.VARCHAR,
                "CN", Types.VARCHAR);
    }

    /**
     * 场景：字段集合只投影真实度量并保持确定顺序。
     * 输入：orders=COUNT、revenue=SUM(amount)。
     * 流程：编译 FIELD_SET。
     * 预期：SQL 按 orders、revenue 输出，投影映射一致且仅绑定起止时间。
     */
    @Test
    void testFieldSetRendersMultipleMeasuresInFieldOrder() {
        MetricDSLDefinition definition = definition()
                .shape(MetricValueShape.FIELD_SET)
                .fields(Map.of(
                        "revenue", value(measure(MetricAggregation.SUM, "amount", null)),
                        "orders", value(measure(MetricAggregation.COUNT, null, null))))
                .build();

        MetricSqlDescriptor result = compiler().compile(definition, query(), binding());

        assertEquals("SELECT count(*) AS `orders`, SUM(`p`.`amount`) AS `revenue`"
                + " FROM `order_fact` AS `p` WHERE (`p`.`created_at` >= ? AND `p`.`created_at` < ?)",
                result.sql());
        assertEquals(Map.of("orders", "orders", "revenue", "revenue"), result.projections());
        assertBindings(result.bindings(),
                START_INSTANT, Types.TIMESTAMP,
                END_INSTANT, Types.TIMESTAMP);
    }

    /**
     * 场景：受控内关联使用冻结字段映射。
     * 输入：order_fact.customer_id 对 customer_fact.id 的 MANY_TO_ONE INNER JOIN。
     * 流程：编译 COUNT。
     * 预期：生成 j0 别名和正确等值 ON 条件，起止时间仍作用于主事实。
     */
    @Test
    void testInnerJoinRendersJoinAliasAndConditions() {
        MetricDSLDefinition definition = definition()
                .joins(List.of(join(MetricJoinType.INNER)))
                .build();

        MetricSqlDescriptor result = compiler().compile(definition, query(), joinBinding());

        assertEquals("SELECT count(*) AS `value` FROM `order_fact` AS `p` JOIN `customer_fact` AS `j0`"
                + " ON `p`.`customer_id` = `j0`.`id` WHERE (`p`.`created_at` >= ? AND `p`.`created_at` < ?)",
                result.sql());
        assertBindings(result.bindings(),
                START_INSTANT, Types.TIMESTAMP,
                END_INSTANT, Types.TIMESTAMP);
    }

    /**
     * 场景：左关联保留指定 JOIN 类型。
     * 输入：同一 customer_id/id 映射配置 LEFT。
     * 流程：编译 COUNT。
     * 预期：生成 LEFT OUTER JOIN 与正确 ON 条件，时间绑定保持顺序。
     */
    @Test
    void testLeftJoinRendersOuterJoin() {
        MetricDSLDefinition definition = definition()
                .joins(List.of(join(MetricJoinType.LEFT)))
                .build();

        MetricSqlDescriptor result = compiler().compile(definition, query(), joinBinding());

        assertEquals("SELECT count(*) AS `value` FROM `order_fact` AS `p` LEFT OUTER JOIN `customer_fact` AS `j0`"
                + " ON `p`.`customer_id` = `j0`.`id` WHERE (`p`.`created_at` >= ? AND `p`.`created_at` < ?)",
                result.sql());
        assertBindings(result.bindings(),
                START_INSTANT, Types.TIMESTAMP,
                END_INSTANT, Types.TIMESTAMP);
    }

    /**
     * 场景：先选行再聚合，LIMIT 不能作用于聚合结果行。
     * 输入：按 created_at 降序选10行的 COUNT。
     * 流程：编译 rowSelection。
     * 预期：内层带窗口、ORDER BY、LIMIT ?，外层 count(*)；最后绑定 INTEGER 10。
     */
    @Test
    void testRowSelectionRendersOrderedLimitedSubquery() {
        MetricRowSelectionDsl selection = new MetricRowSelectionDsl(null,
                List.of(new MetricOrderByDsl("created_at", MetricSortDirection.DESC)),
                new MetricLimitDsl(10, null));
        MetricDSLDefinition definition = definition().rowSelection(selection).build();

        MetricSqlDescriptor result = compiler().compile(definition, query(), binding());

        assertEquals("SELECT count(*) AS `value` FROM (SELECT `p`.`created_at` AS `c0`"
                + " FROM `order_fact` AS `p` WHERE (`p`.`created_at` >= ? AND `p`.`created_at` < ?)"
                + " ORDER BY `p`.`created_at` DESC LIMIT ?) AS `r`", result.sql());
        assertEquals(Map.of("value", "value"), result.projections());
        assertBindings(result.bindings(),
                START_INSTANT, Types.TIMESTAMP,
                END_INSTANT, Types.TIMESTAMP,
                10, Types.INTEGER);
    }

    /**
     * 场景：行数可由声明的查询参数提供。
     * 输入：entryLimit 参数声明，查询值10，按创建时间降序。
     * 流程：编译参数化 rowSelection。
     * 预期：LIMIT 使用占位符，时间参数后绑定 INTEGER 10。
     */
    @Test
    void testParameterizedRowSelectionLimitRendersBoundLimit() {
        MetricRowSelectionDsl selection = new MetricRowSelectionDsl(null,
                List.of(new MetricOrderByDsl("created_at", MetricSortDirection.DESC)),
                new MetricLimitDsl(null, "entryLimit"));
        MetricDSLDefinition definition = definition()
                .parameters(Map.of("entryLimit", parameter(null, null)))
                .rowSelection(selection)
                .build();

        MetricSqlDescriptor result = compiler().compile(definition, query(Map.of(), Map.of("entryLimit", 10)), binding());

        assertEquals("SELECT count(*) AS `value` FROM (SELECT `p`.`created_at` AS `c0`"
                + " FROM `order_fact` AS `p` WHERE (`p`.`created_at` >= ? AND `p`.`created_at` < ?)"
                + " ORDER BY `p`.`created_at` DESC LIMIT ?) AS `r`", result.sql());
        assertBindings(result.bindings(),
                START_INSTANT, Types.TIMESTAMP,
                END_INSTANT, Types.TIMESTAMP,
                10, Types.INTEGER);
    }

    // 校验错误

    /**
     * 场景：SQL 编译必须使用单个非空字符串主体及正向半开窗口。
     * 输入：空白、整数或集合主体；缺起止时间、相等或倒置窗口。
     * 流程：为主体指标和全局指标分别调用公开 compile 入口。
     * 预期：返回 QUERY_INVALID 并定位 subjectId/startTime/endTime，不生成 SQL。
     */
    @Test
    void testCompileRejectsInvalidSubjectAndWindow() {
        MetricDSLDefinition subject = definition().subject(new MetricSubjectDsl("USER", "user_id")).build();
        for (Object subjectId : List.of(" ", 12L, List.of("user-1", "user-2"))) {
            MetricQuery query = new MetricQuery(subjectId, START, END, Map.of(), Map.of());
            assertValidation(MetricErrorCode.QUERY_INVALID, "/subjectId",
                    () -> compiler().compile(subject, query, binding()));
        }
        MetricDSLDefinition global = definition().build();
        assertValidation(MetricErrorCode.QUERY_INVALID, "/startTime",
                () -> compiler().compile(global, new MetricQuery(null, null, END, Map.of(), Map.of()), binding()));
        assertValidation(MetricErrorCode.QUERY_INVALID, "/endTime",
                () -> compiler().compile(global, new MetricQuery(null, START, null, Map.of(), Map.of()), binding()));
        for (LocalDateTime end : List.of(START, START.minusSeconds(1))) {
            assertValidation(MetricErrorCode.QUERY_INVALID, "/endTime",
                    () -> compiler().compile(global, new MetricQuery(null, START, end, Map.of(), Map.of()), binding()));
        }
    }

    /**
     * 场景：通用查询可以携带任意变量，但 SQL 编译必须遵守物理字段和声明参数类型。
     * 输入：空容器；region 的集合、Map、Double 或 null；entryLimit 的非 Integer 或 null。
     * 流程：按 String 维度映射和整数参数定义分别调用 compile。
     * 预期：拒绝非法容器及字段值，保留 QUERY_INVALID 或 METRIC_PARAMETER_TYPE_MISMATCH 的准确路径。
     */
    @Test
    void testCompileRejectsInvalidDimensionAndParameterValues() {
        MetricDSLDefinition global = definition().build();
        assertDoesNotThrow(() -> compiler().compile(global,
                new MetricQuery(null, START, END, null, null), binding()));
        assertValidation(MetricErrorCode.QUERY_INVALID, "/dimensionValues",
                () -> compiler().compile(global, query(Map.of("unexpected", "x")), binding()));
        assertValidation(MetricErrorCode.METRIC_PARAMETER_UNEXPECTED, "/parameterValues/unexpected",
                () -> compiler().compile(global, query(Map.of(), Map.of("unexpected", 2)), binding()));
        MetricDSLDefinition dimension = definition().dimensions(List.of("region")).build();
        assertValidation(MetricErrorCode.QUERY_INVALID, "/dimensionValues",
                () -> compiler().compile(dimension, new MetricQuery(null, START, END, null, Map.of()), binding()));
        for (Object value : Arrays.asList(List.of("CN"), Map.of("code", "CN"), 1.5D, null)) {
            Map<String, Object> dimensions = Collections.singletonMap("region", value);
            assertValidation(MetricErrorCode.QUERY_INVALID, "/dimensionValues/region",
                    () -> compiler().compile(dimension, query(dimensions), binding()));
        }
        MetricDSLDefinition parameter = definition().parameters(Map.of("entryLimit", parameter(1, 10))).build();
        assertValidation(MetricErrorCode.METRIC_PARAMETER_TYPE_MISMATCH, "/parameterValues",
                () -> compiler().compile(parameter, new MetricQuery(null, START, END, Map.of(), null), binding()));
        for (Object value : Arrays.asList("2", 2L, 2.0D, List.of(2), null)) {
            Map<String, Object> parameters = Collections.singletonMap("entryLimit", value);
            assertValidation(MetricErrorCode.METRIC_PARAMETER_TYPE_MISMATCH, "/parameterValues/entryLimit",
                    () -> compiler().compile(parameter, query(Map.of(), parameters), binding()));
        }
        for (String name : Arrays.asList(null, " ")) {
            Map<String, Object> parameters = Collections.singletonMap(name, 2);
            assertValidation(MetricErrorCode.METRIC_PARAMETER_TYPE_MISMATCH, "/parameterValues",
                    () -> compiler().compile(global, query(Map.of(), parameters), binding()));
            assertValidation(MetricErrorCode.METRIC_PARAMETER_TYPE_MISMATCH, "/parameterValues",
                    () -> compiler().compile(parameter, query(Map.of(), parameters), binding()));
        }
    }

    /**
     * 场景：时间维度同样必须在编译边界拒绝空值，不能漏成空指针异常或空值等号条件。
     * 输入：created_at 维度为 null，物理映射为 Instant。
     * 流程：调用 compile 创建时间维度过滤 SQL。
     * 预期：报 QUERY_INVALID，定位 /dimensionValues/created_at。
     */
    @Test
    void testCompileRejectsNullTemporalDimension() {
        MetricDSLDefinition definition = definition().dimensions(List.of("created_at")).build();
        MetricQuery query = query(Collections.singletonMap("created_at", null));
        assertValidation(MetricErrorCode.QUERY_INVALID, "/dimensionValues/created_at",
                () -> compiler().compile(definition, query, binding()));
    }

    /**
     * 场景：公开编译入口拒绝缺少定义或条件的请求。
     * 输入：definition=null 或 query=null。
     * 流程：分别调用 compile。
     * 预期：均报 QUERY_INVALID，错误定位根路径。
     */
    @Test
    void testCompileRejectsNullDefinitionOrQuery() {
        assertValidation(MetricErrorCode.QUERY_INVALID, "",
                () -> compiler().compile(null, query(), binding()));
        assertValidation(MetricErrorCode.QUERY_INVALID, "",
                () -> compiler().compile(definition().build(), null, binding()));
    }

    /**
     * 场景：事实 SQL 编译器不负责跨指标表达式求值。
     * 输入：无 fact，BASE@1 的 value * 2 派生定义。
     * 流程：调用事实 compile。
     * 预期：报 METRIC_EXECUTION_MODE_UNSUPPORTED，定位 /metric/fact。
     */
    @Test
    void testCompileRejectsDerivedMetric() {
        MetricValueDsl value = new MetricValueDsl(MetricValueType.LONG, null, null, null,
                new MetricExpressionDsl(MetricExpressionType.SPEL, "metric('BASE', 'value') * 2"),
                new MetricOrElseDsl(MetricOrElseMode.NULL, null));
        MetricDSLDefinition derived = new MetricDSLDefinition("DERIVED", 1, MetricValueShape.SCALAR,
                null, List.of(), new MetricSubjectDsl("GLOBAL", null), null, List.of(), Map.of(),
                null, value, Map.of(), List.of(new MetricReferenceDsl("BASE", 1)));
        assertValidation(MetricErrorCode.METRIC_EXECUTION_MODE_UNSUPPORTED, "/metric/fact",
                () -> compiler().compile(derived, query(), binding()));
    }

    /**
     * 场景：查询主体类型必须与定义一致。
     * 输入：定义为 USER，查询声明 APP、subjectId=user-1。
     * 流程：调用 compile。
     * 预期：报 QUERY_INVALID，定位 /subjectType。
     */
    @Test
    void testCompileRejectsSubjectTypeMismatch() {
        MetricDSLDefinition definition = definition()
                .subject(new MetricSubjectDsl("USER", "user_id"))
                .build();
        MetricQuery query = MetricQuery.builder()
                .subjectId("user-1")
                .subjectType("APP")
                .timeRange(START, END)
                .dimensions(Map.of())
                .parameters(Map.of())
                .build();

        assertValidation(MetricErrorCode.QUERY_INVALID, "/subjectType",
                () -> compiler().compile(definition, query, binding()));
    }

    /**
     * 场景：全局指标不能暗中接受主体过滤。
     * 输入：GLOBAL 定义却传 subjectId=user-1。
     * 流程：调用 compile。
     * 预期：报 QUERY_INVALID，定位 /subjectId。
     */
    @Test
    void testCompileRejectsSubjectIdForGlobalMetric() {
        assertValidation(MetricErrorCode.QUERY_INVALID, "/subjectId",
                () -> compiler().compile(definition().build(), query("user-1"), binding()));
    }

    /**
     * 场景：主体指标不能退化为全量查询。
     * 输入：USER 定义，查询主体为空。
     * 流程：调用 compile。
     * 预期：报 QUERY_INVALID，定位 /subjectId。
     */
    @Test
    void testCompileRejectsMissingSubjectIdForSubjectMetric() {
        MetricDSLDefinition definition = definition()
                .subject(new MetricSubjectDsl("USER", "user_id"))
                .build();

        assertValidation(MetricErrorCode.QUERY_INVALID, "/subjectId",
                () -> compiler().compile(definition, query(), binding()));
    }

    /**
     * 场景：查询维度必须与定义的集合精确一致。
     * 输入：定义只含 region；查询缺 region 或额外带 channel。
     * 流程：分别调用 compile。
     * 预期：均报 QUERY_INVALID，定位 /dimensionValues。
     */
    @Test
    void testCompileRejectsDimensionKeyMismatch() {
        MetricDSLDefinition definition = definition().dimensions(List.of("region")).build();
        MetricJdbcSqlCompiler compiler = compiler();
        MetricJdbcMapping binding = binding();

        assertValidation(MetricErrorCode.QUERY_INVALID, "/dimensionValues",
                () -> compiler.compile(definition, query(), binding));
        assertValidation(MetricErrorCode.QUERY_INVALID, "/dimensionValues",
                () -> compiler.compile(definition, query(Map.of("region", "CN", "channel", "APP")), binding));
    }

    /**
     * 场景：调用方不能传入定义未声明的参数。
     * 输入：无参数定义却传 entryLimit=2。
     * 流程：调用 compile。
     * 预期：报 METRIC_PARAMETER_UNEXPECTED，定位 entryLimit。
     */
    @Test
    void testCompileRejectsUndeclaredParameter() {
        MetricQuery query = query(Map.of(), Map.of("entryLimit", 2));

        assertValidation(MetricErrorCode.METRIC_PARAMETER_UNEXPECTED, "/parameterValues/entryLimit",
                () -> compiler().compile(definition().build(), query, binding()));
    }

    /**
     * 场景：定义要求的参数不能缺失。
     * 输入：声明 entryLimit 范围1至10，查询无参数。
     * 流程：调用 compile。
     * 预期：报 METRIC_PARAMETER_MISSING，定位 entryLimit。
     */
    @Test
    void testCompileRejectsMissingParameter() {
        MetricDSLDefinition definition = definition()
                .parameters(Map.of("entryLimit", parameter(1, 10)))
                .build();

        assertValidation(MetricErrorCode.METRIC_PARAMETER_MISSING, "/parameterValues/entryLimit",
                () -> compiler().compile(definition, query(), binding()));
    }

    /**
     * 场景：参数值必须满足定义约束。
     * 输入：entryLimit 范围1至10，实际传11。
     * 流程：调用 compile。
     * 预期：报 METRIC_PARAMETER_OUT_OF_RANGE，定位 entryLimit。
     */
    @Test
    void testCompileRejectsOutOfRangeParameter() {
        MetricDSLDefinition definition = definition()
                .parameters(Map.of("entryLimit", parameter(1, 10)))
                .build();

        assertValidation(MetricErrorCode.METRIC_PARAMETER_OUT_OF_RANGE, "/parameterValues/entryLimit",
                () -> compiler().compile(definition, query(Map.of(), Map.of("entryLimit", 11)), binding()));
    }

    /**
     * 场景：固定选行数量不能绕过编译器上限。
     * 输入：rowSelection 固定 limit=5000，使用默认编译器上限。
     * 流程：调用 compile。
     * 预期：报 DSL_VALUE_INVALID，定位 /metric/rowSelection/limit/value。
     */
    @Test
    void testCompileRejectsFixedRowSelectionLimitOutOfRange() {
        MetricRowSelectionDsl selection = new MetricRowSelectionDsl(null,
                List.of(new MetricOrderByDsl("created_at", MetricSortDirection.DESC)),
                new MetricLimitDsl(5000, null));
        MetricDSLDefinition definition = definition().rowSelection(selection).build();

        assertValidation(MetricErrorCode.DSL_VALUE_INVALID, "/metric/rowSelection/limit/value",
                () -> compiler().compile(definition, query(), binding()));
    }

    /**
     * 场景：参数化选行数量也必须受编译器上限约束。
     * 输入：entryLimit 定义无额外范围，查询传5000。
     * 流程：编译以该参数为 limit 的定义。
     * 预期：报 METRIC_PARAMETER_OUT_OF_RANGE，定位 entryLimit。
     */
    @Test
    void testCompileRejectsParameterizedRowSelectionLimitOutOfRange() {
        MetricRowSelectionDsl selection = new MetricRowSelectionDsl(null,
                List.of(new MetricOrderByDsl("created_at", MetricSortDirection.DESC)),
                new MetricLimitDsl(null, "entryLimit"));
        MetricDSLDefinition definition = definition()
                .parameters(Map.of("entryLimit", parameter(null, null)))
                .rowSelection(selection)
                .build();

        assertValidation(MetricErrorCode.METRIC_PARAMETER_OUT_OF_RANGE, "/parameterValues/entryLimit",
                () -> compiler().compile(definition, query(Map.of(), Map.of("entryLimit", 5000)), binding()));
    }

    /**
     * 场景：事实查询至少要有可执行度量。
     * 输入：FIELD_SET 的 fields 为空。
     * 流程：调用 compile。
     * 预期：报 DSL_VALUE_INVALID，定位 /metric/value。
     */
    @Test
    void testCompileRejectsMetricWithoutMeasures() {
        MetricDSLDefinition definition = definition()
                .shape(MetricValueShape.FIELD_SET)
                .fields(Map.of())
                .build();

        assertValidation(MetricErrorCode.DSL_VALUE_INVALID, "/metric/value",
                () -> compiler().compile(definition, query(), binding()));
    }

    // 过滤条件渲染

    /**
     * 场景：已校验比较条件渲染为占位符与有类型绑定。
     * 输入：status EQ APPROVED，受控字段前缀 p。
     * 流程：调用 renderValidatedFilter。
     * 预期：SQL 为 p.status = ?，绑定 APPROVED/VARCHAR。
     */
    @Test
    void testRenderValidatedFilterComparison() {
        MetricJdbcSqlCompiler compiler = compiler();
        List<MetricJdbcParameterBinding> bindings = new ArrayList<>();
        MetricFilterDsl filter = new ComparisonMetricFilterDsl(MetricFilterOperator.EQ, "status",
                new StringMetricLiteralDsl("APPROVED"));

        String sql = compiler.renderValidatedFilter(binding(), filter, bindings, field -> "p." + field);

        assertEquals("p.status = ?", sql);
        assertBindings(bindings, "APPROVED", Types.VARCHAR);
    }

    /**
     * 场景：集合条件保留值顺序与绑定类型。
     * 输入：status IN APPROVED、PENDING。
     * 流程：调用 renderValidatedFilter。
     * 预期：生成两个占位符，按输入顺序绑定两个 VARCHAR 值。
     */
    @Test
    void testRenderValidatedFilterSetMembership() {
        MetricJdbcSqlCompiler compiler = compiler();
        List<MetricJdbcParameterBinding> bindings = new ArrayList<>();
        MetricFilterDsl filter = new SetMetricFilterDsl(MetricFilterOperator.IN, "status",
                List.of(new StringMetricLiteralDsl("APPROVED"), new StringMetricLiteralDsl("PENDING")));

        String sql = compiler.renderValidatedFilter(binding(), filter, bindings, field -> "p." + field);

        assertEquals("p.status IN (?, ?)", sql);
        assertBindings(bindings,
                "APPROVED", Types.VARCHAR,
                "PENDING", Types.VARCHAR);
    }

    /**
     * 场景：空值谓词不引入无意义参数。
     * 输入：status IS_NULL。
     * 流程：调用 renderValidatedFilter。
     * 预期：生成 p.status IS NULL，绑定列表为空。
     */
    @Test
    void testRenderValidatedFilterNullPredicate() {
        MetricJdbcSqlCompiler compiler = compiler();
        List<MetricJdbcParameterBinding> bindings = new ArrayList<>();
        MetricFilterDsl filter = new NullMetricFilterDsl(MetricFilterOperator.IS_NULL, "status");

        String sql = compiler.renderValidatedFilter(binding(), filter, bindings, field -> "p." + field);

        assertEquals("p.status IS NULL", sql);
        assertEquals(List.of(), bindings);
    }

    /**
     * 场景：逻辑组合保留括号和各子条件参数。
     * 输入：status=APPROVED 与 region IS_NULL，以 AND 组合。
     * 流程：调用 renderValidatedFilter。
     * 预期：生成带括号的 AND 表达式，仅绑定 APPROVED/VARCHAR。
     */
    @Test
    void testRenderValidatedFilterLogicalCombination() {
        MetricJdbcSqlCompiler compiler = compiler();
        List<MetricJdbcParameterBinding> bindings = new ArrayList<>();
        MetricFilterDsl filter = new LogicalMetricFilterDsl(MetricFilterOperator.AND, List.of(
                new ComparisonMetricFilterDsl(MetricFilterOperator.EQ, "status",
                        new StringMetricLiteralDsl("APPROVED")),
                new NullMetricFilterDsl(MetricFilterOperator.IS_NULL, "region")));

        String sql = compiler.renderValidatedFilter(binding(), filter, bindings, field -> "p." + field);

        assertEquals("(p.status = ? AND p.region IS NULL)", sql);
        assertBindings(bindings, "APPROVED", Types.VARCHAR);
    }

    /**
     * 场景：过滤渲染所需协作者和输出容器必须存在。
     * 输入：binding、filter、bindings、字段渲染函数依次为 null。
     * 流程：分别调用 renderValidatedFilter。
     * 预期：四种情况均抛出 NullPointerException。
     */
    @Test
    void testRenderValidatedFilterRejectsNullArguments() {
        MetricJdbcSqlCompiler compiler = compiler();
        MetricFilterDsl filter = new NullMetricFilterDsl(MetricFilterOperator.IS_NULL, "status");
        List<MetricJdbcParameterBinding> bindings = new ArrayList<>();

        assertThrows(NullPointerException.class,
                () -> compiler.renderValidatedFilter(null, filter, bindings, field -> "p." + field));
        assertThrows(NullPointerException.class,
                () -> compiler.renderValidatedFilter(binding(), null, bindings, field -> "p." + field));
        assertThrows(NullPointerException.class,
                () -> compiler.renderValidatedFilter(binding(), filter, null, field -> "p." + field));
        assertThrows(NullPointerException.class,
                () -> compiler.renderValidatedFilter(binding(), filter, bindings, null));
    }

    // 测试夹具

    private static MetricJdbcSqlCompiler compiler() {
        return new MetricJdbcSqlCompiler(UTC);
    }

    private static MetricQuery query() {
        return new MetricQuery(null, START, END, Map.of(), Map.of());
    }

    private static MetricQuery query(String subjectId) {
        return new MetricQuery(subjectId, START, END, Map.of(), Map.of());
    }

    private static MetricQuery query(Map<String, Object> dimensions) {
        return new MetricQuery(null, START, END, dimensions, Map.of());
    }

    private static MetricQuery query(Map<String, Object> dimensions, Map<String, Object> parameters) {
        return new MetricQuery(null, START, END, dimensions, parameters);
    }

    private static Definition definition() {
        return new Definition();
    }

    private static MetricValueDsl value(MetricMeasureDsl measure) {
        return new MetricValueDsl(MetricValueType.DECIMAL, null, null, measure, null,
                new MetricOrElseDsl(MetricOrElseMode.NULL, null));
    }

    private static MetricMeasureDsl measure(MetricAggregation aggregation, String field, MetricFilterDsl filter) {
        return new MetricMeasureDsl(aggregation, field, filter);
    }

    private static MetricQueryParameterDsl parameter(Integer minimum, Integer maximum) {
        return new MetricQueryParameterDsl(MetricValueType.INTEGER, minimum, maximum);
    }

    private static MetricJoinDsl join(MetricJoinType joinType) {
        return new MetricJoinDsl("c", "customer_fact", joinType, MetricJoinCardinality.MANY_TO_ONE,
                List.of(new MetricJoinOnDsl("customer_id", "id")));
    }

    private static MetricJdbcMapping binding() {
        return new Binding()
                .table("", "order_fact")
                .column("created_at", "created_at").javaType("created_at", Instant.class)
                .jdbcType("created_at", Types.TIMESTAMP)
                .column("amount", "amount").javaType("amount", BigDecimal.class)
                .jdbcType("amount", Types.DECIMAL)
                .column("user_id", "user_id").javaType("user_id", String.class)
                .jdbcType("user_id", Types.VARCHAR)
                .column("region", "region").javaType("region", String.class)
                .jdbcType("region", Types.VARCHAR)
                .column("channel", "channel").javaType("channel", String.class)
                .jdbcType("channel", Types.VARCHAR)
                .column("status", "status").javaType("status", String.class)
                .jdbcType("status", Types.VARCHAR)
                .build();
    }

    private static MetricJdbcMapping joinBinding() {
        return new Binding()
                .table("", "order_fact")
                .column("created_at", "created_at").javaType("created_at", Instant.class)
                .jdbcType("created_at", Types.TIMESTAMP)
                .column("customer_id", "customer_id")
                .table("c", "customer_fact")
                .column("c.id", "id")
                .build();
    }

    private static void assertValidation(MetricErrorCode code, String path, Runnable action) {
        MetricValidationException error = assertThrows(MetricValidationException.class, action::run);
        assertEquals(code, error.errorCode());
        assertEquals(path, error.fieldPath());
    }

    private static void assertBindings(List<MetricJdbcParameterBinding> actual, Object... expected) {
        assertEquals(expected.length / 2, actual.size(), "binding count");
        for (int i = 0; i < actual.size(); i++) {
            assertEquals(expected[i * 2], actual.get(i).value(), "binding[" + i + "] value");
            assertEquals(expected[i * 2 + 1], actual.get(i).jdbcType(), "binding[" + i + "] jdbcType");
        }
    }

    /**
     * {@link MetricDSLDefinition} 的构建器，默认是一个全局 SCALAR COUNT 指标。
     */
    private static final class Definition {

        private String fact = "order_fact";

        private MetricValueShape shape = MetricValueShape.SCALAR;

        private MetricSubjectDsl subject = new MetricSubjectDsl(MetricSubjectDsl.GLOBAL, null);

        private MetricTimeDsl time = new MetricTimeDsl("created_at");

        private List<String> dimensions = List.of();

        private Map<String, MetricQueryParameterDsl> parameters = Map.of();

        private MetricRowSelectionDsl rowSelection;

        private MetricValueDsl scalarValue =
                MetricJdbcSqlCompilerTests.value(measure(MetricAggregation.COUNT, null, null));

        private Map<String, MetricValueDsl> fields = Map.of();

        private List<MetricJoinDsl> joins = List.of();

        Definition fact(String fact) {
            this.fact = fact;
            return this;
        }

        Definition shape(MetricValueShape shape) {
            this.shape = shape;
            return this;
        }

        Definition subject(MetricSubjectDsl subject) {
            this.subject = subject;
            return this;
        }

        Definition dimensions(List<String> dimensions) {
            this.dimensions = dimensions;
            return this;
        }

        Definition parameters(Map<String, MetricQueryParameterDsl> parameters) {
            this.parameters = parameters;
            return this;
        }

        Definition rowSelection(MetricRowSelectionDsl rowSelection) {
            this.rowSelection = rowSelection;
            return this;
        }

        Definition value(MetricValueDsl value) {
            this.scalarValue = value;
            return this;
        }

        Definition fields(Map<String, MetricValueDsl> fields) {
            this.fields = fields;
            return this;
        }

        Definition joins(List<MetricJoinDsl> joins) {
            this.joins = joins;
            return this;
        }

        MetricDSLDefinition build() {
            return new MetricDSLDefinition("metric_code", 1, shape, fact, joins, subject, time, dimensions,
                    parameters, rowSelection, scalarValue, fields);
        }
    }

    /**
     * 构建 {@link MetricJdbcMapping} 内存版测试替身的构建器，按字段引用返回冻结的物理映射。
     */
    private static final class Binding {

        private final Map<String, String> tables = new HashMap<>();

        private final Map<String, String> columns = new HashMap<>();

        private final Map<String, Class<?>> javaTypes = new HashMap<>();

        private final Map<String, Integer> jdbcTypes = new HashMap<>();

        Binding table(String reference, String name) {
            tables.put(reference, name);
            return this;
        }

        Binding column(String field, String name) {
            columns.put(field, name);
            return this;
        }

        Binding javaType(String field, Class<?> type) {
            javaTypes.put(field, type);
            return this;
        }

        Binding jdbcType(String field, int type) {
            jdbcTypes.put(field, type);
            return this;
        }

        MetricJdbcMapping build() {
            return new MetricJdbcMapping() {
                @Override
                public String tableName(String factReference) {
                    return tables.get(factReference);
                }

                @Override
                public String columnName(String fieldReference) {
                    return columns.get(fieldReference);
                }

                @Override
                public Class<?> javaType(String fieldReference) {
                    return javaTypes.get(fieldReference);
                }

                @Override
                public int jdbcType(String fieldReference) {
                    return jdbcTypes.get(fieldReference);
                }

                @Override
                public Object toJdbcValue(String fieldReference, Object normalizedValue) {
                    return normalizedValue;
                }
            };
        }
    }
}
