package com.wind.integration.metrics.dsl;

import com.wind.integration.metrics.MetricValidationException;
import com.wind.integration.metrics.dsl.materialization.MetricMaterializationPlanDsl;
import com.wind.integration.metrics.dsl.materialization.MetricReferenceDsl;
import com.wind.integration.metrics.enums.MetricErrorCode;
import com.wind.integration.metrics.enums.MetricQueryMode;
import com.wind.integration.metrics.enums.MetricSegmentCode;
import com.wind.integration.metrics.enums.MetricSegmentSourceType;
import com.wind.integration.metrics.enums.SnapshotGranularity;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 指标逻辑物化计划的公共 JSON 合同测试。
 *
 * @author wuxp
 * @date 2026-07-21 17:51
 */
class MetricMaterializationPlanDslCodecTests {

    private final MetricMaterializationPlanDslCodec codec = new MetricMaterializationPlanDslCodec();

    @Test
    void testDeclareMetricsWithoutResolvingOptionalRevision() {
        String source = """
                {
                  "schemaVersion": 2,
                  "executionMode": "SNAPSHOT",
                  "snapshotKeyProviderCode": "VCC_KEYS",
                  "metrics": [{"metricCode": "B", "definitionRevision": 7}, {"metricCode": "A"}],
                  "snapshotGranularity": "DAY",
                  "snapshotTargetCode": "authValue"
                }
                """;

        MetricMaterializationPlanDsl plan = Assertions.assertDoesNotThrow(() -> codec.parse(source));

        Assertions.assertEquals(
                "{\"schemaVersion\":2,\"executionMode\":\"SNAPSHOT\","
                        + "\"snapshotKeyProviderCode\":\"VCC_KEYS\","
                        + "\"metrics\":[{\"metricCode\":\"A\"},{\"metricCode\":\"B\",\"definitionRevision\":7}],"
                        + "\"snapshotGranularity\":\"DAY\",\"snapshotTargetCode\":\"authValue\"}",
                codec.canonicalize(plan));
    }

    @Test
    @DisplayName("DSL-T001 逻辑 Plan 可稳定规范化")
    void testParseAndCanonicalizeSnapshotPlan() {
        String source = """
                {
                  "snapshotTargetCode": "authValue",
                  "snapshotGranularity": "DAY",
                  "snapshotKeyProviderCode": "VCC_CUSTOMER_CURRENCY_KEYS",
                  "executionMode": "SNAPSHOT",
                  "schemaVersion": 2,
                  "metrics": [{"metricCode": "A"}]
                }
                """;

        MetricMaterializationPlanDsl plan = codec.parse(source);

        Assertions.assertEquals(MetricQueryMode.SNAPSHOT, plan.executionMode());
        Assertions.assertEquals(
                "{\"schemaVersion\":2,\"executionMode\":\"SNAPSHOT\","
                        + "\"snapshotKeyProviderCode\":\"VCC_CUSTOMER_CURRENCY_KEYS\","
                        + "\"metrics\":[{\"metricCode\":\"A\"}],"
                        + "\"snapshotGranularity\":\"DAY\",\"snapshotTargetCode\":\"authValue\"}",
                codec.canonicalize(plan));
    }

    @Test
    void testNormalizeSegmentedRecentWindow() {
        String source = """
                {
                  "schemaVersion": 2,
                  "metrics": [{"metricCode": "A"}],
                  "executionMode": "SEGMENTED",
                  "recentWindow": "P090D",
                  "snapshotKeyProviderCode": "VCC_CUSTOMER_CURRENCY_KEYS",
                  "segments": [
                    {
                      "segmentCode": "archive",
                      "sourceType": "SNAPSHOT",
                      "snapshotGranularity": "DAY",
                      "snapshotTargetCode": "authArchiveValue"
                    },
                    {"segmentCode": "recent", "sourceType": "REALTIME"}
                  ]
                }
                """;

        MetricMaterializationPlanDsl plan = codec.parse(source);

        Assertions.assertEquals("P90D", plan.recentWindow());
        Assertions.assertEquals(MetricSegmentCode.ARCHIVE, plan.segments().getFirst().segmentCode());
    }

