package com.wind.integration.funds.enums;

import com.wind.common.enums.DescriptiveEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 账本交易类型
 *
 * @author wuxp
 * @date 2026-01-29 13:37
 **/
@AllArgsConstructor
@Getter
public enum LedgerEntryPostingType implements DescriptiveEnum {

    /**
     * 借记
     */
    DEBIT("借记"),

    /**
     * 贷记
     */
    CREDIT("贷记");

    private final String desc;
}
