package com.wind.integration.alibaba;

import com.aliyun.kms20160120.Client;
import com.aliyun.kms20160120.models.DecryptRequest;
import com.aliyun.kms20160120.models.DecryptResponse;
import com.aliyun.kms20160120.models.EncryptRequest;
import com.aliyun.kms20160120.models.EncryptResponse;
import com.aliyun.kms20160120.models.GetSecretValueRequest;
import com.aliyun.kms20160120.models.GetSecretValueResponse;
import com.aliyun.kms20160120.models.GetSecretValueResponseBody;
import com.wind.common.exception.AssertUtils;
import com.wind.integration.kms.WindCredentialsClient;
import com.wind.integration.kms.WindCryptoClient;
import com.wind.integration.kms.WindKmsException;
import com.wind.integration.kms.model.dto.KmsSecretDetailsDTO;

import java.util.HashMap;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * 阿里云 kms client
 *
 * @author wuxp
 * @date 2025-02-17 18:22
 **/
public class AlibabaCloudKmsCryptoClient implements WindCredentialsClient, WindCryptoClient {

    private static final String ALIBABA_CLOUD_ACCESS_KEY_ID = "ALIBABA_CLOUD_K_AK";

    private static final String ALIBABA_CLOUD_ACCESS_KEY_SECRET = "ALIBABA_CLOUD_K_SK";

    private static final String ALIBABA_CLOUD_ENDPOINT = "ALIBABA_CLOUD_K_ENDPOINT";

    private final Client client;

    public AlibabaCloudKmsCryptoClient(Client client) {
        this.client = client;
    }

    /**
     * 从环境变量中获取配置创建 client
     *
     * @return AlibabaCloudKmsReadyOnlyClient
     */
    public static AlibabaCloudKmsCryptoClient of() {
        return of(text -> text);
    }

    /**
     * 从环境变量中获取配置创建 client
     *
     * @param decryptFunc 解密函数
     * @return AlibabaCloudKmsReadyOnlyClient
     */
    public static AlibabaCloudKmsCryptoClient of(UnaryOperator<String> decryptFunc) {
        // 必填，请确保代码运行环境设置了环境变量 ALIBABA_CLOUD_ACCESS_KEY_ID。
        return form(decryptFunc.apply(System.getenv(ALIBABA_CLOUD_ACCESS_KEY_ID)),
                decryptFunc.apply(System.getenv(ALIBABA_CLOUD_ACCESS_KEY_SECRET)), System.getenv(ALIBABA_CLOUD_ENDPOINT));
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
        HashMap<String, Object> context = new HashMap<>(options);
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
        request.secretName = secretName;
        request.versionId = version;
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
}
