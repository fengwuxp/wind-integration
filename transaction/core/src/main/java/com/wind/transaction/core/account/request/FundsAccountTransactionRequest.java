package com.wind.transaction.core.account.request;


import com.wind.transaction.core.Money;
import com.wind.transaction.core.TransactionContextVariables;
import com.wind.transaction.core.account.FundsAccountId;
import com.wind.transaction.core.account.FundsAccountOwner;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Getter;

/**
 * 资金账户交易请求
 *
 * @author wuxp
 * @date 2023-11-28 19:31
 **/
@Builder
@Getter
public class FundsAccountTransactionRequest {

    /**
     * 账户 owner
     */
    @NotNull
    private final FundsAccountOwner owner;

    /**
     * 交易对方账户
     */
    @NotNull
    private final FundsAccountId counterpartyAccountId;

    /**
     * 交易金额
     */
    @NotNull
    private final Money amount;

    /**
     * 原始金额
     */
    @NotNull
    private final Money originalAmount;

    /**
     * 关联的交易流水号 sn
     */
    @Size(min = 8, max = 80)
    private final String referenceTransactionSn;

    /**
     * 交易类型
     */
    @NotBlank
    @Size(max = 20)
    private final String category;

    /**
     * 业务场景
     */
    @NotBlank
    @Size(max = 30)
    private final String businessScene;

    /**
     * 描述（备注）
     */
    @Size(max = 300)
    private final String description;

    /**
     * 上下文透传变量
     */
    @NotNull
    private final TransactionContextVariables contextVariables = TransactionContextVariables.of();
}
