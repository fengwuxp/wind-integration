package com.wind.integration.funds;

import com.wind.integration.funds.event.FundsTransactionEvent;
import org.jspecify.annotations.NonNull;

/**
 * @author wuxp
 * @date 2026-04-14 09:35
 **/
public interface FundsTransactionEventMapper {

    /**
     * 映射
     *
     * @param payload 业务负载数据
     * @return 资金交易事件
     */
    @NonNull
    FundsTransactionEvent map(Object payload);

    /**
     * 是否支持
     *
     * @param payload 业务负载数据
     * @return true:支持
     */
    boolean support(Object payload);
}
