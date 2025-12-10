package com.wind.transaction.core;

import com.wind.common.WindConstants;
import com.wind.transaction.core.enums.CurrencyIsoCode;

/**
 * 这是一个可用于支出、收入的支付交易账户定义
 *
 * @author wuxp
 * @date 2023-12-01 10:37
 **/
public interface TransactionAccount {

    /**
     * @return 账户 id
     */
    TransactionAccountId getAccountId();

    /**
     * @return 账户所有者
     */
    String getOwner();

    /**
     * 账户转入（充值）累计数额
     *
     * @return 账户总转入数额 （总转入）
     */
    Long getDepositAmount();

    /**
     * 账户当下可用于支出的数额
     * 可用余额 = 累计转入额度 + 累计退款额度 - 累计提现 - 累计支出额度 - 累计冻结额度
     *
     * @return 账户可用数额
     */
    default Long getAvailableBalance() {
        return getDepositAmount() + getRefundedAmount() - getExpensesAmount() - getFreezeAmount();
    }

    /**
     * 账户余额 = 可用余额 + 累计冻结金额
     *
     * @return 账户余额
     */
    default Long getTotalBalance() {
        return getAvailableBalance() + getFreezeAmount();
    }

    /**
     * 累计账户由于未来某时刻支出需要临时冻结一部分余额，以保证在支付阶段不会由于 {@link #getAvailableBalance()} 不够导致支付失败
     *
     * @return 账户已冻结数额（已冻结）
     */
    Long getFreezeAmount();

    /**
     * 累计支出 = 累计支付（消费）+ 累计提现 + 累计手续费
     *
     * @return 累计账户已支出的数额（总支出）
     */
    Long getExpensesAmount();

    /**
     * 累计账户由于成功支付后、交易取消或部分取消退回账户的余额
     *
     * @return 已退款数额（总退款）
     */
    Long getRefundedAmount();

    /**
     * 累计提现
     *
     * @return 累计账户已提现的数额
     */
    Long getWithdrawAmount();

    /**
     * 累计手续费
     *
     * @return 交易服务费用
     */
    default Long getFeeAmount() {
        return 0L;
    }

    /**
     * 账户金额的币种
     *
     * @return 币种
     */
    default CurrencyIsoCode getCurrency() {
        return CurrencyIsoCode.CNY;
    }

    /**
     * @return 是否可用
     */
    default boolean isAvailable() {
        return getAvailableBalance() > 0;
    }

    /**
     * @return 是否允许透支
     */
    default boolean isAllowOverdraft() {
        return false;
    }

    /**
     * @return 账户描述
     */
    default String getDesc() {
        return WindConstants.EMPTY;
    }

}
