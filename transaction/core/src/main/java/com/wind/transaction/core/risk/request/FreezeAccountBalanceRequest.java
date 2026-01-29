package com.wind.transaction.core.risk.request;

import jakarta.validation.constraints.NotNull;

/**
 * 冻结资金账户余额请求
 *
 * @author wuxp
 * @date 2026-01-29 14:41
 **/
public class FreezeAccountBalanceRequest {

    /**
     * 冻结关联的交易流水
     */
    private String transactionSn;

    /**
     * 冻结金额
     */
    @NotNull
    private Long amount;

    /**
     * 业务场景
     */
    @NotNull
    private String businessScene;

    /**
     * 冻结原因
     */
    @NotNull
    private String reason;
}
