package com.wind.transaction.core.account;

/**
 * 账户余额相关列更新 delta
 *
 * @author wuxp
 * @date 2026-02-02 15:59
 **/
public record BalanceColumnDelta(long amount) {

    public static BalanceColumnDelta increase(long amount) {
        return new BalanceColumnDelta(amount);
    }

    public static BalanceColumnDelta decrease(long amount) {
        return new BalanceColumnDelta(-amount);
    }
}
