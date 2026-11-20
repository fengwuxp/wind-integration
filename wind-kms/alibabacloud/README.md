# 阿里云 KMS

KMS 不读取 `wind.alibaba.cloud`，但无参 `defaults()` 会响应 ACK 注入的 OIDC 环境变量。给同一应用启用 RRSA 时，需要核对 KMS 角色权限或保留显式凭据来源；仅将 Spring profile 切回 AK 不会关闭该环境路由。参见[两阶段迁移指南](../../wind-alibabacloud-spring-boot-starter/MIGRATION.md)。

`AlibabaCloudKmsCryptoClient` 提供加密、解密和凭据读取能力。应用可以使用公共凭据工厂创建一个官方凭据客户端，再为 KMS 指定独立的端点和超时。

## 共享凭据接入

```java
import com.aliyun.teaopenapi.models.Config;
import com.wind.integration.alibaba.AlibabaCloudKmsCryptoClient;
import com.wind.integration.alibaba.credentials.AlibabaCloudCredentialsFactory;

var credential = AlibabaCloudCredentialsFactory.oidcRoleArn();
var kms = AlibabaCloudKmsCryptoClient.withCredential(credential, new Config()
        .setEndpoint("kms.us-east-1.aliyuncs.com")
        .setConnectTimeout(3000)
        .setReadTimeout(10000));
```

同一身份和授权范围可复用 `credential`，不同 KMS 客户端可设置各自的连接参数。凭据客户端由应用持有；`withCredential` 在构造时不获取凭据，后续请求通过官方 SDK 读取当前凭据。具体凭据来源、静态 STS 的有效期及角色凭据刷新方式，参见[公共凭据模块](../../wind-alibabacloud-credentials/README.md)。

连接配置不能再包含 `accessKeyId`、`accessKeySecret`、`securityToken`、`bearerToken` 或 `credential`，即使设置为空字符串也会拒绝。这样可防止 Tea SDK 优先采用直接配置的身份，遮蔽传入的共享凭据。

`withCredential` 复制连接配置，不修改调用方输入。显式端点优先；未提供端点时沿用现有规则：先读取 `ALIBABA_CLOUD_K_ID` 生成实例 VPC 域名，再读取 `ALIBABA_CLOUD_K_ENDPOINT`，同名系统属性优先于环境变量。KMS 请求超时与凭据客户端访问 STS 的超时是独立配置。

## 策略选择与兼容入口

凭据创建由公共工厂负责，KMS 在 `defaults` 中集中选择策略，随后统一装配。保留原有公开方法：

| 入口 | 用途 |
| --- | --- |
| `withCredential(credential, clientConfig)` | 复用应用已持有的官方凭据客户端 |
| `defaults()` | 两个 OIDC ARN 齐全时使用环境或缺省路径的 Token，均未设置时读取加密 AK 文件，残缺时报错 |
| `defaults(credentialConfig)` | 显式策略优先，KMS 端点读取现有系统配置；null 检查 OIDC 环境配置 |
| `defaults(credentialConfig, clientConfig)` | 显式策略优先，独立指定 KMS 连接参数；凭据配置为 null 时检查 OIDC 环境配置 |
| `withCredentialConfig(credentialConfig, clientConfig)` | 兼容显式配置入口，委托策略路由；凭据配置不能为空 |
| `withCredentialConfig(credentialConfig)` | 兼容单配置入口，凭据配置不能为空 |
| `withOIDCRoleArn()` | 使用缺省角色配置及环境或缺省 Token 路径创建 OIDC 凭据 |
| `withOIDCRoleArn(credentialConfig)` | 兼容旧用法，只取 OIDC 有效参数，保留调用方输入 |

通用配置入口支持 `access_key`、`sts`、`ram_role_arn`、`ecs_ram_role`、`oidc_role_arn`、`credentials_uri` 六种来源。例如：

```java
import com.aliyun.credentials.utils.AuthConstant;

var credentialConfig = new com.aliyun.credentials.models.Config()
        .setType(AuthConstant.STS)
        .setAccessKeyId(temporaryAccessKeyId)
        .setAccessKeySecret(temporaryAccessKeySecret)
        .setSecurityToken(securityToken);
var kms = AlibabaCloudKmsCryptoClient.defaults(credentialConfig,
        new Config().setEndpoint("kms.us-east-1.aliyuncs.com"));
```

`sts` 使用一组固定临时凭据，不自动续期；长期服务可选择 SDK 支持刷新的角色来源。默认 SPI 的 `getCryptoClient()`、`getCredentialsClient()` 每次调用仍创建新客户端，不自动共享身份实例。应用需要自行持有共享凭据；创建的 KMS 客户端也可以传给已有的 `KmsTextEncryptor(WindCryptoClient)`。

## 凭据文件与默认分支

内部文件分支使用 `ServiceLoader` 发现原 `WindKmsClientCredentialsDecryptor` 实现，并将 `decrypt` 回调交给公共工厂。应用继续在 `META-INF/services/com.wind.integration.kms.WindKmsClientCredentialsDecryptor` 注册解密器。

默认文件为 `/etc/secrets/alibaba_cloud_credential`。公共工厂对文件密文 `trim()` 后调用解密器，按旧规则取解密结果前两条非空白行作为 AK/SK，忽略后续行，不把第三行当作 STS Token。需要自定义路径时，可使用公共工厂 `withCredentialFile(Path, UnaryOperator<String>)` 创建凭据，再传给 KMS 的 `withCredential`。

