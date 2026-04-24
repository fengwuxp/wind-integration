package com.wind.integration.funds.ledger.spec;

import com.wind.integration.funds.enums.LedgerDirection;
import com.wind.integration.funds.enums.LedgerPostingIntentType;
import com.wind.transaction.core.Money;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.NonNull;

import java.beans.Transient;
import java.util.List;

/**
 * LedgerPostingPlan（账务计划 / 记账蓝图）
 *
 * <p>金融语义：
 * 表示一笔交易“应该如何被记账”，
 * 是规则引擎的输出结果，不是执行结果。
 *
 * <p>工程语义：
 * - 可重算（rebuildable）
 * - 与执行解耦
 * - 支持多 plan（拆分账务路径）
 *
 * <p>核心职责：
 * 1. 表达账务意图（Intent）
 * 2. 描述分录结构（Phases）
 * 3. 支撑 Batch 执行
 */
public interface LedgerPostingPlanSpec {

    /**
     * 计划ID（规则生成唯一ID）
     */
    @NonNull
    String getPlanId();

    /**
     * 所属交易
     */
    @NonNull
    String getLedgerTransactionSn();

    /**
     * 账务意图（核心语义）
     *
     * <p>说明“为什么发生这笔账”
     * 比 BatchType 更稳定、更业务化
     */
    @NonNull
    LedgerPostingIntentType getIntent();

    /**
     * 是否分阶段执行
     *
     * <p>true = 需要 Batch + Phase 执行
     * false = 可直接落 entry
     */
    default boolean isPhased() {
        return true;
    }

    /**
     * 账务执行阶段
     */
    @NonNull
    List<LedgerPostingPhaseSpec> getPostingPhases();

    /**
     * 校验是否借贷平衡（Plan级）
     */
    default boolean isBalanced() {
        var entries = getPostingPhases().stream()
                .flatMap(p -> p.getEntries().stream())
                .toList();

        Money debit = entries.stream()
                .filter(e -> e.getLedgerDirection() == LedgerDirection.DEBIT)
                .map(LedgerEntrySpec::getAmount)
                .reduce(Money::add)
                .orElse(Money.ZERO);

        Money credit = entries.stream()
                .filter(e -> e.getLedgerDirection() == LedgerDirection.CREDIT)
                .map(LedgerEntrySpec::getAmount)
                .reduce(Money::add)
                .orElse(Money.ZERO);

        return debit.equals(credit);
    }

    /**
     * 账本交易条目
     */
    @NonNull
    @Transient
    default List<LedgerEntrySpec> getEntries() {
        return getPostingPhases().stream()
                .map(LedgerPostingPhaseSpec::getEntries)
                .flatMap(List::stream)
                .toList();
    }


    /**
     * 借方金额，
     */
    @NotNull
    @Transient
    default Money getTotalDebitAmount() {
        return getEntries().stream()
                .filter(e -> e.getLedgerDirection() == LedgerDirection.DEBIT)
                .map(LedgerEntrySpec::getAmount)
                .reduce(Money::add)
                .orElse(Money.ZERO);
    }

    /**
     * 贷方金额
     */
    @NotNull
    @Transient
    default Money getTotalCreditAmount() {
        return getEntries().stream()
                .filter(e -> e.getLedgerDirection() == LedgerDirection.CREDIT)
                .map(LedgerEntrySpec::getAmount)
                .reduce(Money::add)
                .orElse(Money.ZERO);
    }
}