package com.wind.integration.alibaba;

import com.aliyun.kms20160120.Client;
import com.aliyun.kms20160120.models.DecryptRequest;
import com.aliyun.kms20160120.models.DecryptResponse;
import com.aliyun.kms20160120.models.EncryptRequest;
import com.aliyun.kms20160120.models.EncryptResponse;
import com.aliyun.kms20160120.models.GetSecretValueRequest;
import com.aliyun.kms20160120.models.GetSecretValueResponse;
import com.aliyun.kms20160120.models.GetSecretValueResponseBody;
import com.aliyun.teaopenapi.models.Config;
import com.wind.common.annotations.VisibleForTesting;
import com.wind.common.exception.AssertUtils;
import com.wind.common.exception.BaseException;
import com.wind.common.exception.DefaultExceptionCode;
import com.wind.common.jul.WindJulLogFactory;
import com.wind.integration.alibaba.credential.AlibabaCloudCredentialUtils;
import com.wind.integration.kms.WindCredentialsClient;
import com.wind.integration.kms.WindCryptoClient;
import com.wind.integration.kms.WindKmsException;
import com.wind.integration.kms.model.dto.KmsSecretDetailsDTO;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 阿里云 kms client
 *
 * @author wuxp
 * @date 2025-02-17 18:22
 **/
public class AlibabaCloudKmsCryptoClient implements WindCredentialsClient, WindCryptoClient {

    private static final Logger LOGGER = WindJulLogFactory.getLogger(AlibabaCloudKmsCryptoClient.class);

    /**
     * VPC 域名后缀
     */
    private static final String ALIBABA_CLOUD_KMS_VPC_DOMAIN_SUFFIX = ".cryptoservice.kms.aliyuncs.com";

    /**
     * kms 实例 id
     */
    private static final String ALIBABA_CLOUD_KMS_INSTANCE_ID_NAME = "ALIBABA_CLOUD_K_ID";

    /**
     * kms endpoint
     * 小规格 KMS 建议采用共享网关模式：https://help.aliyun.com/zh/kms/key-management-service/developer-reference/classic-kms-sdkclassic-kms-sdk/?spm=5176.2020520104.console-base_help
     * .dexternal.56ab3a98uyH16c
     * 避免 QPS  超限
     */
    private static final String ALIBABA_CLOUD_ENDPOINT = "ALIBABA_CLOUD_K_ENDPOINT";

    private final Client client;

    AlibabaCloudKmsCryptoClient(Client client) {
        this.client = client;
    }

    /**
     * 通过 OIDCRoleArn 方式创建
     *
     * @return AlibabaCloudKmsCryptoClient
     */
    public static AlibabaCloudKmsCryptoClient withOIDCRoleArn() {
        Config config = AlibabaCloudCredentialUtils.withOIDCRoleArnFormEnv();
        return new AlibabaCloudKmsCryptoClient(buildKmsClient(config));
    }

    /**
     * 通过 OIDCRoleArn 方式创建
     *
     * @return AlibabaCloudKmsCryptoClient
     */
    public static AlibabaCloudKmsCryptoClient withOIDCRoleArn(com.aliyun.credentials.models.Config credentialConfig) {
        Config config = AlibabaCloudCredentialUtils.withOIDCRoleArn(credentialConfig);
        return new AlibabaCloudKmsCryptoClient(buildKmsClient(config));
    }

    /**
     * 通过 withOIDCRoleArn 或 凭据配置文件创建
     *
     * @return AlibabaCloudKmsCryptoClient
     */
    public static AlibabaCloudKmsCryptoClient defaults() {
        Config config = AlibabaCloudCredentialUtils.defaults();
        return new AlibabaCloudKmsCryptoClient(buildKmsClient(config));
    }

    @Override
    public String encrypt(String keyId, String plaintext, Map<String, Object> options) {
        EncryptRequest request = new EncryptRequest();
        request.setKeyId(keyId);
        request.setPlaintext(plaintext);
        Map<String, Object> context = new HashMap<>(options);
        request.setEncryptionContext(context);
        try {
            EncryptResponse response = client.encrypt(request);
            assertSuccessful(response.statusCode, response.body.requestId);
            return response.body.ciphertextBlob;
        } catch (Exception exception) {
            throw new WindKmsException(exception);
        }
    }

    @Override
    public String decrypt(String cipherText, Map<String, Object> options) {
        DecryptRequest request = new DecryptRequest();
        request.setCiphertextBlob(cipherText);
        Map<String, Object> context = new HashMap<>(options);
        request.setEncryptionContext(context);
        try {
            DecryptResponse response = client.decrypt(request);
            assertSuccessful(response.statusCode, response.body.requestId);
            return response.body.plaintext;
        } catch (Exception exception) {
            throw new WindKmsException(exception);
        }
    }

    @Override
    public String decrypt(String keyId, String cipherText, Map<String, Object> options) {
        return decrypt(cipherText, options);
    }

    @Override
    public KmsSecretDetailsDTO getCredentials(String secretName, String version) {
        GetSecretValueRequest request = new GetSecretValueRequest();
        request.setSecretName(secretName);
        request.setVersionId(version);
        try {
            GetSecretValueResponse response = client.getSecretValue(request);
            KmsSecretDetailsDTO result = new KmsSecretDetailsDTO();
            result.setSecretName(secretName);
            GetSecretValueResponseBody body = response.getBody();
            assertSuccessful(response.statusCode, body.requestId);
            result.setVersion(body.versionId);
            result.setContent(body.getSecretData());
            result.setRaw(body);
            return result;
        } catch (Exception exception) {
            throw new WindKmsException(exception);
        }
    }

    private void assertSuccessful(int statusCode, String reqeustId) {
        AssertUtils.state(statusCode >= 200 && statusCode < 300, () -> new WindKmsException("kms request failure", reqeustId));
    }

    private static String getAlibabaCloudKmsEndpoint() {
        String instanceId = System.getProperty(ALIBABA_CLOUD_KMS_INSTANCE_ID_NAME, System.getenv(ALIBABA_CLOUD_KMS_INSTANCE_ID_NAME));
        if (StringUtils.hasText(instanceId)) {
            // 如果配置了 kms 示例 id，则使用 VPC （内网）域名
            return instanceId + ALIBABA_CLOUD_KMS_VPC_DOMAIN_SUFFIX;
        }
        // 使用指定的端点
        return System.getProperty(ALIBABA_CLOUD_ENDPOINT, System.getenv(ALIBABA_CLOUD_ENDPOINT));
    }

    @VisibleForTesting
    static Client buildKmsClient(Config config) {
        try {
            if (config.getEndpoint() == null || config.getEndpoint().isBlank()) {
                config.setEndpoint(getAlibabaCloudKmsEndpoint());
            }
            if (LOGGER.isLoggable(Level.INFO)) {
                LOGGER.info(String.format("alibaba cloud kms init, the first 5 characters of AK = %s", config.getAccessKeyId().substring(0, 5)));
                LOGGER.info("alibaba cloud kms, use endpoint = " + config.getEndpoint());
            }
            return new Client(config);
        } catch (Exception exception) {
            throw new BaseException(DefaultExceptionCode.COMMON_ERROR, "build kms client error", exception);
        }
    }
}
