package com.wind.transaction.core.ledger;

import com.wind.transaction.core.Money;
import com.wind.transaction.core.enums.CurrencyIsoCode;
import com.wind.transaction.core.enums.LedgerEntryType;
import com.wind.transaction.core.enums.LedgerReconcileStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 账本交易
 *
 * @author wuxp
 * @date 2026-01-29 13:40
 **/
@Data
public class LedgerTransaction {

    /**
     * 账本交易
     */
    @NotNull
    private Long id;

    /**
     * 创建时间
     */
    @NotNull
    private LocalDateTime gmtCreate;

    /**
     * 付款账户 id
     */
    private String payerAccountId;

    /**
     * 付款账户类型
     */
    private String payerAccountType;

    /**
     * 付款账户货币
     */
    private CurrencyIsoCode payerCurrency;

    /**
     * 收款账户 id
     */
    private String payeeAccountId;

    /**
     * 收款账户类型
     */
    private String payeeAccountType;

    /**
     * 收款账户货币
     */
    private CurrencyIsoCode payeeCurrency;

    /**
     * 交易流水号
     */
    @NotNull
    private String transactionSn;

    /**
     * 交易发生时间
     */
    @NotNull
    private LocalDateTime transactionTime;

    /**
     * 交易所属账期（用于查询、对账聚合）
     */
    @NotNull
    private String accountingPeriod;

    /**
     * 对账批次
     */
    @NotNull
    private String reconciliationBatch;

    /**
     * 账本交易对账状态
     */
    @NotNull
    private LedgerReconcileStatus status;

    /**
     * 对账备注
     */
    private String reconcileRemark;

    /**
     * 最近一次对账时间
     */
    private LocalDateTime reconciliationTime;

    /**
     * 对账完成时间
     */
    private LocalDateTime reconciliationCompletedTime;

    /**
     * sha256
     */
    @NotNull
    private String sha256;

    /**
     * 账本条目
     */
    @NotNull
    private List<LedgerEntry> entries;

    /**
     * 借方金额
     */
    @NotNull
    public Money getTotalDebitAmount() {
        if (entries == null) {
            return Money.immutable(0, CurrencyIsoCode.UNKNOWN);
        }
        return entries.stream()
                .filter(entry -> entry.getType() == LedgerEntryType.DEBIT)
                .map(e -> e.getCurrency().of(e.getAmount()))
                .reduce(Money::add)
                .orElse(Money.immutable(0, CurrencyIsoCode.UNKNOWN));
    }

    /**
     * 贷方金额
     */
    @NotNull
    public Money getTotalCreditAmount() {
        if (entries == null) {
            return Money.immutable(0, CurrencyIsoCode.UNKNOWN);
        }
        return entries.stream()
                .filter(entry -> entry.getType() == LedgerEntryType.CREDIT)
                .map(e -> e.getCurrency().of(e.getAmount()))
                .reduce(Money::add)
                .orElse(Money.immutable(0, CurrencyIsoCode.UNKNOWN));
    }
}
