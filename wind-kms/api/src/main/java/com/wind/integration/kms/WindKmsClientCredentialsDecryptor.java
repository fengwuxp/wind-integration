package com.wind.integration.kms;

import org.jspecify.annotations.NullMarked;

/**
 * 用于提供 kms client 凭据解密器
 *
 * @author wuxp
 * @date 2026-03-27 11:16
 **/
@NullMarked
public interface WindKmsClientCredentialsDecryptor {

    /**
     * Decrypt the encrypted text string.
     */
    String decrypt(String encryptedText);

}
