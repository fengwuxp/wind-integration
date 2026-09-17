package com.wind.integration.metrics.dsl;

import com.wind.integration.metrics.MetricValidationException;
import com.wind.integration.metrics.dsl.materialization.MetricMaterializationPlanDsl;
import com.wind.integration.metrics.dsl.materialization.MetricReferenceDsl;
import com.wind.integration.metrics.dsl.materialization.MetricSegmentDsl;
import com.wind.integration.metrics.dsl.materialization.MetricSnapshotTargetDsl;
import com.wind.integration.metrics.enums.MetricErrorCode;
import com.wind.integration.metrics.enums.MetricQueryMode;
import com.wind.integration.metrics.enums.MetricSegmentCode;
import com.wind.integration.metrics.enums.MetricSegmentSourceType;
import com.wind.integration.metrics.enums.MetricSnapshotStorageType;
import com.wind.integration.metrics.enums.SnapshotGranularity;
import com.wind.jackson.WindJson;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;

/**
 * 聚合目标对象声明、分段、关闭世界与 JSON 规范化契约测试。
 *
 * @author wuxp
 * @date 2026-09-14 15:30
 */
class MetricMaterializationPlanDslCodecSnapshotTargetTests {

    private static final String WIDE_TARGET = """
            {"storageType":"WIDE_TABLE","bucketTimeField":"bucketEndTime",
             "objectTypeClassName":"com.example.WalletSnapshot"}
            """;

    private final MetricMaterializationPlanDslCodec codec = new MetricMaterializationPlanDslCodec();

    @Test
    void testDeclareObjectTypeWithoutRepeatingMemberMappings() {
        String json = """
                {"schemaVersion":3,"executionMode":"SNAPSHOT","dimensionKeyProviderCode":"WALLET_KEYS",
                 "metrics":[{"metricCode":"income","definitionRevision":2}],
                 "snapshotGranularity":"DAY","snapshotTarget":{"storageType":"WIDE_TABLE",
                 "bucketTimeField":"bucketEndTime","objectTypeClassName":"com.example.WalletSnapshot"}}
                """;

        MetricMaterializationPlanDsl plan = Assertions.assertDoesNotThrow(() -> codec.parse(json));
        String canonical = codec.canonicalize(plan);

        Assertions.assertTrue(canonical.contains("\"objectTypeClassName\":\"com.example.WalletSnapshot\""));
        Assertions.assertFalse(canonical.contains("valueMappings"));
        Assertions.assertEquals(plan, codec.parse(canonical));
    }

