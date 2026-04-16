package com.wind.integration.funds.enums;

import com.wind.common.enums.DescriptiveEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 汇率类型枚举
 *
 * @author wuxp
 * @date 2026-04-15 14:26
 **/
@AllArgsConstructor
@Getter
public enum ExchangeRateType implements DescriptiveEnum {

    /**
     * 中间价（市场价）
     */
    MID("中间价（市场价）"),

    /**
     * 买入价（平台买入）
     */
    BID("买入价（平台买入）"),

    /**
     * 卖出价（平台卖出）
     */
    ASK("卖出价（平台卖出）");

    private final String desc;
}
