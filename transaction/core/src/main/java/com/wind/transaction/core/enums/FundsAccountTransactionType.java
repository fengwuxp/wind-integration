package com.wind.transaction.core.enums;

import com.wind.common.enums.DescriptiveEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 资金账户交易类型枚举
 *
 * @author wuxp
 * @date 2026-02-02 10:56
 **/
@Getter
@AllArgsConstructor
public enum FundsAccountTransactionType implements DescriptiveEnum {

    TRANSFER_IN("入账"),

    TRANSFER_OUT("支出"),

    FEE("手续费"),

    REFUND("退款"),

    FREEZE("冻结"),

    UNFREEZE("解冻"),

    WITHDRAW("提现");

    private final String desc;
}
