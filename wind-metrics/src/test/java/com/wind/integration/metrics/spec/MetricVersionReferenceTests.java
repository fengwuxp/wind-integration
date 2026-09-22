package com.wind.integration.metrics.spec;

import com.wind.integration.metrics.MetricValidationException;
import com.wind.integration.metrics.dsl.definition.MetricExpressionDsl;
import com.wind.integration.metrics.dsl.definition.MetricMeasureDsl;
import com.wind.integration.metrics.dsl.definition.MetricOrElseDsl;
import com.wind.integration.metrics.dsl.definition.MetricReferenceDsl;
import com.wind.integration.metrics.dsl.definition.MetricSubjectDsl;
import com.wind.integration.metrics.dsl.definition.MetricTimeDsl;
import com.wind.integration.metrics.dsl.definition.MetricValueDsl;
import com.wind.integration.metrics.dsl.materialization.MetricMaterializationPlanDsl;
import com.wind.integration.metrics.dsl.materialization.MetricSnapshotTargetDsl;
import com.wind.integration.metrics.enums.MetricAggregation;
import com.wind.integration.metrics.enums.MetricErrorCode;
import com.wind.integration.metrics.enums.MetricExpressionType;
import com.wind.integration.metrics.enums.MetricOrElseMode;
import com.wind.integration.metrics.enums.MetricQueryMode;
import com.wind.integration.metrics.enums.MetricSnapshotStorageType;
import com.wind.integration.metrics.enums.MetricValueShape;
import com.wind.integration.metrics.enums.MetricValueType;
import com.wind.integration.metrics.enums.MetricSnapshotGranularity;
import com.wind.integration.metrics.expression.MetricExpressionCompiler;
import com.wind.integration.metrics.expression.MetricValueReference;
import com.wind.jackson.WindJson;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 完整定义规范的独立 JSON 往返与精确版本引用合同。
 *
 * @author wuxp
 * @since 2026-09-20
 */
class MetricVersionReferenceTests {

    /**
     * 场景：旧 RAW schema1 可独立 JSON 还原完整定义。
     * 输入：COUNT 事实指标封装为 schema1 的 DSL Spec。
     * 流程：序列化后按公共 MetricDefinitionSpec 反序列化。
     * 预期：JSON 含 definitionType=DSL，完整对象相等。
     */
    @Test
    void testRawSpecRoundTripIncludesDefinitionType() {
        assertSpecRoundTrip(new MetricDefinitionSpec.MetricDSLDefinitionSpec(1, raw()));
    }

    /**
     * 场景：SQL Spec 独立往返且不混入 DSL 依赖字段。
     * 输入：schema1 的 COUNT SQL 定义。
     * 流程：按公共 Spec 接口执行 JSON 往返并检查正文。
     * 预期：保留类型判别与对象内容，正文不包含 dependencies。
     */
    @Test
    void testSqlSpecRoundTripIncludesDefinitionType() {
        MetricSqlDefinition definition = new MetricSqlDefinition("COUNT", 1, MetricValueShape.SCALAR,
                "GLOBAL", List.of(), Map.of(), "SELECT COUNT(*) FROM orders");
        MetricDefinitionSpec<?> spec = new MetricDefinitionSpec.MetricSqlDefinitionSpec(1, definition);
        assertSpecRoundTrip(spec);
        Map<?, ?> body = WindJson.parseObject(WindJson.toJsonString(spec.definition()), Map.class);
        Assertions.assertFalse(body.containsKey("dependencies"));
    }

    /**
     * 场景：派生 Spec 自含精确引用，可还原后执行配置表达式。
     * 输入：schema4，APPROVED@2 与 TOTAL@7，结果值1和3。
     * 流程：完整 JSON 往返，再编译并执行 ratio。
     * 预期：两个绑定版本原样保留，输出0.333333。
     */
    @Test
    void testDerivedSpecRetainsExactRevisionsAndEvaluatesRatio() {
        MetricDSLDefinition definition = derived("ratio(metric('APPROVED', 'value'), metric('TOTAL', 'value'))",
                List.of(new MetricReferenceDsl("APPROVED", 2), new MetricReferenceDsl("TOTAL", 7)));
        MetricDefinitionSpec<?> restored = assertSpecRoundTrip(new MetricDefinitionSpec.MetricDSLDefinitionSpec(4, definition));
        MetricDSLDefinition result = (MetricDSLDefinition) restored.definition();
        Assertions.assertEquals(List.of(new MetricReferenceDsl("APPROVED", 2), new MetricReferenceDsl("TOTAL", 7)),
                result.dependencies());
        var expression = new MetricExpressionCompiler().compileDerived(result.value().expression(), "/metric/value/expression");
        Assertions.assertEquals(new BigDecimal("0.333333"), expression.evaluate(result.value(), Map.of(),
                Map.of(new MetricValueReference("APPROVED", "value"), 1L,
                        new MetricValueReference("TOTAL", "value"), 3L), "/metric/value"));
    }

