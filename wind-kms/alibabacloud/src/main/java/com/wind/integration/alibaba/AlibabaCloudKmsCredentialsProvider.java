package com.wind.integration.alibaba;

import com.wind.common.jul.JulLogFactory;
import com.wind.core.WindCredentialsProvider;
import com.wind.integration.kms.WindCredentialsClient;

import javax.validation.constraints.NotBlank;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

/**
 * 阿里云 kms 凭据提供者
 *
 * @author wuxp
 * @date 2025-02-18 15:41
 **/
public class AlibabaCloudKmsCredentialsProvider implements WindCredentialsProvider {

    private static final Logger LOGGER = JulLogFactory.getLogger(AlibabaCloudKmsCredentialsProvider.class);

    private final AtomicReference<WindCredentialsClient> client = new AtomicReference<>();

    @Override
    public String getCredentials(@NotBlank String credentialsName) {
        LOGGER.info("get credentials, credentialsName = " + credentialsName);
        synchronized (client) {
            // 改为延迟初始化，避免不强依赖 kms 时初始化错误
            if (client.get() == null) {
                LOGGER.info("Init WindCredentialsClient");
                client.set(AlibabaCloudKmsCryptoClient.of());
            }
        }
        return client.get().getCredentialsAsText(credentialsName);
    }

}
