package com.wind.integration.metrics.repository;

import com.wind.integration.metrics.query.MetricQuery;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 验证仓储默认方法和保存请求的实际行为；列表读写用端口替身承接，不证明宿主持久化或事务。
 *
 * @author wuxp
 */
class MetricSnapshotRepositoryTests {

    private static final LocalDateTime START = LocalDateTime.of(2026, 1, 1, 0, 0);

    private static final MetricQuery QUERY = MetricQuery.builder().subjectId("user-1").subjectType("USER")
            .dimension("currency", "USD").planCode("PAYMENTS").planRevision(2).build();

    /** 升序完整记录中末项为最新状态；主体、计划条件和值、版本及实际覆盖均原样保留。 */
    @Test
    void testLatestReturnsCompleteLastRecordWithOriginalQuery() {
        Snapshot older = new Snapshot(new BigDecimal("12.50"), 1, START, START.plusMonths(1));
        Snapshot latest = new Snapshot(null, 2, START, START.plusMonths(2));
        RecordingRepository repository = new RecordingRepository(List.of(older, latest));

        Snapshot result = repository.findLatestSnapshotRecord(QUERY);

        assertSame(latest, result);
        assertNull(result.amount());
        assertEquals(2, result.version());
        assertEquals(START.plusMonths(2), result.endTime());
        assertSame(QUERY, repository.lastQuery);
    }

    /** 无记录返回 null，读取故障必须原样传播，不能伪装成无记录。 */
    @Test
    void testMissingAndFailedReadsRemainDistinct() {
        RecordingRepository repository = new RecordingRepository(List.of());
        assertNull(repository.findLatestSnapshotRecord(QUERY));
        IllegalStateException failure = new IllegalStateException("Storage unavailable");
        repository.readFailure = failure;

        assertSame(failure, assertThrows(IllegalStateException.class, () -> repository.findLatestSnapshotRecord(QUERY)));
    }

    /** 单条保存委托完整请求；维度构造后隔离，累计覆盖起点不改成增量起点，写前不额外读旧值。 */
    @Test
    void testSaveRetainsCumulativeCoverageAndCopiedDimensions() {
        Snapshot snapshot = new Snapshot(new BigDecimal("17.50"), 3, START, START.plusMonths(3));
        Map<String, Object> dimensions = new LinkedHashMap<>(Map.of("currency", "USD"));
        MetricSnapshotSaveRequest<Snapshot> request = new MetricSnapshotSaveRequest<>(snapshot, "USER", "user-1",
                dimensions, START, snapshot.endTime());
        dimensions.put("currency", "EUR");
        RecordingRepository repository = new RecordingRepository(List.of());
        repository.readFailure = new IllegalStateException("Save must not load and accumulate old values");

        repository.save(request);

        assertEquals(List.of(request), repository.saved);
        MetricSnapshotSaveRequest<Snapshot> saved = repository.saved.getFirst();
        assertSame(snapshot, saved.snapshotValue());
        assertEquals(Map.of("currency", "USD"), saved.dimensionValues());
        assertEquals(START, saved.coverageStartTime());
        assertEquals(snapshot.endTime(), saved.watermarkTime());
        assertThrows(UnsupportedOperationException.class, () -> saved.dimensionValues().clear());
    }

    /** 列表保存拒绝写入时，单条入口原样传播失败，不返回虚假成功。 */
    @Test
    void testSingleSavePropagatesStorageFailure() {
        RecordingRepository repository = new RecordingRepository(List.of());
        IllegalStateException failure = new IllegalStateException("Snapshot version conflict");
        repository.writeFailure = failure;
        Snapshot value = new Snapshot(BigDecimal.ONE, 1, START, START.plusDays(1));
        MetricSnapshotSaveRequest<Snapshot> request = new MetricSnapshotSaveRequest<>(value, "USER", "user-1", Map.of(), START, value.endTime());

        assertSame(failure, assertThrows(IllegalStateException.class, () -> repository.save(request)));
        assertEquals(List.of(), repository.saved);
    }

    private record Snapshot(BigDecimal amount, int version, LocalDateTime startTime, LocalDateTime endTime) {
    }

    /** 只提供有序读取事实、捕获保存请求及注入存储故障；被测默认方法来自真实接口。 */
    private static final class RecordingRepository implements MetricSnapshotRepository<Snapshot> {

        private final List<Snapshot> records;

        private MetricQuery lastQuery;

        private List<MetricSnapshotSaveRequest<Snapshot>> saved = List.of();

        private RuntimeException readFailure;

        private RuntimeException writeFailure;

        private RecordingRepository(List<Snapshot> records) {
            this.records = records;
        }

        @Override
        public List<Snapshot> querySnapshotRecords(MetricQuery query) {
            lastQuery = query;
            if (readFailure != null) {
                throw readFailure;
            }
            return records;
        }

        @Override
        public void saveAll(List<MetricSnapshotSaveRequest<Snapshot>> requests) {
            if (writeFailure != null) {
                throw writeFailure;
            }
            saved = List.copyOf(requests);
        }
    }
}
