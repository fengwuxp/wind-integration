package com.wind.integration.metrics.repository;

import org.jspecify.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 一个完整逻辑快照的保存请求，携带已累计完成的指标对象、业务身份和拟提交的累计覆盖。
 *
 * <p>由物化流程完成事实读取和累计计算后构造，交给 {@link MetricSnapshotRepository#save(MetricSnapshotSaveRequest)}
 * 或 {@link MetricSnapshotRepository#saveAll(java.util.List)}。请求本身不执行查询、累计或持久化，
 * 构造成功也不代表快照已提交。</p>
 *
 * <h2>保存内容与职责边界</h2>
 * <p>snapshotValue 包含该逻辑快照需要共同提交的全部指标字段，仓储不能再次叠加旧值。
 * 请求中的主体、完整维度和覆盖是本次保存上下文的权威输入；业务对象已有的同义元数据须与其一致，
 * 不能另成一套独立输入。版本及目标身份等宿主元数据继续按具体对象和仓储绑定约定处理。</p>
 * <p>请求只传原始 dimensionValues，不要求调用方同时传入派生的存储维度键。
 * 仓储或所属基础服务按已绑定的精确定义与目标生成存储键，并保持与读取侧相同的规范化规则。
 * 宽表实现可以映射为一行，值表实现可以拆成多行；同一逻辑快照的全部值、覆盖和版本须原子保存。</p>
 *
 * <h2>累计覆盖与水位</h2>
 * <p>保存范围为左闭右开的 {@code [coverageStartTime, watermarkTime)}。
 * coverageStartTime 是这份保存对象累计统计的起点，不是本次新增数据的起点，也不必对齐自然时间桶。</p>
 * <p>例如旧快照覆盖 {@code [1日, 5日)}，本次只查询并累计 {@code [5日, 6日)} 的增量，
 * 保存对象覆盖便是 {@code [1日, 6日)}：coverageStartTime 仍为1日，watermarkTime 为6日。
 * 保存请求中的水位是拟提交末端，只有完整对象成功提交后才成为实际已提交水位；
 * 不能用计划目标时间或计算完成时间替代，也不能在写入失败时推进。</p>
 *
 * <h2>使用约定</h2>
 * <p>主体类型和维度结构由已选定义确定；GLOBAL 的 subjectId 为空，无独立维度时传空 Map。
 * 维度容器只做一次只读浅复制；保存对象及维度值自身的生命周期由调用方维护，提交前不得并发修改。
 * 并发校验和事务由仓储及所属基础服务承担，请求不保证重放幂等；失败或应答不明后应回读实际状态再决定后续计算。</p>
 *
 * @param snapshotValue 该逻辑快照的完整累计对象，包含需要共同提交的全部指标字段
 * @param subjectType 主体类型
 * @param subjectId 规范化主体，GLOBAL 为空
 * @param dimensionValues 完整原始维度值；无维度时为空映射，存储维度键由仓储生成
 * @param coverageStartTime 保存对象的累计覆盖起点 B，包含；不是本次增量起点 W
 * @param watermarkTime 本次拟提交的覆盖末端 E，不含；保存成功后才成为已提交水位
 * @param <T> 保存对象类型
 * @author wuxp
 * @since 2026-09-18
 */
public record MetricSnapshotSaveRequest<T>(T snapshotValue, String subjectType, @Nullable String subjectId,
                                           Map<String, Object> dimensionValues, LocalDateTime coverageStartTime, LocalDateTime watermarkTime) {

    public MetricSnapshotSaveRequest {
        Objects.requireNonNull(snapshotValue, "snapshotValue must not be null");
        Objects.requireNonNull(subjectType, "subjectType must not be null");
        Objects.requireNonNull(dimensionValues, "dimensionValues must not be null");
        Objects.requireNonNull(coverageStartTime, "coverageStartTime must not be null");
        Objects.requireNonNull(watermarkTime, "watermarkTime must not be null");
        if (subjectType.isBlank() || !coverageStartTime.isBefore(watermarkTime)) {
            throw new IllegalArgumentException("Snapshot save requires a subject type and nonempty cumulative coverage");
        }
        dimensionValues = Collections.unmodifiableMap(new LinkedHashMap<>(dimensionValues));
    }
}