    /**
     * 场景：同一上游多个字段和重复引用只绑定一个定义版本。
     * 输入：rate 引用 SUMMARY.approved/total，total 重复引用 SUMMARY.total，绑定 SUMMARY@3。
     * 流程：构造 FIELD_SET 并完整 JSON 往返。
     * 预期：仅保留 SUMMARY@3 一个版本绑定。
     */
    @Test
    void testSeveralFieldsOfSameCodeShareOneRevision() {
        Map<String, MetricValueDsl> fields = Map.of(
                "rate", expression("ratio(metric('SUMMARY', 'approved'), metric('SUMMARY', 'total'))"),
                "total", expression("metric('SUMMARY', 'total') + metric('SUMMARY', 'total')"));
        MetricDSLDefinition definition = new MetricDSLDefinition("SUMMARY_VIEW", 1, MetricValueShape.FIELD_SET,
                null, List.of(), new MetricSubjectDsl("GLOBAL", null), null, List.of(), Map.of(), null, null,
                fields, List.of(new MetricReferenceDsl("SUMMARY", 3)));
        MetricDefinitionSpec<?> restored = assertSpecRoundTrip(new MetricDefinitionSpec.MetricDSLDefinitionSpec(4, definition));
        Assertions.assertEquals(List.of(new MetricReferenceDsl("SUMMARY", 3)),
                ((MetricDSLDefinition) restored.definition()).dependencies());
    }

    /**
     * 场景：字段集合的依赖覆盖所有表达式字段。
     * 输入：first 引用 FIRST，second 引用 SECOND，但只绑定 FIRST@1。
     * 流程：构造派生 FIELD_SET。
     * 预期：报 DSL_VALUE_INVALID，定位 /metric/dependencies。
     */
    @Test
    void testBindingsCoverAllValueFields() {
        assertBindingInvalid(() -> new MetricDSLDefinition("SUMMARY_VIEW", 1, MetricValueShape.FIELD_SET,
                null, List.of(), new MetricSubjectDsl("GLOBAL", null), null, List.of(), Map.of(), null, null,
                Map.of("first", expression("metric('FIRST', 'value')"), "second", expression("metric('SECOND', 'value')")),
                List.of(new MetricReferenceDsl("FIRST", 1))));
    }

    /**
     * 场景：派生依赖不能缺失或只覆盖部分直接引用。
     * 输入：null、空列表，以及引用 BASE/EXTRA 却仅绑定 BASE。
     * 流程：分别构造派生定义。
     * 预期：均报依赖字段校验错误。
     */
    @Test
    void testMissingNullAndEmptyDerivedBindingsAreRejected() {
        assertBindingInvalid(() -> derived("metric('BASE', 'value')", null));
        assertBindingInvalid(() -> derived("metric('BASE', 'value')", List.of()));
        assertBindingInvalid(() -> derived("metric('BASE', 'value') + metric('EXTRA', 'value')",
                List.of(new MetricReferenceDsl("BASE", 1))));
    }

    /**
     * 场景：依赖正文只保存直接引用。
     * 输入：表达式只引用 SECOND_LEVEL，却同时绑定 SECOND_LEVEL@2 和 BASE@1。
     * 流程：构造派生定义。
     * 预期：拒绝多余绑定，不能把传递闭包混入直接依赖。
     */
    @Test
    void testExtraBindingAndTransitiveClosureAreRejected() {
        assertBindingInvalid(() -> derived("metric('SECOND_LEVEL', 'value')",
                List.of(new MetricReferenceDsl("SECOND_LEVEL", 2), new MetricReferenceDsl("BASE", 1))));
    }

    /**
     * 场景：一个指标 code 在定义内只能绑定一次。
     * 输入：BASE@1 与重复 BASE@1 或冲突 BASE@2。
     * 流程：构造引用 BASE 的派生定义。
     * 预期：两种情况均报依赖字段校验错误。
     */
    @Test
    void testDuplicateCodeAndConflictingVersionsAreRejected() {
        for (int revision : List.of(1, 2)) {
            assertBindingInvalid(() -> derived("metric('BASE', 'value')",
                    List.of(new MetricReferenceDsl("BASE", 1), new MetricReferenceDsl("BASE", revision))));
        }
    }

