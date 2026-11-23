package com.wind.integration.kms;

import org.jspecify.annotations.NonNull;

/**
 * kms 客户端提供者
 *
 * @author wuxp
 * @date 2026-03-27 10:46
 **/
public interface WindKmsClientProvider {

    /**
     * 获取 kms 凭据客户端
     *
     * @return WindCredentialsClient
     */
    @NonNull
    WindCredentialsClient getCredentialsClient();

    /**
     * 获取 kms 加解密客户端
     *
     * @return WindCredentialsClient
     */
    @NonNull
    WindCryptoClient getCryptoClient();

}
