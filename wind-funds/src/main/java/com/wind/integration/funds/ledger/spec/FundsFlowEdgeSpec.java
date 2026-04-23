package com.wind.integration.funds.ledger.spec;

import com.wind.integration.funds.account.FundsAccountId;
import com.wind.transaction.core.Money;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;

/**
 * FundsFlowEdge（资金流边 / 转账路径边）
 *
 * <p>金融语义：
 * 表示一条资金从一个账户流向另一个账户的路径边。
 *
 * <p>工程语义：
 * Journal 的最小结构单元，用于描述资金路径图（Graph Edge）。
 *
 * <p>特点：
 * - 不涉及借贷
 * - 不涉及账本逻辑
 * - 只表达 money movement
 *
 * <p>例子：
 * BANK → RESERVE_FUND
 * RESERVE_FUND → USER_WALLET
 */
@Getter
@Builder
public final class FundsFlowEdgeSpec {

    /**
     * 资金来源账户
     */
    @NotNull
    private final FundsAccountId from;

    /**
     * 资金目标账户
     */
    @NotNull
    private final FundsAccountId to;

    @NotNull
    private final Money amount;
}
