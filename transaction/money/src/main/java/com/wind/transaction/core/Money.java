package com.wind.transaction.core;

import com.wind.transaction.core.enums.CurrencyIsoCode;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wind.common.exception.AssertUtils;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * 用于描述货币的对象，包括货币数额和币种
 *
 * @author wuxp
 * @date 2023-10-06 09:01
 **/
@Getter
@EqualsAndHashCode
public final class Money implements Serializable, Comparable<Money> {

    private static final long serialVersionUID = -7696239148769634763L;

    /**
     * 数额
     */
    private final int amount;

    /**
     * 币种
     */
    private final CurrencyIsoCode currency;


    @JsonCreator
    public Money(@JsonProperty("amount") int amount, @JsonProperty("currency") CurrencyIsoCode currency) {
        this.amount = amount;
        this.currency = currency;
    }

    /**
     * 转换为标准币种显示格式
     * eg: $10
     *
     * @return $10
     */
    public String toText() {
        return String.format("%s%s", currency.getSign(), this.fen2Yuan());
    }

    /**
     * money 加法
     *
     * @param money 被加的金额
     * @return 相加后的金额
     */
    public Money add(Money money) {
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

    @Override
    public int compareTo(Money money) {
        AssertUtils.isTrue(Objects.equals(money.getCurrency(), getCurrency()), "currency inconsistency");
        if (money.getAmount() == getAmount()) {
            return 0;
        }
        return getAmount() < money.getAmount() ? -1 : 1;
    }

    /**
     * 当前 Money 对象是否小于 {@param money}
     *
     * @param money 比较对象
     * @return if true 小于
     */
    public boolean lt(Money money) {
        return compareTo(money) < 0;
    }

    /**
     * 当前 Money 对象是否小于等于 {@param money}
     *
     * @param money 比较对象
     * @return if true 小于等于
     */
    public boolean lte(Money money) {
        return compareTo(money) <= 0;
    }

    /**
     * 当前 Money 对象是否大于 {@param money}
     *
     * @param money 比较对象
     * @return if true 大于
     */
    public boolean gt(Money money) {
        return compareTo(money) > 0;
    }

    /**
     * 当前 Money 对象是否大于等于 {@param money}
     *
     * @param money 比较对象
     * @return if true 大于等于
     */
    public boolean gte(Money money) {
        return compareTo(money) >= 0;
    }

    /**
     * 创建一个具有{@param amount} 数额的货币对象
     *
     * @param amount   数额(分)
     * @param currency 货币类型
     * @return 货币实例
     */
    public static Money immutable(int amount, CurrencyIsoCode currency) {
        return new Money(amount, currency);
    }


    /**
     * 元转分：创建一个具有{@param amount} 数额的货币对象
     *
     * @param amount   数额(元)
     * @param currency 货币类型
     * @return 货币实例
     */
    public static Money immutable(@NotNull BigDecimal amount, @NotNull CurrencyIsoCode currency) {
        AssertUtils.notNull(amount, "货币数额不能为空");
        AssertUtils.notNull(currency, "货币类型不能为空");
        int longAmount = amount.scaleByPowerOfTen(currency.getPrecision()).intValue();
        return immutable(longAmount, currency);
    }
}
