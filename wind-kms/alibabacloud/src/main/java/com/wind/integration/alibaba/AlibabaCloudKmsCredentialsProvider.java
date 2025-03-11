package com.wind.integration.alibaba;

import com.wind.core.WindCredentialsProvider;
import com.wind.integration.kms.WindCredentialsClient;
import lombok.extern.slf4j.Slf4j;

import javax.validation.constraints.NotBlank;

/**
 * 阿里云 kms 凭据提供者
 *
 * @author wuxp
 * @date 2025-02-18 15:41
 **/
@Slf4j
public class AlibabaCloudKmsCredentialsProvider implements WindCredentialsProvider {

    private final WindCredentialsClient client = AlibabaCloudKmsCryptoClient.of();

    @Override
    public String getCredentials(@NotBlank String credentialsName) {
        log.debug("get credentials, credentialsName = {}", credentialsName);
        return client.getCredentialsAsText(credentialsName);
    }

}
