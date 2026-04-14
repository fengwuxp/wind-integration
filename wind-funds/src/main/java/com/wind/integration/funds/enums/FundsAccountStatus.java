package com.wind.integration.funds.enums;

import com.wind.common.enums.DescriptiveEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 资金账户状态枚举
 *
 * @author wuxp
 * @date 2026-04-13 10:39
 **/
@AllArgsConstructor
@Getter
public enum FundsAccountStatus implements DescriptiveEnum {

    ACTIVE("已激活"),

    FROZEN("已冻结"),

    SUSPENDED("风控/合规"),

    CLOSED("已关闭");

    private final String desc;

    /**
     * 是否可以出账（借记）
     *
     * @return 是否可以出账
     */
    public boolean canDebit() {
        return this == ACTIVE;
    }

    /**
     * 是否允许入账（贷记）
     *
     * @return 是否允许入账
     */
    public boolean canCredit() {
        return this == ACTIVE || this == FROZEN;
    }
}
