package com.wind.integration.funds.ledger;

import com.wind.integration.funds.account.FundsAccountId;
import com.wind.integration.funds.enums.LedgerEntryPostingType;
import com.wind.integration.funds.enums.LedgerType;
import com.wind.integration.funds.reconcile.LedgerReconciliationDefinition;
import com.wind.integration.funds.settlement.LedgerSettlementDefinition;
import com.wind.transaction.core.Money;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 账本条目定义
 *
 * @author wuxp
 * @date 2026-04-09 09:04
 **/
public interface LedgerEntryDefinition extends LedgerReconciliationDefinition, LedgerSettlementDefinition {

    /**
     * 创建时间 （记账时间（posting time））
     */
    @NotNull
    LocalDateTime getGmtCreate();

    /**
     * 最后更新时间
     */
    @NotNull
    LocalDateTime getGmtModified();

    /**
     * 资金账户 id
     */
    @NotNull
    FundsAccountId getAccountId();

    /**
     * 账目编码
     * 会计科目编码（如 CASH / REVENUE / FEE)
     */
    @NotNull
    String getLedgerCode();

    /**
     * 账目类型
     */
    @NotNull
    LedgerType getLedgerType();

    /**
     * 账本交易流水号
     */
    @NotNull
    String getLedgerTransactionSn();

    /**
     * 账目处理类型
     */
    @NotNull
    LedgerEntryPostingType getPostingType();

    /**
     * 业务场景
     */
    @NotNull
    String getBusinessScene();

    /**
     * 业务流水号（订单号、业务交易流水）
     */
    String getBusinessSn();

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
     * 描述
     */
    String getDescription();

    /**
     * 上下文
     */
    @NotNull
    Map<String, Object> getContextVariables();

    /**
     * sha256，防止数据篡改
     */
    @NotNull
    String getSha256();
}
