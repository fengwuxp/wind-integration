package com.wind.integration.metrics;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.io.Serializable;
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
@FunctionalInterface
public interface WindMetricsDimensionKeyProvider {

    /**
     * 查询一页维度键；每个 Map 是一个完整组合，不能把组合拆成分别分页的维度值。
     *
     * <p>返回的键名集合与 dimensions 一致，值非空，不包含额外运行上下文。
     * 请求维度的 Set 顺序不决定分页顺序。不足 querySize 的非空页仍是有效数据，
     * 调用方继续递增页码直到空列表；空列表只表示遍历结束，不能表示不支持或执行失败。</p>
     *
     * @param dimensions 非空的业务逻辑维度名集合，名称非空白
     * @param queryPage 从 1 开始的页码
     * @param querySize 正整数页大小，按完整组合计数；实现可以限制最大值
     * @return 至多 querySize 个完整维度键，容器及元素非空；空列表表示结束
     * @throws IllegalArgumentException 参数不合法或超出实现声明的分页范围
     * @throws UnsupportedOperationException 不支持请求的维度组合
     * @throws RuntimeException 读取失败，不能将失败转成空页
     */
    @NotNull
    List<Map<String, Serializable>> queryDimensionKeys(
            @NotEmpty Set<@NotBlank String> dimensions,
            @Positive int queryPage,
            @Positive int querySize);
}
