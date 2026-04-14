package com.wind.integration.funds.ledger;

import java.util.List;

/**
 * 账本余额投影服务
 *
 * @author wuxp
 * @date 2026-04-14 09:28
 **/
public interface LedgerBalanceProjectionService {

    /**
     * 投影
     *
     * @param entries 账本条目定义
     */
    void project(List<LedgerEntryDefinition> entries);
}
