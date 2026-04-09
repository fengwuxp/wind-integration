package com.wind.integration.funds.account;

import org.jspecify.annotations.NonNull;

/**
 * @author wuxp
 * @date 2026-04-09 17:22
 **/
public interface FundsAccountTransactionService {

    /**
     * 获取一个资金账户
     *
     * @param accountId 账户标识
     * @return 资金账户
     */
    @NonNull
    FundsAccount getAccount(@NonNull FundsAccountId accountId);

}
