package com.wind.transaction.core.enums;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * @author wuxp
 * @date 2025-03-28 21:52
 **/
class InternationalRegionCodeTests {

    @Test
    void testGetByCode(){
        InternationalRegionCode regionCode = InternationalRegionCode.CN;
        Assertions.assertEquals(regionCode,InternationalRegionCode.getByCode(regionCode.name()));
        Assertions.assertEquals(regionCode,InternationalRegionCode.getByCode(regionCode.getCountryCode()));
        Assertions.assertEquals(regionCode,InternationalRegionCode.getByCode(regionCode.getCountryNumCode()));
        Assertions.assertEquals(regionCode,InternationalRegionCode.getByCallingCode(regionCode.getCountryCallingCode()));
    }
}
