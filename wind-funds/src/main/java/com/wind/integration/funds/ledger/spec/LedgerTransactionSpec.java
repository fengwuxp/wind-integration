package com.wind.integration.funds.ledger.spec;

import com.wind.integration.core.model.TenantIsolationObject;
import com.wind.integration.funds.enums.LedgerTransactionStatus;
import com.wind.transaction.core.Money;
import com.wind.transaction.core.enums.CurrencyIsoCode;
import org.jspecify.annotations.NonNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

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


}
