package com.wind.transaction.core.enums;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * @author wuxp
 * @date 2025-01-22 15:45
 **/
class CurrencyIsoCodeTests {

    @Test
    void testOf() {
        Assertions.assertEquals(CurrencyIsoCode.CNY, CurrencyIsoCode.of("CNY"));
        Assertions.assertEquals(CurrencyIsoCode.HKD, CurrencyIsoCode.of(CurrencyIsoCode.HKD.name()));
        Assertions.assertEquals(CurrencyIsoCode.HKD, CurrencyIsoCode.of(CurrencyIsoCode.HKD.getEnDesc()));
        Assertions.assertEquals(CurrencyIsoCode.HKD, CurrencyIsoCode.of(CurrencyIsoCode.HKD.getValue()));
    }

    @Test
    void testDeprecatedCurrency() {
        Assertions.assertEquals(CurrencyIsoCode.EUR, CurrencyIsoCode.of("250"));
        Assertions.assertEquals(CurrencyIsoCode.EUR, CurrencyIsoCode.of("280"));
        Assertions.assertEquals(CurrencyIsoCode.EUR, CurrencyIsoCode.of("196"));
        Assertions.assertEquals(CurrencyIsoCode.EUR, CurrencyIsoCode.of("382"));
        Assertions.assertEquals(CurrencyIsoCode.EUR, CurrencyIsoCode.of("440"));
        Assertions.assertEquals(CurrencyIsoCode.EUR, CurrencyIsoCode.of("528"));
        Assertions.assertEquals(CurrencyIsoCode.BRL, CurrencyIsoCode.of("986"));
        Assertions.assertEquals(CurrencyIsoCode.BRL, CurrencyIsoCode.of("987"));
        Assertions.assertEquals(CurrencyIsoCode.PLN, CurrencyIsoCode.requireOf("616"));
    }
}
