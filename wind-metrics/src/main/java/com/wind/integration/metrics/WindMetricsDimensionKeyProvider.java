package com.wind.integration.metrics;

import com.wind.common.query.WindQuery;
import com.wind.common.query.supports.QueryOrderField;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.jspecify.annotations.NonNull;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 分页遍历指定业务维度的完整键，由拥有业务数据的项目实现。
 *
 * <p>支持单维度，例如 userId、tenantId，也支持组合维度，例如 cardId 与 currency。
 * 组合来自真实业务关系或明确的业务生成规则，不默认对各维度值做笛卡尔积。
 * 本接口不感知指标 DSL、物化计划、时间桶、快照存储或 Checkpoint。</p>
 *
 * <p>实现负责权限、数据范围和稳定分页；单次遍历使用一致的完整组合排序，
 * 对稳定的数据范围不得漏键或重复返回同一组合。数据变化时的视图/分页一致性由实现说明，
 * 本接口本身不提供历史时点视图、跨数据源一致性或快照覆盖证明。</p>
 *
 * @author wuxp
 * @since 2026-09-15
 */
public interface WindMetricsDimensionKeyProvider {

    /**
     * 查询一页维度键；每个 Map 是一个完整组合，不能把组合拆成分别分页的维度值。
     *
     * <p>返回的键名集合与 dimensions 一致，值非空，不包含额外运行上下文。
     * 请求维度的 Set 顺序不决定分页顺序。不足 querySize 的非空页仍是有效数据，
     * 调用方继续递增页码直到空列表；空列表只表示遍历结束，不能表示不支持或执行失败。</p>
     *
     * @param dimensions 非空的业务逻辑维度名集合，名称非空白
     * @param queryPage  从 1 开始的页码
     * @param querySize  正整数页大小，按完整组合计数；实现可以限制最大值
     * @return 至多 querySize 个完整维度键，容器及元素非空；空列表表示结束
     * @throws IllegalArgumentException      参数不合法或超出实现声明的分页范围
     * @throws UnsupportedOperationException 不支持请求的维度组合
     * @throws RuntimeException              读取失败，不能将失败转成空页
     */
    @NonNull
    List<Map<String, Serializable>> queryDimensionKeys(
            @NotEmpty Set<@NotBlank String> dimensions,
            @Positive int queryPage,
            @Positive int querySize);

    /**
     * 按本轮固定目标时间枚举一页完整业务键，分页选项复用 {@link WindQuery}。
     *
     * <p>targetWatermark 是本轮目标上界，不是已完成水位，也不是卡片创建时间或事实事件筛选窗口。
     * 实现按业务规则返回截至该时间仍有处理义务的完整候选键，包含首次、无事实及历史有效键。
     * 调用方按精确目标回读各键已提交状态，跳过已完成项并从实际水位续算；本方法不证明目标已完成。</p>
     *
     * <p>dimensions 表示完整业务键字段集，非全局场景包含主体字段和全部维度。
     * 全局且无维度时允许空 Set，对应单例空 Map；它与表示遍历结束的空 List 不同。
     * 返回键名与 dimensions 一致，键值、结果容器和元素非空，不附带运行上下文。</p>
     *
     * <p>实现须在读取前校验并固定参数，支持的页码或游标策略、稳定排序和数据范围由实现明确。
     * querySize 必须为有界正数，不支持的分页、排序或计数请求明确失败；不能忽略选项或隐式修改
     * options 回传位置。页码只适用于已证明稳定的键范围，不能对处理后缩小的待处理集合递增偏移。
     * List 不携带下一游标，采用游标时须有与完整键匹配的实际推进约定，不能仅凭选项类型宣称支持。</p>
     *
     * <p>短非空页仍继续，只有 Provider 的空页表示其声明范围已耗尽；调用方按目标状态过滤后为空，
     * 仍须继续原扫描。读取失败或不支持不能转为空页。持久恢复位置只能越过真正提交完成的前缀；
     * 本接口不承诺跨重启复用游标或历史时点视图。不支持本重载的旧实现默认明确失败，不委托旧入口
     * 忽略目标时间。</p>
     *
     * @param dimensions     完整业务键字段名集合，非 null；无维度的全局场景可为空
     * @param targetWatermark 本轮固定的目标时间；由宿主按业务时区解释，遍历期间保持不变
     * @param options        查询大小、查询类型和排序/分页选项；实现不得将其作为输出参数修改
     * @return 至多 querySize 个完整键；空列表仅表示 Provider 遍历结束
     * @throws IllegalArgumentException      参数不合法、大小越界或条件与续读位置不一致
     * @throws UnsupportedOperationException 实现不支持时间范围枚举、维度组合或指定查询选项
     * @throws RuntimeException              读取失败，不能将失败转成空页
     */
    @NonNull
    default List<Map<String, Serializable>> queryDimensionKeys(
            @NotNull Set<@NotBlank String> dimensions,
            @NotNull LocalDateTime targetWatermark,
            @NotNull WindQuery<? extends QueryOrderField> options) {
        throw new UnsupportedOperationException("Time-bounded dimension key enumeration is not supported");
    }

    /**
     * @return 服务提供编码
     */
    @NonNull
    String getProviderCode();

}
