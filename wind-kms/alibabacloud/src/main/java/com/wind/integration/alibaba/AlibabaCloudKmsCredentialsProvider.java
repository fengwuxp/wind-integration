package com.wind.integration.alibaba;

import com.wind.core.WindCredentialsProvider;
import com.wind.integration.kms.WindCredentialsClient;
import lombok.extern.slf4j.Slf4j;

import javax.validation.constraints.NotBlank;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 阿里云 kms 凭据提供者
 *
 * @author wuxp
 * @date 2025-02-18 15:41
 **/
@Slf4j
public class AlibabaCloudKmsCredentialsProvider implements WindCredentialsProvider {

    private final AtomicReference<WindCredentialsClient> client = new AtomicReference<>();

    @Override
    public String getCredentials(@NotBlank String credentialsName) {
        log.debug("get credentials, credentialsName = {}", credentialsName);
        synchronized (client) {
            // 改为延迟初始化，避免不强依赖 kms 时初始化错误
            if (client.get() == null) {
                client.set(AlibabaCloudKmsCryptoClient.of());
            }
        }
        return client.get().getCredentialsAsText(credentialsName);
    }

}
