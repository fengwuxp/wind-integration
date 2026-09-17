package com.wind.integration.alibaba;

import com.aliyun.teaopenapi.models.Config;
import com.wind.integration.alibaba.credential.AlibabaCloudCredentialUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * @author wuxp
 * @date 2025-02-18 11:36
 **/
class AlibabaCloudKmsCryptoClientTests {

    @Test
    void testBuildKmsClientWithCredentialProvider() {
        com.aliyun.credentials.models.Config credentialConfig = new com.aliyun.credentials.models.Config()
                .setRoleArn("acs:ram::123456789:role/test-role")
                .setOidcProviderArn("acs:ram::123456789:oidc-provider/test-provider")
                .setOidcTokenFilePath("/tmp/test-oidc-token")
                .setRoleSessionName("test-session");
        Config config = AlibabaCloudCredentialUtils.withOIDCRoleArn(credentialConfig)
                .setEndpoint("kms.us-east-1.aliyuncs.com");

        Assertions.assertDoesNotThrow(() -> AlibabaCloudKmsCryptoClient.buildKmsClient(config));
    }
}
