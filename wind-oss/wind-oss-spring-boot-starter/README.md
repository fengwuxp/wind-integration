# OSS Spring Boot 自动装配

已有应用按[两阶段迁移指南](../../wind-alibabacloud-spring-boot-starter/MIGRATION.md)升级：先将旧 OSS AK/SK 原样迁到新的产品配置，再切换 OIDC。

使用 `wind.alibaba.cloud.credentials` 配置应用统一身份，使用 `wind.alibaba.cloud.oss` 配置 OSS 端点及可选的独立身份。配置使用公共 `AlibabaCloudProductProperties`，SDK 凭据 `Config` 不作为 Spring 配置属性。

Starter 会传递引入阿里云 OSS 后端。应用主动排除 OSS 后端或 SDK 时，OSS 自动配置安全退让。

## 公共端点与认证 profile

公共 `application.yml` 只放产品 endpoint：

```yaml
wind:
  alibaba:
    cloud:
      oss:
        endpoint: https://oss-cn-hangzhou.aliyuncs.com
```

只配置 OSS endpoint 时使用统一身份。全局凭据 Bean 为 `alibabaCloudDefaultCredential`，只有产品实际需要继承身份时才创建；继承同一全局配置的产品复用该实例。

AK/SK 整组配置放入 `application-ak.yml`：

```yaml
wind:
  alibaba:
    cloud:
      credentials:
        type: access_key
        access-key: ${ALIBABA_CLOUD_ACCESS_KEY_ID}
        secret-key: ${ALIBABA_CLOUD_ACCESS_KEY_SECRET}
```

ACK 已注入 `ALIBABA_CLOUD_ROLE_ARN`、`ALIBABA_CLOUD_OIDC_PROVIDER_ARN`、`ALIBABA_CLOUD_OIDC_TOKEN_FILE` 并投影 Token 文件时，`application-ack.yml` 只需：

```yaml
wind:
  alibaba:
    cloud:
      credentials:
        type: oidc_role_arn
```

将 `SPRING_PROFILES_ACTIVE=ak` 改为 `ack` 后重启或重新装配。不要同时启用两种认证 profile，公共配置也不要留下 AK/SK；只改 type 不会删除 Spring 从其他配置文件合并进来的旧字段，身份混配会报错。这是配置切换，不是热切换。

OIDC 路径按显式 `oidc-token-file-path` → 真实环境变量 `ALIBABA_CLOUD_OIDC_TOKEN_FILE` → `/etc/secrets/alibaba_cloud_credential` 选择。ACK 路径不需要重复映射，显式值仍可覆盖环境值；任何已选值为空白时失败，不换用固定文件。Token 文件由平台轮换，官方 SDK 换取并刷新 STS。配置完整和本地签名测试不代表 ACK 角色信任、权限和网络已验证。

SDK 1.0.3 可用 `ALIBABA_CLOUD_STS_REGION` 与 `ALIBABA_CLOUD_VPC_ENDPOINT_ENABLED` 构造 STS 端点，不直接读取 `ALIBABA_CLOUD_STS_ENDPOINT`。如需该端点变量，在认证组中显式设置 `sts-endpoint: ${ALIBABA_CLOUD_STS_ENDPOINT}`；产品 OSS endpoint 保持独立。

## OSS 独立身份

```yaml
wind:
  alibaba:
    cloud:
      oss:
        endpoint: https://oss-cn-hangzhou.aliyuncs.com
        type: sts
        access-key: ${OSS_STS_ACCESS_KEY_ID}
        secret-key: ${OSS_STS_ACCESS_KEY_SECRET}
        security-token: ${OSS_STS_SECURITY_TOKEN}
```

显式类型沿用阿里云凭据 SDK；未设置类型且提供 AK/SK 时推断 `access_key`，提供 SecurityToken 时推断 `sts`。静态 STS 三件套不会自动续期，角色及 OIDC 等来源由 SDK 刷新。产品独立认证同样应整组放入对应 profile；希望继承全局 OIDC 时移除产品下的所有独立身份字段。

身份按整组选取：OSS 产品身份 → 全局身份。任何产品身份字段已设置，包括空白值，均表示该组已被选择；残缺或无效时直接报错，不从全局补字段或回退。两组都未设置时仍报错，不隐式读取环境身份。

endpoint 必须由 `wind.alibaba.cloud.oss.endpoint` 提供，不从其他配置继承。只配置 endpoint 不会遮蔽全局身份。最大连接数为 256，签名版本沿用 OSS SDK 现有配置。

## 默认 Bucket

```yaml
wind:
  oss:
    default-bucket-name: example-bucket
```

`wind.oss.default-bucket-name` 可省略。未配置或为空白时不创建 `BucketOperationsTemplate`，不影响 `WindOssClient`；配置非空值时创建对应模板。

## 使用 STS 或其他身份

应用可先通过 `AlibabaCloudCredentialsFactory.create(...)`、`oidcRoleArn()` 等方法创建凭据，再装配自定义客户端：

```java
@Bean
public WindOssClient ossClient(
        @Qualifier("windAlibabaCloudOssProperties") AlibabaCloudProductProperties properties) {
    var credential = AlibabaCloudCredentialsFactory.oidcRoleArn();
    return AlibabaCloudOssClient.withCredential(credential,
            properties.getEndpoint(), AlibabaCloudOssClient.getDefaultClientConfiguration());
}
```

需要统一环境推荐方式时使用 `AlibabaCloudCredentialsFactory.defaults()`；多个产品共享身份时，在应用装配处复用同一个凭据客户端。个性化资源使用独立凭据即可。完整入口见 [公共工厂](../../wind-alibabacloud-credentials/README.md) 与 [OSS 客户端](../alibabacloud/README.md)。

这些非 Spring 工厂入口的身份规则保持独立，不会自动读取 `wind.alibaba.cloud`。

`AlibabaCloudProductProperties` 来自公共凭据模块，`Qualifier` 为 Spring 注解。4.1.0 已删除旧 OSS Properties 类及旧配置前缀绑定；应用通过上述具名公共配置 Bean 注入，连接配置使用 OSS 实现的工具方法或显式 SDK 配置。

## 自定义客户端与关闭

应用提供 `WindOssClient` Bean 时，自动装配退让，不选择或创建凭据来源，也不创建额外 OSS 客户端。配置属性仍按 Spring 规则绑定，字段类型等绑定错误仍会报错。

自定义 `AlibabaCloudOssClient` Bean 同样通过 `defaults(...)` 或 `withCredential(...)` 创建，构造器仅供包内使用。容器关闭时调用客户端的 `close()`，释放底层 OSS SDK 客户端的连接池；凭据客户端不会随之关闭。

## 验证

使用 Java 25 在仓库根目录运行：

```bash
mvn -o -pl wind-oss/wind-oss-spring-boot-starter -am test \
  -Dtest=WindOssAutoConfigurationTests -Dsurefire.failIfNoSpecifiedTests=false \
  -Dmaven.test.skip=false -DskipTests=false -Dtest.ignore.failure=false
```

测试覆盖产品 AK/SK、产品与全局 STS 签名、整组身份优先级、产品 endpoint 必填、全局 OIDC 惰性取证、从 AK/SK 重新装配到 ACK OIDC 的换证与签名、自定义客户端退让、共享凭据动态读取及容器关闭。测试只替换外部 HTTP 通信，使用虚构凭据，不请求真实云服务。
