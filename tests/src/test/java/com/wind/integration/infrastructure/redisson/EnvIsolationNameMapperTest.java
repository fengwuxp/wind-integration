package com.wind.integration.infrastructure.redisson;

import com.wind.common.WindConstants;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * @author wuxp
 * @date 2023-11-10 07:56
 **/
class EnvIsolationNameMapperTest {

    private final EnvIsolationNameMapper nameMapper = new EnvIsolationNameMapper(WindConstants.DEV);

    @Test
    void testName() {
        String name = RandomStringUtils.randomAlphanumeric(32);
        Assertions.assertEquals("dev@" + name, nameMapper.map(name));
        Assertions.assertEquals(name, nameMapper.unmap(nameMapper.map(name)));
    }
}
