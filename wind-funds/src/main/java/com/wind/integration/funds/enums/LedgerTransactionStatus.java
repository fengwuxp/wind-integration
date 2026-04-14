package com.wind.integration.funds.enums;

import com.wind.common.enums.DescriptiveEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 账本交易状态枚举
 *
 * @author wuxp
 * @date 2026-04-13 11:04
 **/
@AllArgsConstructor
@Getter
public enum LedgerTransactionStatus implements DescriptiveEnum {

    PENDING("待处理"),

    POSTED("已记账"),

    SETTLED("已结算"),

    REVERSED("已取消"),

    FAILED("处理失败");

    private final String desc;
}