    @Test
    void testRejectInvalidRecentWindow() {
        String source = """
                {
                  "schemaVersion": 2,
                  "metrics": [{"metricCode": "A"}],
                  "executionMode": "SEGMENTED",
                  "recentWindow": "PT0H",
                  "snapshotKeyProviderCode": "VCC_CUSTOMER_CURRENCY_KEYS",
                  "segments": []
                }
                """;

        MetricValidationException exception = Assertions.assertThrows(
                MetricValidationException.class,
                () -> codec.parse(source));

        Assertions.assertEquals(MetricErrorCode.DSL_PLAN_INVALID, exception.errorCode());
        Assertions.assertEquals("/recentWindow", exception.fieldPath());
    }

    @Test
    void testParseDoubleSnapshotAndHourlyPlans() {
        String doubleSnapshot = """
                {
                  "schemaVersion": 2,
                  "metrics": [{"metricCode": "A"}],
                  "executionMode": "SEGMENTED",
                  "recentWindow": "P90D",
                  "snapshotKeyProviderCode": "VCC_CUSTOMER_CURRENCY_KEYS",
                  "segments": [
                    {
                      "segmentCode": "archive",
                      "sourceType": "SNAPSHOT",
                      "snapshotGranularity": "DAY",
                      "snapshotTargetCode": "authArchiveWide"
                    },
                    {
                      "segmentCode": "recent",
                      "sourceType": "SNAPSHOT",
                      "snapshotGranularity": "DAY",
                      "snapshotTargetCode": "authRecentWide"
                    }
                  ]
                }
                """;
        String hourly = """
                {
                  "schemaVersion": 2,
                  "metrics": [{"metricCode": "A"}],
                  "executionMode": "SEGMENTED",
                  "recentWindow": "PT024H",
                  "snapshotKeyProviderCode": "VCC_CUSTOMER_CURRENCY_KEYS",
                  "segments": [
                    {
                      "segmentCode": "archive",
                      "sourceType": "SNAPSHOT",
                      "snapshotGranularity": "HOUR",
                      "snapshotTargetCode": "authArchiveValue"
                    },
                    {"segmentCode": "recent", "sourceType": "REALTIME"}
                  ]
                }
                """;

        MetricMaterializationPlanDsl doubleSnapshotPlan = codec.parse(doubleSnapshot);
        MetricMaterializationPlanDsl hourlyPlan = codec.parse(hourly);

        Assertions.assertEquals(MetricSegmentSourceType.SNAPSHOT, doubleSnapshotPlan.segments().get(1).sourceType());
        Assertions.assertEquals("PT24H", hourlyPlan.recentWindow());
        Assertions.assertEquals(SnapshotGranularity.HOUR, hourlyPlan.segments().getFirst().snapshotGranularity());
        Assertions.assertEquals(
                codec.canonicalize(doubleSnapshotPlan),
                codec.canonicalize(codec.parse(codec.canonicalize(doubleSnapshotPlan))));
    }

    @Test
    void testRejectRealtimeSegmentWithSnapshotTarget() {
        String source = """
                {
                  "schemaVersion": 2,
                  "metrics": [{"metricCode": "A"}],
                  "executionMode": "SEGMENTED",
                  "recentWindow": "P90D",
                  "snapshotKeyProviderCode": "VCC_CUSTOMER_CURRENCY_KEYS",
                  "segments": [
                    {
                      "segmentCode": "archive",
                      "sourceType": "SNAPSHOT",
                      "snapshotGranularity": "DAY",
                      "snapshotTargetCode": "authArchiveValue"
                    },
                    {
                      "segmentCode": "recent",
                      "sourceType": "REALTIME",
                      "snapshotTargetCode": "authRecentValue"
                    }
                  ]
                }
                """;

        MetricValidationException exception = Assertions.assertThrows(
                MetricValidationException.class,
                () -> codec.parse(source));

        Assertions.assertEquals(MetricErrorCode.DSL_PLAN_INVALID, exception.errorCode());
        Assertions.assertEquals("/segments/1", exception.fieldPath());
    }

