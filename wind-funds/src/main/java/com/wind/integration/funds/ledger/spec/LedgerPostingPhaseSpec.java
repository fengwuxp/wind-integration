package com.wind.integration.funds.ledger.spec;

import org.jspecify.annotations.NonNull;

import java.util.List;

/**
 * PostingPhase（会计执行阶段 / 分阶段记账）
 *
 * <p>金融语义：
 * 一个 PostingGroup 内部的“原子会计执行步骤”。
 *
 * <p>工程语义：
 * - 用于分阶段执行 LedgerEntry
 * - 用于控制清结算流程
 * - 用于失败重试粒度控制
 *
 * <p>典型场景：
 * - AUTHORIZATION（冻结额度）
 * - CAPTURE（扣款）
 * - RELEASE（解冻）
 * - SETTLEMENT（清算）
 *
 * <p>核心职责：
 * 1. 定义执行阶段
 * 2. 生成该阶段 LedgerEntry
 * 3. 支持独立重试
 */
public interface LedgerPostingPhaseSpec {

    /**
     * 阶段编码
     */
    @NonNull
    String getPhaseCode();

    /**
     * 当前阶段生成的分录
     */
    @NonNull
    List<? extends LedgerEntrySpec> getEntries();
}
