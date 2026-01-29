package com.wind.transaction.core.risk;

import com.wind.transaction.core.account.FundsAccountId;
import com.wind.transaction.core.risk.request.FreezeAccountBalanceRequest;
import com.wind.transaction.core.risk.request.UnFreezeAccountBalanceRequest;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * 资金账户风控服务
 *
 * @author wuxp
 * @date 2026-01-29 14:39
 **/
public interface FundsAccountRiskService {

    /**
     * 冻结账户
     *
     * @param accountId 账户标识
     * @param reason    风控原因
     */
    void freezeAccount(@NonNull FundsAccountId accountId, @NonNull String reason);

    /**
     * 解冻账户
     *
     * @param accountId 账户标识
     * @param reason    风控原因
     */
    void unfreezeAccount(@NonNull FundsAccountId accountId, @NonNull String reason);

    /**
     * 限制账户交易（可限制部分交易类型或全部交易）
     *
     * @param accountId            账户标识
     * @param reason               风控原因
     * @param restrictedCategories 限制的交易类型
     */
    void limitAccountTransaction(@NonNull FundsAccountId accountId, @NonNull String reason, @Nullable List<String> restrictedCategories);

    /**
     * 解除账户交易限制
     *
     * @param accountId  账户标识
     * @param reason     解控控原因
     * @param categories 解控的交易类型
     */
    void releaseAccountTransactionLimit(@NonNull FundsAccountId accountId, @NonNull String reason, @Nullable List<String> categories);

    /**
     * 冻结账户一部分资金（按交易冻结）
     *
     * @param accountId 账户标识
     * @param requests  冻结资金请求
     * @return 风控冻结流水号
     */
    @NonNull
    List<String> freezeAccountBalance(@NonNull FundsAccountId accountId, @NonNull List<FreezeAccountBalanceRequest> requests);

    /**
     * 解冻某笔交易的资金
     *
     * @param accountId 账户标识
     * @param requests  解冻资金请求
     * @return 风控冻结流水号
     */
    @NonNull
    List<String> unfreezeTransaction(@NonNull FundsAccountId accountId, @NonNull List<UnFreezeAccountBalanceRequest> requests);
}
