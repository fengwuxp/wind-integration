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
import com.wind.integration.metrics.dsl.literal.IntegralMetricLiteralDsl;
import com.wind.integration.metrics.dsl.literal.MetricLiteralDsl;
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
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 以本类固定的消费、退款、授权等业务指标场景为输入，
 * 验证 {@link MetricJdbcSqlCompiler} 对复杂场景的当前覆盖边界。
 *
 * <p>用例直接将 {@link MetricDSLDefinition} 交给编译器，与夹具提供的冻结 {@link MetricJdbcMapping}、
 * {@link MetricQuery} 一起产出参数化 SQL，断言文本、有序绑定及拒绝边界；不连接数据库，
 * 也不验证定义版本服务。以下为 DSL 模式相对 18 组场景的可表达性，
 * 其余未列出者需 SQL 模式或业务事实适配：</p>
 *
 * <table>
 *   <caption>DSL 模式可表达性矩阵（本测试可执行断言的部分）</caption>
 *   <tr><th>场景</th><th>形态</th><th>DSL 表达</th></tr>
 *   <tr><td>S02</td><td>前 N 笔金额</td><td>rowSelection(过滤+排序+limit) + SUM，见 {@link #testS02TopNRefundAmountByParameterizedLimit}</td></tr>
 *   <tr><td>S09</td><td>固定币种付款总额</td><td>subject + 全窗 + measure.filter，见 {@link #testS09FixedCurrencyPaymentTotalWithinWindow}</td></tr>
 *   <tr><td>S14</td><td>内外用户 ID JOIN</td><td>受控等值 JOIN + 主体经 join 别名，见 {@link #testS14JoinResolvesSubjectIdentity}</td></tr>
 *   <tr><td>S16</td><td>标签记录计数</td><td>COUNT + measure.filter，见 {@link #testS16TaggedTransactionCount}</td></tr>
 *   <tr><td>S03/S04/S08</td><td>比率 / 净额 / 特例</td><td>派生指标，{@link #testDerivedRatioMetricIsRejected} 断言拒绝</td></tr>
 *   <tr><td>S01/S05/S06</td><td>可选端点 / 无时间 / 相对窗</td><td>当前编译器要求完整半开窗，{@link #testWindowlessMetricIsRejected} 断言拒绝</td></tr>
 * </table>
 *
 * <p>COUNT DISTINCT(S11/S12)、UNION(S12/S13)、多事件时间列(S13)、相关子查询/去重关联(S15)
 * 在 DSL 类型系统中无对应结构，无法构造出可编译定义，需 SQL 模式（{@code MetricSqlDefinition.sqlTemplate}）
 * 或业务事实提供者适配，不由本测试伪造。</p>
 *
 * @author wuxp
 */
class MetricJdbcSqlCompilerScenarioTests {

    private static final ZoneId UTC = ZoneId.of("UTC");

    private static final LocalDateTime START = LocalDateTime.of(2026, 9, 1, 0, 0);

    private static final LocalDateTime END = START.plusDays(1);

    private static final Instant START_INSTANT = START.atZone(UTC).toInstant();

    private static final Instant END_INSTANT = END.atZone(UTC).toInstant();

    /**
     * 场景：旧系统 S09：按租户统计窗口内固定币种的成功付款总额。
     * 输入：tenant-1、USD、Success、2026-09-01至09-02，金额与时间映射已冻结。
     * 流程：编译 SUM 加度量过滤的 DSL。
     * 预期：生成 CASE 条件汇总及租户/半开窗谓词，5个参数的顺序和 JDBC 类型精确匹配。
     */
    @Test
    void testS09FixedCurrencyPaymentTotalWithinWindow() {
        MetricMeasureDsl measure = measure(MetricAggregation.SUM, "pay_amount",
                and(eq("payment_order_state", str("Success")), eq("pay_currency", str("USD"))));
        MetricDSLDefinition definition = definition()
                .subject(new MetricSubjectDsl("TENANT", "tenant_id"))
                .time(new MetricTimeDsl("gmt_create"))
                .value(value(measure))
                .build();

        MetricSqlDescriptor result = compiler().compile(definition, query("tenant-1"), binding(
                table("", "t_global_payment_income_detail"),
                column("tenant_id", "tenant_id", String.class, Types.VARCHAR),
                column("gmt_create", "gmt_create", Instant.class, Types.TIMESTAMP),
                column("pay_amount", "pay_amount", BigDecimal.class, Types.DECIMAL),
                column("payment_order_state", "payment_order_state", String.class, Types.VARCHAR),
                column("pay_currency", "pay_currency", String.class, Types.VARCHAR)));

        assertEquals("SELECT SUM(CASE WHEN (`p`.`payment_order_state` = ? AND `p`.`pay_currency` = ?)"
                + " THEN `p`.`pay_amount` END) AS `value` FROM `t_global_payment_income_detail` AS `p`"
                + " WHERE (`p`.`tenant_id` = ? AND `p`.`gmt_create` >= ? AND `p`.`gmt_create` < ?)",
                result.sql());
        assertBindings(result.bindings(),
                "Success", Types.VARCHAR,
                "USD", Types.VARCHAR,
                "tenant-1", Types.VARCHAR,
                START_INSTANT, Types.TIMESTAMP,
                END_INSTANT, Types.TIMESTAMP);
    }

    /**
     * 场景：旧系统 S02：筛选窗口内符合状态的前 N 笔退款后求金额和。
     * 输入：vcc-1、firstNPens=2，category/business_scene=6、state=7、hide=0，按授权时间升序。
     * 流程：编译 rowSelection 加 SUM。
     * 预期：过滤/排序/LIMIT 位于内层，SUM 位于外层，8个有序参数与类型匹配；不验证同时间的稳定次序。
     */
    @Test
    void testS02TopNRefundAmountByParameterizedLimit() {
        MetricRowSelectionDsl selection = new MetricRowSelectionDsl(
                and(eq("category", intLit(6)), eq("business_scene", intLit(6)),
                        eq("state", intLit(7)), eq("hide_to_customer", intLit(0))),
                List.of(new MetricOrderByDsl("authorization_time", MetricSortDirection.ASC)),
                new MetricLimitDsl(null, "firstNPens"));
        MetricDSLDefinition definition = definition()
                .subject(new MetricSubjectDsl("VCC", "vcc_id"))
                .time(new MetricTimeDsl("authorization_time"))
                .parameters(Map.of("firstNPens", parameter(null, null)))
                .rowSelection(selection)
                .value(value(measure(MetricAggregation.SUM, "payment_amount", null)))
                .build();

        MetricSqlDescriptor result = compiler().compile(definition, query("vcc-1", Map.of("firstNPens", 2)),
                binding(
                        table("", "t_vcc_transaction_details"),
                        column("vcc_id", "vcc_id", String.class, Types.VARCHAR),
                        column("authorization_time", "authorization_time", Instant.class, Types.TIMESTAMP),
                        column("payment_amount", "payment_amount", BigDecimal.class, Types.DECIMAL),
                        column("category", "category", Integer.class, Types.INTEGER),
                        column("business_scene", "business_scene", Integer.class, Types.INTEGER),
                        column("state", "state", Integer.class, Types.INTEGER),
                        column("hide_to_customer", "hide_to_customer", Integer.class, Types.INTEGER)));

        assertEquals("SELECT SUM(`r`.`c0`) AS `value` FROM (SELECT `p`.`payment_amount` AS `c0`"
                + " FROM `t_vcc_transaction_details` AS `p`"
                + " WHERE (`p`.`vcc_id` = ? AND `p`.`authorization_time` >= ? AND `p`.`authorization_time` < ?"
                + " AND `p`.`category` = ? AND `p`.`business_scene` = ? AND `p`.`state` = ?"
                + " AND `p`.`hide_to_customer` = ?) ORDER BY `p`.`authorization_time` ASC LIMIT ?) AS `r`",
                result.sql());
        assertBindings(result.bindings(),
                "vcc-1", Types.VARCHAR,
                START_INSTANT, Types.TIMESTAMP,
                END_INSTANT, Types.TIMESTAMP,
                6, Types.INTEGER,
                6, Types.INTEGER,
                7, Types.INTEGER,
                0, Types.INTEGER,
                2, Types.INTEGER);
    }

    /**
     * 场景：旧系统 S14：通过受控关联将外部用户身份映射到充值事实。
     * 输入：external-id-101，按 u_id 关联用户表；USD、补扣场景、state=2。
     * 流程：编译 MANY_TO_ONE INNER JOIN 与条件 SUM。
     * 预期：JOIN 使用内部 u_id，主体谓词使用关联表 id；6个参数的值与类型匹配。
     */
    @Test
    void testS14JoinResolvesSubjectIdentity() {
        MetricMeasureDsl measure = measure(MetricAggregation.SUM, "recharge_amount",
                and(eq("business_scene", str("FEE_SUPPLEMENTARY_DEDUCTION")),
                        eq("recharge_currency", str("USD")), eq("state", intLit(2))));
        MetricDSLDefinition definition = definition()
                .subject(new MetricSubjectDsl("USER", "u.id"))
                .time(new MetricTimeDsl("gmt_create"))
                .joins(List.of(new MetricJoinDsl("u", "t_user", MetricJoinType.INNER,
                        MetricJoinCardinality.MANY_TO_ONE,
                        List.of(new MetricJoinOnDsl("u_id", "u_id")))))
                .value(value(measure))
                .build();

        MetricSqlDescriptor result = compiler().compile(definition, query("external-id-101"), binding(
                table("", "t_wallet_manual_recharge"),
                column("u_id", "u_id", String.class, Types.VARCHAR),
                table("u", "t_user"),
                column("u.u_id", "u_id", String.class, Types.VARCHAR),
                column("u.id", "id", String.class, Types.VARCHAR),
                column("gmt_create", "gmt_create", Instant.class, Types.TIMESTAMP),
                column("recharge_amount", "recharge_amount", BigDecimal.class, Types.DECIMAL),
                column("business_scene", "business_scene", String.class, Types.VARCHAR),
                column("recharge_currency", "recharge_currency", String.class, Types.VARCHAR),
                column("state", "state", Integer.class, Types.INTEGER)));

        assertEquals("SELECT SUM(CASE WHEN (`p`.`business_scene` = ? AND `p`.`recharge_currency` = ?"
                + " AND `p`.`state` = ?) THEN `p`.`recharge_amount` END) AS `value`"
                + " FROM `t_wallet_manual_recharge` AS `p` JOIN `t_user` AS `j0` ON `p`.`u_id` = `j0`.`u_id`"
                + " WHERE (`j0`.`id` = ? AND `p`.`gmt_create` >= ? AND `p`.`gmt_create` < ?)",
                result.sql());
        assertBindings(result.bindings(),
                "FEE_SUPPLEMENTARY_DEDUCTION", Types.VARCHAR,
                "USD", Types.VARCHAR,
                2, Types.INTEGER,
                "external-id-101", Types.VARCHAR,
                START_INSTANT, Types.TIMESTAMP,
                END_INSTANT, Types.TIMESTAMP);
    }

    /**
     * 场景：旧系统 S16：按用户统计窗口内指定标签记录数。
     * 输入：user-1、tag_name=WISE_LE、tag_value=300，完整时间窗口。
     * 流程：编译带度量过滤的 COUNT。
     * 预期：生成条件 COUNT 及主体/时间谓词，标签、主体和时间的5个绑定保持顺序与类型。
     */
    @Test
    void testS16TaggedTransactionCount() {
        MetricMeasureDsl measure = measure(MetricAggregation.COUNT, null,
                and(eq("tag_name", str("WISE_LE")), eq("tag_value", str("300"))));
        MetricDSLDefinition definition = definition()
                .subject(new MetricSubjectDsl("USER", "user_id"))
                .time(new MetricTimeDsl("gmt_create"))
                .value(value(measure))
                .build();

        MetricSqlDescriptor result = compiler().compile(definition, query("user-1"), binding(
                table("", "t_vcc_transaction_details_tag"),
                column("user_id", "user_id", String.class, Types.VARCHAR),
                column("gmt_create", "gmt_create", Instant.class, Types.TIMESTAMP),
                column("tag_name", "tag_name", String.class, Types.VARCHAR),
                column("tag_value", "tag_value", String.class, Types.VARCHAR)));

        assertEquals("SELECT COUNT(CASE WHEN (`p`.`tag_name` = ? AND `p`.`tag_value` = ?) THEN 1 END) AS `value`"
                + " FROM `t_vcc_transaction_details_tag` AS `p`"
                + " WHERE (`p`.`user_id` = ? AND `p`.`gmt_create` >= ? AND `p`.`gmt_create` < ?)",
                result.sql());
        assertBindings(result.bindings(),
                "WISE_LE", Types.VARCHAR,
                "300", Types.VARCHAR,
                "user-1", Types.VARCHAR,
                START_INSTANT, Types.TIMESTAMP,
                END_INSTANT, Types.TIMESTAMP);
    }

    /**
     * 场景：真实 SQL 编译场景中的无维度、无参数全局计数。
     * 输入：省略 MetricQuery 的两个可选 Map，使用冻结事实表和 JDBC 类型映射。
     * 流程：编译事实 DSL 并检查参数化 SQL。
     * 预期：只绑定时间窗口，宿主可以直接构造最小查询条件。
     */
    @Test
    void testGlobalCountScenarioAllowsOmittedOptionalQueryContainers() {
        MetricDSLDefinition definition = definition()
                .subject(new MetricSubjectDsl("GLOBAL", null))
                .time(new MetricTimeDsl("gmt_create"))
                .value(value(measure(MetricAggregation.COUNT, null, null)))
                .build();

        MetricSqlDescriptor result = compiler().compile(definition,
                new MetricQuery(null, START, END, null, null), binding(
                        table("", "t_global_payment_income_detail"),
                        column("gmt_create", "gmt_create", Instant.class, Types.TIMESTAMP)));

        assertEquals("SELECT count(*) AS `value` FROM `t_global_payment_income_detail` AS `p`"
                + " WHERE (`p`.`gmt_create` >= ? AND `p`.`gmt_create` < ?)", result.sql());
        assertBindings(result.bindings(), START_INSTANT, Types.TIMESTAMP, END_INSTANT, Types.TIMESTAMP);
    }

    /**
     * 场景：S03/S04/S08 类派生公式不由事实 SQL 编译器直接执行。
     * 输入：无 fact 的 ratio 定义，绑定 APPROVED@1 和 TOTAL@2。
     * 流程：将合法派生定义交给事实 compile。
     * 预期：在 /metric/fact 报执行模式不支持；该拒绝不代表派生查询服务不支持表达式。
     */
    @Test
    void testDerivedRatioMetricIsRejected() {
        MetricValueDsl value = new MetricValueDsl(MetricValueType.DECIMAL, 6, RoundingMode.HALF_UP, null,
                new MetricExpressionDsl(MetricExpressionType.SPEL,
                        "ratio(metric('APPROVED', 'value'), metric('TOTAL', 'value'))"),
                new MetricOrElseDsl(MetricOrElseMode.NULL, null));
        MetricDSLDefinition definition = new MetricDSLDefinition("RATIO", 1, MetricValueShape.SCALAR,
                null, List.of(), new MetricSubjectDsl("GLOBAL", null), null, List.of(), Map.of(),
                null, value, Map.of(), List.of(new MetricReferenceDsl("APPROVED", 1), new MetricReferenceDsl("TOTAL", 2)));

        assertValidation(MetricErrorCode.METRIC_EXECUTION_MODE_UNSUPPORTED, "/metric/fact",
                () -> compiler().compile(definition, query(), binding(
                        table("", "t_vcc"),
                        column("gmt_create", "gmt_create", Instant.class, Types.TIMESTAMP))));
    }

    /**
     * 场景：旧场景的无界/可选时间窗不能直接进入当前 DSL 事实编译器。
     * 输入：VCC 定义分别传入起止均空或仅起点。
     * 流程：调用 compile。
     * 预期：报 QUERY_INVALID，分别定位 startTime 与 endTime；不推断 SQL 模板也受此限制。
     */
    @Test
    void testWindowlessMetricIsRejected() {
        MetricDSLDefinition definition = definition()
                .subject(new MetricSubjectDsl("VCC", "vcc_id"))
                .time(new MetricTimeDsl("gmt_create"))
                .build();
        MetricJdbcSqlCompiler compiler = compiler();
        MetricJdbcMapping binding = binding(
                table("", "t_vcc"),
                column("vcc_id", "vcc_id", String.class, Types.VARCHAR),
                column("gmt_create", "gmt_create", Instant.class, Types.TIMESTAMP));

        assertValidation(MetricErrorCode.QUERY_INVALID, "/startTime",
                () -> compiler.compile(definition, new MetricQuery("vcc-1", null, null, Map.of(), Map.of()), binding));
        assertValidation(MetricErrorCode.QUERY_INVALID, "/endTime",
                () -> compiler.compile(definition, new MetricQuery("vcc-1", START, null, Map.of(), Map.of()), binding));
    }

    // 测试夹具

    private static MetricJdbcSqlCompiler compiler() {
        return new MetricJdbcSqlCompiler(UTC);
    }

    private static MetricQuery query(String subjectId) {
        return new MetricQuery(subjectId, START, END, Map.of(), Map.of());
    }

    private static MetricQuery query() {
        return new MetricQuery(null, START, END, Map.of(), Map.of());
    }

    private static MetricQuery query(String subjectId, Map<String, Object> parameters) {
        return new MetricQuery(subjectId, START, END, Map.of(), parameters);
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

    private static MetricFilterDsl eq(String field, MetricLiteralDsl value) {
        return new ComparisonMetricFilterDsl(MetricFilterOperator.EQ, field, value);
    }

    private static MetricFilterDsl and(MetricFilterDsl... operands) {
        return new LogicalMetricFilterDsl(MetricFilterOperator.AND, List.of(operands));
    }

    private static StringMetricLiteralDsl str(String value) {
        return new StringMetricLiteralDsl(value);
    }

    private static IntegralMetricLiteralDsl intLit(long value) {
        return new IntegralMetricLiteralDsl(BigInteger.valueOf(value));
    }

    private static MetricJdbcMapping binding(Object... parts) {
        Binding binding = new Binding();
        for (int i = 0; i < parts.length; i++) {
            Object part = parts[i];
            if (part instanceof Table table) {
                binding.table(table.reference, table.name);
            } else if (part instanceof Column column) {
                binding.column(column.reference, column.name, column.javaType, column.jdbcType);
            }
        }
        return binding.build();
    }

    private static Table table(String reference, String name) {
        return new Table(reference, name);
    }

    private static Column column(String reference, String name, Class<?> javaType, int jdbcType) {
        return new Column(reference, name, javaType, jdbcType);
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

    private record Table(String reference, String name) {
    }

    private record Column(String reference, String name, Class<?> javaType, int jdbcType) {
    }

    private static final class Binding {

        private final Map<String, String> tables = new HashMap<>();

        private final Map<String, String> columns = new HashMap<>();

        private final Map<String, Class<?>> javaTypes = new HashMap<>();

        private final Map<String, Integer> jdbcTypes = new HashMap<>();

        Binding table(String reference, String name) {
            tables.put(reference, name);
            return this;
        }

        Binding column(String reference, String name, Class<?> javaType, int jdbcType) {
            columns.put(reference, name);
            javaTypes.put(reference, javaType);
            jdbcTypes.put(reference, jdbcType);
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

    private static final class Definition {

        private String fact = "order_fact";

        private MetricValueShape shape = MetricValueShape.SCALAR;

        private MetricSubjectDsl subject = new MetricSubjectDsl(MetricSubjectDsl.GLOBAL, null);

        private MetricTimeDsl time = new MetricTimeDsl("created_at");

        private List<String> dimensions = List.of();

        private Map<String, MetricQueryParameterDsl> parameters = Map.of();

        private MetricRowSelectionDsl rowSelection;

        private MetricValueDsl scalarValue =
                MetricJdbcSqlCompilerScenarioTests.value(measure(MetricAggregation.COUNT, null, null));

        private Map<String, MetricValueDsl> fields = Map.of();

        private List<MetricJoinDsl> joins = List.of();

        Definition fact(String fact) {
            this.fact = fact;
            return this;
        }

        Definition subject(MetricSubjectDsl subject) {
            this.subject = subject;
            return this;
        }

        Definition time(MetricTimeDsl time) {
            this.time = time;
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

        Definition joins(List<MetricJoinDsl> joins) {
            this.joins = joins;
            return this;
        }

        MetricDSLDefinition build() {
            return new MetricDSLDefinition("metric_code", 1, shape, fact, joins, subject, time, dimensions,
                    parameters, rowSelection, scalarValue, fields);
        }
    }
}
