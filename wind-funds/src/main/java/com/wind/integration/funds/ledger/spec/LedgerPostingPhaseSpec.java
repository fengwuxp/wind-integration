package com.wind.integration.funds.ledger.spec;

import com.wind.integration.funds.enums.LedgerPhaseCode;
import org.jspecify.annotations.NonNull;

import java.util.List;

/**
 * PostingPhase（记账阶段 / 资金流转阶段）
 *
 * <p>定义：
 * 描述“资金在该批次中经历的业务阶段”，仅用于语义表达与审计，
 * 不具备事务边界能力。
 *
 * <p>典型用途：
 * - 表达资金路径（FUND_IN → INTERNAL_SETTLE）
 * - 对账解释（why money moved）
 * - 审计日志
 *
 * <p>职责：
 * 1. 标识资金流转阶段
 * 2. 承载该阶段产生的分录
 */
public interface LedgerPostingPhaseSpec {

    /**
     * 阶段编码（资金行为）
     */
    @NonNull
    LedgerPhaseCode getPhaseCode();

    /**
     * 分录列表
     */
    @NonNull
    List<LedgerEntrySpec> getEntries();
}