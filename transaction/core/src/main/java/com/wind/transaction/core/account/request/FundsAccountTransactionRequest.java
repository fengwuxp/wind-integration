package com.wind.transaction.core.account.request;


import com.wind.transaction.core.Money;
import com.wind.transaction.core.TransactionContextVariables;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * 资金账户交易请求
 *
 * @author wuxp
 * @date 2023-11-28 19:31
 **/
@Data
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
public class FundsAccountTransactionRequest {

    /**
     * 交易金额
     */
    @NotNull
    private Money amount;

    /**
     * 关联的交易流水号 sn
     */
    @Size(min = 8, max = 80)
    private String referenceTransactionSn;

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
     * 描述（备注）
     */
    @Size(max = 300)
    private String description;

    /**
     * 上下文透传变量
     */
    @NotNull
    private TransactionContextVariables contextVariables = TransactionContextVariables.of();
}
