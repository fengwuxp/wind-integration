package com.wind.integration.funds.ledger.spec;

import com.wind.integration.funds.account.FundsAccountId;
import com.wind.transaction.core.Money;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;

import java.util.Map;

/**
 * FundsFlowEdge（资金流边 / 意图流转边）
 *
 * <p>定义：
 * 表达资金从一个账户流向另一个账户的“业务意图路径”，
 * 是 Ledger 生成的输入结构之一，但不是账本事实。
 *
 * <p>核心原则：
 * - ❗ 只表达“钱要去哪”
 * - ❗ 不表达借贷（DR/CR）
 * - ❗ 不表达 ledgerCode
 * - ❗ 不直接等价 LedgerEntry
 *
 * <p>用途：
 * - LedgerAssembler 输入
 * - 资金路径建模（graph）
 * - 风控/路由决策
 *
 * <p>对标：
 * - Stripe: Transfer Flow Edge
 * - Adyen: Payment Movement Leg
 */
@Getter
@Builder
public final class FundsFlowEdgeSpec {

    /**
     * 来源账户
     */
    @NotNull
    private final FundsAccountId from;

    /**
     * 目标账户
     */
    @NotNull
    private final FundsAccountId to;

    /**
     * 金额（业务层金额，不是 ledger 分录金额）
     */
    @NotNull
    private final Money amount;

    /**
     * 业务流水号
     */
    @NotNull
    private final String businessSn;

    /**
     * 是否手续费路径
     */
    private final boolean fee;

    /**
     * 是否 FX（汇兑路径）
     */
    private final boolean fx;

    /**
     * 扩展上下文（用于路由 / 风控）
     */
    private final Map<String, Object> attributes;
}