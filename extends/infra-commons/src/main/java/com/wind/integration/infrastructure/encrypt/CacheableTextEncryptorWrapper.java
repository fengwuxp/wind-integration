package com.wind.integration.infrastructure.encrypt;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.wind.common.annotations.VisibleForTesting;
import com.wind.security.crypto.symmetric.AesTextEncryptor;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.security.crypto.keygen.KeyGenerators;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

/**
 * 缓存加密解密
 *
 * @author wuxp
 * @date 2025-06-27 13:29
 **/
public class CacheableTextEncryptorWrapper implements TextEncryptor {

    private final TextEncryptor delegate;

    private final AesTextEncryptor localTextEncryptor;

    private final Cache<String, String> decryptedCaches;

    public CacheableTextEncryptorWrapper(TextEncryptor delegate, Duration cacheExpire, int maxSize) {
        this.delegate = delegate;
        this.decryptedCaches = Caffeine.newBuilder()
                .expireAfterAccess(cacheExpire)
                .maximumSize(maxSize)
                .build();
        this.localTextEncryptor = new AesTextEncryptor(generateAesKey(), generateSalt());
    }

    public CacheableTextEncryptorWrapper(TextEncryptor delegate) {
        // 16 * 1024 = 16384
        this(delegate, Duration.ofMinutes(15), 16384);
    }

    public static CacheableTextEncryptorWrapper alibabaKms() {
        return new CacheableTextEncryptorWrapper(new AlibabaKmsEncryptor());
    }

    @Override
    public String encrypt(String text) {
        return delegate.encrypt(text);
    }

    @Override
    public String decrypt(String encryptedText) {
        String localEncrypt = decryptedCaches.get(DigestUtils.sha256Hex(encryptedText.getBytes(StandardCharsets.UTF_8)), k -> {
            String val = delegate.decrypt(encryptedText);
            return localTextEncryptor.encrypt(val);
        });
        return localTextEncryptor.decrypt(localEncrypt);
    }

    /**
     * 生成 base64 编码的 AES 256-bit 密钥
     */
    @VisibleForTesting
    static String generateAesKey() {
        byte[] key = KeyGenerators.secureRandom(256).generateKey();
        return Base64.getEncoder().encodeToString(key);
    }

    /**
     * 生成 base64 编码的 Salt (128-bit)
     */
    @VisibleForTesting
    static String generateSalt() {
        byte[] salt = KeyGenerators.secureRandom(128).generateKey();
        return Base64.getEncoder().encodeToString(salt);
    }
}
