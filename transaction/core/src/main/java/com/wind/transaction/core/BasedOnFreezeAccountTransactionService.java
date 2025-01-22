package com.wind.transaction.core;


import com.wind.transaction.core.request.FreezeRequest;
import com.wind.transaction.core.request.TransferRequest;

/**
 * 支出基于 {@link TransactionAccount#getFreezeAmount()}，的交易账户
 * 账户有{@link TransactionAccount#getCurrency()} ()}归属，在交易时需要将货币类型转换为一致
 *
 * @author wuxp
 * @date 2023-10-06 08:40
 **/
public interface BasedOnFreezeAccountTransactionService extends AccountTransactionService {

    /**
     * 冻结账户一部分额度，仅累加 {@link TransactionAccount#getFreezeAmount()}
     * 冻结额度不能大于可用额度 {@link TransactionAccount#getAvailableAmount()}
     *
     * @param id      账户标识
     * @param request 冻结请求
     */
    void freeze(TransactionAccountId id, FreezeRequest request);

    /**
     * 解冻{@link TransactionAccount#getFreezeAmount()}已被冻结一部分额度
     * 解冻额度不能大于已冻结额度{@link TransactionAccount#getFreezeAmount()}
     *
     * @param id      账户标识
     * @param request 解冻请求
     */
    void unfreeze(TransactionAccountId id, FreezeRequest request);

    /**
     * 冻结并支出
     * 1：冻结账户余额
     * 2：支出
     *
     * @param id      账户标识
     * @param request 转出请求
     */
    void freezeAndTransferOut(TransactionAccountId id, TransferRequest request);

}
