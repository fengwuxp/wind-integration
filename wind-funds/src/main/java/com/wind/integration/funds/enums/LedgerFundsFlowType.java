package com.wind.integration.funds.enums;

import com.wind.common.enums.DescriptiveEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 账目资金流动类型
 * 用于描述资金在账户体系中的“财务属性归类”，
 * 主要用于账务统计、对账、报表与资金流向分析。
 * 职责：
 * 1. 描述资金的方向性归类（流入 / 流出 / 占用 / 释放 / 调整）
 * 2. 用于财务报表维度聚合（收入、支出、冻结、释放等）
 * 3. 用于对账与资金流结构分析
 *
 * @author wuxp
 * @date 2024-05-14 16:44
 **/
@AllArgsConstructor
@Getter
public enum LedgerFundsFlowType implements DescriptiveEnum {

    /**
     * 充值、退款 等
     */
    INFLOW("资金流入"),

    /**
     * 支付、提现、手续费等
     */
    OUTFLOW("资金流出"),

    /**
     * 占用、冻结、授权
     */
    HOLD("资金占用（冻结/授权）"),

    /**
     * 释放、解冻、取消授权
     */
    RELEASE("资金释放（解冻/撤销）"),

    /**
     * 调整、系统修正
     */
    ADJUSTMENT("资金调整（人工/系统修正）");

    private final String desc;
}
