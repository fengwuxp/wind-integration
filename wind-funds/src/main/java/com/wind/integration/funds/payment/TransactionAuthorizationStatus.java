package com.wind.integration.funds.payment;

import com.wind.common.enums.DescriptiveEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 交易授权状态
 *
 * @author wuxp
 * @date 2026-04-21 14:06
 **/
@AllArgsConstructor
@Getter
public enum TransactionAuthorizationStatus implements DescriptiveEnum {

    APPROVED("授权通过"),

    DECLINED("授权失败"),

    EXPIRED("授权过期"),

    REVERSED("授权撤销");

    private final String desc;

}
