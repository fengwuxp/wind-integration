package com.wind.integration.funds;

import com.wind.integration.funds.event.FundsEvent;
import org.jspecify.annotations.NonNull;

/**
 * 资金交易事件处理
 *
 * @author wuxp
 * @date 2026-04-14 09:22
 **/
public interface WindFundsEventHandler {

    /**
     * 处理
     *
     * @param event 资金交易事件
     */
    void handle(@NonNull FundsEvent event);

    /**
     * 是否支持
     *
     * @param event 资金交易事件
     * @return if true 支持
     */
    boolean support(@NonNull FundsEvent event);
}
