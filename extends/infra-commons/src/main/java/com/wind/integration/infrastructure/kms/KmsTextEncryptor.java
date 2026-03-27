package com.wind.integration.infrastructure.kms;

import com.wind.common.util.ServiceInfoUtils;
import com.wind.integration.kms.ChunkingWindCryptoClient;
import com.wind.integration.kms.WindCryptoClient;
import com.wind.integration.kms.WindKmsClientProvider;
import org.jspecify.annotations.NonNull;
import org.springframework.security.crypto.encrypt.TextEncryptor;

/**
 * 基于 kms 文本加密器
 *
 * @author wuxp
 * @date 2026-03-27 10:53
 **/
public class KmsTextEncryptor implements TextEncryptor {

    /**
     * 线上 kms 加密秘钥 ID
     */
    private static final String ONLINE_DB_ENCRYPT_KEY_ID = ServiceInfoUtils.getSystemProperty("wind.kms.db.encrypt.id");

    static final WindKmsClientProvider KMS_CLIENT_PROVIDER = WindKmsClientProvider.getInstance();

    private final WindCryptoClient cryptoClient;

    public KmsTextEncryptor(WindCryptoClient cryptoClient) {
        this.cryptoClient = cryptoClient;
    }

    private KmsTextEncryptor() {
        this(KMS_CLIENT_PROVIDER.getCryptoClient());
    }

    /**
     * 默认
     *
     * @return TextEncryptor
     */
    public static TextEncryptor defaults() {
        return new KmsTextEncryptor();
    }

    /**
     * 大数据分片加密
     *
     * @return TextEncryptor
     */
    public static TextEncryptor withChunk() {
        ChunkingWindCryptoClient client = new ChunkingWindCryptoClient(KMS_CLIENT_PROVIDER.getCryptoClient());
        return new KmsTextEncryptor(client);
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
