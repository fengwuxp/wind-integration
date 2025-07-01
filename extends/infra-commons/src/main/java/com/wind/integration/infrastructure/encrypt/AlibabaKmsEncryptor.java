package com.wind.integration.infrastructure.encrypt;

import com.wind.common.util.ServiceInfoUtils;
import com.wind.integration.alibaba.AlibabaCloudKmsCryptoClient;
import com.wind.integration.kms.WindCryptoClient;
import org.springframework.security.crypto.encrypt.TextEncryptor;

/**
 * 阿里云 KMS 加密
 *
 * @author fanqingwei
 * @since 2025-05-13
 */
public class AlibabaKmsEncryptor implements TextEncryptor {

    /**
     * 线上 kms 加密秘钥 ID
     */
    private static final String ONLINE_DB_ENCRYPT_KEY_ID = ServiceInfoUtils.getSystemProperty("wind.kms.db.encrypt.id");

    private final WindCryptoClient cryptoClient = AlibabaCloudKmsCryptoClient.of();

    @Override
    public String encrypt(String text) {
        return cryptoClient.encrypt(ONLINE_DB_ENCRYPT_KEY_ID, text);
    }

    @Override
    public String decrypt(String encryptedText) {
        return cryptoClient.decrypt(ONLINE_DB_ENCRYPT_KEY_ID, encryptedText);
    }
}
