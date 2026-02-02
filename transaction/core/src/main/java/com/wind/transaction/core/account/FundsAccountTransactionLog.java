package com.wind.transaction.core.account;


import com.wind.transaction.core.enums.CurrencyIsoCode;
import com.wind.transaction.core.enums.FundsAccountTransactionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 资金账户交易日志
 * 记录账户资金变动、冻结、解冻、支出、退款等操作
 * 可用于审计、对账和风控
 *
 * @author wuxp
 * @date 2026-02-02
 */
@Data
public class FundsAccountTransactionLog implements Serializable {

    /**
     * 主键
     */
    @NotNull
    private Long id;

    /**
     * 创建时间
     */
    @NotNull
    private LocalDateTime gmtCreate;

    /**
     * 最后更新时间
     */
    @NotNull
    private LocalDateTime gmtModified;

    /**
     * 资金账户ID
     */
    @NotNull
    private String accountId;

    /**
     * 账户类型
     */
    @NotNull
    private String accountType;

    /**
     * 关联的业务交易编号
     */
    @NotBlank
    private String transactionSn;

    /**
     * 日志类型
     */
    @NotNull
    private FundsAccountTransactionType type;

    /**
     * 交易类别
     */
    @NotNull
    private String category;

    /**
     * 交易场景
     */
    @NotBlank
    private String businessScene;

    /**
     * 本次变动金额（单位分）
     */
    @NotNull
    private Long amount;

    /**
     * 币种
     */
    @NotNull
    private CurrencyIsoCode currency;

    /**
     * 变动前账户总余额
     */
    @NotNull
    private Long balanceBefore;

    /**
     * 变动前可用余额
     */
    @NotNull
    private Long availableBefore;

    /**
     * 变动前冻结余额
     */
    @NotNull
    private Long freezeBefore;

    /**
     * 变动后账户总余额
     */
    @NotNull
    private Long balanceAfter;

    /**
     * 变动后可用余额
     */
    @NotNull
    private Long availableAfter;

    /**
     * 变动后冻结余额
     */
    @NotNull
    private Long freezeAfter;

    /**
     * 备注
     */
    private String description;

    /**
     * 上下文变量
     */
    private Map<String, String> context;

    /**
     * 防篡改 sha256
     */
    @NotNull
    private String sha256;

}