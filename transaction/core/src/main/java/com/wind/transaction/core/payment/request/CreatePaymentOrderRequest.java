package com.wind.transaction.core.payment.request;

import com.wind.transaction.core.Money;
import lombok.Data;
import org.jspecify.annotations.NonNull;

import java.time.LocalDateTime;

/**
 * 创建支付单请求参数
 *
 * @author wuxp
 * @date 2026-01-29 11:08
 **/
@Data
public class CreatePaymentOrderRequest {

    /**
     * 支付单归属用户
     */
    @NonNull
    private Long payerId;

    /**
     * 收款方标识
     */
    @NonNull
    private String payeeId;

    /**
     * 业务场景
     */
    @NonNull
    private String businessScene;

    /**
     * 订单总金额
     */
    @NonNull
    private Money totalAmount;

    /**
     * 支付单过期时间
     */
    private LocalDateTime paymentExpireTime;

    /**
     * 订单备注
     */
    private String remark;
}
