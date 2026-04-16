package com.wind.integration.funds;

import com.wind.integration.funds.event.FundsEvent;
import org.jspecify.annotations.NonNull;

/**
 * 资金事件工厂
 *
 * @author wuxp
 * @date 2026-04-10 10:08
 **/
public interface WindFundsEventFactory<T> {

    /**
     * 创建资金交易事件
     *
     * @param payload 业务数据或事件
     * @return 资金交易事件
     */
    @NonNull
    FundsEvent create(@NonNull T payload);

    /**
     * 是否支持该事件
     *
     * @param payload 业务数据或事件
     * @return if true 支持
     */
    boolean support(@NonNull T payload);
}
