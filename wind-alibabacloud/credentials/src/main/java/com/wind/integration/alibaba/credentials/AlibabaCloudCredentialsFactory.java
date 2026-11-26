package com.wind.integration.alibaba.credentials;

import com.aliyun.credentials.Client;
import com.aliyun.credentials.models.Config;
import com.aliyun.credentials.provider.EcsRamRoleCredentialProvider;
import com.aliyun.credentials.utils.AuthConstant;
import com.wind.common.exception.AssertUtils;
import com.wind.common.exception.BaseException;
import com.wind.common.exception.DefaultExceptionCode;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * 阿里云凭据客户端工厂，统一显式来源选择和身份参数校验。
 * <p>
 * 同一身份及授权范围可复用返回的客户端；凭据获取、缓存和刷新由官方 SDK 负责。
 * 文件凭据通过调用方提供的解密回调读取，不依赖具体云产品的解密 SPI。
 * 本工厂不使用默认凭据链，也不创建全局客户端。
 */
@NullMarked
@Slf4j
public final class AlibabaCloudCredentialsFactory {

    /**
     * 凭据的缺省文件路径。OIDC 模式读取原始 OIDC Token；AK 文件模式读取密文并交给解密回调。
     * 文件内容由所选认证模式决定，两种格式不能混用；OIDC 优先使用显式路径或 ACK 注入的路径。
     */
    private static final Path DEFAULT_CREDENTIAL_FILE = Path.of("/etc/secrets/alibaba_cloud_credential");

    /**
     * 环境变量名：要扮演的 RAM 角色 ARN，例如 acs:ram::账号ID:role/角色名。
     * 自动 OIDC 路由要求它与 OIDC 提供商 ARN 同时配置且非空白，角色的信任策略需允许该 OIDC 身份。
     */
    private static final String ALIBABA_CLOUD_ROLE_ARN = "ALIBABA_CLOUD_ROLE_ARN";

    /**
     * 环境变量名：RAM 中 OIDC 身份提供商的 ARN，例如 acs:ram::账号ID:oidc-provider/提供商名。
     * 与目标角色及所选文件中的 OIDC Token 一起用于换取 STS 临时凭据。
     */
    private static final String ALIBABA_CLOUD_OIDC_PROVIDER_ARN = "ALIBABA_CLOUD_OIDC_PROVIDER_ARN";

    /**
     * 环境变量名：ACK/RRSA 注入的 OIDC Token 文件路径，文件内容由平台轮换。
     * 未显式指定路径时使用它；变量未设置才采用 DEFAULT_CREDENTIAL_FILE，空白值直接报错。
     */
    private static final String ALIBABA_CLOUD_OIDC_TOKEN_FILE = "ALIBABA_CLOUD_OIDC_TOKEN_FILE";

    /**
     * 环境变量名：长期 AccessKey ID 或 STS 返回的临时 AccessKeyId，须与对应的 Secret 配套。
     * 推荐环境策略仅在没有配置 OIDC 身份时读取它。
     */
    private static final String ALIBABA_CLOUD_ACCESS_KEY_ID = "ALIBABA_CLOUD_ACCESS_KEY_ID";

    /**
     * 环境变量名：与 AccessKey ID 对应的签名密钥，长期 AK 和临时 STS 凭据均需提供。
     */
    private static final String ALIBABA_CLOUD_ACCESS_KEY_SECRET = "ALIBABA_CLOUD_ACCESS_KEY_SECRET";

    /**
     * 环境变量名：STS 返回的 SecurityToken，须与同一次获取的临时 AK/SK 一起使用。
     * 推荐环境策略在未选择 OIDC 且此变量已设置时使用静态 STS；空白会报错，该模式不自动续期。
     */
    private static final String ALIBABA_CLOUD_SECURITY_TOKEN = "ALIBABA_CLOUD_SECURITY_TOKEN";

    private AlibabaCloudCredentialsFactory() {
        throw new AssertionError();
    }

