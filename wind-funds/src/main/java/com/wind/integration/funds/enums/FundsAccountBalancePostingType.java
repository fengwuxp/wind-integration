package com.wind.integration.funds.enums;

import com.wind.common.enums.DescriptiveEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 资金账户余额收支类型
 *
 * @author wuxp
 * @date 2026-04-09 15:19
 **/
@AllArgsConstructor
@Getter
public enum FundsAccountBalancePostingType implements DescriptiveEnum {

    /**
     * 增加余额
     */
    CREDIT_BALANCE("入账"),

    /**
     * 减少余额
     */
    DEBIT_BALANCE("出账"),

    /**
     * 通过冻结余额出账
     */
    DEBIT_BALANCE_FROM_FREEZE("冻结余额转出账"),

    FREEZE_BALANCE("冻结"),

    UNFREEZE_BALANCE("解冻");

    private final String desc;
}
