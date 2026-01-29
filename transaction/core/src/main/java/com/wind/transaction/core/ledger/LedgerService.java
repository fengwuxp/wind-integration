package com.wind.transaction.core.ledger;

import org.jspecify.annotations.NonNull;

import java.util.List;

/**
 * 账本服务
 *
 * @author wuxp
 * @date 2026-01-29 14:27
 **/
public interface LedgerService {

    /**
     * 记录一笔交易
     *
     * @param transaction 交易信息，包含所有 LedgerEntry
     * @return 账本交易ID
     */
    @NonNull
    Long createTransaction(@NonNull LedgerTransaction transaction);

    /**
     * 查询账本交易
     *
     * @param transactionSn 交易流水号
     * @return LedgerTransaction
     */
    LedgerTransaction getTransaction(@NonNull String transactionSn);

    /**
     * 查询某账户的账本条目
     *
     * @param accountId   账户ID
     * @param accountType 账户类型
     * @return LedgerEntry 列表
     */
    List<LedgerEntry> getEntries(@NonNull String accountId, @NonNull String accountType);
}
