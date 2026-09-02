package com.wind.integration.metrics.dsl;

import com.wind.integration.metrics.MetricValidationException;
import com.wind.integration.metrics.dsl.materialization.MetricMaterializationPlanDsl;
import com.wind.integration.metrics.enums.MetricErrorCode;
import com.wind.integration.metrics.enums.MetricQueryMode;
import com.wind.integration.metrics.enums.MetricSegmentCode;
import com.wind.integration.metrics.enums.MetricSegmentSourceType;
import com.wind.integration.metrics.enums.SnapshotGranularity;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 指标逻辑物化计划的公共 JSON 合同测试。
 *
 * @author wuxp
 * @date 2026-07-21 17:51
 */
class MetricMaterializationPlanDslCodecTests {

    private final MetricMaterializationPlanDslCodec codec = new MetricMaterializationPlanDslCodec();

    @Test
    @DisplayName("DSL-T001 逻辑 Plan 可稳定规范化")
    void testParseAndCanonicalizeSnapshotPlan() {
        String source = """
                {
                  "snapshotTargetCode": "authValue",
                  "snapshotGranularity": "DAY",
                  "snapshotKeyProviderCode": "VCC_CUSTOMER_CURRENCY_KEYS",
                  "executionMode": "SNAPSHOT",
                  "schemaVersion": 1
                }
                """;

        MetricMaterializationPlanDsl plan = codec.parse(source);

        Assertions.assertEquals(MetricQueryMode.SNAPSHOT, plan.executionMode());
        Assertions.assertEquals(
                "{\"schemaVersion\":1,\"executionMode\":\"SNAPSHOT\","
                        + "\"snapshotKeyProviderCode\":\"VCC_CUSTOMER_CURRENCY_KEYS\","
                        + "\"snapshotGranularity\":\"DAY\",\"snapshotTargetCode\":\"authValue\"}",
                codec.canonicalize(plan));
    }

