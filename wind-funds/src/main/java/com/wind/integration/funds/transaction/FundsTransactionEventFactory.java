package com.wind.integration.funds.transaction;

import com.wind.integration.funds.event.FundsTransactionEvent;
import org.jspecify.annotations.NonNull;

/**
 * 资金交易事件工厂
 *
 * @author wuxp
 * @date 2026-04-10 10:08
 **/
public interface FundsTransactionEventFactory {

    /**
     * 创建资金交易事件
     *
     * @param event 事件
     * @return 资金交易事件
     */
    @NonNull
    FundsTransactionEvent create(@NonNull Object event);

    /**
     * 是否支持该事件
     *
     * @param event 事件
     * @return if true 支持
     */
    boolean support(@NonNull Object event);
}
