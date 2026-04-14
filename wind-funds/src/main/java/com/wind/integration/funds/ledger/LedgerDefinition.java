package com.wind.integration.funds.ledger;

import com.wind.integration.core.model.TenantIsolationObject;
import com.wind.integration.funds.account.FundsAccountId;
import com.wind.integration.funds.enums.LedgerType;
import org.jspecify.annotations.NonNull;

/**
 * 账本
 *
 * @author wuxp
 * @date 2026-04-13 09:06
 **/
public interface LedgerDefinition extends TenantIsolationObject<Long> {

    /**
     * @return 资金账户ID
     */
    @NonNull
    FundsAccountId getAccountId();

    /**
     * 账本名称
     *
     * @return 账本名称
     */
    @NonNull
    String getLedgerName();

    /**
     * 表示这个账本具体是什么 (业务语义)
     *
     * @return 账本编码 (会计科目编码)
     */
    @NonNull
    String getLedgerCode();

    /**
     * 这个账本属于哪一类会计结构
     *
     * @return 账本类型
     */
    @NonNull
    LedgerType getLedgerType();
}
