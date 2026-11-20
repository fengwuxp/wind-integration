# 阿里云 OSS 凭据接入

`AlibabaCloudOssClient` 复用 `AlibabaCloudCredentialsFactory` 创建凭据，OSS 模块仅负责将凭据适配到 OSS SDK。`defaults` 是推荐的快捷装配方法，参数不依赖 Spring 或产品配置对象。

## Spring Boot 集成

下方 YAML 与 `.properties` 为等价写法，选择一种使用；同一组配置不要在两种文件中重复维护。使用 `.properties` 时，认证 profile 文件对应为 `application-ak.properties`、`application-ack.properties`。

Spring Boot 应用引入 OSS Starter，它会传递引入本模块和公共凭据 Starter，无需重复声明后端依赖。以下版本对应当前源码，应用应替换为实际可用且一致的发布版本；已由项目 BOM 管理时可省略版本：

```xml
<dependency>
    <groupId>com.wind.integration</groupId>
    <artifactId>wind-oss-spring-boot-starter</artifactId>
    <version>4.1.0-SNAPSHOT</version>
</dependency>
```

在 `application.yml` 中配置 OSS 自己的身份和实际 endpoint，环境变量由应用部署提供：

```yaml
wind:
  alibaba:
    cloud:
      oss:
        endpoint: ${OSS_ENDPOINT}
        type: access_key
        access-key: ${OSS_ACCESS_KEY_ID}
        secret-key: ${OSS_ACCESS_KEY_SECRET}
```

等价的 `application.properties`：

```properties
wind.alibaba.cloud.oss.endpoint=${OSS_ENDPOINT}
wind.alibaba.cloud.oss.type=access_key
wind.alibaba.cloud.oss.access-key=${OSS_ACCESS_KEY_ID}
wind.alibaba.cloud.oss.secret-key=${OSS_ACCESS_KEY_SECRET}
```

业务服务直接构造注入 `WindOssClient`，不再手动 `new` 或调用工厂创建第二个实例。例如以下类放入应用组件扫描范围：

```java
import java.io.InputStream;

import com.wind.integration.oss.WindOssClient;
import org.springframework.stereotype.Service;

@Service
public class ObjectStorageService {

    private final WindOssClient oss;

    public ObjectStorageService(WindOssClient oss) {
        this.oss = oss;
    }

    public InputStream download(String bucketName, String objectKey) {
        return oss.downloadFile(bucketName, objectKey);
    }
}
```

下载流由调用方关闭；OSS 客户端由 Spring 在容器关闭时释放，不在每次业务请求后关闭。使用默认 `BucketOperationsTemplate` 时，另配置 `wind.oss.default-bucket-name`，其前缀不变。

ACK 已配置 RRSA、角色权限并注入两个 ARN 和 `ALIBABA_CLOUD_OIDC_TOKEN_FILE` 后，仅切换 OSS 时，将上述 `oss` 认证组替换为：

```yaml
wind:
  alibaba:
    cloud:
      oss:
        endpoint: ${OSS_ENDPOINT}
        type: oidc_role_arn
```

等价的 `application.properties`：

```properties
wind.alibaba.cloud.oss.endpoint=${OSS_ENDPOINT}
wind.alibaba.cloud.oss.type=oidc_role_arn
```

必须从实际生效的全部配置来源移除原 OSS AK/SK、静态 SecurityToken，不能只覆盖 `type`。也可按[两阶段迁移指南](../../wind-alibabacloud-spring-boot-starter/MIGRATION.md)使用互斥认证 profile；重启或重新装配后生效，SDK 负责 OIDC 换证与 STS 刷新。该配置只选择 OSS 身份，邮件和 KMS 的配置保持独立；同一 Pod 的 OIDC 环境注入对 KMS 默认路由的影响见迁移指南。

应用已有 `WindOssClient` Bean 时，自动装配会退让；仅修改 YAML 不会替换自建客户端。需要多个产品共享身份时，再使用 `wind.alibaba.cloud.credentials`，并移除 OSS 产品下整组认证字段。完整选择顺序、自定义 Bean 和 Bucket 配置见 [OSS Starter 文档](../wind-oss-spring-boot-starter/README.md)；旧 AK/SK 配置和代码升级按[迁移指南](../../wind-alibabacloud-spring-boot-starter/MIGRATION.md)执行。

## 推荐环境配置

以下为非 Spring 或应用明确选择手工装配的入口，不会读取 Spring 的 `wind.alibaba.cloud` 配置。

```java
try (AlibabaCloudOssClient oss = AlibabaCloudOssClient.defaults(endpoint)) {
    oss.deleteFile(bucketName, objectKey);
}
```

未传显式凭据配置时，使用公共工厂的推荐环境策略：

1. 优先使用 OIDC：配置 `ALIBABA_CLOUD_ROLE_ARN`、`ALIBABA_CLOUD_OIDC_PROVIDER_ARN`，Token 路径读取真实环境变量 `ALIBABA_CLOUD_OIDC_TOKEN_FILE`；未设置时使用 `/etc/secrets/alibaba_cloud_credential`，已设置但为空白时失败。
2. 没有 OIDC 配置时，使用 `ALIBABA_CLOUD_ACCESS_KEY_ID` 和 `ALIBABA_CLOUD_ACCESS_KEY_SECRET`；同时提供 `ALIBABA_CLOUD_SECURITY_TOKEN` 时使用 STS 凭据。
3. OIDC 或 AK/STS 配置残缺、空白，或全部未配置时直接报错。