OIDC Token 路径按顺序选择：显式 `Config.oidcTokenFilePath`、真实环境变量 `ALIBABA_CLOUD_OIDC_TOKEN_FILE`、公共工厂的 `DEFAULT_CREDENTIAL_FILE`（`/etc/secrets/alibaba_cloud_credential`）。ACK 已注入 Token 路径时可直接采用；环境变量已设置但为空白会报错，不回退到缺省文件。AK 文件模式的缺省路径仍固定，不受该环境变量影响。

文件用途由所选认证方式决定：OIDC 模式读取原始 Token，不经过 AK 解密 SPI；文件 AK 模式读取加密的 AK/SK 行文本。两种内容不能混用。

`defaults` 的路由顺序：

1. 提供非 null 的凭据配置时，严格按其类型选择策略，优先于环境配置。空白、未知类型及 `type=default` 均报错。
2. 未提供凭据配置时，仅读取 `ALIBABA_CLOUD_ROLE_ARN`、`ALIBABA_CLOUD_OIDC_PROVIDER_ARN`。两项均未设置（均为 null）才路由到内部加密 AK 文件分支，保留原文件路径和解密 SPI。
3. 两项齐全且非空白时选择 OIDC，按上述路径顺序读取 Token；只设置其中一项、空字符串或空白字符串均抛 `BaseException`，不回退到 AK 文件身份。

自动 OIDC 路由使用同一次读取的两个 ARN 环境值创建客户端，SDK 的进程内覆盖值不会替换它们。Token 路径读取真实环境变量，不接受 SDK 的进程内路径覆盖值。显式 `withOIDCRoleArn()` 的角色仍按 SDK 缺省规则解析，Token 使用相同路径优先级；显式路径优先于环境，公共工厂不修改输入。环境配置齐全不代表文件可读、角色信任或资源权限已验证。

OIDC 的常量和三态解析由公共工厂的 `findOidcRoleArnFromEnvironment()` 统一维护。KMS 不直接使用公共 `defaults()`：公共推荐方式在无 OIDC 时选择环境 AK/STS，而 KMS 保留原文件身份作为缺省，两者分别使用同一公共凭据能力。

环境 AK/STS 不会自行改变缺省身份，也不会隐式进入 SDK 默认凭据链。策略已经选定后，取证失败或认证失败直接报错，不回退文件。显式配置优先于环境完整性检查，例如显式 `ram_role_arn` 不受残缺 OIDC 环境配置影响。`withCredentialConfig(null)` 仍为错误；`defaults` 接受 null 凭据配置表示使用环境策略。`withOIDCRoleArn()` 始终显式选择 OIDC。

## 迁移说明

- 无 OIDC ARN 配置时保留加密 AK 文件用法。自动 OIDC 需要两个 ARN 齐全且非空白；部分设置或显式空白会报错，不回退到 AK 文件。
- RRSA 可直接采用 ACK 注入的 `ALIBABA_CLOUD_OIDC_TOKEN_FILE`；未注入该变量时才使用 `/etc/secrets/alibaba_cloud_credential`。仅设置 Token 路径而未配置两个 ARN，不会让 KMS 选择 OIDC；程序显式配置 `oidcTokenFilePath` 的用法保留。
- `withOIDCRoleArn(credentialConfig)` 不再修改传入对象的 `type`，而是在副本中固定类型。
- `withOIDCRoleArn(credentialConfig)` 保留旧 SDK 对认证参数的解释：无需设置 type，只使用角色、会话、OIDC、Policy、STS 端点和超时参数，忽略其他来源字段。会话时长及超时的 null 缺省语义保持。
- 新的显式 `defaults(config)`、`withCredentialConfig(config)` 和公共工厂仍严格拒绝混配身份；兼容投影仅限旧 OIDC 入口。旧入口只保留原认证效果，不恢复修改调用方 type 的副作用。
- 旧 `com.wind.integration.alibaba.credential.AlibabaCloudCredentialUtils` 已删除。外部直接调用此类的源码或二进制不兼容，应迁移到公共工厂或上述 KMS 入口。
- `WindKmsClientCredentialsDecryptor` 和 `WindKmsClientProvider` 接口保持不变；公共凭据模块不依赖 KMS。

## 验证

使用 Java 25 在仓库根目录运行：

```bash
mvn -o -pl wind-kms/alibabacloud -am test \
  '-Dtest=AlibabaCloudKms*Tests' -Dsurefire.failIfNoSpecifiedTests=false \
  -Dmaven.test.skip=false -DskipTests=false -Dtest.ignore.failure=false
```

测试验证构造不取证、输入配置保持、身份混配拒绝，以及加密、解密、读凭据三个 SDK 操作的请求令牌、后续凭据轮换、端点与超时透传。六种通用凭据来源均验证到 KMS 请求签名；文件模式验证真实 SPI 发现与解密回调。隔离进程覆盖两个 ARN 的完整、未设置、部分设置和空白情况，以及环境 Token 路径、缺省路径、空白路径拒绝和 SDK 覆盖值隔离；另保留显式路径与取证失败不回退验证。

测试替换外部 HTTP 通信、固定 AK 文件读取和 SDK 的 OIDC Token 读取边界，保留 SDK 请求构造和签名，使用虚构凭据，不调用真实云服务或读取真实 `/etc/secrets` 文件。

这些验证不代表实际 RAM 授权、RRSA 信任关系、KMS 实例与网络连通性已经验证。
