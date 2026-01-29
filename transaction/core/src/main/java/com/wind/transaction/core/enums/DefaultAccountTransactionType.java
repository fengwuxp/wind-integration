package com.wind.transaction.core.enums;

import com.wind.common.enums.DescriptiveEnum;
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
public enum DefaultAccountTransactionType implements DescriptiveEnum {

    /**
     * 充值
     */
    TOP_UP("充值"),

    /**
     * 取款（提现）
     */
    WITHDRAWAL("提现"),

    /**
     * 支付
     */
    PAYMENT("支付"),

    /**
     * 退款
     */
    REFUND("退款"),

    /**
     * 服务费
     */
    SERVICE_FEE("服务费"),

    /**
     * 交易手续费
     */
    TRANSACTION_FEE("交易手续费");

    private final String desc;
}
