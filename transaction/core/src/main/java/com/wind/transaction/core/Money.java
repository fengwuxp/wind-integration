package com.wind.transaction.core;

import com.wind.common.exception.AssertUtils;
import com.wind.transaction.core.enums.CurrencyIsoCode;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 用于描述货币的对象，包括货币数额和币种
 *
 * @author wuxp
 * @date 2023-10-06 09:01
 **/
@Getter
@EqualsAndHashCode
public final class Money implements Serializable {

    private static final long serialVersionUID = -7696239148769634763L;

    /**
     * 金额，单位：分
     */
    private final int amount;

    /**
     * 币种
     */
    private final CurrencyIsoCode currency;

    private Money(int amount, CurrencyIsoCode currency) {
        AssertUtils.isTrue(amount >= 0, "amount must greater than 0");
        AssertUtils.notNull(currency, "argument currencyType must not null");
        this.amount = amount;
        this.currency = currency;
    }

    /**
     * 转换为标准币种显示格式
     * eg: $10
     *
     * @return $10
     */
    public String asText() {
        return String.format("%s%s", currency.getSign(), this.fen2Yuan());
    }

    /**
     * money 加法
     *
     * @param money 被加的金额
     * @return 相加后的金额
     */
    public Money plus(Money money) {
        AssertUtils.isTrue(currency.equals(money.getCurrency()), "币种不一致");
        return Money.immutable(amount + money.getAmount(), currency);
    }

    /**
     * money 减法
     *
     * @param money 被减的金额
     * @return 相减后的金额
     */
    public Money subtract(Money money) {
        AssertUtils.isTrue(currency.equals(money.getCurrency()), "币种不一致");
        return Money.immutable(amount - money.getAmount(), currency);
    }

    /**
     * 获取货币Decimal
     */
    public BigDecimal fen2Yuan() {
        return BigDecimal.valueOf(amount).scaleByPowerOfTen(-currency.getPrecision());
    }

    /**
     * 创建一个具有{@param amount} 数额的货币对象
     *
     * @param amount       数额(分)
     * @param currencyIsoCode 货币类型
     * @return 货币实例
     */
    public static Money immutable(int amount, @NotNull CurrencyIsoCode currencyIsoCode) {
        return new Money(amount, currencyIsoCode);
    }


    /**
     * 元转分：创建一个具有{@param amount} 数额的货币对象
     *
     * @param amount       数额(元)
     * @param currencyIsoCode 货币类型
     * @return 货币实例
     */
    public static Money yuanToFee(@NotNull BigDecimal amount, @NotNull CurrencyIsoCode currencyIsoCode) {
        AssertUtils.notNull(amount, "argument amount must not null");
        int longAmount = amount.scaleByPowerOfTen(currencyIsoCode.getPrecision()).intValue();
        return immutable(longAmount, currencyIsoCode);
    }
}