    @Test
    void testRejectReversedSegmentOrder() {
        String source = """
                {
                  "schemaVersion": 2,
                  "metrics": [{"metricCode": "A"}],
                  "executionMode": "SEGMENTED",
                  "recentWindow": "P90D",
                  "snapshotKeyProviderCode": "VCC_CUSTOMER_CURRENCY_KEYS",
                  "segments": [
                    {"segmentCode": "recent", "sourceType": "REALTIME"},
                    {
                      "segmentCode": "archive",
                      "sourceType": "SNAPSHOT",
                      "snapshotGranularity": "DAY",
                      "snapshotTargetCode": "authArchiveValue"
                    }
                  ]
                }
                """;

        MetricValidationException exception = Assertions.assertThrows(
                MetricValidationException.class,
                () -> codec.parse(source));

        Assertions.assertEquals(MetricErrorCode.DSL_PLAN_INVALID, exception.errorCode());
        Assertions.assertEquals("/segments", exception.fieldPath());
    }

    @Test
    void testRejectUnsupportedSchemaVersionBeforePlanFields() {
        for (int version : new int[]{1, 3}) {
            assertViolation(
                    "{\"schemaVersion\":" + version + ",\"futureField\":true}",
                    MetricErrorCode.DSL_SCHEMA_VERSION_UNSUPPORTED, "/schemaVersion");
        }
    }

    @Test
    void testRejectTrailingComma() {
        assertInvalidJson("""
                {"schemaVersion":1,"executionMode":"SNAPSHOT","snapshotKeyProviderCode":"VCC_KEYS",
                 "snapshotGranularity":"DAY","snapshotTargetCode":"authValue",}
                """);
    }

    @Test
    void testRejectJsonComment() {
        assertInvalidJson("""
                {"schemaVersion":1,/* comment */"executionMode":"SNAPSHOT",
                 "snapshotKeyProviderCode":"VCC_KEYS","snapshotGranularity":"DAY",
                 "snapshotTargetCode":"authValue"}
                """);
    }

    @Test
    void testRejectUnquotedJsonField() {
        assertInvalidJson("""
                {schemaVersion:1,executionMode:"SNAPSHOT",snapshotKeyProviderCode:"VCC_KEYS",
                 snapshotGranularity:"DAY",snapshotTargetCode:"authValue"}
                """);
    }

    @Test
    void testRejectTruncatedJson() {
        assertInvalidJson("{\"schemaVersion\":");
    }

    @Test
    void testRejectNonStandardJsonNumbers() {
        for (String schemaVersion : new String[]{"+1", "0x1", "1."}) {
            assertInvalidJson("""
                    {"schemaVersion":%s,"executionMode":"SNAPSHOT","snapshotKeyProviderCode":"VCC_KEYS",
                     "snapshotGranularity":"DAY","snapshotTargetCode":"authValue"}
                    """.formatted(schemaVersion));
        }
    }

    @Test
    void testRejectExplicitNullSegments() {
        String source = """
                {
                  "schemaVersion": 2,
                  "metrics": [{"metricCode": "A"}],
                  "executionMode": "SNAPSHOT",
                  "snapshotKeyProviderCode": "VCC_KEYS",
                  "snapshotGranularity": "DAY",
                  "snapshotTargetCode": "authValue",
                  "segments": null
                }
                """;

        MetricValidationException exception = Assertions.assertThrows(
                MetricValidationException.class,
                () -> codec.parse(source));

        Assertions.assertEquals(MetricErrorCode.DSL_FIELD_TYPE_INVALID, exception.errorCode());
        Assertions.assertEquals("/segments", exception.fieldPath());
    }

