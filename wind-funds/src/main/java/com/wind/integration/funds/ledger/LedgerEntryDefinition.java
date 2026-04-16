package com.wind.integration.funds.ledger;

import com.wind.integration.funds.account.FundsAccountId;
import com.wind.integration.funds.enums.LedgerAccountType;
import com.wind.integration.funds.enums.LedgerDirection;
import com.wind.integration.funds.enums.LedgerFundsFlowType;
import com.wind.integration.funds.reconcile.LedgerReconciliationDefinition;
import com.wind.integration.funds.settlement.LedgerSettlementDefinition;
import com.wind.transaction.core.Money;
import com.wind.transaction.core.enums.CurrencyIsoCode;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.NonNull;

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
     * 账本账户类型
     */
    @NotNull
    LedgerAccountType getLedgerAccountType();

    /**
     * 账本交易流水号
     */
    @NotNull
    String getLedgerTransactionSn();

    /**
     * 账本资金流向
     */
    @NonNull
    LedgerFundsFlowType getFundsFlowType();

    /**
     * 账本分录方向
     */
    @NotNull
    LedgerDirection getLedgerDirection();

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
     * 币种
     */
    @NonNull
    default CurrencyIsoCode getCurrency() {
        return getAmount().getCurrency();
    }

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
     * sha256，防止数据篡改
     */
    @NotNull
    String getSha256();
}
