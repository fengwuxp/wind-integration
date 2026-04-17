package com.wind.integration.funds.account;

import com.wind.integration.funds.ledger.LedgerEntryDefinition;
import org.jspecify.annotations.NonNull;

/**
 * 资金账户余额服务
 * 基于借记或者贷记进行资金账户余额更新、记录账户流水
 *
 * @author wuxp
 * @date 2026-04-09 17:22
 **/
public interface FundsAccountBalancePostingService {

    /**
     * 账户余额流水
     *
     * @param ledger 流水
     * @return 账户流水标识
     */
    @NonNull
    String post(@NonNull LedgerEntryDefinition ledger);

    /**
     * 是否支持该资金账户
     *
     * @param accountId 账户标识
     * @return 是否支持
     */
    boolean supports(@NonNull FundsAccountId accountId);

}