    @Test
    void testCanonicalizeIndependentMetricRevisions() {
        String source = snapshotPlanWithMetrics("""
                {"metricCode": "B", "definitionRevision": 7},
                {"metricCode": "A", "definitionRevision": 2}
                """);

        MetricMaterializationPlanDsl plan = codec.parse(source);
        String canonical = codec.canonicalize(plan);
        MetricMaterializationPlanDsl canonicalPlan = codec.parse(canonical);

        Assertions.assertEquals(List.of(new MetricReferenceDsl("B", 7), new MetricReferenceDsl("A", 2)), plan.metrics());
        Assertions.assertEquals(List.of(new MetricReferenceDsl("A", 2), new MetricReferenceDsl("B", 7)), canonicalPlan.metrics());
        Assertions.assertEquals(canonical, codec.canonicalize(canonicalPlan));
        Assertions.assertFalse(canonical.contains("dependencies"));
        Assertions.assertFalse(canonical.contains("measures"));
    }

    @Test
    void testCanonicalizeSingleMetricWithoutRevision() {
        MetricMaterializationPlanDsl plan = codec.parse(snapshotPlanWithMetrics("{\"metricCode\":\"A\"}"));

        Assertions.assertNull(plan.metrics().getFirst().definitionRevision());
        String canonical = codec.canonicalize(plan);
        Assertions.assertFalse(canonical.contains("definitionRevision"));
        Assertions.assertEquals(plan, codec.parse(canonical));
        Assertions.assertEquals(canonical, codec.canonicalize(codec.parse(canonical)));
    }

    @Test
    void testCanonicalizeSegmentedMetricReferences() {
        String source = """
                {
                  "schemaVersion": 2,
                  "metrics": [{"metricCode": "B", "definitionRevision": 7}, {"metricCode": "A"}],
                  "executionMode": "SEGMENTED",
                  "snapshotKeyProviderCode": "VCC_KEYS",
                  "recentWindow": "P090D",
                  "segments": [
                    {"segmentCode": "archive", "sourceType": "SNAPSHOT",
                     "snapshotGranularity": "DAY", "snapshotTargetCode": "authArchive"},
                    {"segmentCode": "recent", "sourceType": "REALTIME"}
                  ]
                }
                """;

        String canonical = codec.canonicalize(codec.parse(source));
        MetricMaterializationPlanDsl plan = codec.parse(canonical);

        Assertions.assertEquals(List.of(new MetricReferenceDsl("A", null), new MetricReferenceDsl("B", 7)), plan.metrics());
        Assertions.assertEquals("P90D", plan.recentWindow());
        Assertions.assertEquals(canonical, codec.canonicalize(plan));
    }

    @Test
    void testRequireNonEmptyMetricsArray() {
        String source = snapshotPlanWithMetrics("{\"metricCode\":\"A\"}");
        assertViolation(source.replace("\"metrics\": [{\"metricCode\":\"A\"}],", ""),
                MetricErrorCode.DSL_FIELD_REQUIRED, "/metrics");
        assertViolation(source.replace("[{\"metricCode\":\"A\"}]", "null"),
                MetricErrorCode.DSL_FIELD_REQUIRED, "/metrics");
        assertInvalidPlan(snapshotPlanWithMetrics(""), "/metrics");
        for (String invalid : new String[]{"{}", "1", "\"A\""}) {
            assertViolation(source.replace("[{\"metricCode\":\"A\"}]", invalid),
                    MetricErrorCode.DSL_FIELD_TYPE_INVALID, "/metrics");
        }
    }

