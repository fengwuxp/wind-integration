package com.wind.integration.funds.enums;

import com.wind.common.enums.DescriptiveEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 账本清算状态枚举
 *
 * @author wuxp
 * @date 2026-04-13 15:47
 **/
@AllArgsConstructor
@Getter
public enum LedgerSettlementStatus implements DescriptiveEnum {

    PENDING("待清算"),

    SETTLED("已清算"),

    FAILED("清算失败");

    private final String desc;
}