    /**
     * 推荐的环境凭据快捷入口：OIDC 优先，其次环境 AK/STS，全部未配置时失败。
     * <p>
     * OIDC 部分配置或空白时不降级到 AK；SecurityToken 已设置时必须是完整 STS 三件套。
     * 不搜索 SDK 默认凭据链，创建时不读取文件，也不创建全局实例。环境 STS 不自动续期。
     *
     * @return 按推荐策略创建的官方凭据客户端
     * @throws BaseException 环境凭据未配置、不完整或为空白
     */
    public static Client defaults() {
        Optional<Client> oidc = findOidcRoleArnFromEnvironment();
        if (oidc.isPresent()) {
            return oidc.get();
        }
        String accessKeyId = System.getenv(ALIBABA_CLOUD_ACCESS_KEY_ID);
        String accessKeySecret = System.getenv(ALIBABA_CLOUD_ACCESS_KEY_SECRET);
        String securityToken = System.getenv(ALIBABA_CLOUD_SECURITY_TOKEN);
        AssertUtils.isTrue(accessKeyId != null || accessKeySecret != null || securityToken != null,
                "未配置阿里云环境凭据，请设置 OIDC 或 AK/STS，或显式调用定向快捷方法");
        AssertUtils.hasText(accessKeyId, "AK/STS 环境配置不完整：{} 未设置或为空白", ALIBABA_CLOUD_ACCESS_KEY_ID);
        AssertUtils.hasText(accessKeySecret, "AK/STS 环境配置不完整：{} 未设置或为空白", ALIBABA_CLOUD_ACCESS_KEY_SECRET);
        if (securityToken == null) {
            return accessKey(accessKeyId, accessKeySecret);
        }
        AssertUtils.hasText(securityToken, "STS 环境配置不完整：{} 为空白", ALIBABA_CLOUD_SECURITY_TOKEN);
        return create(new Config().setType(AuthConstant.STS).setAccessKeyId(accessKeyId)
                .setAccessKeySecret(accessKeySecret).setSecurityToken(securityToken));
    }

    /**
     * 按同一组环境快照查找 OIDC 凭据，供产品模块保留各自的无配置行为。
     * <p>
     * 两个 ARN 均未设置时返回空；残缺或空白配置直接失败，不转为其他身份。
     * OIDC 文件优先使用环境路径，未设置时使用缺省路径；创建时不读取文件或换证。
     * SDK 的进程内覆盖值不替换已选定的环境身份及 Token 路径。
     *
     * @return 环境配置的 OIDC 客户端，或全部未配置时的空结果
     * @throws BaseException 两个 OIDC ARN 配置不完整或为空白
     */
    public static Optional<Client> findOidcRoleArnFromEnvironment() {
        String roleArn = System.getenv(ALIBABA_CLOUD_ROLE_ARN);
        String oidcProviderArn = System.getenv(ALIBABA_CLOUD_OIDC_PROVIDER_ARN);
        if (roleArn == null && oidcProviderArn == null) {
            return Optional.empty();
        }
        AssertUtils.hasText(roleArn, "OIDC 环境配置不完整：{} 未设置或为空白", ALIBABA_CLOUD_ROLE_ARN);
        AssertUtils.hasText(oidcProviderArn, "OIDC 环境配置不完整：{} 未设置或为空白", ALIBABA_CLOUD_OIDC_PROVIDER_ARN);
        return Optional.of(oidcRoleArn(roleArn, oidcProviderArn, null));
    }

    /**
     * 将普通认证配置映射为官方配置后委托 create，不读取应用全局配置，也不修改输入。
     * <p>
     * 未提供 type 时，有 SecurityToken 则选择 STS，否则存在 AK/SK 则选择 access_key；其他场景必须显式提供类型。
     * 空白仍属于显式配置，由 create 校验，不回退到其他来源。
     *
     * @param properties 普通 Java 认证配置
     * @return 官方凭据客户端
     */
    public static Client fromProperties(@Nullable AlibabaCloudCredentialsProperties properties) {
        AssertUtils.notNull(properties, "认证配置不能为空");
        String type = properties.getType();
        if (type == null) {
            if (properties.getSecurityToken() != null) {
                type = AuthConstant.STS;
            } else if (properties.getAccessKey() != null || properties.getSecretKey() != null) {
                type = AuthConstant.ACCESS_KEY;
            } else {
                throw BaseException.common("认证配置必须指定 type 或提供 AK/SK");
            }
        }
        Config config = new Config().setType(type)
                .setAccessKeyId(properties.getAccessKey())
                .setAccessKeySecret(properties.getSecretKey())
                .setSecurityToken(properties.getSecurityToken())
                .setRoleArn(properties.getRoleArn())
                .setOidcProviderArn(properties.getOidcProviderArn())
                .setOidcTokenFilePath(properties.getOidcTokenFilePath())
                .setRoleSessionName(properties.getRoleSessionName())
                .setPolicy(properties.getPolicy())
                .setExternalId(properties.getExternalId())
                .setRoleName(properties.getRoleName())
                .setCredentialsUri(properties.getCredentialsUri())
                .setSTSEndpoint(properties.getStsEndpoint())
                .setDisableIMDSv1(properties.getDisableIMDSv1());
        if (properties.getRoleSessionExpiration() != null) {
            config.setRoleSessionExpiration(properties.getRoleSessionExpiration());
        }
        if (properties.getConnectTimeout() != null) {
            config.setConnectTimeout(properties.getConnectTimeout());
        }
        if (properties.getReadTimeout() != null) {
            config.setTimeout(properties.getReadTimeout());
        }
        return create(config);
    }

