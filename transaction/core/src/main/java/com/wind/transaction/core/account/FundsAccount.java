package com.wind.transaction.core.account;

import com.wind.common.WindConstants;
import com.wind.common.exception.AssertUtils;
import com.wind.integration.core.model.TenantIsolationObject;
import com.wind.transaction.core.enums.CurrencyIsoCode;
import org.jspecify.annotations.NonNull;

/**
 * 这是一个可用于支出、收入的资金账户定义，所有金额字段的单位都是分
 *
 * @author wuxp
 * @date 2023-12-01 10:37
 **/
public interface FundsAccount extends TenantIsolationObject<Long> {

    /**
     * 账户标识
     *
     * @return 账户标识
     */
    @NonNull
    FundsAccountId getAccountId();

    /**
     * 账户所属主体
     *
     * @return 账户所属主体
     */
    @NonNull
    FundsAccountOwner getOwner();

    /**
     * 账户当下可用于支出的数额
     * 可用余额 = 累计转入 + 累计退款 - 累计支出 - 冻结余额
     *
     * @return 账户可用数额
     */
    default Long getAvailableBalance() {
        return getDepositAmount() + getRefundedAmount() - getTotalOutflowAmount() - getFrozenBalance();
    }

    /**
     * 账户余额 = 可用余额 + 累计冻结金额
     *
     * @return 账户余额
     */
    default Long getTotalBalance() {
        return getAvailableBalance() + getFrozenBalance();
    }

    /**
     * 账户转入（充值）累计数额
     *
     * @return 账户总转入数额 （总转入）
     */
    Long getDepositAmount();

    /**
     * 累计资金流出（包含支付、提现、手续费）
     *
     * @return 累计账户已支出的数额（总支出）
     */
    default Long getTotalOutflowAmount() {
        return getPaymentAmount() + getFeeAmount() + getWithdrawAmount();
    }

    /**
     * 累计支付金额（消费支出）
     */
    Long getPaymentAmount();

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
     * 累计账户由于成功支付后、交易取消或部分取消退回账户的余额
     *
     * @return 已退款数额（总退款）
     */
    Long getRefundedAmount();

    /**
     * 累计账户因某些交易被冻结的余额（只增加）
     *
     * @return 账户累计冻结金额
     */
    Long getTotalFrozenAmount();

    /**
     * 累计解冻总金额（只增加）
     * 注意：冻结金额在支付成功后转为支出时，先计入总解冻金额（恢复可用余额），在通过累计 {@link #getPaymentAmount()} 支出
     *
     * @return 已解冻数额（总解冻）
     */
    Long getTotalUnfrozenAmount();

    /**
     * 累计账户由于未来某时刻支出需要临时冻结一部分余额，以保证在支付阶段不会由于 {@link #getAvailableBalance()} 不够导致支付失败
     *
     * @return 账户已冻结数额（已冻结）
     */
    default Long getFrozenBalance() {
        long result = getTotalFrozenAmount() - getTotalUnfrozenAmount();
        AssertUtils.isTrue(result >= 0, "account freeze balance less than 0");
        return result;
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
     * 账户是否可用，由具体实现决定
     *
     * @return if true 可用
     */
    default boolean isAvailable() {
        return getAvailableBalance() > 0;
    }

    /**
     * 是否允许透支，由具体实现决定
     *
     * @return if true 账户可以透支
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

    /**
     * 账户数据版本
     *
     * @return 账户数据版本
     */
    @NonNull
    Integer getVersion();
}
