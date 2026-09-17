package com.wind.integration.metrics.query;

import com.wind.integration.metrics.MetricValidationException;
import com.wind.integration.metrics.enums.MetricErrorCode;
import com.wind.integration.tag.WindTag;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 指标查询条件的输入及隔离合同。 */
class MetricQueryTests {

    private static final LocalDateTime START = LocalDateTime.of(2026, 9, 1, 0, 0);

    private static final LocalDateTime END = START.plusDays(1);

    @Test
    void testGlobalCriteriaDoesNotRequireMetricIdentity() {
        MetricQuery criteria = new MetricQuery(null, START, END, Map.of(), Map.of());

        assertNull(criteria.subjectId());
        assertEquals(START, criteria.startTime());
        assertEquals(END, criteria.endTime());
    }

    @Test
    void testDslEntryRejectsInvalidSubjectAndWindow() {
        assertEquals("/subjectId", assertThrows(MetricValidationException.class,
                () -> validate(" ", START, END, Map.of(), Map.of())).fieldPath());
        assertEquals("/startTime", assertThrows(MetricValidationException.class,
                () -> validate(null, null, END, Map.of(), Map.of())).fieldPath());
        assertEquals("/endTime", assertThrows(MetricValidationException.class,
                () -> validate(null, START, null, Map.of(), Map.of())).fieldPath());
        for (LocalDateTime end : List.of(START, START.minusSeconds(1))) {
            MetricValidationException error = assertThrows(MetricValidationException.class,
                    () -> validate(null, START, end, Map.of(), Map.of()));
            assertEquals(MetricErrorCode.QUERY_INVALID, error.errorCode());
            assertEquals("/endTime", error.fieldPath());
        }
    }

    @Test
    void testConditionsStayStableWhenCallerMutatesMapsAndDates() {
        Date date = new Date(1_720_000_000_000L);
        Timestamp timestamp = Timestamp.valueOf("2026-09-01 12:30:00.123456789");
        Timestamp expected = (Timestamp) timestamp.clone();
        Map<String, Object> dimensions = new HashMap<>(Map.of("date", date, "timestamp", timestamp));
        Map<String, Object> parameters = new HashMap<>(Map.of("entryLimit", 3));
        MetricQuery criteria = new MetricQuery("user-1", START, END, dimensions, parameters);

        dimensions.clear();
        parameters.clear();
        date.setTime(0L);
        timestamp.setTime(0L);
        ((Date) criteria.dimensionValues().get("date")).setTime(1L);
        ((Timestamp) criteria.dimensionValues().get("timestamp")).setNanos(0);

        assertEquals(1_720_000_000_000L, ((Date) criteria.dimensionValues().get("date")).getTime());
        assertEquals(expected, criteria.dimensionValues().get("timestamp"));
        assertEquals(Map.of("entryLimit", 3), criteria.parameterValues());
        assertThrows(UnsupportedOperationException.class, () -> criteria.dimensionValues().clear());
        assertThrows(UnsupportedOperationException.class, () -> criteria.parameterValues().clear());
    }

    @Test
    void testDslEntryRejectsUnsupportedDimensionsAndNonIntegerParameters() {
        assertEquals("/dimensionValues", assertThrows(MetricValidationException.class,
                () -> validate(null, START, END, null, Map.of())).fieldPath());
        for (Object dimension : List.of(List.of("USD"), Map.of("code", "USD"), 1.5D)) {
            assertEquals("/dimensionValues/currency", assertThrows(MetricValidationException.class,
                    () -> validate(null, START, END, Map.of("currency", dimension), Map.of()))
                    .fieldPath());
        }
        for (Object parameter : List.of("2", 2L, 2.0D, List.of(2))) {
            MetricValidationException error = assertThrows(MetricValidationException.class,
                    () -> validate(null, START, END, Map.of(), Map.of("entryLimit", parameter)));
            assertEquals(MetricErrorCode.METRIC_PARAMETER_TYPE_MISMATCH, error.errorCode());
            assertEquals("/parameterValues/entryLimit", error.fieldPath());
        }
        assertEquals("/parameterValues", assertThrows(MetricValidationException.class,
                () -> validate(null, START, END, Map.of(), null)).fieldPath());
    }


    @Test
    void testGeneralCriteriaKeepsMultipleSubjectsUnboundedTimeAndRuntimeVariables() {
        Object context = new Object();
        MetricQuery criteria = new MetricQuery(List.of(11L, 12L), null, null,
                Map.of(), Map.of("currency", "USD", "context", context), "USER",
                List.of(WindTag.of("region", "CN")));

        assertEquals(List.of(11L, 12L), criteria.subjectId());
        assertNull(criteria.startTime());
        assertNull(criteria.endTime());
        assertSame(context, criteria.parameterValues().get("context"));
        assertEquals("USER", criteria.subjectType());
        assertEquals("CN", criteria.searchTags().iterator().next().value());
        assertEquals("/subjectId", assertThrows(MetricValidationException.class,
                () -> MetricQueryValidator.validateDsl(criteria)).fieldPath());
    }

    @Test
    void testDslEntryDoesNotSilentlyIgnoreTags() {
        MetricQuery criteria = new MetricQuery("user-1", START, END, Map.of(), Map.of(),
                "USER", List.of(WindTag.of("region", "CN")));
        assertEquals("/searchTags", assertThrows(MetricValidationException.class,
                () -> MetricQueryValidator.validateDsl(criteria)).fieldPath());
    }

    private static void validate(Object subjectId, LocalDateTime start, LocalDateTime end,
                                 Map<String, Object> dimensions, Map<String, Object> parameters) {
        MetricQueryValidator.validateDsl(new MetricQuery(subjectId, start, end, dimensions, parameters));
    }
}
