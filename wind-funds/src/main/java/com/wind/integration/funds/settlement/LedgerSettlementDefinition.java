package com.wind.integration.funds.settlement;

import com.wind.integration.core.model.IdObject;
import com.wind.integration.funds.enums.LedgerSettlementStatus;
import org.jspecify.annotations.NonNull;

import java.time.LocalDateTime;

/**
 * 账本清结算算定义
 *
 * @author wuxp
 * @date 2026-04-13 15:45
 **/
public interface LedgerSettlementDefinition extends IdObject<Long> {

    /**
     * 交易所属账期（用于清结算）例如：
     * 类型	     示例
     * 日对账	2026-04-08
     * 周对账	2026-W14
     * 月对账	2026-04
     * T+1 对账	2026-04-08（对 04-07 数据）
     * </
     */
    @NonNull
    String getSettlementPeriod();

    /**
     * 最后一次结算时间
     */
    LocalDateTime getLatestSettlementTime();

    /**
     * 结算完成时间
     */
    LocalDateTime getSettlementCompletedTime();

    /**
     * 账本结算状态
     */
    @NonNull
    LedgerSettlementStatus getSettlementStatus();
}
