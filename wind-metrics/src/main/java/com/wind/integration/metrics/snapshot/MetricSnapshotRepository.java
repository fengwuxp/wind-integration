package com.wind.integration.metrics.snapshot;

import com.wind.integration.metrics.query.MetricQuery;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * 指标快照的查询与写回，由拥有目标数据的宿主实现。
 *
 * <p>返回具体的完整快照对象，保留指标值、身份和最后快照水位；时间属性由物化计划的 snapshotTarget.bucketTimeField 指定。
 * 实现负责业务键隔离、完整记录、连续覆盖及并发写入校验，不执行事实统计或指标累计。
 * 所有时间范围均左闭右开。</p>
 *
 * @param <T> 具体的完整快照对象类型
 * @author wuxp
 * @since 2026-09-22
 */
public interface MetricSnapshotRepository<T> {

    /**
     * 按主体和完整维度读取快照，按实际覆盖末端时间升序返回。
     *
     * @param query 业务查询条件；不支持的条件应拒绝，不得静默忽略
     * @return 完整快照对象列表，不含 null；确无记录时返回空列表，读取失败不得转为空列表
     */
    @NonNull List<T> querySnapshotRecords(@NonNull MetricQuery query);

    /**
     * 写回已经完成累计的快照对象列表，不再次合并旧值。
     *
     * <p>同一逻辑快照的全部字段、覆盖和版本须原子提交；不要求不同快照之间共享一个事务。
     * 发生错误应抛出异常，不能把失败报告为成功。</p>
     *
     * @param requests 完整快照保存请求列表；空列表不执行写入
     */
    void saveAll(@NonNull List<MetricSnapshotSaveRequest<T>> requests);

    /**
     * 从按时间升序的结果中取得最新的完整快照。
     *
     * @param query 业务查询条件
     * @return 最新快照；没有记录时返回 null
     */
    default @Nullable T findLatestSnapshotRecord(@NonNull MetricQuery query) {
        List<T> records = querySnapshotRecords(query);
        return records.isEmpty() ? null : records.getLast();
    }

    /**
     * 单条写回，复用列表保存能力。
     *
     * @param request 完整快照保存请求
     */
    default void save(@NonNull MetricSnapshotSaveRequest<T> request) {
        saveAll(List.of(request));
    }
}
