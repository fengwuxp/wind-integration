package com.wind.integration.metrics.dsl;

import com.wind.integration.metrics.MetricValidationException;
import com.wind.integration.metrics.dsl.materialization.MetricMaterializationPlanDsl;
import com.wind.integration.metrics.dsl.materialization.MetricReferenceDsl;
import com.wind.integration.metrics.dsl.materialization.MetricSegmentDsl;
import com.wind.integration.metrics.dsl.materialization.MetricSnapshotTargetDsl;
import com.wind.integration.metrics.dsl.materialization.MetricSnapshotTargetMappingDsl;
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

import java.util.ArrayList;
import java.util.List;

/**
 * 快照逻辑保存合同的多指标、FIELD_SET、关闭世界和规范化测试。
 *
 * @author wuxp
 * @date 2026-09-14 15:30
 */
class MetricMaterializationPlanDslCodecSnapshotTargetTests {

    private static final String WIDE_TARGET = """
            {
              "storageType": "WIDE_TABLE",
              "bucketTimeField": "bucketEndTime",
              "valueMappings": [
                {"metricCode": "USER_WALLET_INCOME_TOTAL", "fieldName": "incomeTotal"},
                {"metricCode": "USER_WALLET_TRANSACTION_COUNT", "fieldName": "transactionCount"},
                {"metricCode": "USER_WALLET_SUMMARY", "fieldName": "incomeAmount"},
                {"metricCode": "USER_WALLET_SUMMARY", "fieldName": "incomeCount"}
              ]
            }
            """;

    private final MetricMaterializationPlanDslCodec codec = new MetricMaterializationPlanDslCodec();

    @ParameterizedTest
    @ValueSource(strings = {"WIDE_TABLE", "METRIC_VALUE_TABLE"})
    void testRejectRemovedRowKeys(String storageType) {
        ObjectNode target = (ObjectNode) WindJson.getJsonMapper().readTree(WIDE_TARGET);
        target.put("storageType", storageType);
        target.putArray("rowKeys").add("subjectId").add("dimensionKey");

        assertTargetViolation(WindJson.getJsonMapper().writeValueAsString(target),
                MetricErrorCode.DSL_FIELD_UNKNOWN, "/rowKeys");
    }

    @ParameterizedTest
    @ValueSource(strings = {"WIDE_TABLE", "METRIC_VALUE_TABLE"})
    void testPreserveMultipleMetricsAndFieldSetResults(String storageType) {
        MetricMaterializationPlanDsl plan = codec.parse(planJson(WIDE_TARGET.replace("WIDE_TABLE", storageType)));
        MetricSnapshotTargetDsl target = plan.snapshotTarget();

        Assertions.assertEquals(MetricSnapshotStorageType.valueOf(storageType), target.storageType());
        Assertions.assertEquals("bucketEndTime", target.bucketTimeField());
        Assertions.assertEquals(List.of(
                new MetricSnapshotTargetMappingDsl("USER_WALLET_INCOME_TOTAL", "incomeTotal"),
                new MetricSnapshotTargetMappingDsl("USER_WALLET_TRANSACTION_COUNT", "transactionCount"),
                new MetricSnapshotTargetMappingDsl("USER_WALLET_SUMMARY", "incomeAmount"),
                new MetricSnapshotTargetMappingDsl("USER_WALLET_SUMMARY", "incomeCount")), target.valueMappings());
    }

    @Test
    void testAllowCommonValueFieldForDifferentMetricsInValueTable() {
        String target = WIDE_TARGET.replace("WIDE_TABLE", "METRIC_VALUE_TABLE")
                .replace("incomeTotal", "value").replace("transactionCount", "value");

        MetricMaterializationPlanDsl plan = codec.parse(planJson(target));

        Assertions.assertEquals(4, plan.snapshotTarget().valueMappings().size());
        Assertions.assertEquals(2, plan.snapshotTarget().valueMappings().stream()
                .filter(mapping -> "value".equals(mapping.fieldName())).count());
    }

