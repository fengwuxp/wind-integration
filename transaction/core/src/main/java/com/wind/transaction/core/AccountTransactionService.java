package com.wind.transaction.core;

import com.wind.transaction.core.request.TransferRequest;

/**
 * 账户交易服务
 *
 * @author wuxp
 * @date 2025-01-21 16:30
 **/
public interface AccountTransactionService {

    /**
     * 获取一个交易账号
     *
     * @param id 交易账号 ID
     * @return 交易账号
     */
    TransactionAccount getAccount(TransactionAccountId id);

    /**
     * 转入金额到账户，仅累加 {@link TransactionAccount#getDepositAmount()}
     *
     * @param request 转入请求
     * @return 账号交易流水号
     */
    String transferIn(TransactionAccountId id, TransferRequest request);

    /**
     * 基于冻结余额的账户支出，累加 {@link TransactionAccount#getExpensesAmount()}，扣除{@link TransactionAccount#getFreezeAmount()}
     * 支出 {@param request#getAmount()} 额度不能大于已冻结额度{@link TransactionAccount#getFreezeAmount()} ()}
     *
     * @param id      账户标识
     * @param request 转出请求
     * @return 账号交易流水号
     */
    String transferOut(TransactionAccountId id, TransferRequest request);

    /**
     * 退回金额到账户，仅累加 {@link TransactionAccount#getRefundedAmount()}
     *
     * @param id      账户标识
     * @param request 退款请求
     */
    String refund(TransactionAccountId id, TransferRequest request);

    /**
     * @param id 账户标识
     * @return 是否支持该类型账户
     */
    boolean supports(TransactionAccountId id);
}
