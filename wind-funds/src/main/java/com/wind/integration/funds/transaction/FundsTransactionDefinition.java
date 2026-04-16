package com.wind.integration.funds.transaction;

import com.wind.integration.core.model.TenantIsolationObject;
import com.wind.integration.funds.account.FundsAccountId;
import com.wind.transaction.core.Money;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.NonNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 资金账户交易定义
 *
 * @author wuxp
 * @date 2026-04-10 15:17
 **/
public interface FundsTransactionDefinition extends TenantIsolationObject<Long> {

    /**
     * @return 创建时间
     */
    @NonNull
    LocalDateTime getGmtCreate();

    /**
     * @return 交易类型
     */
    @NonNull
    String getTransactionType();

    /**
     * @return 交易状态
     */
    @NonNull
    String getTransactionStatus();

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
     * @return 业务流水号，需要唯一
     */
    @NonNull
    String getBusinessSn();

    /**
     * @return 业务场景
     */
    @NonNull
    String getBusinessScene();

    /**
     * @return 交易金额
     */
    @NonNull
    Money getAmount();

    /**
     * @return 原始金额
     */
    @NonNull
    Money getOriginalAmount();

    /**
     * @return 已退款金额
     */
    default Money getRefundedAmount() {
        return Money.ZERO;
    }

    /**
     * @return 汇率
     */
    @NonNull
    BigDecimal getExchangeRate();

    /**
     * @return 交易时间
     */
    @NonNull
    LocalDateTime getTransactionTime();

    /**
     * @return 交易完成时间
     */
    LocalDateTime getFinishTime();

    /**
     * @return 最后退款时间
     */
    LocalDateTime getLastRefundedTime();


    /**
     * @return 交易描述
     */
    String getDescription();

    /**
     * @return 可退款金额
     */
    @NonNull
    default Money getRefundableAmount() {
        return getAmount().subtract(getRefundedAmount());
    }
}
