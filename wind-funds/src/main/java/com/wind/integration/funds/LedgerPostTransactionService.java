package com.wind.integration.funds;

import com.wind.integration.funds.ledger.LedgerTransactionDefinition;
import org.jspecify.annotations.NonNull;

/**
 * 资金账户交易服务
 * 基于借记或者贷记进行资金账户交易
 *
 * @author wuxp
 * @date 2026-04-09 10:06
 **/
public interface LedgerPostTransactionService {

    /**
     * 账本交易
     *
     * @param transaction 账本交易定义
     * @return 交易 sn
     */
    @NonNull
    String post(@NonNull LedgerTransactionDefinition transaction);

    /**
     * @param transaction 交易信息
     * @return 是否支持处理该交易
     */
    boolean supports(@NonNull LedgerTransactionDefinition transaction);
}