    /**
     * 场景：调用方修改输入集合不能改变已构造的版本引用。
     * 输入：可变列表含 BASE@8。
     * 流程：构造定义后清空原列表，再尝试清空定义 dependencies。
     * 预期：仍持有 BASE@8，且返回列表不可修改。
     */
    @Test
    void testDependenciesAreAnImmutableSnapshot() {
        List<MetricReferenceDsl> bindings = new ArrayList<>(List.of(new MetricReferenceDsl("BASE", 8)));
        MetricDSLDefinition definition = derived("metric('BASE', 'value')", bindings);
        bindings.clear();
        Assertions.assertEquals(List.of(new MetricReferenceDsl("BASE", 8)), definition.dependencies());
        Assertions.assertThrows(UnsupportedOperationException.class, () -> definition.dependencies().clear());
    }

    /**
     * 场景：精确引用必须有有效 code 与正数版本。
     * 输入：null/空白 code，null/0/-1 revision。
     * 流程：分别构造 MetricReferenceDsl。
     * 预期：null 报空指针校验错误，空白或非正数报参数错误。
     */
    @Test
    void testExactReferenceRequiresCodeAndPositiveRevision() {
        Assertions.assertThrows(NullPointerException.class, () -> new MetricReferenceDsl(null, 1));
        Assertions.assertThrows(NullPointerException.class, () -> new MetricReferenceDsl("BASE", null));
        for (String code : List.of("", " \t")) {
            Assertions.assertThrows(IllegalArgumentException.class, () -> new MetricReferenceDsl(code, 1));
        }
        for (int revision : List.of(0, -1)) {
            Assertions.assertThrows(IllegalArgumentException.class, () -> new MetricReferenceDsl("BASE", revision));
        }
    }

    /**
     * 场景：原生事实定义不能附带跨指标版本绑定。
     * 输入：合法 RAW COUNT 定义额外加入 BASE@1。
     * 流程：以完整构造器重新构造定义。
     * 预期：报 /metric/dependencies 校验错误。
     */
    @Test
    void testRawCannotBindDependencies() {
        MetricDSLDefinition definition = raw();
        assertBindingInvalid(() -> new MetricDSLDefinition(definition.code(), definition.revision(), definition.valueShape(),
                definition.fact(), definition.joins(), definition.subject(), definition.time(), definition.dimensions(),
                definition.parameters(), definition.rowSelection(), definition.value(), definition.fields(),
                List.of(new MetricReferenceDsl("BASE", 1))));
    }

    /**
     * 场景：兼容旧 RAW JSON 中尚不存在依赖字段的情况。
     * 输入：schema1至4，dependencies 分别缺失、null、空列表。
     * 流程：逐组按公共 Spec 反序列化。
     * 预期：均还原为依赖为空的同一 RAW 定义。
     */
    @Test
    void testLegacyRawJsonDefaultsMissingAndNullBindingsToEmpty() {
        Map<String, Object> body = definitionProperties(raw());
        for (int schema : List.of(1, 2, 3, 4)) {
            for (String encoding : List.of("missing", "null", "empty")) {
                body.put("dependencies", encoding.equals("empty") ? List.of() : null);
                if (encoding.equals("missing")) {
                    body.remove("dependencies");
                }
                MetricDefinitionSpec<?> restored = parseSpec(schema, body);
                Assertions.assertEquals(raw(), restored.definition());
            }
        }
    }

    /**
     * 场景：旧派生 JSON 必须补齐绑定并升级 schema 后才能使用。
     * 输入：schema3 缺 dependencies，再补 BASE@1，最后改为 schema4。
     * 流程：依次反序列化三种输入。
     * 预期：先报依赖错误，再报 schema 错误；schema4 保留 BASE@1，不能隐式猜版本。
     */
    @Test
    void testLegacyDerivedJsonRequiresExplicitMigration() {
        Map<String, Object> body = definitionProperties(derived("metric('BASE', 'value')", List.of(new MetricReferenceDsl("BASE", 1))));
        body.remove("dependencies");
        MetricValidationException failure = jsonValidationFailure(() -> parseSpec(3, body));
        Assertions.assertEquals("/metric/dependencies", failure.fieldPath());
        Assertions.assertEquals(MetricErrorCode.DSL_VALUE_INVALID, failure.errorCode());
        body.put("dependencies", List.of(Map.of("metricCode", "BASE", "definitionRevision", 1)));
        Assertions.assertEquals(MetricErrorCode.DSL_SCHEMA_VERSION_UNSUPPORTED,
                jsonValidationFailure(() -> parseSpec(3, body)).errorCode());
        Assertions.assertEquals(List.of(new MetricReferenceDsl("BASE", 1)),
                ((MetricDSLDefinition) parseSpec(4, body).definition()).dependencies());
    }

