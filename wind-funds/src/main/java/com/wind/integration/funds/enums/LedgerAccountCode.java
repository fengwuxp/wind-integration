package com.wind.integration.funds.enums;

import com.wind.common.enums.DescriptiveEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 账本会计科目编码（Ledger Account Code）
 *
 * <p>定义：资金在账户中的“状态分类”，用于余额分桶
 *
 * <h2>核心思想</h2>
 * <ul>
 *   <li>✔ 不表达账户归属</li>
 *   <li>✔ 只表达余额状态</li>
 *   <li>✔ 全系统统一复用（Wallet / Platform / Merchant）</li>
 * </ul>
 *
 * <h2>余额模型</h2>
 * <ul>
 *   <li>AVAILABLE：可用余额</li>
 *   <li>FROZEN：冻结余额</li>
 *   <li>AUTHORIZATION：授权占用</li>
 *   <li>SETTLEMENT：结算中</li>
 *   <li>PREPAYMENT：预收/预付</li>
 *
 * @author wuxp
 * @date 2026-04-23
 */
@AllArgsConstructor
@Getter
public enum LedgerAccountCode implements DescriptiveEnum {

    /**
     * 现金/核心资金
     */
    CASH("银行现金/资金池"),

    /**
     * 可用余额（核心余额）
     */
    AVAILABLE("可用余额"),

    /**
     * 冻结余额（不可用）
     */
    FROZEN("冻结余额"),

    /**
     * 授权占用余额（VCC / preauth）
     */
    AUTHORIZATION("授权占用"),


    /**
     * 在途/清算中资金
     */
    IN_TRANSIT("在途资金"),

    /**
     * 清算中余额
     */
    CLEARING("清算中资金"),

    /**
     * 结算中资金
     */
    SETTLEMENT("结算中资金"),

    /**
     * 预收款（用户已付未归属）
     */
    PREPAYMENT("预收款"),

    /**
     * 风险准备金
     */
    RISK_RESERVE("风险准备金"),

    /**
     * 手续费收入
     */
    FEE_INCOME("手续费收入"),

    /**
     * 系统过渡账户
     */
    SUSPENSE("待分配账户");;

    private final String desc;
}