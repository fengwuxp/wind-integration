package com.wind.integration.metrics.dsl;

import com.wind.integration.metrics.MetricValidationException;
import com.wind.integration.metrics.dsl.materialization.MetricMaterializationPlanDsl;
import com.wind.integration.metrics.enums.MetricErrorCode;
import com.wind.integration.metrics.enums.MetricSegmentCode;
import com.wind.jackson.WindJson;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
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
              "schemaVersion": 2,
              "metrics": [{"metricCode": "B", "definitionRevision": 7}, {"metricCode": "A"}],
              "executionMode": "SEGMENTED",
              "snapshotKeyProviderCode": "VCC_CUSTOMER_CURRENCY_KEYS",
              "recentWindow": "P90D",
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

    private final MetricMaterializationPlanDslCodec codec = new MetricMaterializationPlanDslCodec();

    private final JsonMapper jsonMapper = WindJson.getJsonMapper();

    @Test
    void testDeserializeNestedPlanWithCanonicalSegmentCodes() {
        PlanRequest request = jsonMapper.readValue("{\"plan\":" + PLAN_JSON + "}", PlanRequest.class);

        Assertions.assertEquals(MetricSegmentCode.ARCHIVE, request.plan().segments().getFirst().segmentCode());
        Assertions.assertEquals(MetricSegmentCode.RECENT, request.plan().segments().getLast().segmentCode());
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
    void testDirectBindingPreservesOptionalRevision() {
        MetricMaterializationPlanDsl plan = jsonMapper.readValue(PLAN_JSON, MetricMaterializationPlanDsl.class);

        Assertions.assertEquals(7, plan.metrics().getFirst().definitionRevision());
        Assertions.assertNull(plan.metrics().getLast().definitionRevision());
        Assertions.assertEquals(codec.canonicalize(plan), jsonMapper.writeValueAsString(plan));
        Assertions.assertEquals(codec.canonicalize(plan),
                codec.canonicalize(jsonMapper.readValue(jsonMapper.writeValueAsString(plan), MetricMaterializationPlanDsl.class)));
    }

    @Test
    void testNestedBindingPreservesOptionalRevision() {
        PlanRequest request = jsonMapper.readValue("{\"plan\":" + PLAN_JSON + "}", PlanRequest.class);
        String canonical = jsonMapper.writeValueAsString(request);
        PlanRequest restored = jsonMapper.readValue(canonical, PlanRequest.class);

        Assertions.assertNull(restored.plan().metrics().getFirst().definitionRevision());
        Assertions.assertEquals(7, restored.plan().metrics().getLast().definitionRevision());
        Assertions.assertFalse(canonical.contains("\"definitionRevision\":null"));
        Assertions.assertEquals(canonical, jsonMapper.writeValueAsString(restored));
    }

    @Test
    void testNestedBindingRejectsExplicitNullRevision() {
        assertInvalidNestedPlan(PLAN_JSON.replace("\"definitionRevision\": 7", "\"definitionRevision\": null"),
                MetricErrorCode.DSL_FIELD_TYPE_INVALID, "/metrics/0/definitionRevision");
    }

    @Test
    void testNestedBindingRejectsDuplicateMetricCode() {
        assertInvalidNestedPlan(PLAN_JSON.replace("\"metricCode\": \"B\"", "\"metricCode\": \"A\""),
                MetricErrorCode.DSL_PLAN_INVALID, "/metrics/1/metricCode");
    }

    @Test
    void testNestedBindingRejectsOldPlanDeclarations() {
        assertInvalidNestedPlan(PLAN_JSON.replace("\"schemaVersion\": 2", "\"schemaVersion\": 1"),
                MetricErrorCode.DSL_SCHEMA_VERSION_UNSUPPORTED, "/schemaVersion");
        assertInvalidNestedPlan(PLAN_JSON.replace("\"schemaVersion\": 2", "\"schemaVersion\": 2, \"dependencies\": []"),
                MetricErrorCode.DSL_FIELD_UNKNOWN, "/dependencies");
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
