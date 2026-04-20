package com.wind.integration.funds.ledger;

import com.wind.integration.funds.event.FundsEvent;
import com.wind.integration.funds.ledger.spec.LedgerTransactionSpec;
import org.jspecify.annotations.NonNull;

/**
 * 将 {@link FundsEvent} 转换为 {@link LedgerTransactionSpec}
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
    LedgerTransactionSpec convert(@NonNull FundsEvent event);

    /**
     * 是否支持该业务信息
     *
     * @param event 事件
     * @return 是否支持
     */
    boolean support(@NonNull FundsEvent event);
}
