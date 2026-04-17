package com.wind.integration.funds.ledger;

import com.wind.integration.funds.event.FundsEvent;
import org.jspecify.annotations.NonNull;

import java.util.List;

/**
 * 账本 posting 规则
 *
 * @author wuxp
 * @date 2026-04-17 09:09
 **/
public interface LedgerPostingRule {

    /**
     * 是否支持该资金事件
     */
    boolean supports(@NonNull FundsEvent event);

    /**
     * 生成分录（核心）
     *
     * @param event 资金事件
     */
    @NonNull
    List<LedgerEntryDefinition> generateEntries(@NonNull FundsEvent event);
}