    @Test
    void testCanonicalizeMappingsWithoutConfiguredRowKeys() {
        MetricMaterializationPlanDsl plan = codec.parse(planJson(WIDE_TARGET));
        MetricSnapshotTargetDsl target = plan.snapshotTarget();
        MetricSnapshotTargetDsl reordered = new MetricSnapshotTargetDsl(target.storageType(), target.bucketTimeField(),
                target.valueMappings().reversed());
        String canonical = codec.canonicalize(plan);

        Assertions.assertEquals(canonical, codec.canonicalize(snapshotPlan(reordered)));
        MetricSnapshotTargetDsl restored = codec.parse(canonical).snapshotTarget();
        Assertions.assertFalse(canonical.contains("\"rowKeys\""));
        Assertions.assertEquals(List.of("incomeTotal", "incomeAmount", "incomeCount", "transactionCount"),
                restored.valueMappings().stream().map(MetricSnapshotTargetMappingDsl::fieldName).toList());
        Assertions.assertEquals("transactionCount", target.valueMappings().get(1).fieldName());
        Assertions.assertEquals(canonical, codec.canonicalize(codec.parse(canonical)));
    }

    @Test
    void testRoundTripFieldSetThroughDirectAndNestedJacksonBinding() {
        var mapper = WindJson.getJsonMapper();
        MetricMaterializationPlanDsl plan = mapper.readValue(planJson(WIDE_TARGET), MetricMaterializationPlanDsl.class);
        String canonical = codec.canonicalize(plan);
        String nestedJson = mapper.writeValueAsString(new PlanRequest(plan));
        PlanRequest restored = mapper.readValue(nestedJson, PlanRequest.class);

        Assertions.assertEquals(canonical, mapper.writeValueAsString(plan));
        Assertions.assertEquals("{\"plan\":" + canonical + "}", nestedJson);
        Assertions.assertEquals(codec.parse(canonical), restored.plan());
        Assertions.assertEquals(2, restored.plan().snapshotTarget().valueMappings().stream()
                .filter(mapping -> "USER_WALLET_SUMMARY".equals(mapping.metricCode())).count());
    }

    @ParameterizedTest
    @ValueSource(strings = {"storageType", "bucketTimeField", "valueMappings"})
    void testRequireEveryTargetField(String field) {
        ObjectNode target = (ObjectNode) WindJson.getJsonMapper().readTree(WIDE_TARGET);
        target.remove(field);
        assertTargetViolation(WindJson.getJsonMapper().writeValueAsString(target),
                MetricErrorCode.DSL_FIELD_REQUIRED, "/" + field);
        target.putNull(field);
        assertTargetViolation(WindJson.getJsonMapper().writeValueAsString(target),
                MetricErrorCode.DSL_FIELD_REQUIRED, "/" + field);
    }

    @Test
    void testRejectInvalidTargetAndContainerTypes() {
        assertTargetViolation("null", MetricErrorCode.DSL_FIELD_TYPE_INVALID, "");
        for (String value : new String[]{"[]", "1", "\"target\""}) {
            assertTargetViolation(value, MetricErrorCode.DSL_FIELD_TYPE_INVALID, "");
        }
        assertTargetViolation(WIDE_TARGET.replace("\"WIDE_TABLE\"", "\"OTHER\""),
                MetricErrorCode.DSL_VALUE_INVALID, "/storageType");
        assertTargetViolation(WIDE_TARGET.replace("\"WIDE_TABLE\"", "1"),
                MetricErrorCode.DSL_FIELD_TYPE_INVALID, "/storageType");
        assertTargetViolation("""
                {"storageType":"METRIC_VALUE_TABLE","bucketTimeField":"bucketEndTime",
                 "valueMappings":{}}
                """, MetricErrorCode.DSL_FIELD_TYPE_INVALID, "/valueMappings");
    }

    @ParameterizedTest
    @ValueSource(strings = {"targetCode", "targetType", "snapshotTargetCode", "resultMappings", "targetConfig",
            "storageCode", "binding", "dataSource", "tableName", "columnName", "sqlType", "credentials", "watermarkTime"})
    void testRejectLegacyPhysicalAndRuntimeTargetFields(String field) {
        assertTargetViolation(WIDE_TARGET.replace("\"storageType\":", "\"" + field + "\": {}, \"storageType\":"),
                MetricErrorCode.DSL_FIELD_UNKNOWN, "/" + field);
    }

