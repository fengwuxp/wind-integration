package com.wind.transaction.core.enums;

import com.wind.common.enums.DescriptiveEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 资金账户拥有者类型
 *
 * @author wuxp
 * @date 2026-01-29 11:26
 **/
@AllArgsConstructor
@Getter
public enum FundsAccountOwnerType implements DescriptiveEnum {

    USER("用户"),

    TENANT("租户"),

    MERCHANT("商户"),

    PLATFORM("平台"),
    ;

    private final String desc;
}
