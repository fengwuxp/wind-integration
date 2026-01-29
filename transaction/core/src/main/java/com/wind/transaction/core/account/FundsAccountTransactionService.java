package com.wind.transaction.core.account;

import com.wind.transaction.core.account.request.FundsAccountTransactionRequest;
import org.jspecify.annotations.NonNull;

/**
 * 资金账户交易服务
 *
 * @author wuxp
 * @date 2025-01-21 16:30
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
     * 账户资金转出，根据交易分类累加 {@link FundsAccount#getExpensesAmount()}、 {@link FundsAccount#getFeeAmount()}
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
     * @param id 账户标识
     * @return 是否支持该类型账户
     */
    boolean supports(FundsAccountId id);
}