默认最大连接数为 256，沿用当前 OSS SDK 的签名默认值。环境变量中的静态 STS 三元组不会自动刷新；需要自动换取、刷新临时凭据时使用 OIDC、RAM Role、ECS RAM Role 或 credentials URI 等动态凭据来源。

`AlibabaCloudOssClient.getDefaultClientConfiguration()` 返回独立的默认连接配置，最大连接数为 256；`getBucketEndpoint(...)` 和 `getBucketInternalEndpoint(...)` 提供地址计算。4.1.0 已删除旧 OSS Properties 类，应用直接使用这些工具方法；Spring 配置使用公共 `AlibabaCloudProductProperties`。

内网地址工具仅保留历史 `aliyuncs.com` 转换规则，不据此推导其他站点或自定义域名的内网地址；这些场景应使用实际端点。

## 显式凭据配置

```java
import com.aliyun.credentials.utils.AuthConstant;

Config credentialConfig = new Config().setType(AuthConstant.OIDC_ROLE_ARN);
ClientConfiguration connection = new ClientConfiguration();
connection.setMaxConnections(256);
try (AlibabaCloudOssClient oss = AlibabaCloudOssClient.defaults(
        credentialConfig, endpoint, connection)) {
    oss.deleteFile(bucketName, objectKey);
}
```

这里的 `Config` 是 `com.aliyun.credentials.models.Config`，`ClientConfiguration` 是 `com.aliyun.oss.ClientConfiguration`。显式配置通过公共工厂的 `create` 校验与创建；传入 `null` 时使用公共工厂的推荐环境策略。ACK 已注入两个 ARN 和 Token 文件路径时，示例中的 `type=oidc_role_arn` 即可使用这些配置。文件路径优先级为显式 `oidcTokenFilePath` → 真实 `ALIBABA_CLOUD_OIDC_TOKEN_FILE` 环境变量 → 项目缺省文件；显式值或环境值为空白会失败，不回退到其他文件。

换证端点可由 SDK 1.0.3 的 `ALIBABA_CLOUD_STS_REGION` 和 `ALIBABA_CLOUD_VPC_ENDPOINT_ENABLED` 组合指定。SDK 不直接读取 `ALIBABA_CLOUD_STS_ENDPOINT`，需要使用该变量时，在应用代码中显式设置 `Config.setSTSEndpoint(...)`；Spring 应用映射到 `sts-endpoint` 即可。OSS endpoint 与换证端点相互独立。

`access_key`、`sts`、`ram_role_arn`、`ecs_ram_role`、`oidc_role_arn`、`credentials_uri` 的具体配置规则见 [公共凭据模块](../../wind-alibabacloud-credentials/README.md)。传入的 OSS 连接配置直接交给 SDK 使用，不做复制。

## V4 签名

普通 OSS 端点使用 V4 签名时，需要显式提供 Bucket 所在地域：

```java
ClientConfiguration connection = new ClientConfiguration();
connection.setSignatureVersion(SignVersion.V4);
connection.setMaxConnections(256);
try (AlibabaCloudOssClient oss = AlibabaCloudOssClient.defaults(
        credentialConfig, endpoint, "cn-hangzhou", connection)) {
    oss.deleteFile(bucketName, objectKey);
}
```

`SignVersion` 来自 `com.aliyun.oss.common.comm.SignVersion`。已有凭据客户端时使用 `withCredential(credential, endpoint, region, connection)`。传入地域不会自动切换签名版本，现有入口仍保持 V1 默认行为。

未传地域时保留 SDK 从 CloudBox 端点提取地域的行为。选择 V4 且无法取得地域时，工厂会在创建阶段明确报错并释放连接池，不再等到首次请求时出现签名空指针异常。

## 复用凭据与关闭

已有 `com.aliyun.credentials.Client` 时可以直接复用：

```java
try (AlibabaCloudOssClient oss = AlibabaCloudOssClient.withCredential(
        credential, endpoint, connection)) {
    oss.deleteFile(bucketName, objectKey);
}
```

多个产品需要统一身份时，将公共工厂创建的同一个凭据客户端传给各自的 `withCredential`；需要独立身份的 OSS 客户端传入另一个凭据实例。单个客户端不会按 Bucket 自动切换身份。

从 AK/SK 切到 ACK OIDC 时应替换整组认证配置并重新装配或重启，不能只改 type 而保留旧密钥。Spring 应用将不同认证组分别放入互斥的 profile，公共配置只放产品 endpoint；详见 [Spring 配置示例](../wind-oss-spring-boot-starter/README.md)。

OSS SDK 创建请求上下文时，适配层只调用一次 `credential.getCredential()`，从同一份快照读取 AK、SK 和 Token；缓存与刷新由凭据 SDK 负责，OSS 适配层不会缓存临时凭据。同一请求内部的重试沿用该上下文中的凭据。

构造器为包级可见，应用通过 `defaults(...)` 或 `withCredential(...)` 创建实例，原来的跨包构造调用需要迁移到工厂方法。所有实例均通过 `close()` 关闭底层 OSS 客户端并释放连接池，不关闭凭据客户端。

现有上传、下载、元数据及 URL 方法语义保持不变；`getFileUrl`、`getFileInternalUrl` 返回地址，不会生成带临时凭据的预签名下载链接。Spring Boot 配置示例见 [Starter 文档](../wind-oss-spring-boot-starter/README.md)。
