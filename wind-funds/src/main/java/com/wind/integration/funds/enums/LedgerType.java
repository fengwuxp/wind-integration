package com.wind.integration.funds.enums;

import com.wind.common.enums.DescriptiveEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 账本类型枚举
 *
 * @author wuxp
 * @date 2026-04-13 11:12
 **/
@Getter
@AllArgsConstructor
public enum LedgerType implements DescriptiveEnum {

    /**
     * 资产类账户（用户/商户资金）
     * 正常余额：借（Debit）
     */
    ASSET("资产类", LedgerEntryPostingType.DEBIT),

    /**
     * 负债类账户（平台代管资金）
     * 正常余额：贷（Credit）
     */
    LIABILITY("负债类", LedgerEntryPostingType.CREDIT),

    /**
     * 收入类（平台收入，如手续费）
     * 正常余额：贷
     */
    REVENUE("收入类", LedgerEntryPostingType.CREDIT),

    /**
     * 成本类（平台支出）
     * 正常余额：借
     */
    EXPENSE("成本类", LedgerEntryPostingType.DEBIT),

    /**
     * 清算过渡账户（Clearing / Pending）
     * 正常余额：可正可负（特殊处理）
     */
    CLEARING("清算账户", null);

    private final String desc;

    /**
     * 账本 postingType
     */
    private final LedgerEntryPostingType postingType;
}
