package com.wind.integration.alibaba;

import com.wind.integration.kms.WindCredentialsClient;
import com.wind.integration.kms.WindCryptoClient;
import com.wind.integration.kms.WindKmsClientProvider;
import org.jspecify.annotations.NonNull;

/**
 * 阿里云 kms 客户端提供者
 *
 * @author wuxp
 * @date 2026-03-27 10:50
 **/
public class AlibabaCloudKmsClientProvider implements WindKmsClientProvider {

    private final AlibabaCloudKmsCryptoClient client = AlibabaCloudKmsCryptoClient.defaults();

    @Override
    @NonNull
    public WindCredentialsClient getCredentialsClient() {
        return client;
    }

    @Override
    public @NonNull WindCryptoClient getCryptoClient() {
        return client;
    }
}
