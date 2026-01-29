package com.wind.transaction.core.risk.request;

import jakarta.validation.constraints.NotNull;

/**
 * 解冻资金账户余额请求
 *
 * @author wuxp
 * @date 2026-01-29 14:41
 **/
public class UnFreezeAccountBalanceRequest {

    /**
     * 冻结流水号
     */
    @NotNull
    private String transactionSn;

    /**
     * 解冻金额
     */
    @NotNull
    private Long amount;

    /**
     * 业务场景
     */
    @NotNull
    private String businessScene;

    /**
     * 解冻原因
     */
    @NotNull
    private String reason;
}
