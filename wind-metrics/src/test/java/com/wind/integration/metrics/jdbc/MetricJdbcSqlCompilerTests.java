package com.wind.integration.metrics.jdbc;

import static com.wind.integration.metrics.jdbc.MetricJdbcTestFixtures.binding;

import com.wind.integration.metrics.MetricValidationException;
import com.wind.integration.metrics.json.MetricDefinitionDslCodec;
import com.wind.integration.metrics.spec.MetricDefinitionSpec.MetricDSLDefinitionSpec;
import com.wind.integration.metrics.spec.MetricDSLDefinition;
import com.wind.integration.metrics.dsl.filter.ComparisonMetricFilterDsl;
import com.wind.integration.metrics.dsl.filter.LogicalMetricFilterDsl;
import com.wind.integration.metrics.dsl.filter.NullMetricFilterDsl;
import com.wind.integration.metrics.dsl.filter.SetMetricFilterDsl;
import com.wind.integration.metrics.dsl.literal.StringMetricLiteralDsl;
import com.wind.integration.metrics.enums.MetricErrorCode;
import com.wind.integration.metrics.enums.MetricFilterOperator;
import com.wind.integration.metrics.query.MetricQuery;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;

import java.sql.Types;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;
import java.util.function.BiFunction;

/**
 * REALTIME 指标 SQL 确定性编译测试。
 *
 * @author wuxp
 * @since 2026-07-23
 */
class MetricJdbcSqlCompilerTests {

    /** 指标测试统一使用的系统时区。 */
    private static final ZoneId METRIC_TIME_ZONE = ZoneId.of("Asia/Shanghai");

    /** 验证 FIELD_SET 使用一条 SQL、稳定投影和有序 JDBC bindings，业务值不进入 SQL 文本。 */
    @Test
    void testCompileFieldSetWithJoinAndConditionalMeasures() throws Exception {
        MetricDSLDefinition definition = prepared(MetricJdbcTestFixtures.fieldSetDefinition());
        MetricJdbcSqlCompiler sqlCompiler = new MetricJdbcSqlCompiler(METRIC_TIME_ZONE);
        LocalDateTime startTime = LocalDateTime.of(2026, 7, 1, 0, 0);
        LocalDateTime endTime = LocalDateTime.of(2026, 8, 1, 0, 0);
        String subjectId = "customer' OR 1=1 --";
        String region = "APAC'/*";

        CompiledMetricSql compiled =
                sqlCompiler.compile(
                        definition,
                        new MetricQuery(
                                subjectId, startTime, endTime, Map.of("region", region), Map.of()),
                        binding(definition));

        assertSqlEquivalent(
                "SELECT SUM(CASE WHEN `p`.`status` = ? THEN `p`.`amount` END) "
                        + "AS `approvedAmount`, COUNT(CASE WHEN `p`.`is_refunded` = ? THEN 1 END) "
                        + "AS `refundedCount` FROM `t_metric_order_fact` `p` LEFT JOIN "
                        + "`t_metric_customer_fact` `j0` ON `p`.`customer_id` = `j0`.`customer_id` "
                        + "WHERE `p`.`customer_id` = ? AND `p`.`occurred_at` >= ? "
                        + "AND `p`.`occurred_at` < ? AND `p`.`region` = ?",
                compiled.sql());
        Assertions.assertEquals(
                Map.of(
                        "approvedAmount", "approvedAmount",
                        "refundedCount", "refundedCount"),
                compiled.projections());
        Assertions.assertEquals(
                List.of(
                        new MetricSqlBinding("APPROVED", Types.VARCHAR),
                        new MetricSqlBinding(true, Types.BOOLEAN),
                        new MetricSqlBinding(subjectId, Types.VARCHAR),
                        new MetricSqlBinding(startTime, Types.TIMESTAMP),
                        new MetricSqlBinding(endTime, Types.TIMESTAMP),
                        new MetricSqlBinding(region, Types.VARCHAR)),
                compiled.bindings());
        Assertions.assertFalse(compiled.sql().contains(subjectId));
        Assertions.assertFalse(compiled.sql().contains(region));
    }

    /** 验证表达式字段不进入 SQL 投影，只查询其依赖的基础 measure 字段。 */
    @Test
    void testCompileOnlyMeasureProjectionsForExpressionMetric() throws Exception {
        MetricDSLDefinition definition = prepared(MetricJdbcTestFixtures.factRatioDefinition());
        LocalDateTime startTime = LocalDateTime.of(2026, 7, 1, 0, 0);
        LocalDateTime endTime = LocalDateTime.of(2026, 8, 1, 0, 0);

        CompiledMetricSql compiled =
                new MetricJdbcSqlCompiler(METRIC_TIME_ZONE)
                        .compile(
                                definition,
                                new MetricQuery(
                                        "customer-1",
                                        startTime,
                                        endTime,
                                        Map.of("region", "APAC"),
                                        Map.of()),
                                binding(definition));

        Assertions.assertEquals(
                Map.of(
                        "approvedCount", "approvedCount",
                        "totalCount", "totalCount"),
                compiled.projections());
        Assertions.assertFalse(compiled.sql().contains("approvalRate"));
    }

    /** 验证维度键必须和 Definition 精确一致，缺失或多余均失败关闭。 */
    @Test
    void testRejectMismatchedDimensionKeys() throws Exception {
        MetricDSLDefinition definition = prepared(MetricJdbcTestFixtures.scalarCountDefinition());
        MetricJdbcSqlCompiler sqlCompiler = new MetricJdbcSqlCompiler(METRIC_TIME_ZONE);
        LocalDateTime startTime = LocalDateTime.of(2026, 7, 1, 0, 0);
        LocalDateTime endTime = LocalDateTime.of(2026, 8, 1, 0, 0);

        MetricValidationException exception =
                Assertions.assertThrows(
                        MetricValidationException.class,
                        () ->
                                sqlCompiler.compile(
                                        definition,
                                        new MetricQuery(
                                                "customer-1",
                                                startTime,
                                                endTime,
                                                Map.of("unexpected", "APAC"),
                                                Map.of()),
                                        binding(definition)));

        Assertions.assertEquals(MetricErrorCode.QUERY_INVALID, exception.errorCode());
        Assertions.assertEquals("/dimensionValues", exception.fieldPath());
    }

