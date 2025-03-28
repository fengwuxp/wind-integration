package com.wind.transaction.core.enums;

import com.wind.transaction.core.AccountTransactionType;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 常用的交易类型
 *
 * @author wuxp
 * @date 2024-05-14 16:44
 **/
@AllArgsConstructor
@Getter
public enum DefaultTransactionType implements AccountTransactionType {

    /**
     * 存款（充值）
     */
    DEPOSIT("存款（充值）"),

    /**
     * 取款（提现）
     */
    WITHDRAW("取款（提现）"),

    /**
     * 支付
     */
    PAYMENT("支付"),

    /**
     * 退款
     */
    REFUND("退款"),

    /**
     * 手续费或服务费
     */
    FEE("手续费");

    private final String desc;
}
