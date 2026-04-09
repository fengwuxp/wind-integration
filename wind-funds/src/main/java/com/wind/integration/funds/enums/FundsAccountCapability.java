package com.wind.integration.funds.enums;

import com.wind.common.enums.DescriptiveEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Set;

/**
 * 资金账户能力枚举
 *
 * @author wuxp
 * @date 2026-04-09 15:58
 **/
@AllArgsConstructor
@Getter
public enum FundsAccountCapability implements DescriptiveEnum {

    RECEIVE("收款"),

    PAY("付款"),

    WITHDRAW("提现");

    private final String desc;

    public static Set<FundsAccountCapability> allValues(){
        return Set.of(FundsAccountCapability.values());
    }
}
