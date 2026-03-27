package com.wind.integration.kms;

import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.NullMarked;

import java.util.ServiceLoader;

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

    /**
     * 获取默认的 kms client 凭据解密器
     *
     * @return WindKmsClientCredentialsDecryptor
     */
    @NotNull
    static WindKmsClientCredentialsDecryptor getInstance() {
        ServiceLoader<WindKmsClientCredentialsDecryptor> services = ServiceLoader.load(WindKmsClientCredentialsDecryptor.class);
        return services.findFirst().orElseThrow(() -> new IllegalStateException("No " + WindKmsClientCredentialsDecryptor.class.getName() + " found"));
    }
}
