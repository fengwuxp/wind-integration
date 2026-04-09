package com.wind.integration.funds.ledger;

import com.wind.integration.core.model.TenantIsolationObject;
import com.wind.integration.funds.account.FundsAccountId;
import com.wind.integration.funds.enums.LedgerEntryPostingType;
import com.wind.transaction.core.Money;
import com.wind.transaction.core.enums.CurrencyIsoCode;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 账本交易定义
 *
 * @author wuxp
 * @date 2026-04-09 09:17
 **/
public interface LedgerTransactionDefinition extends TenantIsolationObject<Long>, LedgerReconciliationDefinition {

    /**
     * 付款账户 id
     */
    @NotNull
    FundsAccountId getPayerAccountId();

    /**
     * 收款账户 id
     */
    @NotNull
    FundsAccountId getPayeeAccountId();

    /**
     * 交易编号
     */
    @NotNull
    String getSn();

    /**
     * 业务交易编号
     */
    String getBusinessTransactionSn();

    /**
     * 交易发生时间
     */
    @NotNull
    LocalDateTime getTransactionTime();

    /**
     * 交易完成时间
     */
    LocalDateTime getTransactionFinishTime();

    /**
     * 描述
     */
    String getDescription();

    /**
     * 账本交易条目
     */
    @NotNull
    List<LedgerEntryDefinition> getEntries();

    /**
     * 借方金额
     */
    @NotNull
    default Money getTotalDebitAmount() {
        return getEntries().stream()
                .filter(entry -> entry.getPostingType() == LedgerEntryPostingType.DEBIT)
                .map(LedgerEntryDefinition::getAmount)
                .reduce(Money::add)
                .orElse(Money.immutable(0, CurrencyIsoCode.UNKNOWN));
    }

    /**
     * 贷方金额
     */
    @NotNull
    default Money getTotalCreditAmount() {
        return getEntries().stream()
                .filter(entry -> entry.getPostingType() == LedgerEntryPostingType.CREDIT)
                .map(LedgerEntryDefinition::getAmount)
                .reduce(Money::add)
                .orElse(Money.immutable(0, CurrencyIsoCode.UNKNOWN));
    }

    /**
     * 是否金额一致
     *
     * @return true 金额一致
     */
    default boolean auditAmount() {
        return getTotalCreditAmount().subtract(getTotalDebitAmount()).isZero();
    }
}