    /**
     * 场景：旧12参数构造器不能继续创建无绑定的派生定义。
     * 输入：无 fact，表达式引用 BASE.value，使用旧构造器。
     * 流程：构造 DSL 定义。
     * 预期：报 dependencies 校验错误。
     */
    @Test
    void testLegacyConstructorCannotCreateUnboundDerivedDefinition() {
        assertBindingInvalid(() -> new MetricDSLDefinition("DERIVED", 1, MetricValueShape.SCALAR,
                null, List.of(), new MetricSubjectDsl("GLOBAL", null), null, List.of(), Map.of(), null,
                expression("metric('BASE', 'value')"), Map.of()));
    }

    /**
     * 场景：定义 schema 的兼容范围必须显式校验。
     * 输入：RAW 使用 null/-1/0/6，已绑定 DERIVED 使用1/2/3。
     * 流程：构造 MetricDSLDefinitionSpec。
     * 预期：均报 DSL_SCHEMA_VERSION_UNSUPPORTED；RAW 错误定位 /schemaVersion。
     */
    @Test
    void testDefinitionSchemaBoundaryIsExplicit() {
        for (Integer schema : java.util.Arrays.asList(null, -1, 0, 5)) {
            MetricValidationException failure = Assertions.assertThrows(MetricValidationException.class,
                    () -> new MetricDefinitionSpec.MetricDSLDefinitionSpec(schema, raw()));
            Assertions.assertEquals(MetricErrorCode.DSL_SCHEMA_VERSION_UNSUPPORTED, failure.errorCode());
            Assertions.assertEquals("/schemaVersion", failure.fieldPath());
        }
        MetricDSLDefinition derived = derived("metric('BASE', 'value')", List.of(new MetricReferenceDsl("BASE", 1)));
        for (int schema : List.of(1, 2, 3)) {
            Assertions.assertEquals(MetricErrorCode.DSL_SCHEMA_VERSION_UNSUPPORTED,
                    Assertions.assertThrows(MetricValidationException.class,
                            () -> new MetricDefinitionSpec.MetricDSLDefinitionSpec(schema, derived)).errorCode());
        }
    }

    /**
     * 场景：Plan 使用 schema4 并保存成员精确版本。
     * 输入：快照计划包含 BASE@7。
     * 流程：Plan JSON 往返，再尝试修改成员集合。
     * 预期：计划相等、保留 BASE@7，成员集合不可修改。
     */
    @Test
    void testPlanSchemaFourRoundTripRetainsExactMemberRevision() {
        MetricMaterializationPlanDsl plan = plan(4, List.of(new MetricReferenceDsl("BASE", 7)));
        MetricMaterializationPlanDsl restored = WindJson.parseObject(WindJson.toJsonString(plan), MetricMaterializationPlanDsl.class);
        Assertions.assertEquals(plan, restored);
        Assertions.assertEquals(List.of(new MetricReferenceDsl("BASE", 7)), restored.metrics());
        Assertions.assertThrows(UnsupportedOperationException.class, () -> restored.metrics().clear());
    }

    /**
     * 场景：计划不能使用错误 schema 或不确定的成员集合。
     * 输入：schema2/3/5、空成员、重复 BASE@1 或 BASE@1/2。
     * 流程：分别构造 Plan。
     * 预期：错误 schema 报版本错误；空成员或重复 code 报 DSL_PLAN_INVALID。
     */
    @Test
    void testPlanRejectsUnsupportedSchemaEmptyAndDuplicateMembers() {
        for (int schema : List.of(2, 3, 5)) {
            Assertions.assertEquals(MetricErrorCode.DSL_SCHEMA_VERSION_UNSUPPORTED,
                    Assertions.assertThrows(MetricValidationException.class,
                            () -> plan(schema, List.of(new MetricReferenceDsl("BASE", 1)))).errorCode());
        }
        Assertions.assertEquals(MetricErrorCode.DSL_PLAN_INVALID,
                Assertions.assertThrows(MetricValidationException.class, () -> plan(4, List.of())).errorCode());
        for (int revision : List.of(1, 2)) {
            Assertions.assertEquals(MetricErrorCode.DSL_PLAN_INVALID,
                    Assertions.assertThrows(MetricValidationException.class,
                            () -> plan(4, List.of(new MetricReferenceDsl("BASE", 1), new MetricReferenceDsl("BASE", revision)))).errorCode());
        }
    }

