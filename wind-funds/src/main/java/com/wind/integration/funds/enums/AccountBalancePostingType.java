package com.wind.integration.funds.enums;

import com.wind.common.enums.DescriptiveEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 余额处理类型
 *
 * @author wuxp
 * @date 2026-04-21 13:56
 **/
@AllArgsConstructor
@Getter
public enum AccountBalancePostingType implements DescriptiveEnum {

    /**
     * 增加余额
     */
    AVAILABLE_INCREASE("入账", LedgerFundsFlowType.INFLOW),

    /**
     * 减少余额
     */
    AVAILABLE_DECREASE("出账", LedgerFundsFlowType.OUTFLOW),

    /**
     * 冻结余额
     */
    FREEZE("冻结", LedgerFundsFlowType.HOLD),

    /**
     * 解冻余额
     */
    UNFREEZE("解冻", LedgerFundsFlowType.HOLD),

    /**
     * 授权 pending 金额增加，可用余额减少
     */
    AUTHORIZE_HOLD("授权占用", LedgerFundsFlowType.INFLOW),

    /**
     * 授权撤销 pending 金额减少，可用余额增加
     */
    AUTHORIZE_REVERSE("授权撤销", LedgerFundsFlowType.RELEASE),

    /**
     * 授权 pending 金额减少 可用余额减少
     */
    AUTHORIZE_SETTLED("授权结算", LedgerFundsFlowType.OUTFLOW),

    ;
    private final String desc;

    private final LedgerFundsFlowType flowType;

    /**
     * @return 是否导致可用余额减少（包括实际扣减和冻结）
     */
    public boolean isAvailableDecrease() {
        return flowType == LedgerFundsFlowType.OUTFLOW || flowType == LedgerFundsFlowType.HOLD;
    }

    /**
     * @return 是否导致可用余额增加（包括实际增加和解冻）
     */
    public boolean isAvailableIncrease() {
        return flowType == LedgerFundsFlowType.INFLOW || flowType == LedgerFundsFlowType.RELEASE;
    }
}
