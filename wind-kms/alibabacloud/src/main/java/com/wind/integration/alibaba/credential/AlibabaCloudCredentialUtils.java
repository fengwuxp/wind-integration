package com.wind.integration.alibaba.credential;

import com.aliyun.credentials.models.Config;
import com.wind.common.exception.AssertUtils;
import com.wind.common.exception.BaseException;
import com.wind.common.exception.DefaultExceptionCode;
import com.wind.integration.kms.WindKmsClientCredentialsDecryptor;
import jakarta.validation.constraints.NotNull;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.ServiceLoader;

/**
 * TODO 待移动到独立的包模块下
 * 阿里云凭证工具类
 *
 * @author wuxp
 * @date 2026-03-20 12:50
 **/
public final class AlibabaCloudCredentialUtils {

    /**
     * 阿里云凭据文件
     */
    private static final String ALIBABA_CLOUD_ACCESS_KEY_FILEPATH = "/etc/secrets/alibaba_cloud_credential";

    private AlibabaCloudCredentialUtils() {
        throw new AssertionError();
    }

    /**
     * 通过 OIDCRole 方式创建阿里云 api 凭证配置
     *
     * @return AlibabaCloudKmsCryptoClient
     */
    public static com.aliyun.teaopenapi.models.Config withOIDCRoleArnFormEnv() {
        Config credentialConfig = new Config();
        return withOIDCRoleArn(credentialConfig);
    }

    /**
     * 通过 OIDCRole 方式创建阿里云 api 凭证配置
     * 参见 <a href="https://help.aliyun.com/zh/sdk/developer-reference/v2-manage-access-credentials?spm=a2c4g.11186623.0.0.19284c3eNavDAp#ec8021b053aqe">管理访问凭据#方式六：OIDCRoleArn</a>
     *
     * @param credentialConfig 凭证获取配置
     * @return AlibabaCloudKmsCryptoClient
     */
    public static com.aliyun.teaopenapi.models.Config withOIDCRoleArn(Config credentialConfig) {
        credentialConfig.setType("oidc_role_arn");
        com.aliyun.credentials.Client credentialClient = new com.aliyun.credentials.Client(credentialConfig);
        com.aliyun.teaopenapi.models.Config result = new com.aliyun.teaopenapi.models.Config();
        result.setCredential(credentialClient);
        return result;
    }

    /**
     * 默认的阿里云凭证获取方式
     *
     * @return AlibabaCloudKmsCryptoClient
     */
    public static com.aliyun.teaopenapi.models.Config defaults() {
        if (StringUtils.hasText(System.getenv("ALIBABA_CLOUD_ROLE_ARN")) && StringUtils.hasText(System.getenv("ALIBABA_CLOUD_OIDC_PROVIDER_ARN"))) {
            return withOIDCRoleArnFormEnv();
        }
        return withCredentialFile();
    }

    /**
     * 从文件中加载阿里云 AK/SK
     *
     * @return AlibabaCloudKmsCryptoClient
     */
    public static com.aliyun.teaopenapi.models.Config withCredentialFile() {
        List<String> credential = loadCredentialConfig();
        com.aliyun.teaopenapi.models.Config result = new com.aliyun.teaopenapi.models.Config();
        result.setAccessKeyId(credential.getFirst());
        result.setAccessKeySecret(credential.get(1));
        return result;
    }

    private static List<String> loadCredentialConfig() {
        Path filepath = Path.of(ALIBABA_CLOUD_ACCESS_KEY_FILEPATH);
        AssertUtils.isTrue(Files.exists(filepath), "alibaba cloud credential file not found");
        try {
            String content = Files.readString(filepath);
            String config = loadDecryptor().decrypt(content);
            BufferedReader reader = new BufferedReader(new StringReader(config));
            List<String> result = reader.lines().filter(StringUtils::hasText).toList();
            AssertUtils.isTrue(result.size() >= 2, "alibaba cloud credential file format error");
            return result;
        } catch (IOException exception) {
            throw new BaseException(DefaultExceptionCode.COMMON_ERROR, "load alibaba cloud credential file failure", exception);
        }
    }

    /**
     * 获取默认的 kms client 凭据解密器
     *
     * @return WindKmsClientCredentialsDecryptor
     */
    @NotNull
    private static WindKmsClientCredentialsDecryptor loadDecryptor() {
        ServiceLoader<WindKmsClientCredentialsDecryptor> services = ServiceLoader.load(WindKmsClientCredentialsDecryptor.class);
        return services.findFirst().orElseThrow(() -> new IllegalStateException("No " + WindKmsClientCredentialsDecryptor.class.getName() + " found"));
    }
}
