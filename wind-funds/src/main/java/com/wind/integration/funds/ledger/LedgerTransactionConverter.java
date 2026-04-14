package com.wind.integration.funds.ledger;

import com.wind.integration.funds.event.FundsTransactionEvent;
import org.jspecify.annotations.NonNull;

/**
 * 将 {@link FundsTransactionEvent} 转换为 {@link LedgerTransactionDefinition}
 *
 * @author wuxp
 * @date 2026-04-10 13:59
 **/
public interface LedgerTransactionConverter {

    /**
     * 转换为账本交易定义
     *
     * @param event 事件
     * @return 账本交易定义
     */
    @NonNull
    LedgerTransactionDefinition convert(@NonNull FundsTransactionEvent event);

    /**
     * 是否支持该业务信息
     *
     * @param event 事件
     * @return 是否支持
     */
    boolean support(@NonNull FundsTransactionEvent event);
}
