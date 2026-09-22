package com.wind.integration.metrics.snapshot;

import org.jspecify.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 一个完整业务键的累计快照保存对象和覆盖。
 *
 * <p>snapshotValue 已完成累计，仓储不能再次加旧值。具体仓储通过完整对象的元数据及宿主绑定确定保存目标。
 * 维度容器在构造时固定；泛型保存对象及维度值自身的生命周期由调用方维护。</p>
 *
 * @param snapshotValue 该逻辑快照的完整累计对象，包含需要共同提交的全部指标字段
 * @param subjectType 主体类型
 * @param subjectId 规范化主体，GLOBAL 为空
 * @param dimensionKey 规范化完整维度键
 * @param dimensionValues 完整原始维度值，须与 dimensionKey 一致
 * @param bucketStart 累计覆盖起点 B，包含；不是本次增量起点 W
 * @param bucketEnd 新覆盖末端 E，不含
 * @param <T> 保存对象类型
 * @author wuxp
 * @since 2026-09-18
 */
public record MetricSnapshotSaveRequest<T>(T snapshotValue, String subjectType, @Nullable String subjectId, String dimensionKey,
                                           Map<String, Object> dimensionValues, LocalDateTime bucketStart, LocalDateTime bucketEnd) {

    public MetricSnapshotSaveRequest {
        Objects.requireNonNull(snapshotValue, "snapshotValue must not be null");
        Objects.requireNonNull(subjectType, "subjectType must not be null");
        Objects.requireNonNull(dimensionKey, "dimensionKey must not be null");
        Objects.requireNonNull(dimensionValues, "dimensionValues must not be null");
        Objects.requireNonNull(bucketStart, "bucketStart must not be null");
        Objects.requireNonNull(bucketEnd, "bucketEnd must not be null");
        if (subjectType.isBlank() || !bucketStart.isBefore(bucketEnd)) {
            throw new IllegalArgumentException("Snapshot save requires a subject type and nonempty cumulative coverage");
        }
        dimensionValues = Collections.unmodifiableMap(new LinkedHashMap<>(dimensionValues));
    }
}
