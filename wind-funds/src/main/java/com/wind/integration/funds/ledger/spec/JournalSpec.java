package com.wind.integration.funds.ledger.spec;

import org.jspecify.annotations.NonNull;

import java.util.List;

/**
 * Journal（资金流模型 / 资金路径模型）
 *
 * <p>金融语义：
 * Journal 用于描述一笔业务资金在不同账户之间的“流转路径”，
 * 它不关心借贷规则，也不关心最终账本，只描述 money movement graph。
 *
 * <p>工程语义：
 * - 资金如何流动（from → to）
 * - 不包含 DR/CR
 * - 可用于重算 Ledger
 * - 可用于清结算路由
 *
 * <p>对标系统：
 * - Stripe: Movement / Balance Transfer Graph
 * - Highnote: Funds Movement Model
 *
 * <p>核心职责：
 * 1. 描述资金流向结构
 * 2. 提供可重放的资金路径
 * 3. 支撑 ledger 生成
 */
public interface JournalSpec {

    @NonNull
    String getJournalType();

    @NonNull
    String getBusinessSn();

    /**
     * 资金流路径
     */
    @NonNull
    List<FundsFlowEdgeSpec> getFlow();
}
