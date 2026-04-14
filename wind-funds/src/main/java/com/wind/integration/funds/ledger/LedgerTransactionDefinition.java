package com.wind.integration.funds.ledger;

import com.wind.integration.core.model.TenantIsolationObject;
import com.wind.integration.funds.account.FundsAccountId;
import com.wind.integration.funds.enums.LedgerEntryPostingType;
import com.wind.integration.funds.enums.LedgerTransactionStatus;
import com.wind.transaction.core.Money;
import com.wind.transaction.core.enums.CurrencyIsoCode;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.NonNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 账本交易定义
 *
 * @author wuxp
 * @date 2026-04-09 09:17
 **/
public interface LedgerTransactionDefinition extends TenantIsolationObject<Long> {

    /**
     * 发起交易账户
     */
    @NotNull
    FundsAccountId getInitiatorAccountId();

    /**
     * 对手方账户（可能没有）
     */
    FundsAccountId getCounterpartyAccountId();

    /**
     * 交易编号
     */
    @NotNull
    String getSn();

    /**
     * 事件 id
     */
    @NotNull
    String getEventId();

    /**
     * 事件类型
     */
    @NotNull
    String getEventType();

    /**
     * @return 账本交易状态
     */
    @NonNull
    LedgerTransactionStatus getStatus();

    /**
     * 记账金额，单位：分
     */
    @NotNull
    Money getAmount();

    /**
     * 原始金额，单位：分
     */
    @NotNull
    Money getOriginalAmount();

    /**
     * 汇率
     */
    @NotNull
    BigDecimal getExchangeRate();

    /**
     * 业务交易编号
     */
    String getBusinessSn();

    /**
     * 业务场景
     */
    @NotNull
    String getBusinessScene();

    /**
     * 关联的账本交易流水号
     */
    String getReferenceLedgerTransactionSn();

    /**
     * 交易发生时间
     */
    @NotNull
    LocalDateTime getTransactionTime();

    /**
     * 描述
     */
    String getDescription();

    /**
     * 上下文
     */
    @NotNull
    Map<String, Object> getContextVariables();

    /**
     * 账本交易条目
     */
    @NotNull
    List<? extends LedgerEntryDefinition> getEntries();

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
