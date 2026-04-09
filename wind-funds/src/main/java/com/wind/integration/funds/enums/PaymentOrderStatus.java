package com.wind.integration.funds.enums;

import com.wind.common.enums.DescriptiveEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Set;

/**
 * 账户资金交易订单枚举
 *
 * @author wuxp
 * @date 2026-04-09 13:29
 **/
@AllArgsConstructor
@Getter
public enum PaymentOrderStatus implements DescriptiveEnum {

    PENDING("待支付"),

    PARTIAL_PAYMENT("部分支付"),

    PAID("已支付"),

    PARTIAL_REFUND("部分退款"),

    REFUNDED("已退款"),

    CANCELLED("已取消"),

    EXPIRED("已失效");

    private final String desc;

    private static final Set<PaymentOrderStatus> ALLOW_PAY_STATES = Set.of(PENDING, PARTIAL_PAYMENT);

    private static final Set<PaymentOrderStatus> ALLOW_REFUND_STATES = Set.of(PARTIAL_PAYMENT, PAID, PARTIAL_REFUND);

    /**
     * 判断状态是否允许支付
     *
     * @param state 支付状态
     * @return 是否允许支付
     */
    public static boolean allowPay(PaymentOrderStatus state) {
        return ALLOW_PAY_STATES.contains(state);
    }

    /**
     * 判断状态是否允许退款
     *
     * @param state 支付状态
     * @return 是否允许支付
     */
    public static boolean allowRefund(PaymentOrderStatus state) {
        return ALLOW_REFUND_STATES.contains(state);
    }
}
