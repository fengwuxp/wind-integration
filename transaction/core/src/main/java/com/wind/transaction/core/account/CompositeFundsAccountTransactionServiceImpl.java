package com.wind.transaction.core.account;

import com.wind.common.exception.BaseException;
import com.wind.transaction.core.account.request.FundsAccountFreezeTransactionRequest;
import com.wind.transaction.core.account.request.FundsAccountTransactionRequest;
import lombok.AllArgsConstructor;
import org.jspecify.annotations.NonNull;

import java.util.Collection;
import java.util.Optional;

/**
 * 账户交易服务组合实现
 *
 * @author wuxp
 * @date 2026-02-02 11:32
 **/
@AllArgsConstructor
public class CompositeFundsAccountTransactionServiceImpl implements FundsAccountTransactionService {

    private final Collection<FundsAccountTransactionService> delegates;

    @Override
    public @NonNull FundsAccount getAccount(@NonNull FundsAccountId accountId) {
        return getDelegate(accountId).getAccount(accountId);
    }

    @Override
    public @NonNull String transferIn(@NonNull FundsAccountId accountId, @NonNull FundsAccountTransactionRequest request) {
        return getDelegate(accountId).transferIn(accountId, request);
    }

    @Override
    public @NonNull String transferOut(@NonNull FundsAccountId accountId, @NonNull FundsAccountTransactionRequest request) {
        return getDelegate(accountId).transferOut(accountId, request);
    }

    @Override
    public @NonNull String refund(@NonNull FundsAccountId accountId, @NonNull FundsAccountTransactionRequest request) {
        return getDelegate(accountId).refund(accountId, request);
    }


    @Override
    public @NonNull String freeze(@NonNull FundsAccountId accountId, @NonNull FundsAccountTransactionRequest request) {
        return getDelegate(accountId).freeze(accountId, request);
    }

    @Override
    public @NonNull String unfreeze(@NonNull FundsAccountId accountId, @NonNull FundsAccountFreezeTransactionRequest request) {
        return getDelegate(accountId).unfreeze(accountId, request);
    }

    @Override
    public @NonNull String payFromFrozenBalance(@NonNull FundsAccountId accountId, @NonNull FundsAccountFreezeTransactionRequest request) {
        return getDelegate(accountId).payFromFrozenBalance(accountId, request);
    }

    @Override
    public boolean supports(@NonNull FundsAccountId id) {
        return true;
    }

    private FundsAccountTransactionService getDelegate(FundsAccountId id) {
        Optional<FundsAccountTransactionService> result = delegates.stream().filter(delegate -> delegate.supports(id)).findFirst();
        return result.orElseThrow(() -> BaseException.common("不支持处理的交易账号：id = " + id.getId() + ", accountType = " + id.getAccountType()));
    }

}
