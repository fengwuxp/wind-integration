package com.wind.integration.funds.ledger.spec;

import com.wind.integration.funds.account.FundsAccountId;
import com.wind.integration.funds.enums.LedgerAccountType;
import com.wind.integration.funds.enums.LedgerDirection;
import com.wind.integration.funds.enums.LedgerFundsFlowType;
import com.wind.integration.funds.reconcile.spec.LedgerReconciliationSpec;
import com.wind.integration.funds.settlement.spec.LedgerSettlementSpec;
import com.wind.transaction.core.Money;
import com.wind.transaction.core.enums.CurrencyIsoCode;
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
public interface LedgerEntrySpec extends LedgerReconciliationSpec, LedgerSettlementSpec {

    /**
     * 创建时间 （记账时间（posting time））
     */
    @NonNull
    LocalDateTime getGmtCreate();

    /**
     * 最后更新时间
     */
    @NonNull
    LocalDateTime getGmtModified();

    /**
     * 资金事件类型
     */
//    @NotNull
//    String getEventType();

    /**
     * 资金账户 id
     */
    @NonNull
    FundsAccountId getAccountId();

    /**
     * 账目编码
     * 会计科目编码（如 CASH / REVENUE / FEE)
     */
    @NonNull
    String getLedgerCode();

    /**
     * 账本账户类型
     */
    @NonNull
    LedgerAccountType getLedgerAccountType();

    /**
     * 账本交易流水号
     */
    @NonNull
    String getLedgerTransactionSn();

    /**
     * 账本资金流向
     */
    @NonNull
    LedgerFundsFlowType getFundsFlowType();

    /**
     * 账本分录方向
     */
    @NonNull
    LedgerDirection getLedgerDirection();

    /**
     * 业务场景
     */
    @NonNull
    String getBusinessScene();

    /**
     * 业务流水号（订单号、业务交易流水）
     */
    String getBusinessSn();

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

    /**
     * sha256，防止数据篡改
     */
    @NonNull
    String getSha256();
}
