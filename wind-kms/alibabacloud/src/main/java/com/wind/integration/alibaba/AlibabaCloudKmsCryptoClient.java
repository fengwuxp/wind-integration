package com.wind.integration.alibaba;

import com.aliyun.kms20160120.Client;
import com.aliyun.kms20160120.models.DecryptRequest;
import com.aliyun.kms20160120.models.DecryptResponse;
import com.aliyun.kms20160120.models.EncryptRequest;
import com.aliyun.kms20160120.models.EncryptResponse;
import com.aliyun.kms20160120.models.GetSecretValueRequest;
import com.aliyun.kms20160120.models.GetSecretValueResponse;
import com.aliyun.kms20160120.models.GetSecretValueResponseBody;
import com.wind.common.WindConstants;
import com.wind.common.exception.AssertUtils;
import com.wind.common.exception.BaseException;
import com.wind.common.exception.DefaultExceptionCode;
import com.wind.common.jul.WindJulLogFactory;
import com.wind.integration.kms.WindCredentialsClient;
import com.wind.integration.kms.WindCryptoClient;
import com.wind.integration.kms.WindKmsException;
import com.wind.integration.kms.model.dto.KmsSecretDetailsDTO;
import com.wind.security.crypto.symmetric.AesTextEncryptor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.function.UnaryOperator;
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
     * 用于解密 kms ak/sk 的秘钥文件路径
     */
    private static final String KMS_KEY_ASE_KEY_FILEPATH = "classpath*:kms_key_ase_key.key";

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
     * 小规格 KMS 建议采用共享网关模式：https://help.aliyun.com/zh/kms/key-management-service/developer-reference/classic-kms-sdkclassic-kms-sdk/?spm=5176.2020520104.console-base_help.dexternal.56ab3a98uyH16c
     * 避免 QPS  超限
     */
    private static final String ALIBABA_CLOUD_ENDPOINT = "ALIBABA_CLOUD_K_ENDPOINT";

    private static final String ALIBABA_CLOUD_ACCESS_KEY_NAME = "ALIBABA_CLOUD_K_AK";

    private static final String ALIBABA_CLOUD_ACCESS_SECRET_NAME = "ALIBABA_CLOUD_K_SK";

    private final Client client;

    public AlibabaCloudKmsCryptoClient(Client client) {
        this.client = client;
    }

    /**
     * 从环境变量中获取配置创建 client
     *
     * @return AlibabaCloudKmsReadyOnlyClient
     */
    public static AlibabaCloudKmsCryptoClient defaults() {
        return of(text -> text);
    }

    /**
     * 从环境变量中获取配置创建 client
     *
     * @return AlibabaCloudKmsReadyOnlyClient
     */
    public static AlibabaCloudKmsCryptoClient of() {
        String key = loadFileAsText();
        if (StringUtils.hasText(key)) {
            try {
                BufferedReader reader = new BufferedReader(new StringReader(key));
                TextEncryptor encryptor = new AesTextEncryptor(reader.readLine(), reader.readLine());
                return AlibabaCloudKmsCryptoClient.of(encryptor::decrypt);
            } catch (IOException exception) {
                throw new BaseException(DefaultExceptionCode.COMMON_ERROR, "build kms client error", exception);
            }
        }
        return defaults();
    }

    /**
     * 从环境变量中获取配置创建 client
     *
     * @param decryptFunc 解密函数
     * @return AlibabaCloudKmsReadyOnlyClient
     */
    public static AlibabaCloudKmsCryptoClient of(UnaryOperator<String> decryptFunc) {
        // 必填，请确保代码运行环境设置了环境变量 ALIBABA_CLOUD_ACCESS_KEY_ID。
        return form(decryptFunc.apply(requireEnv(ALIBABA_CLOUD_ACCESS_KEY_NAME)),
                decryptFunc.apply(requireEnv(ALIBABA_CLOUD_ACCESS_SECRET_NAME)), getAlibabaCloudKmsEndpoint());
    }

    /**
     * 创建 AlibabaCloudKmsReadyOnlyClient
     *
     * @param ak       assess key
     * @param sk       access secret key
     * @param endpoint Endpoint 请参考：https://api.aliyun.com/product/Kms
     * @return AlibabaCloudKmsReadyOnlyClient
     */
    public static AlibabaCloudKmsCryptoClient form(String ak, String sk, String endpoint) {
        AssertUtils.hasText(ak, "argument ak must not empty");
        AssertUtils.hasText(sk, "argument sk must not empty");
        AssertUtils.hasText(endpoint, "argument endpoint must not empty");
        if (LOGGER.isLoggable(Level.INFO)) {
            LOGGER.info(String.format("alibaba cloud kms init, the first 5 characters of AK= %s", ak.substring(0, 5)));
            LOGGER.info("alibaba cloud kms, use endpoint = " + endpoint);
        }
        com.aliyun.teaopenapi.models.Config config = new com.aliyun.teaopenapi.models.Config()
                .setAccessKeyId(ak)
                .setAccessKeySecret(sk)
                .setEndpoint(endpoint);
        try {
            return new AlibabaCloudKmsCryptoClient(new Client(config));
        } catch (Exception exception) {
            throw new WindKmsException(exception);
        }
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

    private static String loadFileAsText() {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        try {
            Resource[] resources = resolver.getResources(AlibabaCloudKmsCryptoClient.KMS_KEY_ASE_KEY_FILEPATH);
            if (ObjectUtils.isEmpty(resources)) {
                return WindConstants.EMPTY;
            }
            return StreamUtils.copyToString(resources[0].getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            return WindConstants.EMPTY;
        }
    }

    private static String requireEnv(String name) {
        String result = System.getProperty(name, System.getenv(name));
        AssertUtils.notNull(result, String.format("env name =%s not configure", name));
        return result;
    }

    private static String getAlibabaCloudKmsEndpoint() {
        String instanceId = System.getProperty(ALIBABA_CLOUD_KMS_INSTANCE_ID_NAME, System.getenv(ALIBABA_CLOUD_KMS_INSTANCE_ID_NAME));
        if (StringUtils.hasText(instanceId)) {
            // 如果配置了 kms 示例 id，则使用 VPC （内网）域名
            return instanceId + ALIBABA_CLOUD_KMS_VPC_DOMAIN_SUFFIX;
        }
        // 使用指定的端点
        return requireEnv(ALIBABA_CLOUD_ENDPOINT);
    }
}