    /**
     * 按显式类型创建客户端，不修改输入配置，不在创建时读取 Token 文件或获取 STS。
     * <p>
     * 支持 access_key、sts、ram_role_arn、ecs_ram_role、oidc_role_arn、credentials_uri。
     * sts 仅消费调用方传入的临时凭据，不自动续期；其余角色及 URI 模式使用 SDK 的刷新机制。
     * OIDC 文件路径为 null 时优先使用 ACK 环境路径，变量未设置时使用缺省文件。
     * 角色 ARN、OIDC 提供商 ARN 及 URI 缺省值由 SDK 解析。
     * ECS 使用官方同步刷新模式，不创建后台刷新任务；各提供者的缓存失败策略保持 SDK 原行为。
     * 客户端创建后，修改原配置不代表切换运行中的凭据。
     *
     * @param config 显式凭据配置；身份参数不得混用其他来源
     * @return 应由应用持有并复用的官方凭据客户端
     * @throws BaseException 显式配置为空、类型或选项不支持、必填参数空白或身份来源冲突
     */
    public static Client create(@Nullable Config config) {
        AssertUtils.notNull(config, "凭据配置不能为空");
        String type = config.getType();
        AssertUtils.hasText(type, "凭据参数 type 不能为空白");
        log.info("阿里云凭据客户端已创建 type = {}", type);
        switch (type) {
            case AuthConstant.ACCESS_KEY, AuthConstant.STS, AuthConstant.RAM_ROLE_ARN -> {
                AssertUtils.hasText(config.getAccessKeyId(), "凭据参数 accessKeyId 不能为空白");
                AssertUtils.hasText(config.getAccessKeySecret(), "凭据参数 accessKeySecret 不能为空白");
                if (AuthConstant.STS.equals(type) || config.getSecurityToken() != null) {
                    AssertUtils.hasText(config.getSecurityToken(), "凭据参数 securityToken 不能为空白");
                }
                if (AuthConstant.ACCESS_KEY.equals(type)) {
                    AssertUtils.isNull(config.getSecurityToken(), "当前凭据类型不允许配置 securityToken");
                }
            }
            case AuthConstant.OIDC_ROLE_ARN, AuthConstant.ECS_RAM_ROLE, AuthConstant.CREDENTIALS_URI -> {
                AssertUtils.isNull(config.getAccessKeyId(), "当前凭据类型不允许配置 accessKeyId");
                AssertUtils.isNull(config.getAccessKeySecret(), "当前凭据类型不允许配置 accessKeySecret");
                AssertUtils.isNull(config.getSecurityToken(), "当前凭据类型不允许配置 securityToken");
            }
            default -> throw BaseException.common("不支持该凭据类型，请显式选择 access_key、sts、ram_role_arn、ecs_ram_role、oidc_role_arn 或 credentials_uri");
        }
        AssertUtils.isNull(config.getBearerToken(), "当前凭据类型不允许配置 bearerToken");
        AssertUtils.isNull(config.getPublicKeyId(), "当前凭据类型不允许配置 publicKeyId");
        AssertUtils.isNull(config.getPrivateKeyFile(), "当前凭据类型不允许配置 privateKeyFile");
        AssertUtils.isNull(config.getProxy(), "当前凭据 SDK 不支持 Config.proxy");
        AssertUtils.isNull(config.getHost(), "当前凭据 SDK 不支持 Config.host，角色模式请使用 STSEndpoint");
        AssertUtils.isNull(config.getEnableIMDSv2(), "当前凭据 SDK 不支持 Config.enableIMDSv2，请使用 disableIMDSv1 控制是否允许降级");

        if (AuthConstant.OIDC_ROLE_ARN.equals(type) || AuthConstant.RAM_ROLE_ARN.equals(type)) {
            if (config.getRoleArn() != null) {
                AssertUtils.hasText(config.getRoleArn(), "凭据参数 roleArn 不能为空白");
            }
        } else {
            AssertUtils.isNull(config.getRoleArn(), "当前凭据类型不允许配置 roleArn");
            AssertUtils.isNull(config.getPolicy(), "当前凭据类型不允许配置 policy");
        }
        if (!AuthConstant.RAM_ROLE_ARN.equals(type)) {
            AssertUtils.isNull(config.getExternalId(), "当前凭据类型不允许配置 externalId");
        }
        if (AuthConstant.OIDC_ROLE_ARN.equals(type)) {
            if (config.getOidcProviderArn() != null) {
                AssertUtils.hasText(config.getOidcProviderArn(), "凭据参数 oidcProviderArn 不能为空白");
            }
            if (config.getOidcTokenFilePath() != null) {
                AssertUtils.hasText(config.getOidcTokenFilePath(), "凭据参数 oidcTokenFilePath 不能为空白");
            }
        } else {
            AssertUtils.isNull(config.getOidcProviderArn(), "当前凭据类型不允许配置 oidcProviderArn");
            AssertUtils.isNull(config.getOidcTokenFilePath(), "当前凭据类型不允许配置 oidcTokenFilePath");
        }
        if (AuthConstant.ECS_RAM_ROLE.equals(type)) {
            if (config.getRoleName() != null) {
                AssertUtils.hasText(config.getRoleName(), "凭据参数 roleName 不能为空白");
            }
        } else {
            AssertUtils.isNull(config.getRoleName(), "当前凭据类型不允许配置 roleName");
        }
        if (AuthConstant.CREDENTIALS_URI.equals(type)) {
            if (config.getCredentialsUri() != null) {
                AssertUtils.hasText(config.getCredentialsUri(), "凭据参数 credentialsURI 不能为空白");
            }
        } else {
            AssertUtils.isNull(config.getCredentialsUri(), "当前凭据类型不允许配置 credentialsURI");
        }
        if (AuthConstant.ECS_RAM_ROLE.equals(type)) {
            EcsRamRoleCredentialProvider provider = EcsRamRoleCredentialProvider.builder()
                    .roleName(config.getRoleName())
                    .disableIMDSv1(config.getDisableIMDSv1())
                    .connectionTimeout(config.getConnectTimeout())
                    .readTimeout(config.getTimeout())
                    .asyncCredentialUpdateEnabled(false)
                    .build();
            return new Client(provider);
        }
        if (AuthConstant.OIDC_ROLE_ARN.equals(type) && config.getOidcTokenFilePath() == null) {
            String tokenFilePath = System.getenv(ALIBABA_CLOUD_OIDC_TOKEN_FILE);
            if (tokenFilePath == null) {
                tokenFilePath = DEFAULT_CREDENTIAL_FILE.toString();
            } else {
                AssertUtils.hasText(tokenFilePath, "OIDC 环境配置不完整：{} 为空白", ALIBABA_CLOUD_OIDC_TOKEN_FILE);
            }
            Config resolved = Config.build(config.toMap()).setOidcTokenFilePath(tokenFilePath);
            return new Client(resolved);
        }
        return new Client(config);
    }

