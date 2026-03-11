package com.wind.integration.kms;

import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.Map;

/**
 * @author wuxp
 * @date 2026-03-11 17:05
 **/
class ChunkingWindCryptoClientTests {


    private ChunkingWindCryptoClient client;

    @BeforeEach
    void setup() {
        client = new ChunkingWindCryptoClient(new WindCryptoClient() {
            @Override
            public String encrypt(String keyId, String plaintext, Map<String, Object> options) {
                return Base64.getEncoder().encodeToString(plaintext.getBytes(StandardCharsets.UTF_8));
            }

            @Override
            public String decrypt(String cipherText, Map<String, Object> options) {
                return new String(Base64.getDecoder().decode(cipherText), StandardCharsets.UTF_8);
            }

            @Override
            public String decrypt(String keyId, String cipherText, Map<String, Object> options) {
                return new String(Base64.getDecoder().decode(cipherText), StandardCharsets.UTF_8);
            }
        });
    }

    @Test
    void testChunking(){
        String plaintext = RandomStringUtils.secure().nextAlphanumeric(9 * 1024);
        String encrypt = client.encrypt("keyId", plaintext, Collections.emptyMap());
        Assertions.assertTrue(encrypt.contains(" "));
        String decrypt = client.decrypt(encrypt, Collections.emptyMap());
        Assertions.assertEquals(plaintext, decrypt);
    }
}
