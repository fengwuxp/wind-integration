package com.wind.integration.funds.enums;

import com.wind.common.enums.DescriptiveEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 账本分录方向（借贷）
 *
 * @author wuxp
 * @date 2026-01-29 13:37
 **/
@AllArgsConstructor
@Getter
public enum LedgerDirection implements DescriptiveEnum {

    /**
     * 借记
     */
    DEBIT("借记","Dr"),

    /**
     * 贷记
     */
    CREDIT("贷记","Cr");

    private final String desc;

    private final String symbol;

    /**
     * 返回相反方向
     */
    public LedgerDirection reverse() {
        return this == DEBIT ? CREDIT : DEBIT;
    }
}
