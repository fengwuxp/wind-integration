package com.wind.integration.alibaba.credentials;

import lombok.Data;
import lombok.ToString;

/**
 * 与装配框架无关的阿里云认证配置；未配置字段保持 null，不与其他身份逐字段合并。
 */
@Data
@ToString(onlyExplicitlyIncluded = true)
public class AlibabaCloudCredentialsProperties {

    /**
     * 官方凭据类型；未指定时仅根据 AK/SK 或 SecurityToken 推断静态类型。
     */
    @ToString.Include
    private String type;

    /**
     * 长期或临时 AccessKey ID；角色模式中用于提供源身份。
     */
    private String accessKey;

    /**
     * 与 AccessKey ID 配套的签名密钥。
     */
    private String secretKey;

    /**
     * 与临时 AK/SK 同次签发的 STS Token；仅配置此值不能组成有效凭据。
     */
    private String securityToken;

    /**
     * AssumeRole 或 OIDC 需要扮演的目标 RAM 角色 ARN。
     */
    private String roleArn;

    /**
     * RAM 中信任的 OIDC 身份提供商 ARN。
     */
    private String oidcProviderArn;

    /**
     * OIDC Token 文件路径；null 时由 Factory 使用 ACK 环境路径，环境未设置才采用缺省文件。
     */
    private String oidcTokenFilePath;

    /**
     * 角色会话名称，用于区分访问来源。
     */
    private String roleSessionName;

    /**
     * 限制角色会话权限的 JSON 策略，不能用于扩大角色本身权限。
     */
    private String policy;

    /**
     * AssumeRole 的外部身份标识，用于角色信任校验。
     */
    private String externalId;

    /**
     * ECS 实例关联的角色名称，非角色 ARN。
     */
    private String roleName;

    /**
     * 外部临时凭据服务的 URI，由 SDK 请求并刷新返回的凭据。
     */
    private String credentialsUri;

    /**
     * 角色换证使用的 STS 服务端点，独立于具体云产品的端点。
     */
    private String stsEndpoint;

    /**
     * 角色会话有效期，单位秒。
     */
    private Integer roleSessionExpiration;

    /**
     * 凭据获取请求的连接超时，单位毫秒。
     */
    private Integer connectTimeout;

    /**
     * 凭据获取请求的读取超时，单位毫秒。
     */
    private Integer readTimeout;

    /**
     * 为 true 时禁止 ECS 元数据认证从 IMDSv2 降级到 IMDSv1；null 保留 SDK 缺省行为。
     */
    private Boolean disableIMDSv1;

    /**
     * 判断是否显式配置认证；空白和 false 也算已配置，完整性由工厂校验。
     *
     * @return 任意认证字段非 null 时返回 true
     */
    public boolean hasCredentials() {
        return type != null || accessKey != null || secretKey != null || securityToken != null
                || roleArn != null || oidcProviderArn != null || oidcTokenFilePath != null || roleSessionName != null
                || policy != null || externalId != null || roleName != null || credentialsUri != null || stsEndpoint != null
                || roleSessionExpiration != null || connectTimeout != null || readTimeout != null || disableIMDSv1 != null;
    }
}