    @Test
    void testNormalizeSegmentedRecentWindow() {
        String source = """
                {
                  "schemaVersion": 1,
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
                  "schemaVersion": 1,
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
                  "schemaVersion": 1,
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
                  "schemaVersion": 1,
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
                  "schemaVersion": 1,
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
                  "schemaVersion": 1,
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
    void testRejectUnsupportedSchemaVersionBeforeV1Fields() {
        MetricValidationException exception = Assertions.assertThrows(
                MetricValidationException.class,
                () -> codec.parse("{\"schemaVersion\":2,\"futureField\":true}"));

        Assertions.assertEquals(MetricErrorCode.DSL_SCHEMA_VERSION_UNSUPPORTED, exception.errorCode());
        Assertions.assertEquals("/schemaVersion", exception.fieldPath());
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
                  "schemaVersion": 1,
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
    void testParseAndCanonicalizeJointDependencies() {
        String source = """
                {
                  "schemaVersion": 1,
                  "executionMode": "SEGMENTED",
                  "snapshotKeyProviderCode": "VCC_CUSTOMER_CURRENCY_KEYS",
                  "dependencies": [
                    {
                      "metricCode": "VCC_REFUND_COUNT",
                      "definitionRevision": 3,
                      "measures": [
                        {"valueField": "latestAt", "mergeState": "MAX"},
                        {"valueField": "value", "mergeState": "SUM"}
                      ]
                    },
                    {
                      "metricCode": "VCC_REFUND_AMOUNT",
                      "definitionRevision": 2,
                      "measures": [
                        {"valueField": "minimum", "mergeState": "MIN"},
                        {"valueField": "value", "mergeState": "SUM"}
                      ]
                    }
                  ],
                  "recentWindow": "P90D",
                  "segments": [
                    {
                      "segmentCode": "archive",
                      "sourceType": "SNAPSHOT",
                      "snapshotGranularity": "DAY",
                      "snapshotTargetCode": "refundRatioArchive"
                    },
                    {"segmentCode": "recent", "sourceType": "REALTIME"}
                  ]
                }
                """;

        MetricMaterializationPlanDsl plan = codec.parse(source);
        String canonical = codec.canonicalize(plan);

        Assertions.assertEquals(2, plan.dependencies().size());
        Assertions.assertTrue(canonical.indexOf("VCC_REFUND_AMOUNT") < canonical.indexOf("VCC_REFUND_COUNT"));
        Assertions.assertTrue(canonical.indexOf("\"valueField\":\"minimum\"")
                < canonical.indexOf("\"valueField\":\"value\""));
        Assertions.assertEquals(canonical, codec.canonicalize(codec.parse(canonical)));
    }

    @Test
    void testRejectExplicitNullDependencies() {
        MetricValidationException exception = Assertions.assertThrows(
                MetricValidationException.class,
                () -> codec.parse("""
                        {
                          "schemaVersion": 1,
                          "executionMode": "SNAPSHOT",
                          "snapshotKeyProviderCode": "VCC_KEYS",
                          "dependencies": null,
                          "snapshotGranularity": "DAY",
                          "snapshotTargetCode": "authValue"
                        }
                        """));

        Assertions.assertEquals(MetricErrorCode.DSL_FIELD_TYPE_INVALID, exception.errorCode());
        Assertions.assertEquals("/dependencies", exception.fieldPath());
    }

    @Test
    void testNormalizeExplicitEmptyDependencies() {
        MetricMaterializationPlanDsl plan = codec.parse("""
                {
                  "schemaVersion": 1,
                  "executionMode": "SNAPSHOT",
                  "snapshotKeyProviderCode": "VCC_KEYS",
                  "dependencies": [],
                  "snapshotGranularity": "DAY",
                  "snapshotTargetCode": "authValue"
                }
                """);

        String canonical = codec.canonicalize(plan);
        Assertions.assertTrue(plan.dependencies().isEmpty());
        Assertions.assertFalse(canonical.contains("\"dependencies\""));
        Assertions.assertEquals(plan, codec.parse(canonical));
    }

    @Test
    void testCanonicalizeSingleDependency() {
        String source = snapshotPlanWithDependencies("""
                {
                  "metricCode": "VCC_REFUND_AMOUNT",
                  "definitionRevision": 1,
                  "measures": [{"valueField": "value", "mergeState": "SUM"}]
                }
                """);

        String canonical = codec.canonicalize(codec.parse(source));

        Assertions.assertEquals(canonical, codec.canonicalize(codec.parse(canonical)));
    }

    @Test
    void testRejectInvalidDependencies() {
        assertInvalidPlan(
                snapshotPlanWithDependencies("""
                        {
                          "metricCode": "VCC_REFUND_AMOUNT",
                          "definitionRevision": 0,
                          "measures": [{"valueField": "value", "mergeState": "SUM"}]
                        }
                        """),
                "/dependencies/0/definitionRevision");
        assertInvalidPlan(
                snapshotPlanWithDependencies("""
                        {
                          "metricCode": "VCC_REFUND_AMOUNT",
                          "definitionRevision": 1,
                          "measures": []
                        }
                        """),
                "/dependencies/0/measures");
        assertInvalidPlan(
                snapshotPlanWithDependencies("""
                        {
                          "metricCode": "VCC_REFUND_AMOUNT",
                          "definitionRevision": 1,
                          "measures": [{"valueField": "value", "mergeState": "SUM"}]
                        },
                        {
                          "metricCode": "VCC_REFUND_AMOUNT",
                          "definitionRevision": 2,
                          "measures": [{"valueField": "value", "mergeState": "SUM"}]
                        }
                        """),
                "/dependencies/1/metricCode");
        assertInvalidPlan(
                snapshotPlanWithDependencies("""
                        {
                          "metricCode": "VCC_REFUND_AMOUNT",
                          "definitionRevision": 1,
                          "measures": [
                            {"valueField": "value", "mergeState": "SUM"},
                            {"valueField": "value", "mergeState": "MAX"}
                          ]
                        },
                        {
                          "metricCode": "VCC_REFUND_COUNT",
                          "definitionRevision": 1,
                          "measures": [{"valueField": "value", "mergeState": "SUM"}]
                        }
                        """),
                "/dependencies/0/measures/1/valueField");
    }

    @Test
    void testRejectUnsupportedJointMergeState() {
        MetricValidationException exception = Assertions.assertThrows(
                MetricValidationException.class,
                () -> codec.parse(snapshotPlanWithDependencies("""
                        {
                          "metricCode": "VCC_REFUND_AMOUNT",
                          "definitionRevision": 1,
                          "measures": [{"valueField": "value", "mergeState": "AVG"}]
                        },
                        {
                          "metricCode": "VCC_REFUND_COUNT",
                          "definitionRevision": 1,
                          "measures": [{"valueField": "value", "mergeState": "SUM"}]
                        }
                        """)));

        Assertions.assertEquals(MetricErrorCode.DSL_VALUE_INVALID, exception.errorCode());
        Assertions.assertEquals("/dependencies/0/measures/0/mergeState", exception.fieldPath());
    }

    @Test
    void testRejectLegacyJointFieldsAndSegmentedRootGranularity() {
        for (String field : new String[]{
                "dependencyClosure", "materializationScope", "watermarkPolicy",
                "sourceReadinessPolicy", "recentReadConsistency"}) {
            MetricValidationException exception = Assertions.assertThrows(
                    MetricValidationException.class,
                    () -> codec.parse("""
                            {
                              "schemaVersion": 1,
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
                  "schemaVersion": 1,
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

    private String snapshotPlanWithDependencies(String dependencies) {
        return """
                {
                  "schemaVersion": 1,
                  "executionMode": "SNAPSHOT",
                  "snapshotKeyProviderCode": "VCC_KEYS",
                  "dependencies": [%s],
                  "snapshotGranularity": "DAY",
                  "snapshotTargetCode": "refundValue"
                }
                """.formatted(dependencies);
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
