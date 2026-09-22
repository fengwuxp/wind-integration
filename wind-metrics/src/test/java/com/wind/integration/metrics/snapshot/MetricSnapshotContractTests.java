package com.wind.integration.metrics.snapshot;

import com.wind.common.exception.BaseException;
import com.wind.integration.metrics.enums.MetricSegmentCode;
import com.wind.integration.metrics.enums.MetricSegmentSourceType;
import com.wind.integration.metrics.query.MetricQuery;
import com.wind.integration.metrics.query.MetricSegmentResult;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 验证泛型快照与单条默认操作的合同，不替代具体仓储的数据库验证。
 *
 * @author wuxp
 * @since 2026-09-22
 */
class MetricSnapshotContractTests {

    private static final LocalDateTime START = LocalDateTime.of(2024, 1, 1, 0, 0);

    private static final LocalDateTime END = LocalDateTime.of(2026, 6, 24, 10, 30);

    /** 最新读取保留具体业务对象和它自己的时间属性，空结果表示不存在。 */
    @Test
    void testLatestSnapshotRetainsConcreteObjectAndWatermark() {
        CustomerSnapshot first = new CustomerSnapshot(3L, START.plusYears(1));
        CustomerSnapshot latest = new CustomerSnapshot(7L, END);
        MetricQuery query = new MetricQuery("customer-1", null, null, Map.of(), Map.of());

        CustomerSnapshot result = new ListAdapter(List.of(first, latest)).findLatestSnapshotRecord(query);

        Assertions.assertSame(latest, result);
        Assertions.assertEquals(7L, result.orderCount());
        Assertions.assertEquals(END, result.completedThrough());
        Assertions.assertNull(new ListAdapter(List.of()).findLatestSnapshotRecord(query));
    }

    /** 单条保存将完整请求交给批量入口，不丢失覆盖和业务对象。 */
    @Test
    void testSingleSaveDelegatesCompleteRequestToBatch() {
        ListAdapter repository = new ListAdapter(List.of());
        CustomerSnapshot value = new CustomerSnapshot(10L, END);
        MetricSnapshotSaveRequest<CustomerSnapshot> request = new MetricSnapshotSaveRequest<>(value,
                "CUSTOMER", "customer-1", "none", Map.of(), START, END);

        repository.save(request);

        Assertions.assertEquals(1, repository.saved.size());
        Assertions.assertSame(request, repository.saved.getFirst());
        Assertions.assertSame(value, repository.saved.getFirst().snapshotValue());
        Assertions.assertEquals(START, repository.saved.getFirst().bucketStart());
        Assertions.assertEquals(END, repository.saved.getFirst().bucketEnd());
    }

    /** 累计覆盖允许跨自然周期，维度不受调用方后续修改影响。 */
    @Test
    void testSaveRequestPreservesCumulativeRangeAndDimensions() {
        Map<String, Object> dimensions = new LinkedHashMap<>(Map.of("currency", "CNY"));
        MetricSnapshotSaveRequest<CustomerSnapshot> request = new MetricSnapshotSaveRequest<>(new CustomerSnapshot(10L, END),
                "CUSTOMER", "customer-1", "CNY", dimensions, START, END);
        dimensions.put("currency", "USD");

        Assertions.assertEquals(Map.of("currency", "CNY"), request.dimensionValues());
        Assertions.assertEquals(START, request.bucketStart());
        Assertions.assertEquals(END, request.bucketEnd());
        Assertions.assertThrows(UnsupportedOperationException.class, () -> request.dimensionValues().clear());
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> new MetricSnapshotSaveRequest<>(new CustomerSnapshot(0L, END), "GLOBAL", null, "none", Map.of(), END, END));
    }

    /** 整体历史没有统一粒度，但仍须提供完整覆盖。 */
    @Test
    void testWholeHistorySummaryRetainsCoverageWithoutGranularity() {
        MetricSegmentResult summary = new MetricSegmentResult(MetricSegmentCode.ARCHIVE,
                MetricSegmentSourceType.SNAPSHOT, START, END, null, START, END, null);

        Assertions.assertNull(summary.snapshotGranularity());
        Assertions.assertEquals(END, summary.watermarkTime());
        Assertions.assertThrows(BaseException.class,
                () -> new MetricSegmentResult(MetricSegmentCode.ARCHIVE, MetricSegmentSourceType.SNAPSHOT,
                        START, END, null, START, END.minusSeconds(1), null));
    }

    private record CustomerSnapshot(Long orderCount, LocalDateTime completedThrough) {
    }

    /** 仅验证接口默认委托，不模拟数据库存储。 */
    private static class ListAdapter implements MetricSnapshotRepository<CustomerSnapshot> {

        private final List<CustomerSnapshot> records;

        private List<MetricSnapshotSaveRequest<CustomerSnapshot>> saved = List.of();

        private ListAdapter(List<CustomerSnapshot> records) {
            this.records = records;
        }

        @Override
        public List<CustomerSnapshot> querySnapshotRecords(MetricQuery query) {
            return records;
        }

        @Override
        public void saveAll(List<MetricSnapshotSaveRequest<CustomerSnapshot>> requests) {
            saved = requests;
        }
    }
}