    /** 验证 GLOBAL 指标不生成主体条件，并拒绝查询携带 subjectId。 */
    @Test
    void testCompileGlobalMetricWithoutSubjectCondition() throws Exception {
        String json =
                new MetricDefinitionDslCodec()
                        .canonicalize(MetricJdbcTestFixtures.scalarCountDefinition())
                        .replace(
                                "\"subject\":{\"type\":\"CUSTOMER\",\"field\":\"customerId\"}",
                                "\"subject\":{\"type\":\"GLOBAL\"}");
        MetricDSLDefinition definition = prepared(MetricJdbcTestFixtures.parse(json));
        LocalDateTime startTime = LocalDateTime.of(2026, 7, 1, 0, 0);
        LocalDateTime endTime = LocalDateTime.of(2026, 8, 1, 0, 0);

        CompiledMetricSql compiled =
                new MetricJdbcSqlCompiler(METRIC_TIME_ZONE)
                        .compile(
                                definition,
                                new MetricQuery(
                                        null,
                                        startTime,
                                        endTime,
                                        Map.of("region", "APAC"),
                                        Map.of()),
                                binding(definition));

        assertSqlEquivalent(
                "SELECT COUNT(*) AS `value` FROM `t_metric_order_fact` `p` WHERE `p`.`occurred_at`"
                    + " >= ? AND `p`.`occurred_at` < ? AND `p`.`region` = ?",
                compiled.sql());
        Assertions.assertEquals(
                List.of(
                        new MetricSqlBinding(startTime, Types.TIMESTAMP),
                        new MetricSqlBinding(endTime, Types.TIMESTAMP),
                        new MetricSqlBinding("APAC", Types.VARCHAR)),
                compiled.bindings());
    }

    /** 验证查询维度 Map 的插入顺序不影响 SQL 和绑定顺序。 */
    @Test
    void testCompileDeterministicallyAcrossDimensionMapOrder() throws Exception {
        String json =
                new MetricDefinitionDslCodec()
                        .canonicalize(MetricJdbcTestFixtures.scalarCountDefinition())
                        .replace(
                                "\"dimensions\":[\"region\"]",
                                "\"dimensions\":[\"region\",\"quantity\"]");
        MetricDSLDefinition definition = prepared(MetricJdbcTestFixtures.parse(json));
        MetricJdbcSqlCompiler compiler = new MetricJdbcSqlCompiler(METRIC_TIME_ZONE);
        LocalDateTime startTime = LocalDateTime.of(2026, 7, 1, 0, 0);
        LocalDateTime endTime = LocalDateTime.of(2026, 8, 1, 0, 0);
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("region", "APAC");
        first.put("quantity", 2);
        Map<String, Object> second = new LinkedHashMap<>();
        second.put("quantity", 2);
        second.put("region", "APAC");

        CompiledMetricSql firstCompiled =
                compiler.compile(
                        definition,
                        new MetricQuery("customer-1", startTime, endTime, first, Map.of()),
                        binding(definition));
        CompiledMetricSql secondCompiled =
                compiler.compile(
                        definition,
                        new MetricQuery("customer-1", startTime, endTime, second, Map.of()),
                        binding(definition));

        Assertions.assertEquals(firstCompiled, secondCompiled);
    }

    /** 验证基础编译链对尚未接入的运行时参数失败关闭。 */
    @Test
    void testRejectUnexpectedQueryParameter() throws Exception {
        MetricDSLDefinition definition = prepared(MetricJdbcTestFixtures.scalarCountDefinition());
        LocalDateTime startTime = LocalDateTime.of(2026, 7, 1, 0, 0);
        LocalDateTime endTime = LocalDateTime.of(2026, 8, 1, 0, 0);

        MetricValidationException exception =
                Assertions.assertThrows(
                        MetricValidationException.class,
                        () ->
                                new MetricJdbcSqlCompiler(METRIC_TIME_ZONE)
                                        .compile(
                                                definition,
                                                new MetricQuery(
                                                        "customer-1",
                                                        startTime,
                                                        endTime,
                                                        Map.of("region", "APAC"),
                                                        Map.of("limit", 10)),
                                                binding(definition)));

        Assertions.assertEquals(MetricErrorCode.METRIC_PARAMETER_UNEXPECTED, exception.errorCode());
        Assertions.assertEquals("/parameterValues/limit", exception.fieldPath());
    }

