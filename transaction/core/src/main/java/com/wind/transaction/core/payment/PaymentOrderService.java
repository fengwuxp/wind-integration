package com.wind.transaction.core.payment;

import com.wind.transaction.core.payment.request.CreatePaymentOrderRequest;
import org.jspecify.annotations.NonNull;

/**
 * 支付单服务
 *
 * @author wuxp
 * @date 2026-01-29 10:13
 **/
public interface PaymentOrderService {

    /**
     * 创建支付单
     *
     * @param request 创建支付单请求
     * @return 支付单
     */
    @NonNull
    PaymentOrder create(@NonNull CreatePaymentOrderRequest request);
}
