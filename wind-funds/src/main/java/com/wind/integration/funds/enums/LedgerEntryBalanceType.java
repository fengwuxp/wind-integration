package com.wind.integration.funds.enums;

import com.wind.common.enums.DescriptiveEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 账本交易余额类型
 *
 * @author wuxp
 * @date 2026-04-09 13:12
 **/
@AllArgsConstructor
@Getter
public enum LedgerEntryBalanceType implements DescriptiveEnum {

    /**
     * 可用余额
     */
    AVAILABLE("可用余额"),

    /**
     * 冻结余额
     */
    FROZEN("冻结余额");

    private final String desc;
}
