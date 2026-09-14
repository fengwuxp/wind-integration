package com.wind.integration.metrics.materialization;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.Instant;

/**
 * 按固定物化计划修订生成快照，并返回各快照分段的实际完成覆盖。
 *
 * <p>宿主依据计划编码和修订加载已发布的 Plan DSL、精确指标成员及持久冻结绑定，
 * 完成键枚举、指标计算、业务结果与必要状态保存、回读校验和 Checkpoint 推进。
 * 调用方不重复传 DSL、水位、物理目标或行键。</p>
 *
 * <p>本接口提供同步物化能力，不负责计划创建、发布、启停或任务调度。宿主既有应用入口
 * 负责参数校验、授权、可信操作者和审计；实现继续遵守计划生命周期及执行容量限制。
 * Wind 不提供物化执行器或存储实现。</p>
 *
 * @author wuxp
 * @since 2026-09-14
 */
public interface MetricSnapshotMaterializer {

    /**
     * 将计划的各 SNAPSHOT 分段从已提交水位推进到本次允许的关闭桶目标。
     *
     * <p>首次起点来自计划配置，之后恢复各段 Checkpoint。实现方一次捕获参考时间，
     * 按目标上界、各段粒度、冻结时区、安全延迟、覆盖截止及适用的近期窗口规则计算有效目标。
     * 仅处理完整关闭桶；已覆盖的目标直接返回已有覆盖，水位不回退，也不重算已提交桶。</p>
     *
     * <p>正常返回表示所有应执行的 SNAPSHOT 分段均达到各自有效目标。一个分段的完整桶
     * 是提交边界，包含该桶全部成员、键页、业务结果、必要状态和 Checkpoint CAS。
     * 后续桶或分段失败须抛出异常，但此前已提交桶保留，不能将调用失败理解为整计划回滚。</p>
     *
     * <p>响应丢失时，Checkpoint 只能证明已提交覆盖，不能证明原调用已停止。
     * 使用同一计划修订和目标续进时，须在宿主既有计划及 Checkpoint 事务锁内重读水位；
     * 并发调用等待或报告冲突，不重复累计同一桶。恢复不依赖新增 Run 记录。</p>
     *
     * @param planCode 非空白的稳定计划编码
     * @param planRevision 正整数计划修订，必须具有发布记录并满足宿主既有执行资格
     * @param targetTime 本次希望推进到的时间上界，不表示该时刻已经形成完整桶
     * @return 按计划顺序排列的全部 SNAPSHOT 分段完成结果，不包含 REALTIME 分段
     * @throws IllegalArgumentException 入参违反声明的前置约束
     * @throws RuntimeException 修订或绑定不可用、无执行资格、超过容量限制，或计算、存储、并发提交失败；
     *                          具体错误由宿主异常合同表达，禁止用部分完成结果冒充成功
     */
    @NotNull
    MetricMaterializationResult materialize(
            @NotBlank String planCode,
            @Positive int planRevision,
            @NotNull Instant targetTime);
}
