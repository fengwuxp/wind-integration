# 阿里云凭据工厂 Java 参考

`AlibabaCloudCredentialsFactory` 校验认证配置并创建官方 `com.aliyun.credentials.Client`。应用持有返回的客户端，SDK 按所选身份获取、缓存或刷新凭据。

普通 Java 调用从方法参数或环境变量读取身份。Spring Boot 应用使用 Starter 绑定配置。[Spring Boot 接入](../README.md#spring-boot) · [已有应用迁移](../spring-boot-starter/MIGRATION.md)

## 引入依赖并选择来源

导入 `com.wind.integration:wind-integration-dependencies` BOM 后，可省略依赖版本。

```xml
<dependency>
    <groupId>com.wind.integration</groupId>
    <artifactId>wind-alibabacloud-credentials</artifactId>
</dependency>
```

普通 Java 应用可以直接调用工厂。传入 SDK 配置时使用 `com.aliyun.credentials.utils.AuthConstant` 中的类型常量。

| 类型 / 常量 | 常用入口与所需身份 | 凭据更新方式 |
| --- | --- | --- |
| `access_key` / `ACCESS_KEY` | `accessKey(ak, sk)` | 静态 AK，应用负责轮换 |
| `sts` / `STS` | `create(Config)`，同次签发的临时 AK、SK、SecurityToken | 固定三元组，不自动续期 |
| `ram_role_arn` / `RAM_ROLE_ARN` | `ramRoleArn(ak, sk, roleArn)`；源身份可通过 Config 携带 STS Token | SDK 获取并刷新目标角色 STS；源身份须保持有效 |
| `ecs_ram_role` / `ECS_RAM_ROLE` | `ecsRamRole()` 或 `ecsRamRole(roleName)` | SDK 从实例元数据取得并刷新凭据 |
| `oidc_role_arn` / `OIDC_ROLE_ARN` | `oidcRoleArn()` 或 `oidcRoleArn(roleArn, providerArn, tokenPath)` | SDK 使用 OIDC Token 换取并刷新 STS |
| `credentials_uri` / `CREDENTIALS_URI` | `credentialsUri(uri)` | SDK 请求外部凭据服务，并按有效期刷新 |

显式选择来源不经过 `defaults()`。例如，固定使用 AK 时调用 `accessKey(ak, sk)`，不会被 OIDC 环境配置覆盖。静态 STS 使用通用入口：

```java
import com.aliyun.credentials.models.Config;
import com.aliyun.credentials.utils.AuthConstant;
import com.wind.integration.alibaba.credentials.AlibabaCloudCredentialsFactory;

var credential = AlibabaCloudCredentialsFactory.create(new Config()
        .setType(AuthConstant.STS)
        .setAccessKeyId(accessKeyId)
        .setAccessKeySecret(accessKeySecret)
        .setSecurityToken(securityToken));
```

`create(Config)` 要求明确的类型，拒绝 null 配置、空白或未知类型、`default` 类型以及身份参数混配。选定来源失败时不会换用其他身份。

## 使用环境默认策略

```java
var credential = AlibabaCloudCredentialsFactory.defaults();
```

身份选择只检查真实进程环境变量，顺序为：

1. 检查 `ALIBABA_CLOUD_ROLE_ARN` 和 `ALIBABA_CLOUD_OIDC_PROVIDER_ARN`。两者完整且非空白时选择 OIDC；只设置一项或任一项为空白，直接失败。
2. 仅当两个 ARN 均未设置时，读取 `ALIBABA_CLOUD_ACCESS_KEY_ID`、`ALIBABA_CLOUD_ACCESS_KEY_SECRET` 和 `ALIBABA_CLOUD_SECURITY_TOKEN`。
3. AK/SK 必须完整且非空白。Token 未设置时选择长期 AK；Token 已设置时选择静态 STS，空白 Token 报错。
4. 全部未配置时报错，不继续搜索 JVM 系统属性、CLI 配置、AK 文件或 ECS，不进入 SDK 默认凭据链。

如果装配代码需要自行处理“没有 OIDC”的情况，可调用 `findOidcRoleArnFromEnvironment()`：两个 ARN 均未设置才返回 `Optional.empty()`，部分配置仍会报错；它不读取环境 AK/STS。该入口与 `defaults()` 的 OIDC 分支都不接受 SDK 进程内覆盖值替换已选定的环境身份。

## 配置参数

直接使用 SDK `Config` 可设置角色策略、会话时长、STS 端点等选项；所有快捷方法共用 `create` 的校验。工厂不修改输入配置，创建后修改原配置不会切换现有客户端身份。

也可使用普通 Java 配置对象，经 `fromProperties` 映射为 SDK Config：

```java
import com.wind.integration.alibaba.credentials.AlibabaCloudCredentialsProperties;

var properties = new AlibabaCloudCredentialsProperties();
properties.setAccessKey(accessKeyId);
properties.setSecretKey(accessKeySecret);
var credential = AlibabaCloudCredentialsFactory.fromProperties(properties);
```

所有属性初值为 null。只有 `type == null` 时才推断类型：存在 `securityToken` 则选 `sts`，否则存在 AK/SK 字段则选 `access_key`；其他模式必须指定类型。空字符串属于已配置值，残缺配置报错，不从环境或其他身份补字段。

| Properties 属性 | SDK Config 对应参数与用途 |
| --- | --- |
| `type` | `type`，六种来源之一 |
| `accessKey`、`secretKey`、`securityToken` | `accessKeyId`、`accessKeySecret`、`securityToken` |
| `roleArn`、`oidcProviderArn`、`oidcTokenFilePath` | 同名参数；角色及 OIDC 身份 |
| `roleSessionName`、`roleSessionExpiration` | 会话名称、有效期；有效期单位为秒 |
| `policy`、`externalId` | 会话权限限制、AssumeRole 外部身份标识 |
| `roleName`、`disableIMDSv1` | ECS 实例角色名称、是否禁止降级到 IMDSv1 |
| `credentialsUri` | 外部凭据服务 URI |
| `stsEndpoint` | `STSEndpoint`，角色换证端点 |
| `connectTimeout`、`readTimeout` | `connectTimeout`、`timeout`，凭据请求超时，单位毫秒 |

这些超时是获取凭据时使用的参数，产品 SDK 的连接参数需单独设置。`AlibabaCloudProductProperties` 另有 `endpoint`，公共工厂不使用它创建产品客户端。`hasCredentials()` 只判断是否有任意认证属性非 null，空白和 false 也算已配置；仅设置 endpoint 不算身份配置，返回 true 也不表示凭据已有效。

项目使用 `credentials-java 1.0.3`。以下字段有版本或模式限制。

- `policy` 仅用于 `ram_role_arn`、`oidc_role_arn`；`externalId` 仅用于 `ram_role_arn`。其他模式传入会报错。
- `roleArn`、OIDC 字段、`roleName`、`credentialsUri` 只能用于各自的模式；OIDC、ECS、URI 模式不能混入 AK/SK/Token。工厂也拒绝 `bearerToken`、`publicKeyId`、`privateKeyFile`。
- SDK 不消费 `Config.proxy`、`host`、`enableIMDSv2`，工厂拒绝显式设置；换证地址使用 `STSEndpoint`，禁止 IMDSv1 降级使用 `disableIMDSv1=true`。
- `metadataTokenDuration` 不在工厂中额外校验；当前 SDK 的 IMDSv2 Token 时长固定为 `21600` 秒，设置该字段不会改变这一行为。

## OIDC Token 文件与 STS 端点

ACK/RRSA 已注入角色、提供商 ARN 和 Token 文件路径时，直接调用 `oidcRoleArn()`。显式 OIDC 配置中，`roleArn`、`oidcProviderArn` 为 null 时由 SDK 读取其进程内覆盖值或环境变量；显式非空参数优先。角色模式的缺省 ARN、URI 模式的缺省地址也交给 SDK 解析。

OIDC Token 文件路径由工厂单独确定，优先级为：

1. 显式 `oidcTokenFilePath`。
2. 真实环境变量 `ALIBABA_CLOUD_OIDC_TOKEN_FILE`。
3. `/etc/secrets/alibaba_cloud_credential`。

只有 null 才进入下一项。显式路径或环境变量为空白时直接失败，SDK 的进程内路径覆盖不会替换已选定路径。创建客户端不读取 Token 文件，也不调用 STS；首次取证和后续刷新由 SDK 读取原始 Token 并调用 `AssumeRoleWithOIDC`。运行平台负责挂载、限制文件访问并轮换 Token。

若首次换证失败，检查角色的信任策略、Token 的受众和主体，以及 Pod 到 STS 的网络。若已取得凭据但产品返回拒绝访问，检查角色对目标资源的权限。

角色换证可显式设置 `Config.setSTSEndpoint(...)`。未设置时，SDK 可根据 `ALIBABA_CLOUD_STS_REGION` 和 `ALIBABA_CLOUD_VPC_ENDPOINT_ENABLED` 构造区域端点，例如 `cn-hangzhou` 与 `true` 对应 `sts-vpc.cn-hangzhou.aliyuncs.com`。SDK 1.0.3 不直接读取 `ALIBABA_CLOUD_STS_ENDPOINT`；如使用该变量，由应用读取后传入 `setSTSEndpoint`。STS 端点与 OSS、邮件等产品 endpoint 各自配置。

## 读取加密 AK 文件

```java
import java.nio.file.Path;

var credential = AlibabaCloudCredentialsFactory.withCredentialFile(decryptor::decrypt);
var custom = AlibabaCloudCredentialsFactory.withCredentialFile(
        Path.of("/path/to/encrypted-credential"), decryptor::decrypt);
```

默认路径为 `/etc/secrets/alibaba_cloud_credential`。工厂按 UTF-8 读取文件，将整体 `trim()` 后的密文交给应用回调，再取解密结果的前两条非空白行作为 AK/SK。行内字符不裁剪，额外行忽略，不解释为 STS Token。明文文件可显式传入 `UnaryOperator.identity()`；工厂不会在解密失败后改按明文尝试。

文件在创建客户端时只读一次，返回静态 AK；后续修改文件不会自动轮换凭据。读取失败抛出保留 I/O 原因的 `BaseException`，格式错误由统一断言报告，解密回调异常原样传播。公共模块只接收回调，不依赖产品的解密 SPI。

同一默认路径在 OIDC 模式下存放原始 Token，在 AK 文件模式下存放待解密的 AK/SK 文本，两种内容不能混用。OIDC 不调用 AK 解密回调。文件应只对所需进程开放读取权限，避免记录 Token、解密结果或完整凭据对象；普通 Properties 的 `toString()` 只输出 type，产品 Properties 还输出 endpoint。

## 共享、刷新与生命周期

在应用装配处创建并持有 Client。同一身份及授权范围可共享一个实例，再传入产品的 `withCredential(...)`；不同角色或权限策略使用独立实例。工厂不缓存已创建的客户端，复用由调用方负责。

动态来源由 SDK 缓存和刷新，静态 AK/STS 由应用更新。切换来源或身份需重新装配；SDK 续期只更新当前身份的临时凭据。刷新失败时的缓存行为由各 SDK 提供者决定：OIDC 刷新失败会传播异常，ECS 的 `ALLOW` 策略可能继续返回缓存，不能将它们视为相同策略。

工厂使用 ECS 官方提供者的同步刷新模式，避免创建后台刷新任务；当前 SDK Client 没有关闭入口。产品 SDK 连接池仍按其自身生命周期释放，不因关闭某个产品客户端而关闭共享凭据。

自行适配产品 SDK 时，每次通过一次 `getCredential()` 取得完整三元组，避免启动时固定动态凭据或分三次读取导致身份不一致。当前 Tea OpenAPI 接入使用 `Config.setCredential(credential)`，连接配置不要再混填直接 AK/SK/Token，以免它们优先于动态凭据。

本模块校验失败抛出 `BaseException`；SDK 的缺省解析、参数检查及取证异常保留原类型。创建配置与实际取证是两个阶段，调用方应在真正使用凭据时处理失败。