    @ParameterizedTest
    @ValueSource(strings = {"resultField", "targetField", "columnName", "sqlType", "aggregation", "measures", "state"})
    void testRejectPhysicalAndCalculationMappingFields(String field) {
        assertTargetViolation(WIDE_TARGET.replace("\"fieldName\": \"incomeTotal\"",
                        "\"fieldName\": \"incomeTotal\", \"" + field + "\": {}"),
                MetricErrorCode.DSL_FIELD_UNKNOWN, "/valueMappings/0/" + field);
    }

    @Test
    void testRejectUndeclaredMetric() {
        assertTargetViolation(WIDE_TARGET.replace("USER_WALLET_INCOME_TOTAL", "UNKNOWN"),
                MetricErrorCode.DSL_PLAN_INVALID, "/valueMappings/0/metricCode");
    }

    @Test
    void testPreserveLogicalDeclarationsWithoutInferringPhysicalColumnConflicts() {
        for (String storageType : new String[]{"WIDE_TABLE", "METRIC_VALUE_TABLE"}) {
            for (String field : new String[]{"subjectId", "bucketEndTime", "transactionCount"}) {
                MetricMaterializationPlanDsl plan = codec.parse(planJson(
                        WIDE_TARGET.replace("WIDE_TABLE", storageType).replace("incomeTotal", field)));
                Assertions.assertEquals(field, plan.snapshotTarget().valueMappings().getFirst().fieldName());
                Assertions.assertEquals(4, codec.parse(codec.canonicalize(plan)).snapshotTarget().valueMappings().size());
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"WIDE_TABLE", "METRIC_VALUE_TABLE"})
    void testPreserveRepeatedMappings(String storageType) {
        MetricMaterializationPlanDsl plan = codec.parse(planJson(WIDE_TARGET
                .replace("WIDE_TABLE", storageType).replace("incomeCount", "incomeAmount")));
        String canonical = codec.canonicalize(plan);
        MetricSnapshotTargetDsl restored = codec.parse(canonical).snapshotTarget();

        Assertions.assertEquals(2, restored.valueMappings().stream().filter(mapping ->
                mapping.equals(new MetricSnapshotTargetMappingDsl("USER_WALLET_SUMMARY", "incomeAmount"))).count());
        MetricMaterializationPlanDsl direct = WindJson.getJsonMapper().readValue(
                WindJson.getJsonMapper().writeValueAsString(plan), MetricMaterializationPlanDsl.class);
        PlanRequest nested = WindJson.getJsonMapper().readValue(
                WindJson.getJsonMapper().writeValueAsString(new PlanRequest(plan)), PlanRequest.class);
        Assertions.assertEquals(restored.valueMappings(), direct.snapshotTarget().valueMappings());
        Assertions.assertEquals(restored.valueMappings(), nested.plan().snapshotTarget().valueMappings());
        Assertions.assertEquals(canonical, codec.canonicalize(direct));
        Assertions.assertEquals(canonical, codec.canonicalize(nested.plan()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"WIDE_TABLE", "METRIC_VALUE_TABLE"})
    void testNestedSnapshotSegmentRejectsRemovedRowKeys(String storageType) {
        MetricMaterializationPlanDsl snapshot = codec.parse(planJson(WIDE_TARGET.replace("WIDE_TABLE", storageType)));
        MetricMaterializationPlanDsl segmented = new MetricMaterializationPlanDsl(2, MetricQueryMode.SEGMENTED,
                snapshot.dimensionKeyProviderCode(), snapshot.metrics(), null, null, "P90D", List.of(
                new MetricSegmentDsl(MetricSegmentCode.ARCHIVE, MetricSegmentSourceType.SNAPSHOT,
                        SnapshotGranularity.DAY, snapshot.snapshotTarget()),
                new MetricSegmentDsl(MetricSegmentCode.RECENT, MetricSegmentSourceType.REALTIME, null, null)));
        String json = codec.canonicalize(segmented).replace("\"storageType\":", "\"rowKeys\":[],\"storageType\":");
        MetricValidationException direct = Assertions.assertThrows(MetricValidationException.class, () -> codec.parse(json));
        Assertions.assertEquals(MetricErrorCode.DSL_FIELD_UNKNOWN, direct.errorCode());
        Assertions.assertEquals("/segments/0/snapshotTarget/rowKeys", direct.fieldPath());

        DatabindException nested = Assertions.assertThrows(DatabindException.class,
                () -> WindJson.getJsonMapper().readValue("{\"plan\":" + json + "}", PlanRequest.class));
        MetricValidationException cause = Assertions.assertInstanceOf(MetricValidationException.class, nested.getCause());
        Assertions.assertEquals(direct.errorCode(), cause.errorCode());
        Assertions.assertEquals(direct.fieldPath(), cause.fieldPath());
    }

    @Test
    void testValidateLogicalIdentifiersAndMetricCodeLength() {
        for (String invalid : new String[]{"9field", "table.field", "field-name", "A".repeat(65)}) {
            assertTargetViolation(WIDE_TARGET.replace("bucketEndTime", invalid),
                    MetricErrorCode.DSL_IDENTIFIER_INVALID, "/bucketTimeField");
            assertTargetViolation(WIDE_TARGET.replace("incomeTotal", invalid),
                    MetricErrorCode.DSL_IDENTIFIER_INVALID, "/valueMappings/0/fieldName");
        }
        String longCode = planJson(WIDE_TARGET).replace("USER_WALLET_SUMMARY", "A".repeat(100));
        Assertions.assertDoesNotThrow(() -> codec.parse(longCode));
        assertTargetViolation(WIDE_TARGET.replace("USER_WALLET_INCOME_TOTAL", "A".repeat(101)),
                MetricErrorCode.DSL_IDENTIFIER_INVALID, "/valueMappings/0/metricCode");
    }

    @Test
    void testRejectInvalidMappingEntries() {
        String template = """
                {"storageType":"METRIC_VALUE_TABLE","bucketTimeField":"bucketEndTime",
                 "valueMappings":[%s]}
                """;
        assertTargetViolation(template.formatted(""), MetricErrorCode.DSL_PLAN_INVALID, "/valueMappings");
        for (String entry : new String[]{"null", "1", "[]", "\"value\""}) {
            assertTargetViolation(template.formatted(entry), MetricErrorCode.DSL_FIELD_TYPE_INVALID, "/valueMappings/0");
        }
        for (String entry : new String[]{"{}", "{\"metricCode\":null,\"fieldName\":\"value\"}"}) {
            assertTargetViolation(template.formatted(entry), MetricErrorCode.DSL_FIELD_REQUIRED, "/valueMappings/0/metricCode");
        }
        assertTargetViolation(template.formatted("{\"metricCode\":\"USER_WALLET_SUMMARY\"}"),
                MetricErrorCode.DSL_FIELD_REQUIRED, "/valueMappings/0/fieldName");
        assertTargetViolation(template.formatted("{\"metricCode\":true,\"fieldName\":\"value\"}"),
                MetricErrorCode.DSL_FIELD_TYPE_INVALID, "/valueMappings/0/metricCode");
        assertTargetViolation(template.formatted("{\"metricCode\":\"USER_WALLET_SUMMARY\",\"fieldName\":1}"),
                MetricErrorCode.DSL_FIELD_TYPE_INVALID, "/valueMappings/0/fieldName");
    }

    @Test
    void testRejectDuplicateJsonMembers() {
        assertTargetViolation(WIDE_TARGET.replace("\"bucketTimeField\":", "\"bucketTimeField\": \"otherTime\", \"bucketTimeField\":"),
                MetricErrorCode.DSL_FIELD_DUPLICATED, "/bucketTimeField");
        assertTargetViolation(WIDE_TARGET.replace("\"fieldName\": \"incomeTotal\"",
                        "\"fieldName\": \"incomeTotal\", \"fieldName\": \"value\""),
                MetricErrorCode.DSL_FIELD_DUPLICATED, "/valueMappings/0/fieldName");
    }

    @Test
    void testApplySameContractToSnapshotSegments() {
        MetricMaterializationPlanDsl snapshot = codec.parse(planJson(WIDE_TARGET));
        MetricMaterializationPlanDsl segmented = new MetricMaterializationPlanDsl(2, MetricQueryMode.SEGMENTED,
                snapshot.dimensionKeyProviderCode(), snapshot.metrics(), null, null, "P90D", List.of(
                new MetricSegmentDsl(MetricSegmentCode.ARCHIVE, MetricSegmentSourceType.SNAPSHOT,
                        SnapshotGranularity.DAY, snapshot.snapshotTarget()),
                new MetricSegmentDsl(MetricSegmentCode.RECENT, MetricSegmentSourceType.REALTIME, null, null)));
        String json = codec.canonicalize(segmented);

        Assertions.assertEquals(4, codec.parse(json).segments().getFirst().snapshotTarget().valueMappings().size());
        MetricValidationException exception = Assertions.assertThrows(MetricValidationException.class,
                () -> codec.parse(json.replace("\"fieldName\":\"incomeTotal\"", "\"targetField\":\"incomeTotal\"")));
        Assertions.assertEquals(MetricErrorCode.DSL_FIELD_UNKNOWN, exception.errorCode());
        Assertions.assertEquals("/segments/0/snapshotTarget/valueMappings/0/targetField", exception.fieldPath());
    }

    @Test
    void testDefensivelyCopyTargetCollections() {
        List<MetricSnapshotTargetMappingDsl> mappings = new ArrayList<>(
                List.of(new MetricSnapshotTargetMappingDsl("USER_WALLET_SUMMARY", "incomeAmount")));
        MetricSnapshotTargetDsl target = new MetricSnapshotTargetDsl(
                MetricSnapshotStorageType.WIDE_TABLE, "bucketEndTime", mappings);
        mappings.clear();

        Assertions.assertEquals(1, target.valueMappings().size());
        Assertions.assertThrows(UnsupportedOperationException.class, () -> target.valueMappings().clear());
    }

    @Test
    void testProgrammaticValidationAndSerializationCannotBypassMappingRules() {
        MetricSnapshotTargetDsl invalid = new MetricSnapshotTargetDsl(MetricSnapshotStorageType.WIDE_TABLE,
                "bucketEndTime", List.of(new MetricSnapshotTargetMappingDsl("UNKNOWN", "value")));
        MetricMaterializationPlanDsl plan = snapshotPlan(invalid);

        Assertions.assertThrows(MetricValidationException.class, () -> codec.validateBasic(plan));
        Assertions.assertThrows(MetricValidationException.class, () -> codec.canonicalize(plan));
        Assertions.assertThrows(DatabindException.class, () -> WindJson.getJsonMapper().writeValueAsString(new PlanRequest(plan)));
    }

    private String planJson(String target) {
        return """
                {"schemaVersion":2,"executionMode":"SNAPSHOT","dimensionKeyProviderCode":"WALLET_KEYS",
                 "metrics":[{"metricCode":"USER_WALLET_INCOME_TOTAL","definitionRevision":2},
                            {"metricCode":"USER_WALLET_TRANSACTION_COUNT","definitionRevision":7},
                            {"metricCode":"USER_WALLET_SUMMARY","definitionRevision":3}],
                 "snapshotGranularity":"DAY","snapshotTarget":%s}
                """.formatted(target);
    }

    private MetricMaterializationPlanDsl snapshotPlan(MetricSnapshotTargetDsl target) {
        return new MetricMaterializationPlanDsl(2, MetricQueryMode.SNAPSHOT, "WALLET_KEYS", List.of(
                new MetricReferenceDsl("USER_WALLET_INCOME_TOTAL", 2),
                new MetricReferenceDsl("USER_WALLET_TRANSACTION_COUNT", 7),
                new MetricReferenceDsl("USER_WALLET_SUMMARY", 3)), SnapshotGranularity.DAY, target, null, List.of());
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
