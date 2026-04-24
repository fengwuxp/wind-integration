package com.wind.integration.funds.ledger.spec;

import org.jspecify.annotations.NonNull;

import java.util.List;

/**
 * LedgerIntent（账务意图模型）
 *
 * <p>定义：
 * 表达“一次业务资金行为的标准化意图”，
 * 是 Ledger 体系的统一输入源。
 *
 * <p>核心设计原则：
 * - ✔ 只描述“要发生什么资金变化”
 * - ✔ 不包含借贷规则（DR/CR）
 * - ✔ 不包含执行阶段（Phase）
 * - ✔ 不包含账本结构（Entry）
 *
 * <p>职责边界：
 * 1. 描述资金流转意图（from → to）
 * 2. 提供 LedgerAssembler 输入
 * 3. 支持可重放（replay source）
 *
 * <p>典型用途：
 * - 入金 / 出金 / 转账 / 退款
 * - 清结算前的统一表达
 * - 风控 / 路由决策输入
 *
 * <p>对标系统：
 * - Stripe: Transfer Intent
 * - Adyen: Payment Instruction
 */
public interface LedgerIntentSpec {

    /**
     * 意图类型（业务语义）
     */
    @NonNull
    String getIntentType();

    /**
     * 业务流水号（幂等键）
     */
    @NonNull
    String getBusinessSn();

    /**
     * 资金流转路径（from → to）
     *
     * <p>注意：
     * 这里只表达“账户关系”，不表达借贷
     */
    @NonNull
    List<FundsFlowEdgeSpec> getFlows();
}