    @Test
    void testRejectInvalidMetricReferences() {
        for (String invalid : new String[]{"null", "1", "[]", "\"A\""}) {
            assertViolation(snapshotPlanWithMetrics(invalid), MetricErrorCode.DSL_FIELD_TYPE_INVALID, "/metrics/0");
        }
        assertViolation(snapshotPlanWithMetrics("{}"), MetricErrorCode.DSL_FIELD_REQUIRED, "/metrics/0/metricCode");
        assertViolation(snapshotPlanWithMetrics("{\"metricCode\":null}"),
                MetricErrorCode.DSL_FIELD_REQUIRED, "/metrics/0/metricCode");
        for (String invalid : new String[]{"true", "1", "\"\"", "\" \""}) {
            assertViolation(snapshotPlanWithMetrics("{\"metricCode\":" + invalid + "}"),
                    MetricErrorCode.DSL_FIELD_TYPE_INVALID, "/metrics/0/metricCode");
        }
        for (String invalid : new String[]{"9A", "A-B", "A".repeat(101)}) {
            assertViolation(snapshotPlanWithMetrics("{\"metricCode\":\"" + invalid + "\"}"),
                    MetricErrorCode.DSL_IDENTIFIER_INVALID, "/metrics/0/metricCode");
        }
        Assertions.assertDoesNotThrow(() -> codec.parse(snapshotPlanWithMetrics(
                "{\"metricCode\":\"" + "A".repeat(100) + "\"}")));
    }

    @Test
    void testRejectDuplicateMetricCodeRegardlessOfRevision() {
        for (String second : new String[]{"{\"metricCode\":\"A\"}",
                "{\"metricCode\":\"A\",\"definitionRevision\":2}",
                "{\"metricCode\":\"A\",\"definitionRevision\":7}"}) {
            assertInvalidPlan(snapshotPlanWithMetrics(
                    "{\"metricCode\":\"A\",\"definitionRevision\":2}," + second), "/metrics/1/metricCode");
        }
    }

    @Test
    void testRejectInvalidOptionalRevision() {
        for (String invalid : new String[]{"null", "\"7\"", "true", "1.5", "2147483648", "{}", "[]"}) {
            assertViolation(snapshotPlanWithMetrics("{\"metricCode\":\"A\",\"definitionRevision\":" + invalid + "}"),
                    MetricErrorCode.DSL_FIELD_TYPE_INVALID, "/metrics/0/definitionRevision");
        }
        for (int invalid : new int[]{0, -1}) {
            assertInvalidPlan(snapshotPlanWithMetrics("{\"metricCode\":\"A\",\"definitionRevision\":" + invalid + "}"),
                    "/metrics/0/definitionRevision");
        }
        for (int revision : new int[]{1, Integer.MAX_VALUE}) {
            MetricMaterializationPlanDsl plan = codec.parse(snapshotPlanWithMetrics(
                    "{\"metricCode\":\"A\",\"definitionRevision\":" + revision + "}"));
            Assertions.assertEquals(revision, plan.metrics().getFirst().definitionRevision());
        }
    }

    @Test
    void testRejectComputedFieldsInMetricReference() {
        for (String field : new String[]{"measures", "dependencies", "valueField", "resolvedDefinitionRevision"}) {
            assertViolation(snapshotPlanWithMetrics("{\"metricCode\":\"A\",\"" + field + "\":{}}"),
                    MetricErrorCode.DSL_FIELD_UNKNOWN, "/metrics/0/" + field);
        }
    }

    @Test
    void testValidateProgrammaticMetricReferences() {
        for (List<MetricReferenceDsl> metrics : List.of(
                List.<MetricReferenceDsl>of(),
                List.of(new MetricReferenceDsl("A", 0)),
                List.of(new MetricReferenceDsl("A", 2), new MetricReferenceDsl("A", 7)))) {
            MetricMaterializationPlanDsl plan = snapshotPlan(metrics);
            Assertions.assertThrows(MetricValidationException.class, () -> codec.validateBasic(plan));
            Assertions.assertThrows(MetricValidationException.class, () -> codec.canonicalize(plan));
        }
    }

