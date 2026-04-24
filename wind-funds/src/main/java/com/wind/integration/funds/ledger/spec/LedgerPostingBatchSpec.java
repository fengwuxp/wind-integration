package com.wind.integration.funds.ledger.spec;

import com.wind.integration.funds.enums.LedgerPostingBatchType;
import org.jspecify.annotations.NonNull;

/**
 * LedgerPostingBatch（记账执行单元）
 *
 * <p>定义：
 * 一次可执行的账务任务，具备事务边界。
 *
 * <p>职责：
 * - 执行 Plan
 * - 控制 Phase
 * - 幂等控制
 * - 失败重试
 */
public interface LedgerPostingBatchSpec {

    /**
     * 批次ID（幂等）
     */
    @NonNull
    String getBatchId();

    /**
     * 所属交易
     */
    @NonNull
    String getLedgerTransactionSn();

    /**
     * 执行类型（执行策略）
     */
    @NonNull
    LedgerPostingBatchType getBatchType();

    /**
     * 对应的 Plan
     */
    @NonNull
    LedgerPostingPlanSpec getPlan();
}