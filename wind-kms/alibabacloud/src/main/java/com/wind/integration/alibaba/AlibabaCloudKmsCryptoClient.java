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
import com.wind.integration.kms.WindCredentialsClient;
import com.wind.integration.kms.WindCryptoClient;
import com.wind.integration.kms.WindKmsException;
import com.wind.integration.kms.model.dto.KmsSecretDetailsDTO;
import com.wind.security.crypto.symmetric.AesTextEncryptor;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.util.ResourceUtils;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.logging.ConsoleHandler;
import java.util.logging.Logger;

/**
 * 阿里云 kms client
 *
 * @author wuxp
 * @date 2025-02-17 18:22
 **/
public class AlibabaCloudKmsCryptoClient implements WindCredentialsClient, WindCryptoClient {

    private static final Logger LOGGER = Logger.getLogger(AlibabaCloudKmsCryptoClient.class.getName());

    static {
        LOGGER.addHandler(new ConsoleHandler());
    }

    /**
     * 用于解密 kms ak/sk 的秘钥
     */
    private static final String KMS_KEY_ASE_KEY_FILE = "classpath:kms_key_ase_key.key";

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
        LOGGER.info("init Alibaba Cloud Kms Client");
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
        return form(decryptFunc.apply(requireEnv(ALIBABA_CLOUD_ACCESS_KEY_ID)),
                decryptFunc.apply(requireEnv(ALIBABA_CLOUD_ACCESS_KEY_SECRET)), requireEnv(ALIBABA_CLOUD_ENDPOINT));
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

    private static String loadFileAsText() {
        try {
            Path path = ResourceUtils.getFile(AlibabaCloudKmsCryptoClient.KMS_KEY_ASE_KEY_FILE).toPath();
            return StreamUtils.copyToString(Files.newInputStream(path), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            return WindConstants.EMPTY;
        }
    }

    private static String requireEnv(String name) {
        String result = System.getProperty(name, System.getenv(name));
        AssertUtils.notNull(result, String.format("env name =%s not configure", name));
        return result;
    }
}
