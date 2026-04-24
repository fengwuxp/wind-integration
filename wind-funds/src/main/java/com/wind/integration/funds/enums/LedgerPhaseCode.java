package com.wind.integration.funds.enums;

import com.wind.common.enums.DescriptiveEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 账本资金动作阶段（Ledger Phase）
 *
 * <p>定义：描述“资金发生了什么行为”，不涉及余额计算规则
 *
 * <h2>设计原则</h2>
 * <ul>
 *   <li>✔ 只描述资金流动行为</li>
 *   <li>✔ 不包含账户类型、不包含借贷方向</li>
 *   <li>✔ 可用于 PostingGroup 分组</li>
 * </ul>
 *
 * <h2>典型用途</h2>
 * <ul>
 *   <li>充值 / 提现 / 转账</li>
 *   <li>冻结 / 解冻</li>
 *   <li>结算 / 退款 / 调账</li>
 * </ul>
 */
@AllArgsConstructor
@Getter
public enum LedgerPhaseCode implements DescriptiveEnum {

    /**
     * 外部入金
     */
    FUND_IN("外部入金"),

    /**
     * 外部出金
     */
    FUND_OUT("外部出金"),

    /**
     * 清算/结算
     */
    SETTLEMENT("资金结算"),

    /**
     * 转账
     */
    TRANSFER("转账"),

    /**
     * 冻结资金
     */
    FREEZE("资金冻结"),

    /**
     * 解冻资金
     */
    UNFREEZE("资金解冻"),

    /**
     * 预授权占用
     */
    AUTHORIZATION("授权占用"),

    /**
     * 预授权释放
     */
    REVERSAL("授权释放"),

    /**
     * 授权转结算（capture）
     */
    CAPTURE("授权结算"),

    /**
     * 退款
     */
    REFUND("退款"),

    /**
     * 调账
     */
    ADJUSTMENT("调账");

    private final String desc;
}