    @ParameterizedTest
    @ValueSource(strings = {"WIDE_TABLE", "METRIC_VALUE_TABLE"})
    void testTargetTypeDoesNotRepeatOrRestrictMembers(String storageType) {
        MetricMaterializationPlanDsl plan = codec.parse(planJson(WIDE_TARGET.replace("WIDE_TABLE", storageType)));

        Assertions.assertEquals(MetricSnapshotStorageType.valueOf(storageType), plan.snapshotTarget().storageType());
        Assertions.assertEquals("bucketEndTime", plan.snapshotTarget().bucketTimeField());
        Assertions.assertEquals("com.example.WalletSnapshot", plan.snapshotTarget().objectTypeClassName());
        Assertions.assertEquals(List.of(new MetricReferenceDsl("income", 2), new MetricReferenceDsl("summary", 7)),
                plan.metrics());
        Assertions.assertEquals(plan, codec.parse(codec.canonicalize(plan)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"com.example.UninstalledRow", "com.example.Rows$Wallet", "example.指标快照"})
    void testAcceptBinaryClassNamesWithoutLoadingHostTypes(String className) {
        MetricMaterializationPlanDsl plan = codec.parse(planJson(WIDE_TARGET.replace("com.example.WalletSnapshot", className)));

        Assertions.assertEquals(className, plan.snapshotTarget().objectTypeClassName());
        Assertions.assertEquals(plan, codec.parse(codec.canonicalize(plan)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Row", "int", "java.lang.String[]", "[Lcom.example.Row;", "com..Row",
            ".com.Row", "com.Row.", "com.9Row", "com.example.Row.class ", "com/Row", "com.example.Row<T>"})
    void testRejectInvalidClassName(String name) {
        assertTargetViolation(WIDE_TARGET.replace("com.example.WalletSnapshot", name),
                MetricErrorCode.DSL_IDENTIFIER_INVALID, "/objectTypeClassName");
    }

    @Test
    void testRejectExcessivelyLongClassName() {
        assertTargetViolation(WIDE_TARGET.replace("com.example.WalletSnapshot", "com." + "A".repeat(509)),
                MetricErrorCode.DSL_IDENTIFIER_INVALID, "/objectTypeClassName");
    }

    @ParameterizedTest
    @ValueSource(strings = {"storageType", "bucketTimeField", "objectTypeClassName"})
    void testRequireEveryTargetField(String field) {
        ObjectNode target = (ObjectNode) WindJson.getJsonMapper().readTree(WIDE_TARGET);
        target.remove(field);
        assertTargetViolation(target.toString(), MetricErrorCode.DSL_FIELD_REQUIRED, "/" + field);
        target.putNull(field);
        assertTargetViolation(target.toString(), MetricErrorCode.DSL_FIELD_REQUIRED, "/" + field);
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "[]", "1", "\"target\""})
    void testRejectInvalidTargetTypes(String value) {
        assertTargetViolation(value, MetricErrorCode.DSL_FIELD_TYPE_INVALID, "");
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "{}", "[]", "true", "\"\"", "\" \""})
    void testRejectInvalidObjectTypeValues(String value) {
        assertTargetViolation(WIDE_TARGET.replace("\"com.example.WalletSnapshot\"", value),
                MetricErrorCode.DSL_FIELD_TYPE_INVALID, "/objectTypeClassName");
    }

    @ParameterizedTest
    @ValueSource(strings = {"valueMappings", "aggregatorFactoryClassName", "rowKeys", "targetCode", "targetType",
            "resultMappings", "targetConfig", "binding", "dataSource", "tableName", "columnName", "watermarkTime"})
    void testRejectRemovedAndPhysicalTargetFields(String field) {
        ObjectNode target = (ObjectNode) WindJson.getJsonMapper().readTree(WIDE_TARGET);
        target.putObject(field);
        assertTargetViolation(target.toString(), MetricErrorCode.DSL_FIELD_UNKNOWN, "/" + field);
    }

    @Test
    void testValidateStorageTypeAndBucketField() {
        assertTargetViolation(WIDE_TARGET.replace("WIDE_TABLE", "OTHER"),
                MetricErrorCode.DSL_VALUE_INVALID, "/storageType");
        for (String invalid : new String[]{"9field", "table.field", "field-name", "A".repeat(65)}) {
            assertTargetViolation(WIDE_TARGET.replace("bucketEndTime", invalid),
                    MetricErrorCode.DSL_IDENTIFIER_INVALID, "/bucketTimeField");
        }
    }

    @Test
    void testRejectDuplicateObjectTypeDeclaration() {
        assertTargetViolation(WIDE_TARGET.replace("\"objectTypeClassName\":",
                        "\"objectTypeClassName\":\"com.example.Other\",\"objectTypeClassName\":"),
                MetricErrorCode.DSL_FIELD_DUPLICATED, "/objectTypeClassName");
    }

    @Test
    void testNestedBindingHasTheSameCanonicalTargetContract() {
        var mapper = WindJson.getJsonMapper();
        MetricMaterializationPlanDsl plan = mapper.readValue(planJson(WIDE_TARGET), MetricMaterializationPlanDsl.class);
        String canonical = codec.canonicalize(plan);
        String nestedJson = mapper.writeValueAsString(new PlanRequest(plan));

        Assertions.assertEquals(canonical, mapper.writeValueAsString(plan));
        Assertions.assertEquals("{\"plan\":" + canonical + "}", nestedJson);
        Assertions.assertEquals(plan, mapper.readValue(nestedJson, PlanRequest.class).plan());
    }

    @Test
    void testSnapshotSegmentsCanSelectDifferentObjectTypes() {
        MetricMaterializationPlanDsl snapshot = codec.parse(planJson(WIDE_TARGET));
        MetricSnapshotTargetDsl recent = new MetricSnapshotTargetDsl(MetricSnapshotStorageType.METRIC_VALUE_TABLE,
                "bucketEndTime", "com.example.SnapshotValue");
        MetricMaterializationPlanDsl segmented = new MetricMaterializationPlanDsl(3, MetricQueryMode.SEGMENTED,
                snapshot.dimensionKeyProviderCode(), snapshot.metrics(), null, null, "P90D", List.of(
                new MetricSegmentDsl(MetricSegmentCode.ARCHIVE, MetricSegmentSourceType.SNAPSHOT,
                        SnapshotGranularity.DAY, snapshot.snapshotTarget()),
                new MetricSegmentDsl(MetricSegmentCode.RECENT, MetricSegmentSourceType.SNAPSHOT,
                        SnapshotGranularity.HOUR, recent)));
        String json = codec.canonicalize(segmented);

        Assertions.assertEquals(segmented, codec.parse(json));
        String invalid = json.replace("\"objectTypeClassName\":\"com.example.WalletSnapshot\"", "\"valueMappings\":[]");
        DatabindException nested = Assertions.assertThrows(DatabindException.class,
                () -> WindJson.getJsonMapper().readValue("{\"plan\":" + invalid + "}", PlanRequest.class));
        MetricValidationException cause = Assertions.assertInstanceOf(MetricValidationException.class, nested.getCause());
        Assertions.assertEquals(MetricErrorCode.DSL_FIELD_UNKNOWN, cause.errorCode());
        Assertions.assertEquals("/segments/0/snapshotTarget/valueMappings", cause.fieldPath());
    }

    @Test
    void testProgrammaticValidationAndSerializationCannotBypassClassNameRules() {
        MetricMaterializationPlanDsl source = codec.parse(planJson(WIDE_TARGET));
        MetricSnapshotTargetDsl invalid = new MetricSnapshotTargetDsl(MetricSnapshotStorageType.WIDE_TABLE,
                "bucketEndTime", "com..Row");
        MetricMaterializationPlanDsl plan = new MetricMaterializationPlanDsl(3, source.executionMode(),
                source.dimensionKeyProviderCode(), source.metrics(), source.snapshotGranularity(), invalid, null, List.of());

        Assertions.assertThrows(MetricValidationException.class, () -> codec.validateBasic(plan));
        Assertions.assertThrows(MetricValidationException.class, () -> codec.canonicalize(plan));
        Assertions.assertThrows(DatabindException.class,
                () -> WindJson.getJsonMapper().writeValueAsString(new PlanRequest(plan)));
    }

    private String planJson(String target) {
        return """
                {"schemaVersion":3,"executionMode":"SNAPSHOT","dimensionKeyProviderCode":"WALLET_KEYS",
                 "metrics":[{"metricCode":"income","definitionRevision":2},{"metricCode":"summary","definitionRevision":7}],
                 "snapshotGranularity":"DAY","snapshotTarget":%s}
                """.formatted(target);
    }

    private void assertTargetViolation(String target, MetricErrorCode code, String suffix) {
        MetricValidationException exception = Assertions.assertThrows(MetricValidationException.class,
                () -> codec.parse(planJson(target)));
        Assertions.assertEquals(code, exception.errorCode());
        Assertions.assertEquals("/snapshotTarget" + suffix, exception.fieldPath());
    }

    private record PlanRequest(MetricMaterializationPlanDsl plan) {
    }
}
