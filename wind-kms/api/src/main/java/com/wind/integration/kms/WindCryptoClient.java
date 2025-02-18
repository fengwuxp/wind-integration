package com.wind.integration.kms;

import java.util.Collections;
import java.util.Map;

/**
 * kms 加解密 client
 *
 * @author wuxp
 * @date 2025-02-17 18:01
 **/
public interface WindCryptoClient {

    /**
     * 数据加密
     *
     * @param keyId     密钥的全局唯一标识符
     * @param plaintext 待加密的明文数据
     * @return 加密后的数据
     */
    default String encrypt(String keyId, String plaintext) {
        return encrypt(keyId, plaintext, Collections.emptyMap());
    }

    /**
     * 数据加密
     *
     * @param keyId     密钥的全局唯一标识符
     * @param plaintext 待加密的明文数据
     * @param options   加密请求配置
     * @return 加密后的数据
     */
    String encrypt(String keyId, String plaintext, Map<String, Object> options);

    /**
     * 数据加密
     *
     * @param cipherText 待解密的密文数据
     * @return 解密后的数据
     */
    default String decrypt(String cipherText) {
        return decrypt(cipherText, Collections.emptyMap());
    }

    /**
     * 数据加密
     *
     * @param cipherText 待解密的密文数据
     * @param options    解密请求配置
     * @return 解密后的数据
     */
    String decrypt(String cipherText, Map<String, Object> options);

    /**
     * 数据加密
     *
     * @param keyId      密钥的全局唯一标识符
     * @param cipherText 待解密的密文数据
     * @return 解密后的数据
     */
    default String decrypt(String keyId, String cipherText) {
        return decrypt(keyId, cipherText, Collections.emptyMap());
    }

    /**
     * 数据加密
     *
     * @param keyId      密钥的全局唯一标识符
     * @param cipherText 待解密的密文数据
     * @param options    解密请求配置
     * @return 解密后的数据
     */
    String decrypt(String keyId, String cipherText, Map<String, Object> options);
}
