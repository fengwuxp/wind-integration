package com.wind.integration.funds.account;

import org.jspecify.annotations.NonNull;

/**
 * 资金账户查询服务
 *
 * @author wuxp
 * @date 2026-04-16 15:49
 **/
public interface FundsAccountQueryService {

    /**
     * 获取一个资金账户
     *
     * @param accountId 账户标识
     * @return 资金账户
     */
    @NonNull
    FundsAccount getAccount(@NonNull FundsAccountId accountId);

    /**
     * 获取账户余额
     *
     * @param accountId 账户标识
     * @return 账户余额
     */
    @NonNull
    FundsAccountBalanceView getBalance(@NonNull FundsAccountId accountId);

    /**
     * 是否支持该资金账户
     *
     * @param accountId 账户标识
     * @return 是否支持
     */
    boolean supports(@NonNull FundsAccountId accountId);
}
