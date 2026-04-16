package com.wind.integration.funds.event;

import com.wind.core.ReadonlyContextVariables;
import com.wind.integration.core.task.WindRetryableTask;
import com.wind.integration.funds.account.FundsAccountId;
import com.wind.integration.funds.enums.FundsTransactionEventStatus;
import com.wind.transaction.core.Money;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.LocalDateTime;

/**
 * 资金事件
 * 例如：用于创建交易订单、驱动交易生命周期完成，不一定会涉及记账或余额更新
 *
 * @author wuxp
 * @date 2026-04-10 09:57
 **/
public interface FundsEvent extends WindRetryableTask {

    @Override
    @NonNull
    default String getRetryId() {
        return getEventId();
    }

    /**
     * 事件 ID 全局唯一
     */
    @NonNull
    String getEventId();

    /**
     * 事件类型，描述业务事实
     */
    @NonNull
    String getEventType();

    /**
     * 事件发生时间
     */
    @NonNull
    LocalDateTime getEventTime();

    /**
     * 事件处理状态
     */
    @NonNull
    FundsTransactionEventStatus getStatus();

    /**
     * 业务场景
     */
    @NonNull
    String getBusinessScene();

    /**
     * 业务交易流水号
     */
    String getBusinessSn();

    /**
     * 交易（原始）金额
     */
    @NonNull
    Money getAmount();

    /**
     * 交易发起方账户
     */
    @Nullable
    FundsAccountId getInitiatorAccountId();

    /**
     * 交易对手方账户
     */
    @Nullable
    FundsAccountId getCounterpartyAccountId();

    /**
     * 上下文
     */
    @NonNull
    ReadonlyContextVariables getContext();

    /**
     * 交易描述
     */
    String getDescription();

}
