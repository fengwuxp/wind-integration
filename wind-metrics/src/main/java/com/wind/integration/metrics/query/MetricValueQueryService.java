package com.wind.integration.metrics.query;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

/**
 * 按生效或指定 DSL 定义修订查询最终指标值，支持单查及共同条件下的批量查询。
 *
 * <p>宿主加载固定指标定义 DSL 和依赖，校验主体、完整维度、参数及时间区间，
 * 按入口约定的实时能力或已生效读取路线取数、合并必要原始状态并求表达式，返回完整 {@link MetricResult}。
 * 调用方不传定义 DSL、物理目标或快照行键；本接口不要求另行调用存储 Reader。</p>
 *
 * <p>REALTIME 按定义计算事实；SNAPSHOT 要求获准查询区间全部被已提交快照覆盖；SEGMENTED
 * 按各 RAW 来源自身的已提交覆盖执行各段。当前生效查询与批量查询消费生效读取绑定和稳定覆盖；
 * 指定修订查询承接实时试算约定，整个依赖闭包按 REALTIME 执行。各入口均不选择候选物化计划、
 * 不重新解释 Plan DSL；已有计划标识只能作为结果溯源。快照必须匹配固定成员版本，并使用与写回相同的
 * 身份解释和绑定。查询不得触发物化、推进 Checkpoint、切换生效版本，或在失败时擅自改走其他取数路线。</p>
 *
 * <p>执行入口依据所选定义和执行模式校验条件；事实 SQL 查询
 * 只接收单个字符串主体或全局主体、必填半开窗口、匹配物理字段的维度及声明的整数参数，不接收查询标签。
 * 通用条件中的 subjectType 非空时必须与所选定义一致；不能忽略不支持的条件后执行。
 * 编码、修订及批量列表也由入口校验，批量还须拒绝非空参数。注解不会自动拦截直接 Java 调用。</p>
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
     * <p>实现方在本次查询的一致性读取边界内选定实际定义、固定依赖及生效读取绑定，
     * 执行期间不重新读取变化的生效指针，也不扫描或选择候选计划。生效选择不等于最大的定义修订号；
     * 结果中的计划标识（若有）只来自底层读取证据。</p>
     *
     * @param metricCode 非空白指标编码，由宿主在执行前校验
     * @param query      主体、半开时间区间、完整维度和参数；时间按宿主选定的业务时区解释
     * @return 包含真实定义修订、单值或 FIELD_SET 及实际取数信息的完整结果，正常空值沿定义表达
     * @throws IllegalArgumentException 查询条件不满足声明或定义约束
     * @throws RuntimeException         指标或路线不可用、覆盖不足，或任一依赖/分段查询失败；具体错误沿宿主合同
     */
    @NotNull
    MetricResult query(@NotBlank String metricCode, @NotNull MetricQuery query);

    /**
     * 按指定已保存定义修订进行实时查询，承接草稿及已发布修订的试算职责。
     *
     * <p>宿主允许符合状态约束的 DRAFT/PUBLISHED 修订；不要求该修订已生效，不修改当前生效选择。
     * 已发布修订使用持久化的精确依赖；草稿依赖在本次一致性读取边界内临时固定，
     * 不回写草稿或依赖关系，不自动替换为依赖的当前修订。依赖必须满足宿主既有发布、启用和读取资格。</p>
     *
     * <p>整个依赖闭包按 REALTIME 能力执行，即使指标当前采用 SNAPSHOT 或 SEGMENTED 路线；
     * 不读取快照、生效读取绑定或候选计划，不触发物化，不改变生效指针或水位。
     * 不支持实时计算时明确失败，不回退到快照；未保存 DSL 不属于本入口。</p>
     *
     * @param metricCode         非空白指标编码
     * @param definitionRevision 非空正整数保存修订，由宿主入口显式校验；null 不表示当前修订
     * @param query              主体、半开时间区间、完整维度和声明参数
     * @return 该精确修订的实时结果，definitionRevision 等于请求修订，保留完整值类型与实际 RAW 来源
     * @throws IllegalArgumentException 编码、修订或条件不合法
     * @throws RuntimeException         修订或依赖不符合查询资格、不支持实时计算，或任一查询失败
     */
    @NotNull
    MetricResult query(@NotBlank String metricCode, @NotNull @Positive Integer definitionRevision, @NotNull MetricQuery query);

    /**
     * 在相同主体、半开时间区间和完整维度条件下批量查询各指标的生效定义。
     *
     * <p>实现方先选定每项实际定义、依赖及路线，并完成全部条件校验，再执行查询。
     * 各项保留自己的修订、值结构和数据来源，不共用一个定义修订号，也不因批量而强制走实时。
     * 批量不承诺单条 SQL；宿主须说明支持的指标组合及一致性读取范围。</p>
     *
     * <p>返回顺序和数量与 metricCodes 严格对应；任一项失败则整次抛出异常，不返回部分成功。
     * 本阶段批量条件的 parameterValues 必须为空，非空应明确拒绝，不能丢弃后执行；
     * 需要查询参数或逐项修订时使用单查入口。输入编码列表须由宿主在执行前校验并固定副本。</p>
     *
     * @param metricCodes 非空、不重复、元素非空白的指标编码列表
     * @param query       所有指标共用的主体、时间区间和完整维度；查询参数必须为空
     * @return 与请求编码逐项对应的完整结果列表，不包含 null 项
     * @throws IllegalArgumentException 批量条件不合法，或共同主体/完整维度与某项定义不兼容
     * @throws RuntimeException         某项指标、依赖、路线或覆盖不可用，或任一查询失败；不得返回部分成功
     */
    @NotNull
    List<MetricResult> batchQuery(@NotEmpty List<@NotBlank String> metricCodes, @NotNull MetricQuery query);

}
