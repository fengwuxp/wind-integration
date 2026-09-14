package com.wind.integration.metrics.query;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

/**
 * 按生效或指定定义修订查询最终指标值，支持单查及共同条件下的批量查询。
 *
 * <p>宿主加载固定指标定义 DSL 和依赖，校验主体、完整维度、参数及时间区间，
 * 按已选路线取数、合并必要原始状态并求表达式，返回完整 {@link MetricResult}。
 * 调用方不传定义 DSL、物理目标或快照行键；本接口不要求另行调用存储 Reader。</p>
 *
 * <p>REALTIME 按定义计算事实；SNAPSHOT 要求获准查询区间全部被快照覆盖；SEGMENTED
 * 按所选计划及真实覆盖执行各段。快照必须匹配固定成员版本，并使用与写回相同的身份解释和绑定。
 * 查询不得触发物化、推进 Checkpoint、切换生效版本，或在失败时擅自改走其他取数路线。</p>
 *
 * <p>参数校验、授权及主体/维度数据归属由宿主既有应用入口承接。Wind 提供公共合同和模型，
 * 不注册服务实现，也不保证宿主支持所有指标组合；不支持的路线或条件必须明确失败。</p>
 *
 * @author wuxp
 * @since 2026-09-14
 */
public interface MetricValueQueryService {

    /**
     * 按指标当前生效定义及适用路线查询。
     *
     * <p>实现方在本次查询的一致性读取边界内选定实际定义、固定依赖及计划，
     * 执行期间不重新读取变化的生效指针。生效选择不等于最大的定义修订号。</p>
     *
     * @param query 指标编码及主体、半开时间区间、完整维度和参数；时间按宿主选定的业务时区解释
     * @return 包含真实定义修订、单值或 FIELD_SET 及实际取数信息的完整结果，正常空值沿定义表达
     * @throws IllegalArgumentException 查询条件不满足声明或定义约束
     * @throws RuntimeException 指标或路线不可用、覆盖不足，或任一依赖/分段查询失败；具体错误沿宿主合同
     */
    @NotNull
    MetricResult query(@NotNull MetricQuery query);

    /**
     * 按指定的已发布定义修订查询，不修改当前生效选择。
     *
     * <p>指标编码取自 query。宿主加载该精确修订及固定依赖，选择版本匹配且具有查询资格的路线。
     * 找不到适用计划时失败，不能使用其他修订的快照或自动回退实时。草稿及指定候选计划的预览
     * 继续由宿主已有预览能力承接，不属于本方法。</p>
     *
     * @param query 指标编码及完整查询条件
     * @param definitionRevision 正整数定义修订，必须满足宿主已发布定义的查询资格
     * @return 该精确修订的完整结果，definitionRevision 必须等于请求修订
     * @throws IllegalArgumentException 查询条件或定义修订参数不合法
     * @throws RuntimeException 指定修订或匹配路线不可用、覆盖不足，或任一依赖/分段查询失败
     */
    @NotNull
    MetricResult query(@NotNull MetricQuery query, @Positive int definitionRevision);

    /**
     * 在相同主体、半开时间区间和完整维度条件下批量查询各指标的生效定义。
     *
     * <p>实现方先选定每项实际定义、依赖及路线，并完成全部条件校验，再执行查询。
     * 各项保留自己的修订、值结构和数据来源，不共用一个定义修订号，也不因批量而强制走实时。
     * 批量不承诺单条 SQL；宿主须说明支持的指标组合及一致性读取范围。</p>
     *
     * <p>返回顺序和数量与 metricCodes 严格对应；任一项失败则整次抛出异常，不返回部分成功。
     * 当前请求模型不接收查询参数或逐项修订，需要这些条件时使用单查入口。</p>
     *
     * @param query 非空、不重复的指标编码列表及所有指标共用的主体、时间区间和维度条件
     * @return 与请求编码逐项对应的完整结果列表，不包含 null 项
     * @throws IllegalArgumentException 批量条件不合法，或共同主体/完整维度与某项定义不兼容
     * @throws RuntimeException 某项指标、依赖、路线或覆盖不可用，或任一查询失败；不得返回部分成功
     */
    @NotNull
    List<MetricResult> batchQuery(@NotNull MetricBatchQuery query);
}
