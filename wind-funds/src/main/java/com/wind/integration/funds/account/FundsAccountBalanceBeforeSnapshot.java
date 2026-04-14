package com.wind.integration.funds.account;

import org.jspecify.annotations.NonNull;

/**
 * 账户资金变动前快照
 *
 * @author wuxp
 * @date 2026-04-13 10:50
 **/
public interface FundsAccountBalanceBeforeSnapshot {

    /**
     * @return 变动前账户总余额
     */
    @NonNull
    Long getBeforeBalance();

    /**
     * @return 变动前可用余额
     */
    @NonNull
    Long getBeforeAvailable();

    /**
     * @return 变动前冻结余额
     */
    @NonNull
    Long getBeforeFreeze();

    /**
     * @return 变动前账户总余额
     */
    @NonNull
    Long getAfterBalance();

    /**
     * @return 变动前可用余额
     */
    @NonNull
    Long getAfterAvailable();

    /**
     * @return 变动前冻结余额
     */
    @NonNull
    Long getAfterFreeze();

}
