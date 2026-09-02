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
              "schemaVersion": 1,
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

    private record PlanRequest(MetricMaterializationPlanDsl plan) {
    }
}
