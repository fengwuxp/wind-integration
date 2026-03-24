package com.wind.integration.infrastructure.encrypt;

import com.wind.common.util.ServiceInfoUtils;
import com.wind.integration.alibaba.AlibabaCloudKmsCryptoClient;
import com.wind.integration.kms.ChunkingWindCryptoClient;
import com.wind.integration.kms.WindCryptoClient;
import org.jspecify.annotations.NonNull;
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

    private final WindCryptoClient cryptoClient;

    public AlibabaKmsEncryptor(WindCryptoClient cryptoClient) {
        this.cryptoClient = cryptoClient;
    }

    public AlibabaKmsEncryptor() {
        this(AlibabaCloudKmsCryptoClient.defaults());
    }

    /**
     * 默认
     * @return TextEncryptor
     */
    public static TextEncryptor defaults() {
        return new AlibabaKmsEncryptor();
    }

    /**
     * 大数据分片加密
     *
     * @return TextEncryptor
     */
    public static TextEncryptor withChunk() {
        ChunkingWindCryptoClient client = new ChunkingWindCryptoClient(AlibabaCloudKmsCryptoClient.defaults());
        return new AlibabaKmsEncryptor(client);
    }

    @Override
    public @NonNull String encrypt(@NonNull String text) {
        return cryptoClient.encrypt(ONLINE_DB_ENCRYPT_KEY_ID, text);
    }

    @Override
    public @NonNull String decrypt(@NonNull String encryptedText) {
        return cryptoClient.decrypt(ONLINE_DB_ENCRYPT_KEY_ID, encryptedText);
    }
}
