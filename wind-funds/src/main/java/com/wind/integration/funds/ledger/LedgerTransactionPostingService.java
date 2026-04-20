package com.wind.integration.funds.ledger;

import com.wind.integration.funds.ledger.spec.LedgerTransactionSpec;
import org.jspecify.annotations.NonNull;

/**
 * 账本交易处理服务
 *
 * @author wuxp
 * @date 2026-04-09 10:06
 **/
public interface LedgerTransactionPostingService {

    /**
     * 账本交易处理
     *
     * @param transaction 账本交易
     */
    void post(@NonNull LedgerTransactionSpec transaction);

}
