package com.wind.integration.metrics.dsl;

import com.wind.integration.metrics.MetricValidationException;
import com.wind.integration.metrics.dsl.materialization.MetricMaterializationPlanDsl;
import com.wind.integration.metrics.enums.MetricErrorCode;
import com.wind.integration.metrics.enums.MetricSegmentCode;
import com.wind.integration.metrics.json.MetricMaterializationPlanDslCodec;
import com.wind.jackson.WindJson;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.json.JsonMapper;

/**
 * 指标物化 Plan DSL 的 Jackson HTTP JSON 契约测试。
 *
 * @author wuxp
 * @date 2026-09-02 16:20
 */
class MetricMaterializationPlanDslJsonBindingTests {

    private static final String PLAN_JSON = """
            {
              "schemaVersion": 3,
              "metrics": [{"metricCode": "B", "definitionRevision": 7}, {"metricCode": "A", "definitionRevision": 2}],
              "executionMode": "SEGMENTED",
              "dimensionKeyProviderCode": "VCC_CUSTOMER_CURRENCY_KEYS",
              "recentWindow": "P90D",
              "segments": [
                {
                  "segmentCode": "archive",
                  "sourceType": "SNAPSHOT",
                  "snapshotGranularity": "DAY",
                  "snapshotTarget": {"storageType": "METRIC_VALUE_TABLE",
                    "bucketTimeField": "bucketEndTime",
                    "objectTypeClassName": "com.example.SnapshotRow"}
                },
                {"segmentCode": "recent", "sourceType": "REALTIME"}
              ]
            }
            """;

    private final MetricMaterializationPlanDslCodec codec = new MetricMaterializationPlanDslCodec();

    private final JsonMapper jsonMapper = WindJson.getJsonMapper();

    @Test
    void testDeserializeNestedPlanWithCanonicalSegmentCodes() {
        PlanRequest request = jsonMapper.readValue("{\"plan\":" + PLAN_JSON + "}", PlanRequest.class);

        Assertions.assertEquals(MetricSegmentCode.ARCHIVE, request.plan().segments().getFirst().segmentCode());
        Assertions.assertEquals(MetricSegmentCode.RECENT, request.plan().segments().getLast().segmentCode());
        Assertions.assertEquals("VCC_CUSTOMER_CURRENCY_KEYS", request.plan().dimensionKeyProviderCode());
    }

    @Test
    void testSerializeNestedPlanAsCanonicalDslJson() {
        MetricMaterializationPlanDsl plan = codec.parse(PLAN_JSON);

        String json = jsonMapper.writeValueAsString(new PlanRequest(plan));

        Assertions.assertEquals("{\"plan\":" + codec.canonicalize(plan) + "}", json);
    }

    @Test
    void testRejectExplicitNullPlan() {
        DatabindException exception = Assertions.assertThrows(
                DatabindException.class,
                () -> jsonMapper.readValue("{\"plan\":null}", PlanRequest.class));
        MetricValidationException cause = Assertions.assertInstanceOf(
                MetricValidationException.class, exception.getCause());

        Assertions.assertEquals(MetricErrorCode.DSL_ROOT_NOT_OBJECT, cause.errorCode());
        Assertions.assertEquals("", cause.fieldPath());
    }

    @Test
    void testDirectBindingPreservesExplicitRevision() {
        MetricMaterializationPlanDsl plan = jsonMapper.readValue(PLAN_JSON, MetricMaterializationPlanDsl.class);

        Assertions.assertEquals(7, plan.metrics().getFirst().definitionRevision());
        Assertions.assertEquals(2, plan.metrics().getLast().definitionRevision());
        Assertions.assertEquals("VCC_CUSTOMER_CURRENCY_KEYS", plan.dimensionKeyProviderCode());
        Assertions.assertEquals(codec.canonicalize(plan), jsonMapper.writeValueAsString(plan));
        Assertions.assertEquals(codec.canonicalize(plan),
                codec.canonicalize(jsonMapper.readValue(jsonMapper.writeValueAsString(plan), MetricMaterializationPlanDsl.class)));
    }

