package com.wind.integration.metrics.query;

import com.wind.integration.metrics.MetricValidationException;
import com.wind.integration.metrics.enums.MetricErrorCode;
import com.wind.jackson.WindJson;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.DeserializationFeature;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 新条件 JSON 与旧请求 JSON 的协议边界。 */
class MetricQueryJsonParserTests {

    private static final String JSON = """
            {"subjectId":"user-1","startTime":"2026-09-01T00:00:00",
             "endTime":"2026-09-02T00:00:00","dimensionValues":{"currency":"USD"}}
            """;

    private final MetricQueryJsonParser parser = new MetricQueryJsonParser();

    @Test
    void testDeserializeAndSerializeOnlyCommonConditions() {
        MetricQuery criteria = WindJson.getJsonMapper().readValue(JSON, MetricQuery.class);

        assertEquals("user-1", criteria.subjectId());
        assertEquals(Map.of(), criteria.parameterValues());
        assertEquals(Set.of("subjectId", "startTime", "endTime", "dimensionValues", "parameterValues", "subjectType", "searchTags"),
                WindJson.getJsonMapper().readValue(WindJson.toJsonString(criteria), Map.class).keySet());
        assertEquals(criteria, parser.parse(WindJson.toJsonString(criteria)));
    }

    @Test
    void testAcceptSpaceSeparatedTimeAndIntegerLimits() {
        MetricQuery criteria = parser.parse(extra(JSON.replace("T00:00:00", " 00:00:00"),
                "parameterValues", "{\"min\":-2147483648,\"max\":2147483647}"));

        assertEquals(LocalDateTime.of(2026, 9, 1, 0, 0), criteria.startTime());
        assertEquals(Map.of("min", Integer.MIN_VALUE, "max", Integer.MAX_VALUE), criteria.parameterValues());
    }

    @Test
    void testRejectMetricIdentityAndServerFieldsEvenWithLenientMapper() {
        var mapper = WindJson.getJsonMapper().rebuild()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
        for (String field : List.of("metricCode", "metricCodes", "definitionRevision", "executionMode",
                "planCode", "tableName", "filter", "dimensionKey")) {
            String json = extra(JSON, field, "\"unexpected\"");
            MetricValidationException parsed = assertThrows(MetricValidationException.class, () -> parser.parse(json));
            MetricValidationException bound = assertThrows(MetricValidationException.class,
                    () -> mapper.readValue(json, MetricQuery.class));
            assertEquals(MetricErrorCode.QUERY_INVALID, parsed.errorCode());
            assertEquals("/" + field, parsed.fieldPath());
            assertEquals(parsed.fieldPath(), bound.fieldPath());
        }
    }

    @Test
    void testRejectMalformedDuplicateAndTrailingJson() {
        assertEquals(MetricErrorCode.DSL_JSON_INVALID,
                assertThrows(MetricValidationException.class, () -> parser.parse(JSON + "{}")).errorCode());
        assertEquals(MetricErrorCode.DSL_JSON_INVALID,
                assertThrows(MetricValidationException.class, () -> parser.parse("{\"subjectId\":]")).errorCode());
        MetricValidationException duplicated = assertThrows(MetricValidationException.class,
                () -> parser.parse(extra(JSON, "subjectId", "\"other\"")));
        assertEquals(MetricErrorCode.DSL_FIELD_DUPLICATED, duplicated.errorCode());
        assertEquals("/subjectId", duplicated.fieldPath());
        assertEquals(MetricErrorCode.QUERY_INVALID, assertThrows(MetricValidationException.class,
                () -> WindJson.getJsonMapper().readValue("null", MetricQuery.class)).errorCode());
    }

