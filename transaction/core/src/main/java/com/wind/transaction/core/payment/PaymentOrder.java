package com.wind.transaction.core.payment;

import com.wind.integration.core.model.TenantIsolationObject;
import com.wind.transaction.core.Money;
import com.wind.transaction.core.enums.PaymentOrderStatus;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/**
 * 支付订单（只表达业务订单）
 *
 * @author wuxp
 * @date 2026-01-29 10:14
 **/
public interface PaymentOrder extends TenantIsolationObject<Long> {

    /**
     * 创建时间
     */
    @NotNull
    LocalDateTime getGmtCreate();

    /**
     * 订单编号
     */
    @NotNull
    String getSn();

    /**
     * 业务场景
     */
    @NotNull
    String getBusinessScene();

    /**
     * 订单归属用户（业务视角）
     * 不等同于实际出资方
     */
    @NotNull
    Long getOwnerUserId();

    /**
     * 付款方标识
     */
    @NotNull
    String getPayerId();

    /**
     * 付款方类型
     */
    @NotNull
    String getPayerType();

    /**
     * 收款方标识
     */
    @NotNull
    String getPayeeId();

    /**
     * 收款方类型
     */
    @NotNull
    String getPayeeType();

    /**
     * 订单总金额
     */
    @NotNull
    Money getTotalAmount();

    /**
     * 订单实付金额
     */
    @NotNull
    Money getActualAmount();

    /**
     * 已退款金额
     */
    @NotNull
    Money getRefundAmount();

    /**
     * 状态
     */
    @NotNull
    PaymentOrderStatus getStatus();

    /**
     * 付款时间
     */
    LocalDateTime getPaymentTime();

    /**
     * 订单最后退款时间
     */
    LocalDateTime getLastRefundTime();

    /**
     * 订单完成时间（不关心是否成功）
     * {@link #getStatus()}
     */
    LocalDateTime getCompletedTime();

    /**
     * 支付有效期截止时间（超时未完成支付将过期）
     * 为空表示不限制
     */
    LocalDateTime getPaymentExpireTime();

    /**
     * 备注
     */
    String getRemark();

    /**
     * @return 未支付金额
     */
    @NotNull
    default Money getUnpaidAmount() {
        return getTotalAmount().subtract(getActualAmount());
    }

    /**
     * @return 可退款金额
     */
    @NotNull
    default Money getRefundableAmount() {
        return getActualAmount().subtract(getRefundAmount());
    }

    /**
     * 部分支付
     *
     * @return true:是
     */
    default boolean isPartiallyPaid() {
        return !getActualAmount().isZero() && getActualAmount().lt(getTotalAmount());
    }

    /**
     * 部分退款
     *
     * @return true:是
     */
    default boolean isPartiallyRefunded() {
        return !getRefundAmount().isZero() && getRefundAmount().lt(getActualAmount());
    }

}
