package com.wind.integration.funds.account;

import com.wind.transaction.core.Money;
import com.wind.transaction.core.enums.CurrencyIsoCode;
import org.jspecify.annotations.NonNull;

import java.time.LocalDateTime;

/**
 * 账户资金余额变更版本快照
 *
 * @author wuxp
 * @date 2026-04-13 10:50
 **/
public interface FundsAccountBalanceVersionSnapshot {

    /**
     * 账户ID
     */
    @NonNull
    FundsAccountId getAccountId();

    /**
     * 币种
     */
    @NonNull
    CurrencyIsoCode getCurrency();

    /**
     * 账本分录ID
     */
    @NonNull
    Long getLedgerEntryId();

    /**
     * 账本交易流水号
     */
    @NonNull
    String getLedgerTransactionSn();

    /**
     * 变更前余额
     */
    @NonNull
    Money getBeforeBalance();

    /**
     * 变更后余额
     */
    @NonNull
    Money getAfterBalance();

    /**
     * 变更前可用余额
     */
    @NonNull
    Money getBeforeAvailable();

    /**
     * 变更后可用余额
     */
    @NonNull
    Money getAfterAvailable();

    /**
     * 变更前冻结金额
     */
    @NonNull
    Money getBeforeFrozen();

    /**
     * 变更后冻结金额
     */
    @NonNull
    Money getAfterFrozen();

    /**
     * 关键：变化量（用于审计/风控）存在正负值
     */
    @NonNull
    default Long getDeltaBalance() {
        return getAfterBalance().getAmount() - getBeforeBalance().getAmount();
    }

    @NonNull
    default Long getDeltaAvailable() {
        return getAfterAvailable().getAmount() - getBeforeAvailable().getAmount();
    }

    @NonNull
    default Long getDeltaFrozen() {
        return getAfterFrozen().getAmount() - getBeforeFrozen().getAmount();
    }

    /**
     * 创建时间
     */
    @NonNull
    LocalDateTime getGmtCreate();

}
