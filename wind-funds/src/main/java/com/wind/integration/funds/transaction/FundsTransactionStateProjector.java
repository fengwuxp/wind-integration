package com.wind.integration.funds.transaction;

import com.wind.integration.funds.event.FundsEvent;

/**
 * 交易状态投影器（Event → Transaction State）
 * 只负责根据事件更新交易状态，不包含任何资金逻辑
 *
 * @author wuxp
 * @date 2026-04-10 13:06
 **/
public interface FundsTransactionStateProjector {

    /**
     * 处理资金交易事件
     *
     * @param event 资金交易事件
     */
    void apply(FundsEvent event);

    /**
     * 是否支持该事件
     *
     * @param event 资金交易事件
     * @return true 支持
     */
    boolean supports(FundsEvent event);
}
