package com.wind.integration.funds.ledger;

import com.wind.integration.funds.enums.LedgerReconcileStatus;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/**
 * 账本对账定义
 *
 * @author wuxp
 * @date 2026-04-09 09:19
 **/
public interface LedgerReconciliationDefinition {

    /**
     * 交易所属账期（用于查询、对账聚合）例如：
     * <pre>
     * 类型	     示例
     * 日对账	2026-04-08
     * 周对账	2026-W14
     * 月对账	2026-04
     * T+1 对账	2026-04-08（对 04-07 数据）
     * </
     */
    String getAccountingPeriod();

    /**
     * 对账批次，例如：
     * <pre>
     * 同一个周期可以有多个 batch：
     * 场景	    示例
     * 初次对账	batch-001
     * 重跑	    batch-002
     * 修复差异	batch-003
     * 支持 幂等 & 重试
     * 支持 多版本结果
     * 保留审计历史
     * </pre>
     */
    String getReconciliationBatch();

    /**
     * 账本交易对账状态
     */
    @NotNull
    LedgerReconcileStatus getReconcileStatus();

    /**
     * 对账备注
     */
    String getReconcileRemark();

    /**
     * 最近一次对账时间
     */
    LocalDateTime getReconciliationTime();

    /**
     * 对账完成时间
     */
    LocalDateTime getReconciliationCompletedTime();

}
