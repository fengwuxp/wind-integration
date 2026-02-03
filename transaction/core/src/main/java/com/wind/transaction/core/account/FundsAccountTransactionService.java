package com.wind.transaction.core.account;


import com.wind.transaction.core.account.request.FundsAccountFreezeTransactionRequest;
import com.wind.transaction.core.account.request.FundsAccountTransactionRequest;
import org.jspecify.annotations.NonNull;

/**
 * 资金账户交易服务
 * 支持直接指出 {@link #transferOut(FundsAccountId, FundsAccountTransactionRequest)}
 * 支持基于 {@link FundsAccount#getFrozenBalance()} ()} 支出的资金账户交易服务
 * 账户有{@link FundsAccount#getCurrency()} ()}归属，在交易时需要将货币类型转换为一致
 *
 * @author wuxp
 * @date 2023-10-06 08:40
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

    /**
     * 转入金额到账户，仅累加 {@link FundsAccount#getDepositAmount()}
     *
     * @param accountId 账户标识
     * @param request   转入请求
     * @return 账号交易流水号
     */
    @NonNull
    String transferIn(@NonNull FundsAccountId accountId, @NonNull FundsAccountTransactionRequest request);

    /**
     * 普通资金流出（非冻结路径），不涉及冻结余额
     * 账户资金转出，根据交易场景累加 {@link FundsAccount#getPaymentAmount()}、 {@link FundsAccount#getFeeAmount()}、{@link FundsAccount#getWithdrawAmount())}
     *
     * @param accountId 账户标识
     * @param request   转出请求
     * @return 账号交易流水号
     */
    @NonNull
    String transferOut(@NonNull FundsAccountId accountId, @NonNull FundsAccountTransactionRequest request);

    /**
     * 退回金额到账户，仅累加 {@link FundsAccount#getRefundedAmount()}
     *
     * @param accountId 账户标识
     * @param request   退款请求
     */
    @NonNull
    String refund(@NonNull FundsAccountId accountId, @NonNull FundsAccountTransactionRequest request);

    /**
     * 冻结账户一部分额度，仅累加 {@link FundsAccount#getTotalFrozenAmount()}，冻结额度不能大于可用额度 {@link FundsAccount#getAvailableBalance()}
     * 通过累计 {@link FundsAccount#getTotalFrozenAmount()} 实现
     *
     * @param accountId 账户标识
     * @param request   冻结请求
     * @return 账户交易流水号
     */
    @NonNull
    String freeze(@NonNull FundsAccountId accountId, @NonNull FundsAccountTransactionRequest request);

    /**
     * 解冻{@link FundsAccount#getFrozenBalance()}中的额度，解冻额度不能大于已冻结额度{@link FundsAccount#getFrozenBalance()}
     * 通过累计 {@link FundsAccount#getTotalUnfrozenAmount()} 实现
     *
     * @param accountId 账户标识
     * @param request   解冻请求
     * @return 账户交易流水号
     */
    @NonNull
    String unfreeze(@NonNull FundsAccountId accountId, @NonNull FundsAccountFreezeTransactionRequest request);

    /**
     * 基于冻结余额优先的账户支出，减少{@link FundsAccount#getFrozenBalance()}，累加 {@link FundsAccount#getPaymentAmount()}
     * 支出 {@param request#getAmount()} 金额不能大于已冻结额度{@link FundsAccount#getFrozenBalance()}
     * 注意：若冻结余额不足，可自动使用可用余额补足或者抛异常，可由具体的实现决定。
     * 例如：
     * 1.当 {@param request#freezeSn} 对应的冻结金额 小于 支出金额时，优先扣除冻结金额，剩余金额从{@link FundsAccount#getAvailableBalance()}扣除
     * 2.当 {@param request#freezeSn} 对应的冻结金额 大于 支出金额时，需要将对应冻结流水中支出后的剩余金额解冻
     *
     * @param accountId 账户标识
     * @param request   转出请求
     * @return 账号交易流水号
     */
    @NonNull
    String payFromFrozenBalance(@NonNull FundsAccountId accountId, @NonNull FundsAccountFreezeTransactionRequest request);

    /**
     * @param id 账户标识
     * @return 是否支持该类型账户
     */
    boolean supports(@NonNull FundsAccountId id);

}