    /**
     * 创建 RRSA/OIDC 客户端，角色及提供商 ARN 的缺省值由 SDK 解析。
     * Token 文件优先使用 ACK 环境路径，变量未设置时使用缺省路径；缺少身份配置时失败，不回退到 AK。
     *
     * @return 使用环境或缺省文件及角色、提供商配置的客户端
     */
    public static Client oidcRoleArn() {
        return create(new Config().setType(AuthConstant.OIDC_ROLE_ARN));
    }

    /**
     * 使用长期 AK 创建凭据客户端，轮换仍由应用负责。
     *
     * @param accessKeyId     长期访问密钥 ID
     * @param accessKeySecret 长期访问密钥 Secret
     * @return 官方凭据客户端
     */
    public static Client accessKey(String accessKeyId, String accessKeySecret) {
        return create(new Config().setType(AuthConstant.ACCESS_KEY).setAccessKeyId(accessKeyId).setAccessKeySecret(accessKeySecret));
    }

    /**
     * 使用源 AK 扮演 RAM 角色，SDK 自动获取、缓存和刷新目标角色的 STS 凭据。
     *
     * @param accessKeyId     源身份的访问密钥 ID
     * @param accessKeySecret 源身份的访问密钥 Secret
     * @param roleArn         目标角色 ARN，为 null 时由 SDK 解析缺省值
     * @return 可刷新的官方凭据客户端
     */
    public static Client ramRoleArn(String accessKeyId, String accessKeySecret, @Nullable String roleArn) {
        return create(new Config().setType(AuthConstant.RAM_ROLE_ARN).setAccessKeyId(accessKeyId)
                .setAccessKeySecret(accessKeySecret).setRoleArn(roleArn));
    }