    /** 验证整数主体标识从查询字符串严格反解为事实字段类型。 */
    @Test
    void testCompileNumericSubjectId() throws Exception {
        String json =
                new MetricDefinitionDslCodec()
                        .canonicalize(MetricJdbcTestFixtures.scalarCountDefinition())
                        .replace("\"field\":\"customerId\"", "\"field\":\"customerNumber\"");
        MetricDSLDefinition definition = prepared(MetricJdbcTestFixtures.parse(json));
        LocalDateTime startTime = LocalDateTime.of(2026, 7, 1, 0, 0);
        LocalDateTime endTime = LocalDateTime.of(2026, 8, 1, 0, 0);

        CompiledMetricSql compiled =
                new MetricJdbcSqlCompiler(METRIC_TIME_ZONE)
                        .compile(
                                definition,
                                new MetricQuery(
                                        "42",
                                        startTime,
                                        endTime,
                                        Map.of("region", "APAC"),
                                        Map.of()),
                                binding(definition));

        Assertions.assertEquals(
                new MetricSqlBinding(42, Types.INTEGER), compiled.bindings().getFirst());
        MetricValidationException exception =
                Assertions.assertThrows(
                        MetricValidationException.class,
                        () ->
                                new MetricJdbcSqlCompiler(METRIC_TIME_ZONE)
                                        .compile(
                                                definition,
                                                new MetricQuery(
                                                        "042",
                                                        startTime,
                                                        endTime,
                                                        Map.of("region", "APAC"),
                                                        Map.of()),
                                                binding(definition)));
        Assertions.assertEquals(MetricErrorCode.QUERY_INVALID, exception.errorCode());
        Assertions.assertEquals("/subjectId", exception.fieldPath());
    }

    /** 验证字符串主体标识不允许首尾空白，避免查询合法但错误的主体。 */
    @Test
    void testRejectStringSubjectIdWithSurroundingWhitespace() throws Exception {
        MetricDSLDefinition definition = prepared(MetricJdbcTestFixtures.scalarCountDefinition());
        LocalDateTime startTime = LocalDateTime.of(2026, 7, 1, 0, 0);
        LocalDateTime endTime = LocalDateTime.of(2026, 8, 1, 0, 0);

        MetricValidationException exception =
                Assertions.assertThrows(
                        MetricValidationException.class,
                        () ->
                                new MetricJdbcSqlCompiler(METRIC_TIME_ZONE)
                                        .compile(
                                                definition,
                                                new MetricQuery(
                                                        " customer-1",
                                                        startTime,
                                                        endTime,
                                                        Map.of("region", "APAC"),
                                                        Map.of()),
                                                binding(definition)));

        Assertions.assertEquals(MetricErrorCode.QUERY_INVALID, exception.errorCode());
        Assertions.assertEquals("/subjectId", exception.fieldPath());
    }

