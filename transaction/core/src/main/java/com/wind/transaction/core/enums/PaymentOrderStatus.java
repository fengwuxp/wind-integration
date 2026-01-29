package com.wind.transaction.core.enums;

import com.wind.common.enums.DescriptiveEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 支付订单状态
 *
 * @author wuxp
 * @date 2026-01-29 10:37
 **/
@AllArgsConstructor
@Getter
public enum PaymentOrderStatus implements DescriptiveEnum {

    CREATED("已创建"),

    /**
     * 资金预留
     */
    FUNDS_RESERVED("资金预留"),

    /**
     * 已支付，可能是部分支付
     */
    PAID("已支付"),

    /**
     * 已退款，可能是部分退款
     */
    REFUNDED("已退款"),

    CANCELLED("已取消"),

    EXPIRED("已过期"),

    CLOSED("已关闭");

    private final String desc;
}
