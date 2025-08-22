package com.wind.transaction.core;

import com.wind.common.exception.BaseException;
import com.wind.transaction.core.enums.CurrencyIsoCode;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static com.wind.transaction.core.Money.CURRENCY_ISO_CODE_NOT_MATCH;

/**
 * @author wuxp
 * @date 2024-09-19 17:12
 **/

class MoneyTests {

    @Test
    void testComparable() {
        Money o1 = Money.immutable(1, CurrencyIsoCode.AED);
        Money o2 = Money.immutable(2, CurrencyIsoCode.AED);
        Assertions.assertTrue(o1.lt(o2));
        Assertions.assertTrue(o1.lte(Money.immutable(1, CurrencyIsoCode.AED)));
        Assertions.assertTrue(o2.gt(o1));
        Assertions.assertTrue(o2.gte(Money.immutable(2, CurrencyIsoCode.AED)));
        Assertions.assertNotEquals(o2, o1);
        Assertions.assertEquals(o2, Money.immutable(2, CurrencyIsoCode.AED));

        BaseException exception = Assertions.assertThrows(BaseException.class, () -> o1.lte(Money.immutable(1, CurrencyIsoCode.BDT)));
        Assertions.assertEquals(CURRENCY_ISO_CODE_NOT_MATCH, exception.getMessage());
    }

    @Test
    void testNegative() {
        Assertions.assertEquals("$1.00", Money.immutable(100, CurrencyIsoCode.USD).toString());
        Assertions.assertEquals("-$1.00", Money.immutable(100, CurrencyIsoCode.USD).asNegativeString());
    }
}
