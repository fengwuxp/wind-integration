package com.wind.integration.funds.ledger;

import com.wind.integration.funds.account.FundsAccountId;
import com.wind.integration.funds.ledger.spec.LedgerEntrySpec;
import org.jspecify.annotations.NonNull;

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
    void project(@NonNull List<LedgerEntrySpec> entries);

    /**
     * 是否支持
     *
     * @param accountId 账户ID
     * @return true:支持
     */
    boolean support(@NonNull FundsAccountId accountId);
}