    /** 验证带 offset 的时间 literal 按系统时区归一后绑定，不依赖数据库字符串转换。 */
    @Test
    void testCompileTemporalFilterLiteral() throws Exception {
        String json =
                new MetricDefinitionDslCodec()
                        .canonicalize(MetricJdbcTestFixtures.scalarCountDefinition())
                        .replace(
                                "\"measure\":{\"aggregation\":\"COUNT\"}",
                                "\"measure\":{\"aggregation\":\"COUNT\",\"filter\":{\"ge\":{"
                                        + "\"occurredAt\":\"2026-07-01T00:00:00Z\"}}}");
        MetricDSLDefinition definition = prepared(MetricJdbcTestFixtures.parse(json));
        LocalDateTime startTime = LocalDateTime.of(2026, 7, 1, 0, 0);
        LocalDateTime endTime = LocalDateTime.of(2026, 8, 1, 0, 0);

        ZoneId metricTimeZone = ZoneId.of("Asia/Shanghai");
        TimeZone previous = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            CompiledMetricSql utcJvm =
                    new MetricJdbcSqlCompiler(metricTimeZone)
                            .compile(
                                    definition,
                                    new MetricQuery(
                                            "customer-1",
                                            startTime,
                                            endTime,
                                            Map.of("region", "APAC"),
                                            Map.of()),
                                    binding(definition));
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
            CompiledMetricSql newYorkJvm =
                    new MetricJdbcSqlCompiler(metricTimeZone)
                            .compile(
                                    definition,
                                    new MetricQuery(
                                            "customer-1",
                                            startTime,
                                            endTime,
                                            Map.of("region", "APAC"),
                                            Map.of()),
                                    binding(definition));

            LocalDateTime expected =
                    OffsetDateTime.parse("2026-07-01T00:00:00Z")
                            .atZoneSameInstant(metricTimeZone)
                            .toLocalDateTime();
            Assertions.assertEquals(utcJvm.bindings(), newYorkJvm.bindings());
            Assertions.assertEquals(
                    new MetricSqlBinding(expected, Types.TIMESTAMP), utcJvm.bindings().getFirst());
        } finally {
            TimeZone.setDefault(previous);
        }
    }

    /** 验证时间维度按字段 Java 类型和指标系统时区归一，不把 OffsetDateTime 原样交给驱动。 */
    @Test
    void testNormalizeTemporalDimensionByFactFieldTypeAndMetricTimeZone() throws Exception {
        String json =
                new MetricDefinitionDslCodec()
                        .canonicalize(MetricJdbcTestFixtures.scalarCountDefinition())
                        .replace("\"dimensions\":[\"region\"]", "\"dimensions\":[\"occurredAt\"]");
        MetricDSLDefinition definition = prepared(MetricJdbcTestFixtures.parse(json));
        OffsetDateTime dimensionValue = OffsetDateTime.parse("2026-07-01T00:00:00Z");

        CompiledMetricSql compiled =
                new MetricJdbcSqlCompiler(METRIC_TIME_ZONE)
                        .compile(
                                definition,
                                new MetricQuery(
                                        "customer-1",
                                        LocalDateTime.of(2026, 7, 1, 0, 0),
                                        LocalDateTime.of(2026, 8, 1, 0, 0),
                                        Map.of("occurredAt", dimensionValue),
                                        Map.of()),
                                binding(definition));

        Assertions.assertEquals(
                new MetricSqlBinding(LocalDateTime.of(2026, 7, 1, 8, 0), Types.TIMESTAMP),
                compiled.bindings().getLast());
    }

    /** 验证 AVG、MAX、MIN 聚合共享主体、时间和维度条件，并按 valueField 稳定生成投影。 */
    @Test
    void testCompileAverageMaximumAndMinimumAggregations() throws Exception {
        MetricDSLDefinition definition =
                prepared(MetricJdbcTestFixtures.aggregateFieldSetDefinition());
        LocalDateTime startTime = LocalDateTime.of(2026, 7, 1, 0, 0);
        LocalDateTime endTime = LocalDateTime.of(2026, 8, 1, 0, 0);

        CompiledMetricSql compiled =
                new MetricJdbcSqlCompiler(METRIC_TIME_ZONE)
                        .compile(
                                definition,
                                new MetricQuery(
                                        "customer-1",
                                        startTime,
                                        endTime,
                                        Map.of("region", "APAC"),
                                        Map.of()),
                                binding(definition));

        assertSqlEquivalent(
                "SELECT AVG(`p`.`amount`) AS `averageAmount`, MAX(`p`.`quantity`) AS"
                    + " `maximumQuantity`, MIN(`p`.`quantity`) AS `minimumQuantity` FROM"
                    + " `t_metric_order_fact` `p` WHERE `p`.`customer_id` = ? AND `p`.`occurred_at`"
                    + " >= ? AND `p`.`occurred_at` < ? AND `p`.`region` = ?",
                compiled.sql());
        Assertions.assertEquals(
                Map.of(
                        "averageAmount", "averageAmount",
                        "maximumQuantity", "maximumQuantity",
                        "minimumQuantity", "minimumQuantity"),
                compiled.projections());
        Assertions.assertEquals(
                List.of(
                        new MetricSqlBinding("customer-1", Types.VARCHAR),
                        new MetricSqlBinding(startTime, Types.TIMESTAMP),
                        new MetricSqlBinding(endTime, Types.TIMESTAMP),
                        new MetricSqlBinding("APAC", Types.VARCHAR)),
                compiled.bindings());
    }

    /** 验证参数化行选择先形成稳定的最早 N 条共享行集，再由 FIELD_SET 一次完成聚合。 */
    @Test
    void testCompileParameterizedRowSelection() throws Exception {
        MetricDSLDefinition definition =
                prepared(MetricJdbcTestFixtures.parameterizedRowSelectionDefinition());
        LocalDateTime startTime = LocalDateTime.of(2026, 7, 1, 0, 0);
        LocalDateTime endTime = LocalDateTime.of(2026, 8, 1, 0, 0);

        CompiledMetricSql compiled =
                new MetricJdbcSqlCompiler(METRIC_TIME_ZONE)
                        .compile(
                                definition,
                                new MetricQuery(
                                        "customer-1",
                                        startTime,
                                        endTime,
                                        Map.of("region", "APAC"),
                                        Map.of("entryLimit", 3)),
                                binding(definition));

        assertSqlEquivalent(
                "SELECT SUM(`r`.`c0`) AS `approvedAmount`, COUNT(*) AS `approvedCount` "
                        + "FROM (SELECT `p`.`amount` AS `c0` FROM `t_metric_order_fact` `p` "
                        + "WHERE `p`.`customer_id` = ? AND `p`.`occurred_at` >= ? "
                        + "AND `p`.`occurred_at` < ? AND `p`.`region` = ? "
                        + "AND (`p`.`status` = ? AND `p`.`is_refunded` = ?) "
                        + "ORDER BY `p`.`occurred_at` ASC, `p`.`id` ASC LIMIT ?) `r`",
                compiled.sql());
        Assertions.assertEquals(
                List.of(
                        new MetricSqlBinding("customer-1", Types.VARCHAR),
                        new MetricSqlBinding(startTime, Types.TIMESTAMP),
                        new MetricSqlBinding(endTime, Types.TIMESTAMP),
                        new MetricSqlBinding("APAC", Types.VARCHAR),
                        new MetricSqlBinding("APPROVED", Types.VARCHAR),
                        new MetricSqlBinding(false, Types.BOOLEAN),
                        new MetricSqlBinding(3, Types.INTEGER)),
                compiled.bindings());
        Assertions.assertEquals(
                Map.of(
                        "approvedAmount", "approvedAmount",
                        "approvedCount", "approvedCount"),
                compiled.projections());
    }

    /** 外层 measure 过滤参数必须排在行集过滤和参数化 LIMIT 之前。 */
    @Test
    void testCompileConditionalMeasuresWithinParameterizedRowSelection() {
        MetricDSLDefinition source = MetricJdbcTestFixtures.parameterizedRowSelectionDefinition().definition();
        MetricDSLDefinition definition = new MetricDSLDefinition(
                source.code(), source.valueShape(), source.fact(), source.joins(), source.subject(), source.time(),
                source.dimensions(), source.parameters(), source.rowSelection(), source.value(),
                MetricJdbcTestFixtures.fieldSetDefinition().definition().fields());
        LocalDateTime startTime = LocalDateTime.of(2026, 7, 1, 0, 0);
        LocalDateTime endTime = LocalDateTime.of(2026, 8, 1, 0, 0);

        CompiledMetricSql compiled = new MetricJdbcSqlCompiler(METRIC_TIME_ZONE).compile(
                definition,
                new MetricQuery("customer-1", startTime, endTime, Map.of("region", "APAC"), Map.of("entryLimit", 3)),
                binding(definition));

        assertSqlEquivalent(
                "SELECT SUM(CASE WHEN `r`.`c2` = ? THEN `r`.`c0` END) AS `approvedAmount`, "
                        + "COUNT(CASE WHEN `r`.`c1` = ? THEN 1 END) AS `refundedCount` "
                        + "FROM (SELECT `p`.`amount` AS `c0`, `p`.`is_refunded` AS `c1`, `p`.`status` AS `c2` "
                        + "FROM `t_metric_order_fact` `p` WHERE `p`.`customer_id` = ? "
                        + "AND `p`.`occurred_at` >= ? AND `p`.`occurred_at` < ? AND `p`.`region` = ? "
                        + "AND (`p`.`status` = ? AND `p`.`is_refunded` = ?) "
                        + "ORDER BY `p`.`occurred_at` ASC, `p`.`id` ASC LIMIT ?) `r`",
                compiled.sql());
        Assertions.assertEquals(List.of(
                new MetricSqlBinding("APPROVED", Types.VARCHAR),
                new MetricSqlBinding(true, Types.BOOLEAN),
                new MetricSqlBinding("customer-1", Types.VARCHAR),
                new MetricSqlBinding(startTime, Types.TIMESTAMP),
                new MetricSqlBinding(endTime, Types.TIMESTAMP),
                new MetricSqlBinding("APAC", Types.VARCHAR),
                new MetricSqlBinding("APPROVED", Types.VARCHAR),
                new MetricSqlBinding(false, Types.BOOLEAN),
                new MetricSqlBinding(3, Types.INTEGER)), compiled.bindings());
        Assertions.assertEquals(
                Map.of("approvedAmount", "approvedAmount", "refundedCount", "refundedCount"), compiled.projections());
    }

    /** 纯 COUNT 的有限行集仍选择有效列，并允许 LIMIT 等于编译器上限。 */
    @Test
    void testCompileCountRowSelectionAtSystemMaximum() {
        MetricDSLDefinition source = MetricJdbcTestFixtures.scalarCountDefinition().definition();
        MetricDSLDefinition definition = new MetricDSLDefinition(
                source.code(), source.valueShape(), source.fact(), source.joins(), source.subject(), source.time(),
                source.dimensions(), source.parameters(),
                MetricJdbcTestFixtures.fixedRowSelectionDefinition(10).definition().rowSelection(),
                source.value(), source.fields());
        LocalDateTime startTime = LocalDateTime.of(2026, 7, 1, 0, 0);
        LocalDateTime endTime = LocalDateTime.of(2026, 8, 1, 0, 0);

        CompiledMetricSql compiled = new MetricJdbcSqlCompiler(METRIC_TIME_ZONE, 10).compile(
                definition, new MetricQuery("customer-1", startTime, endTime, Map.of("region", "APAC"), Map.of()),
                binding(definition));

        assertSqlEquivalent(
                "SELECT COUNT(*) AS `value` FROM (SELECT `p`.`occurred_at` AS `c0` "
                        + "FROM `t_metric_order_fact` `p` WHERE `p`.`customer_id` = ? "
                        + "AND `p`.`occurred_at` >= ? AND `p`.`occurred_at` < ? AND `p`.`region` = ? "
                        + "AND (`p`.`status` = ? AND `p`.`is_refunded` = ?) "
                        + "ORDER BY `p`.`occurred_at` ASC, `p`.`id` ASC LIMIT ?) `r`",
                compiled.sql());
        Assertions.assertEquals(List.of(
                new MetricSqlBinding("customer-1", Types.VARCHAR),
                new MetricSqlBinding(startTime, Types.TIMESTAMP),
                new MetricSqlBinding(endTime, Types.TIMESTAMP),
                new MetricSqlBinding("APAC", Types.VARCHAR),
                new MetricSqlBinding("APPROVED", Types.VARCHAR),
                new MetricSqlBinding(false, Types.BOOLEAN),
                new MetricSqlBinding(10, Types.INTEGER)), compiled.bindings());
        Assertions.assertEquals(Map.of("value", "value"), compiled.projections());
    }

    /** 验证 rowSelection 引用的查询参数缺失时在 SQL 生成前失败关闭。 */
    @Test
    void testRejectMissingRowSelectionParameter() throws Exception {
        MetricDSLDefinition definition =
                prepared(MetricJdbcTestFixtures.parameterizedRowSelectionDefinition());
        LocalDateTime startTime = LocalDateTime.of(2026, 7, 1, 0, 0);
        LocalDateTime endTime = LocalDateTime.of(2026, 8, 1, 0, 0);

        MetricValidationException exception =
                Assertions.assertThrows(
                        MetricValidationException.class,
                        () ->
                                new MetricJdbcSqlCompiler(METRIC_TIME_ZONE)
                                        .compile(
                                                definition,
                                                new MetricQuery(
                                                        "customer-1",
                                                        startTime,
                                                        endTime,
                                                        Map.of("region", "APAC"),
                                                        Map.of()),
                                                binding(definition)));

        Assertions.assertEquals(MetricErrorCode.METRIC_PARAMETER_MISSING, exception.errorCode());
        Assertions.assertEquals("/parameterValues/entryLimit", exception.fieldPath());
    }

    /** 验证 rowSelection 查询参数严格遵守 Definition 声明的闭区间。 */
    @Test
    void testRejectOutOfRangeRowSelectionParameter() throws Exception {
        MetricDSLDefinition definition =
                prepared(MetricJdbcTestFixtures.parameterizedRowSelectionDefinition());
        LocalDateTime startTime = LocalDateTime.of(2026, 7, 1, 0, 0);
        LocalDateTime endTime = LocalDateTime.of(2026, 8, 1, 0, 0);

        MetricValidationException exception =
                Assertions.assertThrows(
                        MetricValidationException.class,
                        () ->
                                new MetricJdbcSqlCompiler(METRIC_TIME_ZONE)
                                        .compile(
                                                definition,
                                                new MetricQuery(
                                                        "customer-1",
                                                        startTime,
                                                        endTime,
                                                        Map.of("region", "APAC"),
                                                        Map.of("entryLimit", 101)),
                                                binding(definition)));

        Assertions.assertEquals(
                MetricErrorCode.METRIC_PARAMETER_OUT_OF_RANGE, exception.errorCode());
        Assertions.assertEquals("/parameterValues/entryLimit", exception.fieldPath());
    }

    /** 验证查询参数即使满足 Definition 范围，也不能超过系统行选择上限。 */
    @Test
    void testRejectRowSelectionLimitAboveSystemMaximum() throws Exception {
        MetricDSLDefinition definition =
                prepared(MetricJdbcTestFixtures.parameterizedRowSelectionDefinition());
        LocalDateTime startTime = LocalDateTime.of(2026, 7, 1, 0, 0);
        LocalDateTime endTime = LocalDateTime.of(2026, 8, 1, 0, 0);

        MetricValidationException exception =
                Assertions.assertThrows(
                        MetricValidationException.class,
                        () ->
                                new MetricJdbcSqlCompiler(METRIC_TIME_ZONE, 10)
                                        .compile(
                                                definition,
                                                new MetricQuery(
                                                        "customer-1",
                                                        startTime,
                                                        endTime,
                                                        Map.of("region", "APAC"),
                                                        Map.of("entryLimit", 11)),
                                                forbiddenBinding()));

        Assertions.assertEquals(
                MetricErrorCode.METRIC_PARAMETER_OUT_OF_RANGE, exception.errorCode());
        Assertions.assertEquals("/parameterValues/entryLimit", exception.fieldPath());

        MetricDSLDefinition fixedLimitDefinition =
                prepared(MetricJdbcTestFixtures.fixedRowSelectionDefinition(11));
        MetricValidationException fixedLimitException =
                Assertions.assertThrows(
                        MetricValidationException.class,
                        () ->
                                new MetricJdbcSqlCompiler(METRIC_TIME_ZONE, 10)
                                        .compile(
                                                fixedLimitDefinition,
                                                new MetricQuery(
                                                        "customer-1",
                                                        startTime,
                                                        endTime,
                                                        Map.of("region", "APAC"),
                                                        Map.of()),
                                                forbiddenBinding()));

        Assertions.assertEquals(MetricErrorCode.DSL_VALUE_INVALID, fixedLimitException.errorCode());
        Assertions.assertEquals(
                "/metric/rowSelection/limit/value", fixedLimitException.fieldPath());
    }

    /** 验证枚举维度值必须精确匹配事实模型的枚举常量。 */
    @Test
    void testRejectUnknownEnumDimensionValue() throws Exception {
        String json =
                new MetricDefinitionDslCodec()
                        .canonicalize(MetricJdbcTestFixtures.scalarCountDefinition())
                        .replace("\"dimensions\":[\"region\"]", "\"dimensions\":[\"status\"]");
        MetricDSLDefinition definition = prepared(MetricJdbcTestFixtures.parse(json));
        LocalDateTime startTime = LocalDateTime.of(2026, 7, 1, 0, 0);
        LocalDateTime endTime = LocalDateTime.of(2026, 8, 1, 0, 0);

        MetricValidationException exception =
                Assertions.assertThrows(
                        MetricValidationException.class,
                        () ->
                                new MetricJdbcSqlCompiler(METRIC_TIME_ZONE)
                                        .compile(
                                                definition,
                                                new MetricQuery(
                                                        "customer-1",
                                                        startTime,
                                                        endTime,
                                                        Map.of("status", "APPROVE"),
                                                        Map.of()),
                                                binding(definition)));

        Assertions.assertEquals(MetricErrorCode.QUERY_INVALID, exception.errorCode());
        Assertions.assertEquals("/dimensionValues/status", exception.fieldPath());
    }

    /** 验证两个直接 Join 按 alias 排序，复合 Join Key 按主事实字段排序后生成确定性 SQL。 */
    @Test
    void testCompileTwoJoinsWithCompositeKeyDeterministically() throws Exception {
        MetricDSLDefinition definition = prepared(MetricJdbcTestFixtures.doubleJoinDefinition());
        LocalDateTime startTime = LocalDateTime.of(2026, 7, 1, 0, 0);
        LocalDateTime endTime = LocalDateTime.of(2026, 8, 1, 0, 0);

        CompiledMetricSql compiled =
                new MetricJdbcSqlCompiler(METRIC_TIME_ZONE)
                        .compile(
                                definition,
                                new MetricQuery(
                                        "customer-1",
                                        startTime,
                                        endTime,
                                        Map.of("regionInfo.groupName", "ASIA"),
                                        Map.of()),
                                binding(definition));

        assertSqlEquivalent(
                "SELECT COUNT(*) AS `value` FROM `t_metric_order_fact` `p` "
                        + "LEFT JOIN `t_metric_customer_fact` `j0` "
                        + "ON `p`.`customer_id` = `j0`.`customer_id` "
                        + "INNER JOIN `t_metric_region_fact` `j1` "
                        + "ON `p`.`country` = `j1`.`country` AND `p`.`region` = `j1`.`region` "
                        + "WHERE `p`.`customer_id` = ? AND `p`.`occurred_at` >= ? "
                        + "AND `p`.`occurred_at` < ? AND `j1`.`group_name` = ?",
                compiled.sql());
        Assertions.assertEquals(
                List.of(
                        new MetricSqlBinding("customer-1", Types.VARCHAR),
                        new MetricSqlBinding(startTime, Types.TIMESTAMP),
                        new MetricSqlBinding(endTime, Types.TIMESTAMP),
                        new MetricSqlBinding("ASIA", Types.VARCHAR)),
                compiled.bindings());
    }

    @Test
    void testCustomCodecReceivesNormalizedStringUuidAndTimeExactlyOnce() {
        MetricDSLDefinition definition = MetricJdbcTestFixtures.scalarCountDefinition().definition();
        MetricJdbcBinding original = binding(definition);
        String subject = "a0895fa8-9d5f-457a-a03d-4ad42f3eb9ba";
        LocalDateTime start = LocalDateTime.of(2026, 7, 1, 0, 0);
        LocalDateTime end = start.plusDays(1);
        List<String> encoded = new ArrayList<>();
        MetricJdbcBinding customized =
                new CodecBinding(
                        original,
                        Map.of("customerId", UUID.class, "occurredAt", Instant.class),
                        (field, value) -> {
                            encoded.add(field);
                            return switch (field) {
                                case "customerId" -> {
                                    Assertions.assertInstanceOf(UUID.class, value);
                                    yield "uuid:" + value;
                                }
                                case "occurredAt" -> ((Instant) value).toEpochMilli();
                                case "region" -> "region:" + value;
                                default ->
                                        throw new AssertionError(
                                                "Unexpected encoded field: " + field);
                            };
                        });
        CompiledMetricSql compiled =
                new MetricJdbcSqlCompiler(METRIC_TIME_ZONE)
                        .compile(
                                definition,
                                new MetricQuery(
                                        subject, start, end, Map.of("region", "APAC"), Map.of()),
                                customized);
        Assertions.assertEquals(
                List.of("customerId", "occurredAt", "occurredAt", "region"), encoded);
        Assertions.assertEquals(
                List.of(
                        "uuid:" + subject,
                        start.atZone(METRIC_TIME_ZONE).toInstant().toEpochMilli(),
                        end.atZone(METRIC_TIME_ZONE).toInstant().toEpochMilli(),
                        "region:APAC"),
                compiled.bindings().stream().map(MetricSqlBinding::value).toList());
    }

    @Test
    void testCharacterCodecReceivesCharacterForSubjectDimensionAndFilter() {
        MetricDSLDefinition definition = MetricJdbcTestFixtures.scalarCountDefinition().definition();
        MetricJdbcBinding original = binding(definition);
        MetricJdbcBinding customized =
                new CodecBinding(
                        original,
                        Map.of("customerId", Character.class, "region", char.class),
                        (field, value) -> {
                            if (field.equals("customerId") || field.equals("region")) {
                                return "char:"
                                        + Assertions.assertInstanceOf(Character.class, value);
                            }
                            return value;
                        });
        MetricJdbcSqlCompiler compiler = new MetricJdbcSqlCompiler(METRIC_TIME_ZONE);
        LocalDateTime start = LocalDateTime.of(2026, 7, 1, 0, 0);
        CompiledMetricSql compiled =
                compiler.compile(
                        definition,
                        new MetricQuery(
                                "A", start, start.plusDays(1), Map.of("region", 'B'), Map.of()),
                        customized);
        Assertions.assertEquals("char:A", compiled.bindings().getFirst().value());
        Assertions.assertEquals("char:B", compiled.bindings().getLast().value());
        List<MetricSqlBinding> parameters = new ArrayList<>();
        compiler.renderValidatedFilter(
                customized,
                new ComparisonMetricFilterDsl(
                        MetricFilterOperator.EQ, "region", new StringMetricLiteralDsl("C")),
                parameters,
                field -> "`page`.`region`");
        Assertions.assertEquals("char:C", parameters.getFirst().value());
        MetricValidationException exception =
                Assertions.assertThrows(
                        MetricValidationException.class,
                        () ->
                                compiler.compile(
                                        definition,
                                        new MetricQuery(
                                                "AB",
                                                start,
                                                start.plusDays(1),
                                                Map.of("region", 'B'),
                                                Map.of()),
                                        customized));
        Assertions.assertEquals("/subjectId", exception.fieldPath());
    }

    @Test
    void testDerivedDefinitionIsRejectedBeforePhysicalBindingAccess() {
        MetricDSLDefinition definition = MetricJdbcTestFixtures.derivedRatioDefinition().definition();
        MetricValidationException exception =
                Assertions.assertThrows(
                        MetricValidationException.class,
                        () ->
                                new MetricJdbcSqlCompiler(METRIC_TIME_ZONE)
                                        .compile(definition, criteria(), forbiddenBinding()));
        Assertions.assertEquals(
                MetricErrorCode.METRIC_EXECUTION_MODE_UNSUPPORTED, exception.errorCode());
        Assertions.assertEquals("/metric/fact", exception.fieldPath());
    }

    @Test
    void testFilterFailureDoesNotAppendPartialParameters() {
        MetricDSLDefinition definition = MetricJdbcTestFixtures.scalarCountDefinition().definition();
        MetricJdbcBinding customized =
                new CodecBinding(
                        binding(definition),
                        Map.of("customerId", UUID.class),
                        (field, value) -> value);
        List<MetricSqlBinding> parameters =
                new ArrayList<>(List.of(new MetricSqlBinding("existing", Types.VARCHAR)));
        LogicalMetricFilterDsl filter =
                new LogicalMetricFilterDsl(
                        MetricFilterOperator.AND,
                        List.of(
                                new ComparisonMetricFilterDsl(
                                        MetricFilterOperator.EQ,
                                        "region",
                                        new StringMetricLiteralDsl("APAC")),
                                new ComparisonMetricFilterDsl(
                                        MetricFilterOperator.EQ,
                                        "customerId",
                                        new StringMetricLiteralDsl("bad"))));
        Assertions.assertThrows(
                MetricValidationException.class,
                () ->
                        new MetricJdbcSqlCompiler(METRIC_TIME_ZONE)
                                .renderValidatedFilter(
                                        customized,
                                        filter,
                                        parameters,
                                        field -> "`page`.`" + field + "`"));
        Assertions.assertEquals(
                List.of(new MetricSqlBinding("existing", Types.VARCHAR)), parameters);
    }

    @Test
    void testSharedFilterRendersOrderedSetsNullsAndNestedLogic() {
        LogicalMetricFilterDsl filter =
                new LogicalMetricFilterDsl(
                        MetricFilterOperator.AND,
                        List.of(
                                new SetMetricFilterDsl(
                                        MetricFilterOperator.NOT_IN,
                                        "region",
                                        List.of(
                                                new StringMetricLiteralDsl("APAC'"),
                                                new StringMetricLiteralDsl("EU"))),
                                new LogicalMetricFilterDsl(
                                        MetricFilterOperator.OR,
                                        List.of(
                                                new NullMetricFilterDsl(
                                                        MetricFilterOperator.IS_NULL, "customerId"),
                                                new ComparisonMetricFilterDsl(
                                                        MetricFilterOperator.NE,
                                                        "customerId",
                                                        new StringMetricLiteralDsl("excluded"))))));
        List<MetricSqlBinding> parameters = new ArrayList<>();
        String sql =
                new MetricJdbcSqlCompiler(METRIC_TIME_ZONE)
                        .renderValidatedFilter(
                                binding(MetricJdbcTestFixtures.scalarCountDefinition().definition()),
                                filter,
                                parameters,
                                field -> "`page`.`" + field + "`");
        Assertions.assertEquals(
                "(`page`.`region` NOT IN (?, ?) AND (`page`.`customerId` IS NULL "
                        + "OR `page`.`customerId` <> ?))",
                sql);
        Assertions.assertEquals(
                List.of("APAC'", "EU", "excluded"),
                parameters.stream().map(MetricSqlBinding::value).toList());
    }

    @Test
    void testCodecFailureReportsTheQueryField() {
        MetricDSLDefinition definition = MetricJdbcTestFixtures.scalarCountDefinition().definition();
        MetricJdbcBinding broken =
                new CodecBinding(
                        binding(definition),
                        Map.of(),
                        (field, value) -> {
                            throw new IllegalArgumentException("Cannot encode");
                        });
        MetricValidationException exception =
                Assertions.assertThrows(
                        MetricValidationException.class,
                        () ->
                                new MetricJdbcSqlCompiler(METRIC_TIME_ZONE)
                                        .compile(definition, criteria(), broken));
        Assertions.assertEquals(MetricErrorCode.QUERY_INVALID, exception.errorCode());
        Assertions.assertEquals("/subjectId", exception.fieldPath());
        Assertions.assertInstanceOf(IllegalArgumentException.class, exception.getCause());
    }

    @Test
    void testPhysicalIdentifiersCannotContainSql() {
        MetricDSLDefinition definition = MetricJdbcTestFixtures.scalarCountDefinition().definition();
        MetricJdbcBinding original = binding(definition);
        for (boolean table : List.of(true, false)) {
            MetricJdbcBinding invalid =
                    new MetricJdbcBinding() {
                        @Override
                        public String tableName(String reference) {
                            return table ? "orders` --" : original.tableName(reference);
                        }

                        @Override
                        public String columnName(String reference) {
                            return "id) OR 1=1 --";
                        }

                        @Override
                        public Class<?> javaType(String reference) {
                            return original.javaType(reference);
                        }

                        @Override
                        public int jdbcType(String reference) {
                            return original.jdbcType(reference);
                        }

                        @Override
                        public Object toJdbcValue(String reference, Object value) {
                            return value;
                        }
                    };
            MetricValidationException exception =
                    Assertions.assertThrows(
                            MetricValidationException.class,
                            () ->
                                    new MetricJdbcSqlCompiler(METRIC_TIME_ZONE)
                                            .compile(definition, criteria(), invalid));
            Assertions.assertEquals(MetricErrorCode.DSL_VALUE_INVALID, exception.errorCode());
        }
    }

    private static MetricQuery criteria() {
        LocalDateTime start = LocalDateTime.of(2026, 7, 1, 0, 0);
        return new MetricQuery(
                "customer-1", start, start.plusDays(1), Map.of("region", "APAC"), Map.of());
    }

    private static MetricJdbcBinding forbiddenBinding() {
        return new MetricJdbcBinding() {
            @Override
            public String tableName(String factReference) {
                throw new AssertionError("Unexpected binding read");
            }

            @Override
            public String columnName(String fieldReference) {
                throw new AssertionError("Unexpected binding read");
            }

            @Override
            public Class<?> javaType(String fieldReference) {
                throw new AssertionError("Unexpected binding read");
            }

            @Override
            public int jdbcType(String fieldReference) {
                throw new AssertionError("Unexpected binding read");
            }

            @Override
            public Object toJdbcValue(String fieldReference, Object value) {
                throw new AssertionError("Unexpected codec");
            }
        };
    }

    private record CodecBinding(
            MetricJdbcBinding delegate,
            Map<String, Class<?>> types,
            BiFunction<String, Object, Object> codec)
            implements MetricJdbcBinding {
        @Override
        public String tableName(String reference) {
            return delegate.tableName(reference);
        }

        @Override
        public String columnName(String reference) {
            return delegate.columnName(reference);
        }

        @Override
        public Class<?> javaType(String reference) {
            return types.getOrDefault(reference, delegate.javaType(reference));
        }

        @Override
        public int jdbcType(String reference) {
            return delegate.jdbcType(reference);
        }

        @Override
        public Object toJdbcValue(String reference, Object value) {
            return codec.apply(reference, value);
        }
    }

    private static MetricDSLDefinition prepared(MetricDSLDefinitionSpec definition) {
        return definition.definition();
    }
    /** 渲染器允许增加 AS/OUTER/括号；投影、运算和参数次序仍逐项比较。 */
    private static void assertSqlEquivalent(String expected, String actual) {
        var dsl = DSL.using(SQLDialect.MYSQL);
        Assertions.assertEquals(dsl.render(dsl.parser().parseQuery(expected)),
                dsl.render(dsl.parser().parseQuery(actual)));
    }

}