    /**
     * 使用 SDK 缺省实例角色配置，未指定角色时由实例元数据发现角色。
     *
     * @return 使用官方同步刷新机制的凭据客户端
     */
    public static Client ecsRamRole() {
        return create(new Config().setType(AuthConstant.ECS_RAM_ROLE));
    }

    /**
     * 使用指定实例角色创建凭据客户端。
     *
     * @param roleName 实例角色名称，为 null 时沿用 SDK 缺省解析
     * @return 使用官方同步刷新机制的凭据客户端
     */
    public static Client ecsRamRole(@Nullable String roleName) {
        return create(new Config().setType(AuthConstant.ECS_RAM_ROLE).setRoleName(roleName));
    }

    /**
     * 使用 OIDC 身份扮演 RAM 角色；ARN 为 null 时由 SDK 解析，文件路径为 null 时采用环境路径或缺省文件。
     *
     * @param roleArn           目标角色 ARN
     * @param oidcProviderArn   OIDC 身份提供商 ARN
     * @param oidcTokenFilePath OIDC Token 文件路径，为 null 时优先使用 ACK 环境路径，未设置时使用缺省文件
     * @return 可刷新的官方凭据客户端
     */
    public static Client oidcRoleArn(@Nullable String roleArn, @Nullable String oidcProviderArn, @Nullable String oidcTokenFilePath) {
        return create(new Config().setType(AuthConstant.OIDC_ROLE_ARN).setRoleArn(roleArn)
                .setOidcProviderArn(oidcProviderArn).setOidcTokenFilePath(oidcTokenFilePath));
    }

    /**
     * 从外部凭据服务获取并刷新临时凭据。
     *
     * @param uri 凭据服务 URI，为 null 时沿用 SDK 缺省解析
     * @return 可刷新的官方凭据客户端
     */
    public static Client credentialsUri(@Nullable String uri) {
        return create(new Config().setType(AuthConstant.CREDENTIALS_URI).setCredentialsUri(uri));
    }

    /**
     * 判断默认凭据文件是否存在。无法确定文件状态时继续尝试文件模式，由读取操作报告错误。
     *
     * @return 仅在确认文件不存在时返回 false
     */
    public static boolean hasDefaultCredentialFile() {
        return !Files.notExists(DEFAULT_CREDENTIAL_FILE);
    }

    /**
     * 读取默认路径 /etc/secrets/alibaba_cloud_credential 的加密 AK 文件。
     *
     * @param decryptor 将文件密文转换为 AK/SK 行文本的解密回调
     * @return 仅创建时读取文件的静态 AK 凭据客户端
     */
    public static Client withCredentialFile(@Nullable UnaryOperator<String> decryptor) {
        return withCredentialFile(DEFAULT_CREDENTIAL_FILE, decryptor);
    }

    /**
     * 读取 UTF-8 文件，将去除首尾空白的密文交给回调，再取解密结果的前两条非空白行作为 AK/SK。
     * <p>
     * 保持既有文件格式：行内字符不裁剪，额外行忽略，不解释为 STS Token。明文文件可显式传入
     * {@link UnaryOperator#identity()}。读取一次后不监控文件，解密回调异常保留原类型。
     *
     * @param filepath  凭据文件路径
     * @param decryptor 解密回调
     * @return 使用文件 AK/SK 的官方凭据客户端
     * @throws BaseException 参数缺失、文件读取失败或解密结果不足两条非空白行
     */
    public static Client withCredentialFile(@Nullable Path filepath, @Nullable UnaryOperator<String> decryptor) {
        AssertUtils.notNull(filepath, "凭据文件路径不能为空");
        AssertUtils.notNull(decryptor, "凭据文件解密回调不能为空");
        String encrypted;
        try {
            encrypted = Files.readString(filepath);
        } catch (IOException exception) {
            throw new BaseException(DefaultExceptionCode.COMMON_ERROR, "读取阿里云凭据文件失败", exception);
        }
        String decrypted = decryptor.apply(encrypted.trim());
        AssertUtils.hasText(decrypted, "阿里云凭据文件解密结果不能为空白");
        List<String> credentials = decrypted.lines().filter(line -> !line.isBlank()).toList();
        AssertUtils.isTrue(credentials.size() >= 2, "阿里云凭据文件格式错误，需要两条非空白的 AK/SK 行");
        return accessKey(credentials.getFirst(), credentials.get(1));
    }
}
