package com.wind.integration.funds.enums;

import com.wind.common.enums.DescriptiveEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * LedgerPostingIntent（账务意图）
 * 定义：这笔账“为什么发生”
 * ⚠️ 注意：
 * - 不等于执行阶段（Phase）
 * - 不等于执行方式（BatchType）
 */
@AllArgsConstructor
@Getter
public enum LedgerPostingIntentType implements DescriptiveEnum {

    // =========================
    // 1. 资金流基础行为
    // =========================

    /**
     * 充值
     */
    TOPUP("充值"),

    /**
     * 提现
     */
    WITHDRAWAL("提现"),

    /**
     * 转账
     */
    TRANSFER("转账"),


    // =========================
    // 2. 支付类（核心）
    // =========================

    /**
     * 授权（预占用资金）
     */
    AUTHORIZATION("授权"),

    /**
     * 扣款（确认消费）
     */
    CAPTURE("扣款"),

    /**
     * 授权撤销
     */
    AUTHORIZATION_REVERSAL("授权撤销"),

    /**
     * 授权结算（最终扣款）
     */
    AUTHORIZATION_SETTLEMENT("授权结算"),

    // =========================
    // 3. 清结算类
    // =========================

    /**
     * 清算
     */
    SETTLEMENT("清算"),

    /**
     * 退款
     */
    REFUND("退款"),

    /**
     * 冲正 / 撤销
     */
    REVERSAL("冲正"),

    /**
     * 调账
     */
    ADJUSTMENT("调账");

    private final String desc;
}