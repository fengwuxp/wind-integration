package com.wind.integration.alibaba;

import com.aliyun.kms20160120.Client;
import com.aliyun.kms20160120.models.DecryptRequest;
import com.aliyun.kms20160120.models.DecryptResponse;
import com.aliyun.kms20160120.models.EncryptRequest;
import com.aliyun.kms20160120.models.EncryptResponse;
import com.aliyun.kms20160120.models.GetSecretValueRequest;
import com.aliyun.kms20160120.models.GetSecretValueResponse;
import com.aliyun.kms20160120.models.GetSecretValueResponseBody;
import com.aliyun.tea.TeaModel;
import com.aliyun.teaopenapi.models.Config;
import com.aliyun.credentials.utils.AuthConstant;
import com.wind.common.annotations.VisibleForTesting;
import com.wind.common.exception.AssertUtils;
import com.wind.common.exception.BaseException;
import com.wind.common.exception.DefaultExceptionCode;
import com.wind.common.jul.WindJulLogFactory;
import com.wind.integration.alibaba.credentials.AlibabaCloudCredentialsFactory;
import com.wind.integration.kms.WindCredentialsClient;
import com.wind.integration.kms.WindCryptoClient;
import com.wind.integration.kms.WindKmsClientCredentialsDecryptor;
import com.wind.integration.kms.WindKmsException;
import com.wind.integration.kms.model.dto.KmsSecretDetailsDTO;
import org.jspecify.annotations.Nullable;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
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
     * 使用应用持有的共享凭据创建独立 KMS 客户端，创建时不获取凭据。
     * <p>
     * 连接配置不得包含身份字段；本方法复制配置后补全缺省端点，不修改调用方输入。
     * 凭据客户端由调用方管理，KMS 请求通过该实例取得当前凭据。
     *
     * @param credential   应用持有并复用的官方凭据客户端
     * @param clientConfig KMS 端点、超时等连接配置
     * @return 使用共享凭据的 KMS 客户端
     * @throws BaseException 参数为空、混配身份或 KMS 客户端初始化失败
     */
    public static AlibabaCloudKmsCryptoClient withCredential(com.aliyun.credentials.Client credential, Config clientConfig) {
        AssertUtils.notNull(credential, "共享凭据客户端不能为空");
        AssertUtils.notNull(clientConfig, "KMS 连接配置不能为空");
        AssertUtils.isNull(clientConfig.getAccessKeyId(), "KMS 连接配置不允许设置 accessKeyId");
        AssertUtils.isNull(clientConfig.getAccessKeySecret(), "KMS 连接配置不允许设置 accessKeySecret");
        AssertUtils.isNull(clientConfig.getSecurityToken(), "KMS 连接配置不允许设置 securityToken");
        AssertUtils.isNull(clientConfig.getBearerToken(), "KMS 连接配置不允许设置 bearerToken");
        AssertUtils.isNull(clientConfig.getCredential(), "KMS 连接配置不允许设置 credential");
        Config config = TeaModel.toModel(clientConfig.toMap(), new Config()).setCredential(credential);
        return new AlibabaCloudKmsCryptoClient(buildKmsClient(config));
    }

    /**
     * 按公共工厂支持的显式来源创建凭据，并应用独立的 KMS 连接配置。
     *
     * @param credentialConfig 官方凭据配置，不修改调用方输入
     * @param clientConfig     不包含身份字段的 KMS 连接配置
     * @return KMS 客户端
     */
    public static AlibabaCloudKmsCryptoClient withCredentialConfig(com.aliyun.credentials.models.Config credentialConfig, Config clientConfig) {
        AssertUtils.notNull(credentialConfig, "显式凭据配置不能为空");
        return defaults(credentialConfig, clientConfig);
    }

    /**
     * 按显式来源创建凭据，KMS 端点沿用系统属性和环境变量配置。
     *
     * @param credentialConfig 官方凭据配置
     * @return KMS 客户端
     */
    public static AlibabaCloudKmsCryptoClient withCredentialConfig(com.aliyun.credentials.models.Config credentialConfig) {
        return withCredentialConfig(credentialConfig, new Config());
    }

    /**
     * 通过 OIDCRoleArn 方式创建
     *
     * @return AlibabaCloudKmsCryptoClient
     */
    public static AlibabaCloudKmsCryptoClient withOIDCRoleArn() {
        return defaults(new com.aliyun.credentials.models.Config().setType(AuthConstant.OIDC_ROLE_ARN));
    }

    /**
     * 兼容原 OIDC 用法：只使用 OIDC 有效参数，忽略其他凭据类型字段，不修改输入。
     *
     * @param credentialConfig OIDC 凭据配置，无需设置 type
     * @return AlibabaCloudKmsCryptoClient
     */
    public static AlibabaCloudKmsCryptoClient withOIDCRoleArn(com.aliyun.credentials.models.Config credentialConfig) {
        AssertUtils.notNull(credentialConfig, "OIDC 凭据配置不能为空");
        return defaults(toOidcCredentialConfig(credentialConfig));
    }

    /**
     * 两个 OIDC ARN 环境配置齐全时使用 OIDC，Token 优先读取环境路径，路径未设置时使用缺省文件。
     * 两个 ARN 均未设置时使用加密 AK 文件，残缺配置报错。
     *
     * @return AlibabaCloudKmsCryptoClient
     */
    public static AlibabaCloudKmsCryptoClient defaults() {
        return defaults(null);
    }

    /**
     * 使用显式凭据配置或 OIDC 环境策略，KMS 端点沿用现有系统配置。
     *
     * @param credentialConfig 显式配置；仅 null 表示检查 OIDC 环境配置
     * @return KMS 客户端
     */
    public static AlibabaCloudKmsCryptoClient defaults(com.aliyun.credentials.models.@Nullable Config credentialConfig) {
        return defaults(credentialConfig, new Config());
    }

    /**
     * 统一选择凭据来源并装配 KMS 客户端。显式配置优先，选定来源失败时不会回退到其他身份。
     * <p>
     * 未传凭据配置时，仅检查角色 ARN 和 OIDC 提供商 ARN；均未设置才使用加密 AK 文件。
     * 部分设置或空白值均报错；选中 OIDC 时由公共工厂优先解析环境 Token 路径，未设置时采用缺省路径。
     * 不读取环境 AK/STS，也不进入官方默认凭据链。
     *
     * @param credentialConfig 显式配置；仅 null 表示检查 OIDC 环境配置，空 type 或 default 类型仍为错误
     * @param clientConfig     不包含身份字段的 KMS 连接配置
     * @return KMS 客户端
     */
    public static AlibabaCloudKmsCryptoClient defaults(com.aliyun.credentials.models.@Nullable Config credentialConfig, Config clientConfig) {
        AssertUtils.notNull(clientConfig, "KMS 连接配置不能为空");
        if (credentialConfig != null) {
            return withCredential(AlibabaCloudCredentialsFactory.create(credentialConfig), clientConfig);
        }
        Optional<com.aliyun.credentials.Client> oidc = AlibabaCloudCredentialsFactory.findOidcRoleArnFromEnvironment();
        return oidc.map(value -> withCredential(value, clientConfig)).orElseGet(() -> withCredentialFile(clientConfig));
    }

    /**
     * 从默认凭据文件创建客户端，密文由既有 KMS 解密 SPI 处理。
     *
     * @return 使用文件中 AK/SK 的 KMS 客户端
     * @throws IllegalStateException 未注册凭据解密器
     */
    @VisibleForTesting
    static AlibabaCloudKmsCryptoClient withCredentialFile() {
        return withCredentialFile(new Config());
    }

    private static AlibabaCloudKmsCryptoClient withCredentialFile(Config clientConfig) {
        WindKmsClientCredentialsDecryptor decryptor = ServiceLoader.load(WindKmsClientCredentialsDecryptor.class)
                .findFirst().orElseThrow(() -> new IllegalStateException("No " + WindKmsClientCredentialsDecryptor.class.getName() + " found"));
        return withCredential(AlibabaCloudCredentialsFactory.withCredentialFile(decryptor::decrypt), clientConfig);
    }

    private static com.aliyun.credentials.models.Config toOidcCredentialConfig(com.aliyun.credentials.models.Config source) {
        // 原 SDK 的 OIDC 入口仅消费这些参数，兼容时不把其他身份字段带入严格工厂。
        com.aliyun.credentials.models.Config result = new com.aliyun.credentials.models.Config()
                .setType(AuthConstant.OIDC_ROLE_ARN)
                .setRoleArn(source.getRoleArn())
                .setRoleSessionName(source.getRoleSessionName())
                .setRoleSessionExpiration(source.roleSessionExpiration)
                .setOidcProviderArn(source.getOidcProviderArn())
                .setOidcTokenFilePath(source.getOidcTokenFilePath())
                .setPolicy(source.getPolicy())
                .setSTSEndpoint(source.getSTSEndpoint());
        // SDK 的超时 setter 只接收 int，直接复制以保留 null 的缺省语义。
        result.connectTimeout = source.getConnectTimeout();
        result.timeout = source.getTimeout();
        return result;
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

    private void assertSuccessful(int statusCode, String requestId) {
        AssertUtils.state(statusCode >= 200 && statusCode < 300, () -> new WindKmsException("kms request failure", requestId));
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
                String accessKeyId = config.getAccessKeyId();
                String accessKeyPrefix = accessKeyId == null || accessKeyId.length() <= 4 ? "<credential-provider>" : accessKeyId.substring((accessKeyId.length() - 4));
                LOGGER.info(String.format("alibaba cloud kms init, the last 4 characters of AK = %s", accessKeyPrefix));
                LOGGER.info("alibaba cloud kms, use endpoint = " + config.getEndpoint());
            }
            return new Client(config);
        } catch (Exception exception) {
            throw new BaseException(DefaultExceptionCode.COMMON_ERROR, "build kms client error", exception);
        }
    }
}