    @Test
    void testNestedBindingPreservesExplicitRevision() {
        PlanRequest request = jsonMapper.readValue("{\"plan\":" + PLAN_JSON + "}", PlanRequest.class);
        String canonical = jsonMapper.writeValueAsString(request);
        PlanRequest restored = jsonMapper.readValue(canonical, PlanRequest.class);

        Assertions.assertEquals(2, restored.plan().metrics().getFirst().definitionRevision());
        Assertions.assertEquals(7, restored.plan().metrics().getLast().definitionRevision());
        Assertions.assertEquals("VCC_CUSTOMER_CURRENCY_KEYS", restored.plan().dimensionKeyProviderCode());
        Assertions.assertTrue(canonical.contains("\"dimensionKeyProviderCode\":\"VCC_CUSTOMER_CURRENCY_KEYS\""));
        Assertions.assertFalse(canonical.contains("snapshotKeyProviderCode"));
        Assertions.assertFalse(canonical.contains("\"definitionRevision\":null"));
        Assertions.assertEquals(canonical, jsonMapper.writeValueAsString(restored));
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "null", "0", "-1"})
    void testDirectAndNestedBindingRequirePositiveMemberRevision(String revision) {
        String source = "missing".equals(revision)
                ? PLAN_JSON.replace(", \"definitionRevision\": 7", "")
                : PLAN_JSON.replace("\"definitionRevision\": 7", "\"definitionRevision\": " + revision);
        MetricErrorCode expected = "missing".equals(revision) || "null".equals(revision)
                ? MetricErrorCode.DSL_FIELD_REQUIRED : MetricErrorCode.DSL_PLAN_INVALID;
        MetricValidationException parsed = Assertions.assertThrows(MetricValidationException.class,
                () -> codec.parse(source));
        Assertions.assertEquals(expected, parsed.errorCode());
        Assertions.assertEquals("/metrics/0/definitionRevision", parsed.fieldPath());
        MetricValidationException direct = Assertions.assertThrows(MetricValidationException.class,
                () -> jsonMapper.readValue(source, MetricMaterializationPlanDsl.class));
        Assertions.assertEquals(expected, direct.errorCode());
        Assertions.assertEquals(parsed.fieldPath(), direct.fieldPath());
        assertInvalidNestedPlan(source, expected, parsed.fieldPath());
    }

    @Test
    void testNestedBindingRejectsDuplicateMetricCode() {
        assertInvalidNestedPlan(PLAN_JSON.replace("\"metricCode\": \"B\"", "\"metricCode\": \"A\""),
                MetricErrorCode.DSL_PLAN_INVALID, "/metrics/1/metricCode");
    }

    @Test
    void testNestedBindingRejectsOldPlanDeclarations() {
        assertInvalidNestedPlan(PLAN_JSON.replace("\"schemaVersion\": 3", "\"schemaVersion\": 1"),
                MetricErrorCode.DSL_SCHEMA_VERSION_UNSUPPORTED, "/schemaVersion");
        assertInvalidNestedPlan(PLAN_JSON.replace("\"schemaVersion\": 3", "\"schemaVersion\": 3, \"dependencies\": []"),
                MetricErrorCode.DSL_FIELD_UNKNOWN, "/dependencies");
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "null", "blank", "number", "invalid"})
    void testDirectAndNestedBindingRequireDimensionProvider(String input) {
        String declaration = "\"dimensionKeyProviderCode\": \"VCC_CUSTOMER_CURRENCY_KEYS\"";
        String source = switch (input) {
            case "missing" -> PLAN_JSON.replace(declaration + ",", "");
            case "null" -> PLAN_JSON.replace(declaration, "\"dimensionKeyProviderCode\": null");
            case "blank" -> PLAN_JSON.replace("VCC_CUSTOMER_CURRENCY_KEYS", " ");
            case "number" -> PLAN_JSON.replace(declaration, "\"dimensionKeyProviderCode\": 123");
            default -> PLAN_JSON.replace("VCC_CUSTOMER_CURRENCY_KEYS", "INVALID-KEYS");
        };
        MetricErrorCode expected = switch (input) {
            case "missing", "null" -> MetricErrorCode.DSL_FIELD_REQUIRED;
            case "blank", "number" -> MetricErrorCode.DSL_FIELD_TYPE_INVALID;
            default -> MetricErrorCode.DSL_IDENTIFIER_INVALID;
        };

        assertInvalidDirectAndNestedPlan(source, expected, "/dimensionKeyProviderCode");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void testRejectLegacyProviderNameEvenWhenNewNameIsPresent(boolean includeNewName) {
        String source = includeNewName
                ? PLAN_JSON.replace("\"dimensionKeyProviderCode\":",
                        "\"snapshotKeyProviderCode\": \"OTHER_KEYS\", \"dimensionKeyProviderCode\":")
                : PLAN_JSON.replace("dimensionKeyProviderCode", "snapshotKeyProviderCode");

        assertInvalidDirectAndNestedPlan(source, MetricErrorCode.DSL_FIELD_UNKNOWN, "/snapshotKeyProviderCode");
    }

    private void assertInvalidDirectAndNestedPlan(String source, MetricErrorCode errorCode, String fieldPath) {
        MetricValidationException parsed = Assertions.assertThrows(MetricValidationException.class,
                () -> codec.parse(source));
        Assertions.assertEquals(errorCode, parsed.errorCode());
        Assertions.assertEquals(fieldPath, parsed.fieldPath());
        MetricValidationException direct = Assertions.assertThrows(MetricValidationException.class,
                () -> jsonMapper.readValue(source, MetricMaterializationPlanDsl.class));
        Assertions.assertEquals(errorCode, direct.errorCode());
        Assertions.assertEquals(fieldPath, direct.fieldPath());
        assertInvalidNestedPlan(source, errorCode, fieldPath);
    }

    private void assertInvalidNestedPlan(String plan, MetricErrorCode errorCode, String fieldPath) {
        DatabindException exception = Assertions.assertThrows(DatabindException.class,
                () -> jsonMapper.readValue("{\"plan\":" + plan + "}", PlanRequest.class));
        MetricValidationException cause = Assertions.assertInstanceOf(MetricValidationException.class, exception.getCause());

        Assertions.assertEquals(errorCode, cause.errorCode());
        Assertions.assertEquals(fieldPath, cause.fieldPath());
    }

    private record PlanRequest(MetricMaterializationPlanDsl plan) {
    }
}
