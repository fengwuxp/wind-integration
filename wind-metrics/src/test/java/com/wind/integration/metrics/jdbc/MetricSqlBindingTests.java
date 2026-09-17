package com.wind.integration.metrics.jdbc;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 验证 SQL 产物与可变 JDBC 参数的冻结边界。
 *
 * @author wuxp
 */
class MetricSqlBindingTests {

    @Test
    void testBytesAreCopiedOnConstructionAndRead() {
        byte[] bytes = {1, 2, 3};
        MetricSqlBinding binding = new MetricSqlBinding(bytes, Types.BINARY);
        bytes[0] = 9;
        byte[] read = (byte[]) binding.value();
        read[1] = 9;
        Assertions.assertArrayEquals(new byte[] {1, 2, 3}, (byte[]) binding.value());
    }

    @Test
    void testTimestampPreservesNanosAndRejectsExternalMutation() {
        Timestamp timestamp = Timestamp.valueOf("2026-09-15 12:30:00.123456789");
        MetricSqlBinding binding = new MetricSqlBinding(timestamp, Types.TIMESTAMP);
        timestamp.setNanos(0);
        Timestamp read = (Timestamp) binding.value();
        Assertions.assertEquals(123456789, read.getNanos());
        read.setTime(0);
        Assertions.assertEquals(
                Timestamp.valueOf("2026-09-15 12:30:00.123456789"), binding.value());
    }

    @Test
    void testDatesPreserveTheirJdbcTypeAndValue() {
        for (Date date :
                List.of(new Date(1234), new java.sql.Date(1234), new java.sql.Time(1234))) {
            MetricSqlBinding binding = new MetricSqlBinding(date, Types.DATE);
            date.setTime(99);
            Date read = (Date) binding.value();
            Assertions.assertEquals(date.getClass(), read.getClass());
            Assertions.assertEquals(1234, read.getTime());
            read.setTime(0);
            Assertions.assertEquals(1234, ((Date) binding.value()).getTime());
        }
    }

    @Test
    void testCompiledResultCopiesContainersAndPreservesNull() {
        List<MetricSqlBinding> bindings = new ArrayList<>();
        bindings.add(new MetricSqlBinding(null, Types.VARCHAR));
        Map<String, String> projections = new LinkedHashMap<>();
        projections.put("amount", "amount");
        projections.put("count", "count");
        CompiledMetricSql compiled = new CompiledMetricSql("SELECT ?", bindings, projections);
        bindings.clear();
        projections.clear();
        Assertions.assertNull(compiled.bindings().getFirst().value());
        Assertions.assertEquals(
                List.of("amount", "count"), List.copyOf(compiled.projections().keySet()));
        Assertions.assertThrows(
                UnsupportedOperationException.class, () -> compiled.bindings().clear());
        Assertions.assertThrows(
                UnsupportedOperationException.class, () -> compiled.projections().clear());
    }
}