    /**
     * 场景：旧计划不能用空值或零代替成员精确版本。
     * 输入：将合法 JSON 的 BASE@7 改为 null 或0。
     * 流程：反序列化 Plan 并检查根因。
     * 预期：失败原因指向 definitionRevision，不回退到当前版本。
     */
    @Test
    void testLegacyPlanJsonWithoutExactRevisionIsRejected() {
        String json = WindJson.toJsonString(plan(4, List.of(new MetricReferenceDsl("BASE", 7))));
        for (String replacement : List.of("\"definitionRevision\":null", "\"definitionRevision\":0")) {
            String invalid = json.replace("\"definitionRevision\":7", replacement);
            Assertions.assertNotEquals(json, invalid);
            RuntimeException failure = Assertions.assertThrows(RuntimeException.class,
                    () -> WindJson.parseObject(invalid, MetricMaterializationPlanDsl.class));
            Throwable cause = failure;
            while (cause.getCause() != null) {
                cause = cause.getCause();
            }
            Assertions.assertTrue(cause instanceof NullPointerException || cause instanceof IllegalArgumentException);
            Assertions.assertTrue(cause.getMessage().contains("definitionRevision"));
        }
    }

    private static MetricDSLDefinition raw() {
        MetricValueDsl value = new MetricValueDsl(MetricValueType.LONG, null, null,
                new MetricMeasureDsl(MetricAggregation.COUNT, null, null), null,
                new MetricOrElseDsl(MetricOrElseMode.NULL, null));
        return new MetricDSLDefinition("BASE", 1, MetricValueShape.SCALAR,
                "ORDERS", List.of(), new MetricSubjectDsl("GLOBAL", null), new MetricTimeDsl("created_at"),
                List.of(), Map.of(), null, value, Map.of());
    }

    private static MetricDSLDefinition derived(String expression, List<MetricReferenceDsl> bindings) {
        return new MetricDSLDefinition("DERIVED", 1, MetricValueShape.SCALAR, null, List.of(),
                new MetricSubjectDsl("GLOBAL", null), null, List.of(), Map.of(), null, expression(expression), Map.of(), bindings);
    }

    private static MetricValueDsl expression(String expression) {
        return new MetricValueDsl(MetricValueType.DECIMAL, 6, RoundingMode.HALF_UP, null,
                new MetricExpressionDsl(MetricExpressionType.SPEL, expression), new MetricOrElseDsl(MetricOrElseMode.NULL, null));
    }

    private static MetricMaterializationPlanDsl plan(int schema, List<MetricReferenceDsl> metrics) {
        return new MetricMaterializationPlanDsl(schema, MetricQueryMode.SNAPSHOT, "CUSTOMER", metrics,
                MetricSnapshotGranularity.DAY, new MetricSnapshotTargetDsl(MetricSnapshotStorageType.METRIC_VALUE_TABLE,
                "bucketTime", "com.example.Snapshot"), List.of());
    }

    private static void assertBindingInvalid(org.junit.jupiter.api.function.Executable action) {
        MetricValidationException failure = Assertions.assertThrows(MetricValidationException.class, action);
        Assertions.assertEquals(MetricErrorCode.DSL_VALUE_INVALID, failure.errorCode());
        Assertions.assertEquals("/metric/dependencies", failure.fieldPath());
    }

    private static Map<String, Object> definitionProperties(MetricDSLDefinition definition) {
        Map<?, ?> properties = WindJson.parseObject(WindJson.toJsonString(definition), Map.class);
        Map<String, Object> result = new LinkedHashMap<>();
        properties.forEach((key, value) -> result.put((String) key, value));
        return result;
    }

    private static MetricDefinitionSpec<?> parseSpec(int schema, Map<String, Object> body) {
        return WindJson.parseObject(WindJson.toJsonString(Map.of("schemaVersion", schema,
                "definitionType", "DSL", "definition", body)), MetricDefinitionSpec.class);
    }

    private static MetricValidationException jsonValidationFailure(org.junit.jupiter.api.function.Executable action) {
        RuntimeException failure = Assertions.assertThrows(RuntimeException.class, action);
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof MetricValidationException validation) {
                return validation;
            }
        }
        throw new AssertionError("Expected a DSL validation failure from JSON construction", failure);
    }

    private static MetricDefinitionSpec<?> assertSpecRoundTrip(MetricDefinitionSpec<?> specification) {
        String json = WindJson.toJsonString(specification);
        Map<?, ?> properties = WindJson.parseObject(json, Map.class);
        Assertions.assertEquals(specification.definitionType().name(), properties.get("definitionType"));
        MetricDefinitionSpec<?> restored = WindJson.parseObject(json, MetricDefinitionSpec.class);
        Assertions.assertEquals(specification, restored);
        return restored;
    }
}
