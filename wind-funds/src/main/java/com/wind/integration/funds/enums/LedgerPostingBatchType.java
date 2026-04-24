package com.wind.integration.funds.enums;

import com.wind.common.enums.DescriptiveEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * LedgerPostingBatchType（执行策略类型）
 *
 * <p>定义：这次账务“怎么执行”
 */
@AllArgsConstructor
@Getter
public enum LedgerPostingBatchType implements DescriptiveEnum {

    IMMEDIATE("立即执行"),
    DEFERRED("延迟执行"),
    SETTLEMENT("清算批次"),
    AUTHORIZATION("授权批次"),
    REVERSAL("冲正批次"),
    RECONCILIATION("对账修复");

    private final String desc;
}