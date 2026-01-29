package com.wind.transaction.core.ledger;

import com.wind.transaction.core.enums.CurrencyIsoCode;
import com.wind.transaction.core.enums.LedgerEntryType;
import com.wind.transaction.core.enums.LedgerReconcileStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 账本条目
 *
 * @author wuxp
 * @date 2026-01-29 13:34
 **/
@Data
public class LedgerEntry implements Serializable {

    /**
     * 账本条目id
     */
    @NotNull
    private Long id;

    /**
     * 创建时间
     */
    @NotNull
    private LocalDateTime gmtCreate;

    /**
     * 资金账户 id
     */
    @NotNull
    private String accountId;

    /**
     * 资金账户类型
     */
    @NotNull
    private String accountType;

    /**
     * 会计科目编码（如 CASH / REVENUE / FEE)
     */
    @NotNull
    private String accountCode;

    /**
     * 账本条目类型
     */
    @NotNull
    private LedgerEntryType type;

    /**
     * 账本交易 id
     */
    @NotNull
    private Long ledgerTransactionId;

    /**
     * 交易流水号
     */
    @NotNull
    private String transactionSn;

    /**
     * 交易类型
     */
    @NotBlank
    @Size(max = 20)
    private String category;

    /**
     * 业务场景
     */
    @NotBlank
    @Size(max = 30)
    private String businessScene;

    /**
     * 记账金额，单位：分
     */
    @NotNull
    private Long amount;

    /**
     * 记账币种
     */
    @NotNull
    private CurrencyIsoCode currency;

    /**
     * 原始金额，单位：分
     */
    @NotNull
    private Long originalAmount;

    /**
     * 原始币种
     */
    @NotNull
    private CurrencyIsoCode originalCurrency;

    /**
     * 汇率
     */
    @NotNull
    private BigDecimal exchangeRate;

    /**
     * 描述
     */
    private String description;

    /**
     * 上下文
     */
    private Map<String, String> context;

    /**
     * 账本交易对账状态
     */
    @NotNull
    private LedgerReconcileStatus status;

    /**
     * 对账备注
     */
    private String reconcileRemark;

    /**
     * sha256
     */
    @NotNull
    private String sha256;
}
