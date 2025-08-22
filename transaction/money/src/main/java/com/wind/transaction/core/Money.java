package com.wind.transaction.core;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wind.common.annotations.VisibleForTesting;
import com.wind.common.exception.AssertUtils;
import com.wind.transaction.core.enums.CurrencyIsoCode;
import jakarta.validation.constraints.NotNull;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.io.Serial;
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

    @Serial
    private static final long serialVersionUID = -7696239148769634763L;

    private static final String CURRENCY_ISO_CODE_NOT_NULL = "currency iso code must not null";

    @VisibleForTesting
    static final String CURRENCY_ISO_CODE_NOT_MATCH = "currency mismatch";

    /**
     * 数额，单位: 分
     */
    private final long amount;

    /**
     * 币种
     */
    private final CurrencyIsoCode currency;

    @JsonCreator
    public Money(@JsonProperty("amount") long amount, @JsonProperty("currency") CurrencyIsoCode currency) {
        AssertUtils.isTrue(amount >= 0, "amount must >= 0");
        AssertUtils.notNull(currency, "argument currency must not null");
        this.amount = amount;
        this.currency = currency;
    }

    /**
     * 注意：单位为分，仅在金额不会超过 {@link Integer#MAX_VALUE} 时使用
     *
     * @return 金额值（int）
     */
    public int getIntAmount() {
        return Math.toIntExact(amount);
    }

    /**
     * money 格式化
     * eg: 10 CNY
     *
     * @return 10 CNY
     */
    public String formatWithCurrency() {
        return String.format("%s %s", this.fen2Yuan(), currency.name());
    }

    /**
     * money 加法
     *
     * @param money 被加的金额
     * @return 相加后的金额
     */
    public Money add(Money money) {
        AssertUtils.isTrue(currency.equals(money.getCurrency()), CURRENCY_ISO_CODE_NOT_MATCH);
        return Money.immutable(amount + money.getAmount(), currency);
    }

    /**
     * money 减法
     *
     * @param money 被减的金额
     * @return 相减后的金额
     */
    public Money subtract(Money money) {
        AssertUtils.isTrue(currency.equals(money.getCurrency()), CURRENCY_ISO_CODE_NOT_MATCH);
        return Money.immutable(amount - money.getAmount(), currency);
    }

    /**
     * 分转元
     *
     * @return 金额元
     */
    public BigDecimal fen2Yuan() {
        return fenToYuan(amount, currency);
    }

    @Override
    public int compareTo(Money money) {
        AssertUtils.isTrue(Objects.equals(money.getCurrency(), getCurrency()), CURRENCY_ISO_CODE_NOT_MATCH);
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

    public static Money immutable(long amount, CurrencyIsoCode currency) {
        return new Money(amount, currency);
    }

    /**
     * 元转分：创建一个具有{@param amount} 数额的货币对象
     *
     * @param amountYuanText 数额(元)
     * @param currency       货币类型
     * @return 货币实例
     */
    public static Money immutable(String amountYuanText, CurrencyIsoCode currency) {
        return immutable(new BigDecimal(amountYuanText), currency);
    }

    /**
     * 元转分：创建一个具有{@param amount} 数额的货币对象
     *
     * @param amountYuan 数额(元)
     * @param currency   货币类型
     * @return 货币实例
     */
    public static Money immutable(@NotNull BigDecimal amountYuan, @NotNull CurrencyIsoCode currency) {
        AssertUtils.notNull(amountYuan, "argument amountYuan must not null");
        AssertUtils.notNull(currency, CURRENCY_ISO_CODE_NOT_NULL);
        long amount = amountYuan.scaleByPowerOfTen(currency.getPrecision()).longValue();
        return immutable(amount, currency);
    }

    /**
     * 分转元
     *
     * @return 金额元
     */
    public static BigDecimal fenToYuan(long amount, @NotNull CurrencyIsoCode currency) {
        AssertUtils.notNull(currency, CURRENCY_ISO_CODE_NOT_NULL);
        return BigDecimal.valueOf(amount).scaleByPowerOfTen(-currency.getPrecision());
    }

    /**
     * 分转元
     *
     * @return 金额元
     */
    public static BigDecimal fenToYuan(long amount) {
        return BigDecimal.valueOf(amount).scaleByPowerOfTen(-2);
    }

    @Override
    public String toString() {
        return String.format("%s%s", currency.getSign(), this.fen2Yuan());
    }

    /**
     * @return 负数金额字符串
     */
    public String asNegativeString() {
        return String.format("-%s%s", currency.getSign(), this.fen2Yuan());
    }
}
