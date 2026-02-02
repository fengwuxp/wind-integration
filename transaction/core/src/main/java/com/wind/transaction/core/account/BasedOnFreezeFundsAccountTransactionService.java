package com.wind.transaction.core.account;


import com.wind.transaction.core.account.request.FundsAccountTransactionRequest;
import org.jspecify.annotations.NonNull;

/**
 * 基于 {@link FundsAccount#getFreezeAmount()} 支出的资金账户交易服务
 * 账户有{@link FundsAccount#getCurrency()} ()}归属，在交易时需要将货币类型转换为一致
 *
 * @author wuxp
 * @date 2023-10-06 08:40
 **/
public interface BasedOnFreezeFundsAccountTransactionService extends FundsAccountTransactionService {

    /**
     * 基于冻结余额的账户支出，累加 {@link FundsAccount#getExpensesAmount()}，扣除{@link FundsAccount#getFreezeAmount()}
     * 支出 {@param request#getAmount()} 金额不能大于已冻结额度{@link FundsAccount#getFreezeAmount()} ()}
     *
     * @param accountId 账户标识
     * @param request   转出请求
     * @return 账号交易流水号
     */
    @NonNull
    String transferOut(@NonNull FundsAccountId accountId, @NonNull FundsAccountTransactionRequest request);

    /**
     * 冻结账户一部分额度，仅累加 {@link FundsAccount#getFreezeAmount()}
     * 冻结额度不能大于可用额度 {@link FundsAccount#getAvailableBalance()}
     *
     * @param accountId 账户标识
     * @param request   冻结请求
     * @return 账户交易流水号
     */
    @NonNull
    String freeze(@NonNull FundsAccountId accountId, @NonNull FundsAccountTransactionRequest request);

    /**
     * 解冻{@link FundsAccount#getFreezeAmount()}已被冻结一部分额度
     * 解冻额度不能大于已冻结额度{@link FundsAccount#getFreezeAmount()}
     *
     * @param accountId      账户标识
     * @param request 解冻请求
     * @return 账户交易流水号
     */
    @NonNull
    String unfreeze(@NonNull FundsAccountId accountId, @NonNull FundsAccountTransactionRequest request);

    /**
     * 原子操作：冻结账户余额并扣款
     * - 冻结金额不能大于可用余额
     * - 扣款金额不能大于冻结金额
     *
     * @param accountId      账户标识
     * @param request 转出请求
     * @return 账户交易流水号
     */
    @NonNull
    String freezeAndDebit(@NonNull FundsAccountId accountId, @NonNull FundsAccountTransactionRequest request);

    /**
     * 直接从可用余额扣款，不走预冻结流程
     *
     * @param accountId 账户标识
     * @param request   转出请求
     * @return 账户交易流水号
     */
    @NonNull
    String debitAvailableBalance(@NonNull FundsAccountId accountId, @NonNull FundsAccountTransactionRequest request);

}
