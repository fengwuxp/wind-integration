package com.wind.integration.funds.ledger.spec;

import com.wind.common.exception.AssertUtils;
import com.wind.integration.core.model.TenantIsolationObject;
import com.wind.integration.funds.account.FundsAccountId;
import com.wind.integration.funds.enums.LedgerDirection;
import com.wind.integration.funds.enums.LedgerTransactionStatus;
import com.wind.transaction.core.Money;
import com.wind.transaction.core.enums.CurrencyIsoCode;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.NonNull;

import java.beans.Transient;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 账本交易定义
 *
 * @author wuxp
 * @date 2026-04-09 09:17
 **/
public interface LedgerTransactionSpec extends TenantIsolationObject<Long> {

    /**
     * 交易编号
     */
    @NonNull
    String getSn();

    /**
     * 事件类型
     */
    @NonNull
    String getEventType();

    /**
     * @return 账本交易状态
     */
    @NonNull
    LedgerTransactionStatus getStatus();

    /**
     * 记账金额，单位：分
     */
    @NonNull
    Money getAmount();

    /**
     * 币种
     */
    @NonNull
    default CurrencyIsoCode getCurrency() {
        return getAmount().getCurrency();
    }

    /**
     * 原始金额，单位：分
     */
    @NonNull
    Money getOriginalAmount();

    /**
     * 汇率
     */
    @NonNull
    BigDecimal getExchangeRate();

    /**
     * 业务交易编号
     */
    String getBusinessSn();

    /**
     * 业务场景
     */
    @NonNull
    String getBusinessScene();

    /**
     * 关联的账本交易流水号
     */
    String getReferenceLedgerTransactionSn();

    /**
     * 交易发生时间
     */
    @NonNull
    LocalDateTime getTransactionTime();

    /**
     * 描述
     */
    String getDescription();

    /**
     * 上下文
     */
    @NonNull
    Map<String, Object> getContextVariables();


    @NotNull
    JournalSpec getJournal();

    @NotNull
    List<LedgerPostingGroupSpec> getPostingGroups();

    /**
     * 账本交易条目
     */
    @NonNull
    @Transient
    default List<LedgerEntrySpec> getEntries() {
        return getPostingGroups().stream()
                .map(LedgerPostingGroupSpec::getPhases)
                .flatMap(List::stream)
                .map(LedgerPostingPhaseSpec::getEntries)
                .flatMap(List::stream)
                .collect(Collectors.toList());
    }

    /**
     * 借方金额，
     */
    @NotNull
    default Money getTotalDebitAmount() {
        return getEntries().stream()
                .filter(e -> e.getLedgerDirection() == LedgerDirection.DEBIT)
                .map(LedgerEntrySpec::getAmount)
                .reduce(Money::add)
                .orElse(Money.immutable(0, getCurrency()));
    }

    /**
     * 贷方金额
     */
    @NotNull
    default Money getTotalCreditAmount() {
        return getEntries().stream()
                .filter(e -> e.getLedgerDirection() == LedgerDirection.CREDIT)
                .map(LedgerEntrySpec::getAmount)
                .reduce(Money::add)
                .orElse(Money.immutable(0, getCurrency()));
    }

    /**
     * 获取账目交易关联的资金账户
     *
     * @return 资金账户id 集合
     */
    @NonNull
    default Set<FundsAccountId> getLedgerAccountIds() {
        return getEntries().stream()
                .map(LedgerEntrySpec::getAccountId)
                .collect(Collectors.toSet());
    }

    /**
     * 验证借贷金额
     *
     * @return true 金额一致
     */
    default boolean auditAmount() {
        // 1. 币种一致性（transaction级约束）
        boolean sameCurrency = getEntries().stream()
                .allMatch(e -> e.getAmount().getCurrency().equals(getCurrency()));
        AssertUtils.isTrue(sameCurrency, "Inconsistent currency in ledger entries");
        return getTotalDebitAmount().equals(getTotalCreditAmount());
    }

}
