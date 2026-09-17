package com.wind.integration.metrics;

import com.wind.integration.metrics.query.MetricQuery;
import com.wind.integration.tag.WindTag;
import com.wind.jackson.WindJson;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 旧查询到通用条件的值保持、旧求值入口和模板属性兼容。 */
@SuppressWarnings("deprecation")
class WindMetricsCriteriaCompatibilityTests {

    @Test
    void testLegacyConditionsRoundTripWithoutInventingDimensionsOrConvertingSubjects() {
        Object context = new Object();
        WindMetricsAggregationQuery legacy = WindMetricsAggregationQuery.newBuilder("USER", Set.of(11L, 12L))
                .tag("currency", "USD").queryVariable(Map.of("runtime", context, "state", "SETTLED")).build();
        MetricQuery criteria = legacy.toCriteria();

        assertEquals(Set.of(11L, 12L), criteria.subjectId());
        assertEquals(Map.of(), criteria.dimensionValues());
        assertSame(context, criteria.parameterValues().get("runtime"));
        assertEquals(legacy, WindMetricsAggregationQuery.fromCriteria(criteria));
        assertNull(criteria.startTime());
        assertNull(criteria.endTime());
    }

    @Test
    void testLegacyNullValuesAndGetterMutabilityRemainAvailable() {
        WindMetricsAggregationQuery empty = new WindMetricsAggregationQuery(null, null, null, null, null, null);
        assertEquals(empty, WindMetricsAggregationQuery.fromCriteria(empty.toCriteria()));
        Map<String, Object> variables = new HashMap<>();
        WindMetricsAggregationQuery legacy = new WindMetricsAggregationQuery("USER", 1L,
                new HashSet<>(), variables, null, null);
        legacy.getQueryVariables().put("state", "SETTLED");
        legacy.getSearchTags().add(WindTag.of("currency", "USD"));
        assertEquals("SETTLED", legacy.toCriteria().parameterValues().get("state"));
        assertEquals(1, legacy.toCriteria().searchTags().size());
    }

    @Test
    void testCommonEvaluatorDelegatesWithoutSerializingRuntimeContext() {
        Object context = new Object();
        MetricQuery criteria = new MetricQuery(List.of(1L, 2L), null, null,
                Map.of(), Map.of("runtime", context), "USER", List.of());
        WindMetricsEvaluator<Object> evaluator = query -> query == null ? null : query.getQueryVariables().get("runtime");

        assertSame(context, evaluator.evaluateWithCriteria(criteria));
        assertNull(evaluator.evaluateWithCriteria(null));
        assertNull(evaluator.evaluate(null));
    }

    @Test
    void testLegacyProjectionRejectsIndependentDimensionsWithoutDroppingOrMergingThem() {
        MetricQuery criteria = new MetricQuery("1", null, null,
                Map.of("currency", "USD"), Map.of("currency", "EUR"));
        WindMetricsEvaluator<Object> evaluator = query -> {
            throw new AssertionError("Invalid projection must fail before evaluation");
        };
        assertThrows(IllegalArgumentException.class, () -> evaluator.evaluateWithCriteria(criteria));
    }

    @Test
    void testNativeCriteriaEvaluatorReceivesDistinctNamespaces() {
        MetricQuery criteria = new MetricQuery("1", null, null,
                Map.of("currency", "USD"), Map.of("currency", "EUR"));
        WindMetricsEvaluator<List<Object>> evaluator = new WindMetricsEvaluator<>() {
            @Override
            public List<Object> evaluate(WindMetricsAggregationQuery query) {
                return evaluateWithCriteria(query.toCriteria());
            }

            @Override
            public List<Object> evaluateWithCriteria(MetricQuery query) {
                return List.of(query.dimensionValues().get("currency"), query.parameterValues().get("currency"));
            }
        };
        assertEquals(List.of("USD", "EUR"), evaluator.evaluateWithCriteria(criteria));
    }

    @Test
    void testLegacyJsonStillContainsOnlyTheSixTemplateProperties() {
        WindMetricsAggregationQuery legacy = WindMetricsAggregationQuery.of("USER", 1L);
        Map<?, ?> json = WindJson.getJsonMapper().readValue(WindJson.toJsonString(legacy), Map.class);
        assertEquals(Set.of("dimensions", "dimensionsId", "searchTags", "queryVariables", "minGmtCreate", "maxGmtCreate"), json.keySet());
    }
}
