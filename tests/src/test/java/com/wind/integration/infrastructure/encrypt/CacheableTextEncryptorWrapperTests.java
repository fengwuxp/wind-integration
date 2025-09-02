package com.wind.integration.infrastructure.encrypt;

import com.wind.security.crypto.symmetric.AesTextEncryptor;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * @author wuxp
 * @date 2025-06-27 15:29
 **/
class CacheableTextEncryptorWrapperTests {

    private CacheableTextEncryptorWrapper encryptor;

    @BeforeEach
    void setup() {
        encryptor = new CacheableTextEncryptorWrapper(new AesTextEncryptor(CacheableTextEncryptorWrapper.generateAesKey(),
                CacheableTextEncryptorWrapper.generateSalt()));
    }

    @Test
    void testDecrypt() {
        for (int i = 0; i < 1000; i++) {
            String value = RandomStringUtils.secure().next(32);
            String encrypt = encryptor.encrypt(value);
            Assertions.assertEquals(value, encryptor.decrypt(encrypt));
        }
    }
}