    @Test
    void testDefensivelyCopyMetricReferences() {
        List<MetricReferenceDsl> metrics = new ArrayList<>();
        metrics.add(new MetricReferenceDsl("A", null));
        MetricMaterializationPlanDsl plan = snapshotPlan(metrics);

        metrics.add(new MetricReferenceDsl("B", 7));

        Assertions.assertEquals(List.of(new MetricReferenceDsl("A", null)), plan.metrics());
        Assertions.assertThrows(UnsupportedOperationException.class,
                () -> plan.metrics().add(new MetricReferenceDsl("B", 7)));
        Assertions.assertThrows(NullPointerException.class, () -> new MetricReferenceDsl(null, null));
    }

    @Test
    void testRejectLegacyJointFieldsAndSegmentedRootGranularity() {
        for (String field : new String[]{
                "dependencies", "dependencyClosure", "materializationScope", "watermarkPolicy",
                "sourceReadinessPolicy", "recentReadConsistency"}) {
            MetricValidationException exception = Assertions.assertThrows(
                    MetricValidationException.class,
                    () -> codec.parse("""
                            {
                              "schemaVersion": 2,
                              "metrics": [{"metricCode": "A"}],
                              "executionMode": "SNAPSHOT",
                              "snapshotKeyProviderCode": "VCC_KEYS",
                              "snapshotGranularity": "DAY",
                              "snapshotTargetCode": "authValue",
                              "%s": {}
                            }
                            """.formatted(field)));
            Assertions.assertEquals(MetricErrorCode.DSL_FIELD_UNKNOWN, exception.errorCode());
            Assertions.assertEquals("/" + field, exception.fieldPath());
        }

        assertInvalidPlan("""
                {
                  "schemaVersion": 2,
                  "metrics": [{"metricCode": "A"}],
                  "executionMode": "SEGMENTED",
                  "snapshotKeyProviderCode": "VCC_KEYS",
                  "snapshotGranularity": "DAY",
                  "recentWindow": "P90D",
                  "segments": [
                    {
                      "segmentCode": "archive",
                      "sourceType": "SNAPSHOT",
                      "snapshotGranularity": "DAY",
                      "snapshotTargetCode": "archiveValue"
                    },
                    {"segmentCode": "recent", "sourceType": "REALTIME"}
                  ]
                }
                """, "");
    }

    private String snapshotPlanWithMetrics(String metrics) {
        return """
                {
                  "schemaVersion": 2,
                  "executionMode": "SNAPSHOT",
                  "snapshotKeyProviderCode": "VCC_KEYS",
                  "metrics": [%s],
                  "snapshotGranularity": "DAY",
                  "snapshotTargetCode": "refundValue"
                }
                """.formatted(metrics);
    }

    private MetricMaterializationPlanDsl snapshotPlan(List<MetricReferenceDsl> metrics) {
        return new MetricMaterializationPlanDsl(2, MetricQueryMode.SNAPSHOT, "VCC_KEYS", metrics,
                SnapshotGranularity.DAY, "refundValue", null, List.of());
    }

    private void assertViolation(String source, MetricErrorCode errorCode, String fieldPath) {
        MetricValidationException exception = Assertions.assertThrows(
                MetricValidationException.class, () -> codec.parse(source));

        Assertions.assertEquals(errorCode, exception.errorCode());
        Assertions.assertEquals(fieldPath, exception.fieldPath());
    }

    private void assertInvalidPlan(String source, String fieldPath) {
        MetricValidationException exception = Assertions.assertThrows(
                MetricValidationException.class,
                () -> codec.parse(source));

        Assertions.assertEquals(MetricErrorCode.DSL_PLAN_INVALID, exception.errorCode());
        Assertions.assertEquals(fieldPath, exception.fieldPath());
    }

    private void assertInvalidJson(String source) {
        MetricValidationException exception = Assertions.assertThrows(
                MetricValidationException.class,
                () -> codec.parse(source));

        Assertions.assertEquals(MetricErrorCode.DSL_JSON_INVALID, exception.errorCode());
        Assertions.assertEquals("", exception.fieldPath());
    }
}
