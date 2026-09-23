package com.wind.integration.metrics.repository;

import com.wind.integration.metrics.query.MetricQuery;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * 指标快照存储与查询的业务扩展接口，隔离具体业务方的数据访问方式和存储布局。
 *
 * <p>Wind 声明共同读写约定，由拥有目标数据的业务方实现。指标查询和物化流程依赖本接口，
 * 具体实现封装数据源、表列映射、查询条件转换及持久化操作，可以复用所属业务的基础服务。</p>
 *
 * <h2>逻辑快照与存储布局</h2>
 * <p>读写单位是完整逻辑快照，不是物理行。T 可以是业务 DTO、record 或约定的 Map，
 * 保留指标值、业务身份、实际覆盖及实现所需的版本信息；对象类型本身不决定存储布局。</p>
 * <ul>
 * <li>宽表实现：一行保存多个指标或字段，将整行映射为完整快照对象。</li>
 * <li>指标值表实现：一行保存一个指标值或指标字段；相关行共同组成一个逻辑快照，
 * 实现负责组装读取和拆行写入，不能把缺字段的半组记录交给调用方补齐。</li>
 * </ul>
 * <p>例如订单数量和订单金额可以在宽表中占一行，也可以在值表中占两行；两种实现都应向
 * 上层提供该业务键下完整且一致的快照。普通报表宽表只有满足身份、覆盖和并发约定后才能作为快照使用。</p>
 *
 * <h2>职责边界</h2>
 * <p>实现负责主体与完整维度键隔离、目标身份匹配、字段完整性、存储记录的覆盖一致性和并发写入校验。
 * 统计范围内的事实计算、跨段合并、表达式求值、指标累计及物化调度由上层承担；仓储只保存
 * 已计算完成的对象，不再叠加旧值，也不自行选取指标修订、切换计划或在失败时改查实时数据。</p>
 * <p>时间范围均左闭右开。快照对象的实际覆盖末端属性由物化计划的
 * {@code snapshotTarget.bucketTimeField} 指定，该属性表示已提交水位；计划目标时间和计算完成时间
 * 不能替代实际覆盖。跨不同来源的分段衔接仍由查询流程校验。</p>
 *
 * <h2>使用场景与接入建议</h2>
 * <ul>
 * <li>指标查询：读取完整快照及实际覆盖，交给上层完成指标计算。</li>
 * <li>增量物化与恢复：读取最新完整快照确定已提交位置，上层计算后再写回完整对象。</li>
 * <li>业务接入：在装配位置按明确目标选择实现，业务流程仅依赖本接口，避免直接绑定某一种表结构。</li>
 * </ul>
 * <p>读写应使用同一目标身份解释。泛型 T 不能单独证明目标唯一，例如多个仓储都返回 Map 时，
 * 宿主仍需明确其绑定与选择方式。建议用真实持久化测试验证读写往返、业务键隔离、完整组原子性、
 * 并发冲突和失败恢复；接口默认方法的测试不代表宽表或值表实现已经通过这些验证。</p>
 *
 * @param <T> 调用方与具体仓储约定的完整逻辑快照对象类型
 * @author wuxp
 * @since 2026-09-22
 */
public interface MetricSnapshotRepository<T> {

    /**
     * 按主体和完整维度读取快照，按实际覆盖末端时间升序返回。
     *
     * <p>每个元素都应包含同一逻辑快照所需的全部字段及对应覆盖，值表实现须先归组再返回。
     * 返回的值、覆盖和版本应来自一致的已提交状态；缺字段、重复或损坏不能伪装成无记录。
     * 仓储返回自身目标的完整记录，上层负责不同来源之间的连续性及最终计算。</p>
     *
     * @param query 业务查询条件；不支持的条件应拒绝，不得静默忽略
     * @return 完整快照对象列表，不含 null；确无记录时返回空列表，读取失败不得转为空列表
     * @throws RuntimeException 条件不支持、快照不完整或数据访问失败；具体异常沿宿主约定传播
     */
    @NonNull List<T> querySnapshotRecords(@NonNull MetricQuery query);

    /**
     * 写回已经完成累计的快照对象列表，不再次合并旧值。
     *
     * <p>snapshotValue 是已经完成累计的完整对象。宽表实现映射为列，值表实现拆成行；
     * 同一逻辑快照的全部字段、覆盖和版本须原子提交，并核验对象身份与保存请求、绑定目标一致。
     * 不能只写一部分字段或先推进水位。</p>
     * <p>保存上下文以请求中的主体、dimensionValues 和
     * {@code [coverageStartTime, watermarkTime)} 为准；存储维度键由实现或所属基础服务按固定目标生成，
     * 不要求调用方提供。watermarkTime 在请求中只是拟提交末端，成功提交前不得作为已完成覆盖对外可见。</p>
     * <p>不同逻辑快照不要求共享一个事务，因此抛错时可能已有部分对象提交，不能据此假定整批回滚。
     * 并发写入须由实现及其基础服务校验；重试前应回读实际提交状态，不得盲目重放旧计算结果。
     * 正常返回表示全部请求已完成持久化，失败必须抛出异常。</p>
     *
     * @param requests 完整快照保存请求列表；空列表不执行写入
     * @throws RuntimeException 对象或身份不合法、并发冲突或持久化失败；不同对象可能已有部分提交
     */
    void saveAll(@NonNull List<MetricSnapshotSaveRequest<T>> requests);

    /**
     * 取得指定业务条件下实际覆盖末端最新的完整快照。
     *
     * <p>默认委托列表查询并取末项，依赖其升序和完整性约定；最新不按插入时间或计算完成时间判断。
     * 数据量较大时实现可以优化为目标侧查询，但须保持完整组、身份及覆盖语义，不能只取值表最后一行。</p>
     *
     * @param query 业务查询条件
     * @return 最新快照；没有记录时返回 null
     * @throws RuntimeException 条件或快照不合法、数据访问失败；沿列表查询约定传播
     */
    default @Nullable T findLatestSnapshotRecord(@NonNull MetricQuery query) {
        List<T> records = querySnapshotRecords(query);
        return records.isEmpty() ? null : records.getLast();
    }

    /**
     * 单条写回，复用列表保存能力。
     *
     * <p>遵守 {@link #saveAll(List)} 的完整对象、原子提交及并发约定，不额外执行累计计算。</p>
     *
     * @param request 完整快照保存请求
     * @throws RuntimeException 对象或身份不合法、并发冲突或持久化失败
     */
    default void save(@NonNull MetricSnapshotSaveRequest<T> request) {
        saveAll(List.of(request));
    }
}
