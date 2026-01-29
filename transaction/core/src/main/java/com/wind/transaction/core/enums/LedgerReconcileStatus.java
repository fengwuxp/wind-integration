package com.wind.transaction.core.enums;

import com.wind.common.enums.DescriptiveEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 账本交易对账状态
 *
 * @author wuxp
 * @date 2026-01-29
 */
@AllArgsConstructor
@Getter
public enum LedgerReconcileStatus implements DescriptiveEnum {

    /**
     * 交易完成，但尚未对账
     */
    PENDING("待对账"),

    /**
     * 交易金额、状态与外部账单一致
     */
    MATCHED("对账成功"),

    /**
     * 发现异常，待人工确认
     */
    REVIEWING("待复核"),

    /**
     * 交易金额不一致
     */
    AMOUNT_MISMATCH("金额不符"),

    /**
     * 交易状态不同，如支付成功但系统未确认
     */
    STATUS_MISMATCH("状态不符"),

    /**
     * 交易在某一方账单缺失
     */
    MISSING("漏单"),

    /**
     * 存在相同交易重复记账
     */
    DUPLICATE("重复交易"),

    /**
     * 由于数据错误、系统异常等导致对账未完成
     */
    FAILED("对账失败"),

    /**
     * 发现异常后，已进行调整
     */
    CORRECTED("已修正"),

    /**
     * 资金已清算，无异常
     */
    SETTLED("已结算");

    private final String desc;

}
