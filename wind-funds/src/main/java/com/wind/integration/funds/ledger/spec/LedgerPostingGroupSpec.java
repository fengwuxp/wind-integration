package com.wind.integration.funds.ledger.spec;

import com.wind.integration.funds.enums.LedgerDirection;
import com.wind.transaction.core.Money;
import org.jspecify.annotations.NonNull;

import java.util.List;

/**
 * PostingGroup（会计分录组 / 会计执行单元）
 *
 * <p>金融语义：
 * 一次 Journal 会被拆解成一个或多个 PostingGroup，
 * 每个 Group 表示一个“会计处理阶段”或“结算步骤”。
 *
 * <p>工程语义：
 * - 一个可执行的记账单元
 * - 支持分阶段执行（Phase-based）
 * - 支持失败恢复
 *
 * <p>核心职责：
 * 1. 将 Journal 转换为可执行账务结构
 * 2. 管理 Phase 执行顺序
 * 3. 保证借贷平衡（或分阶段平衡）
 */
public interface LedgerPostingGroupSpec {

    @NonNull
    String getPostingGroupId();

    @NonNull
    String getBusinessSn();

    @NonNull
    List<LedgerPostingPhaseSpec> getPhases();

    /**
     * 是否借贷平衡
     */
    default boolean isBalanced() {
        List<? extends LedgerEntrySpec> entries = getPhases().stream()
                .map(LedgerPostingPhaseSpec::getEntries)
                .flatMap(List::stream)
                .toList();
        Money debitAmount = entries.stream()
                .filter(e -> e.getLedgerDirection() == LedgerDirection.DEBIT)
                .map(LedgerEntrySpec::getAmount)
                .reduce(Money::add)
                .orElse(Money.ZERO);
        Money creditAmount = entries.stream()
                .filter(e -> e.getLedgerDirection() == LedgerDirection.CREDIT)
                .map(LedgerEntrySpec::getAmount)
                .reduce(Money::add)
                .orElse(Money.ZERO);
        return debitAmount.equals(creditAmount);
    }
}