    @Test
    void testRejectInvalidParameterValuesAndEscapeTheirPaths() {
        for (String value : List.of("null", "\"2\"", "2.0", "2147483648", "-2147483649", "[]", "{}")) {
            MetricValidationException error = assertThrows(MetricValidationException.class,
                    () -> parser.parseDsl(extra(JSON, "parameterValues", "{\"a~/b\":" + value + "}")));
            assertEquals(MetricErrorCode.METRIC_PARAMETER_TYPE_MISMATCH, error.errorCode());
            assertEquals("/parameterValues/a~0~1b", error.fieldPath());
        }
        assertEquals("/parameterValues", assertThrows(MetricValidationException.class,
                () -> parser.parseDsl(extra(JSON, "parameterValues", "null"))).fieldPath());
    }

    @Test
    void testPreserveDecimalDimensionPrecision() {
        MetricQuery criteria = parser.parse(JSON.replace("\"USD\"", "12345678901234567890.123456789"));

        assertEquals(new BigDecimal("12345678901234567890.123456789"), criteria.dimensionValues().get("currency"));
    }

    @Test
    void testBindNestedCriteriaWithoutReadingPastItsObject() {
        Envelope envelope = WindJson.getJsonMapper().readValue(
                "{\"criteria\":" + JSON + ",\"label\":\"after\"}", Envelope.class);

        assertEquals("after", envelope.label());
        assertEquals("user-1", envelope.criteria().subjectId());
    }

    private static String extra(String source, String name, String value) {
        return source.substring(0, source.lastIndexOf('}')) + ",\"" + name + "\":" + value + "}";
    }

    private record Envelope(MetricQuery criteria, String label) {
    }

    @Test
    void testGeneralJsonKeepsLegacyConditionsAndDslParsingRejectsThem() {
        String json = """
                {"subjectType":"USER","subjectId":[11,12],"searchTags":[{"name":"region","value":"CN"}],
                 "parameterValues":{"currency":"USD","ratio":1.25,"states":["SETTLED","PENDING"]}}
                """;
        MetricQuery criteria = parser.parse(json);
        assertEquals(List.of(11, 12), criteria.subjectId());
        assertEquals(Map.of(), criteria.dimensionValues());
        assertEquals("CN", criteria.searchTags().iterator().next().value());
        assertEquals(new BigDecimal("1.25"), criteria.parameterValues().get("ratio"));
        assertEquals(criteria, parser.parse(WindJson.toJsonString(criteria)));
        assertThrows(MetricValidationException.class, () -> parser.parseDsl(json));
    }

    @Test
    void testDslParserPreservesInvalidParameterContainerError() {
        for (String parameters : List.of("[]", "null", "2", "\"text\"", "{\"\":2}")) {
            MetricValidationException failure = assertThrows(MetricValidationException.class,
                    () -> parser.parseDsl(extra(JSON, "parameterValues", parameters)));
            assertEquals(MetricErrorCode.METRIC_PARAMETER_TYPE_MISMATCH, failure.errorCode());
            assertEquals("/parameterValues", failure.fieldPath());
        }
    }
    @Test
    void testGeneralJsonPreservesExplicitNullContainers() {
        MetricQuery criteria = parser.parse("""
                {"dimensionValues":null,"parameterValues":null,"searchTags":null}
                """);
        assertNull(criteria.dimensionValues());
        assertNull(criteria.parameterValues());
        assertNull(criteria.searchTags());
        assertEquals(criteria, parser.parse(WindJson.toJsonString(criteria)));
    }

    @Test
    void testTagJsonRejectsUnknownAttributesAndInvalidShapes() {
        for (String tags : List.of("{}", "[null]", "[{\"name\":\"region\",\"value\":\"CN\",\"unexpected\":true}]")) {
            MetricValidationException failure = assertThrows(MetricValidationException.class,
                    () -> parser.parse(extra(JSON, "searchTags", tags)));
            assertEquals(MetricErrorCode.QUERY_INVALID, failure.errorCode());
            assertTrue(failure.fieldPath().startsWith("/searchTags"));
        }
    }

    @Test
    void testSubjectTypeDoesNotCoerceNonStringJson() {
        for (String value : List.of("true", "12", "{}")) {
            MetricValidationException error = assertThrows(MetricValidationException.class,
                    () -> parser.parse(extra(JSON, "subjectType", value)));
            assertEquals("/subjectType", error.fieldPath());
        }
    }

}
