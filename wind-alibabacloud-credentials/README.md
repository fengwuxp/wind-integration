# 阿里云凭据

`wind-alibabacloud-credentials` 为阿里云产品适配器和应用装配代码提供统一的凭据创建入口。返回值直接使用 `com.aliyun.credentials.Client`，凭据获取、缓存和刷新交给官方 `credentials-java`。

本模块通过 `wind-core` 复用 `AssertUtils` 和统一的 `BaseException`，会传递引入 Spring Core，但无需 Spring 容器。不依赖 KMS、OSS、邮件 SDK。文件凭据通过调用方传入的解密回调加载，不依赖具体产品的解密 SPI。[KMS](../wind-kms/alibabacloud/README.md)、[OSS](../wind-oss/alibabacloud/README.md) 和 [邮件](../wind-message/email/README.md) 均可接入公共凭据。

Spring Boot 应用可直接按下方示例接入 OSS 和邮件推送。配置绑定由 `wind-alibabacloud-spring-boot-starter` 完成；只引入本凭据模块不会自动绑定 YAML 或创建产品客户端。

## Spring Boot 集成

下方 YAML 与 `.properties` 为等价写法，选择一种使用；同一组配置不要在两种文件中重复维护。使用 `.properties` 时，认证 profile 文件对应为 `application-ak.properties`、`application-ack.properties`。

### 依赖与云产品支持

应用使用匹配的 Wind 4.1.0 依赖版本/BOM 和 Spring Boot 运行环境。当前源码构建版本为 `4.1.0-SNAPSHOT`，Java 25、Spring Boot 4.1.0；版本以实际发布产物为准。以下示例省略由 BOM 管理的版本。

| 应用场景 | 引入的 Maven artifactId | 自动装配结果 |
| --- | --- | --- |
| 仅使用凭据 Java API | `wind-alibabacloud-credentials` | 不自动装配，调用 Factory |
| 仅使用 OSS | `wind-alibabacloud-spring-boot-starter` + `wind-oss-alibabacloud` | 创建 `WindOssClient` |
| 仅使用邮件推送 | `wind-alibabacloud-spring-boot-starter` + `wind-message-emial-albabacloud` | 提供 `AlibabaCloudEmailAccountSupplier` Bean 后创建发送器 |
| 同时使用 OSS、邮件 | `wind-alibabacloud-spring-boot-starter` + `wind-oss-alibabacloud` + `wind-message-emial-albabacloud` | 同一个 Starter 按产品依赖分别装配 |
| 其他阿里云 SDK | `wind-alibabacloud-spring-boot-starter` + 对应 SDK | 复用全局凭据，产品客户端由应用创建 |

上表依赖均使用 `com.wind.integration`，外部云产品 SDK 使用其官方坐标。邮件 artifactId 沿用现有的 `emial-albabacloud` 拼写。当前内置 OSS 和邮件产品自动装配，其他产品需由应用装配客户端，不能通过增加一个配置后缀自动生成。

同时使用 OSS、邮件的依赖：

```xml
<dependency>
    <groupId>com.wind.integration</groupId>
    <artifactId>wind-alibabacloud-spring-boot-starter</artifactId>
</dependency>
<dependency>
    <groupId>com.wind.integration</groupId>
    <artifactId>wind-oss-alibabacloud</artifactId>
</dependency>
<dependency>
    <groupId>com.wind.integration</groupId>
    <artifactId>wind-message-emial-albabacloud</artifactId>
</dependency>
```

仅使用一个产品时，只保留公共 Starter 和该产品模块。两个产品在 Starter 中均为可选依赖，应用需显式引入；无需再单独声明本凭据模块。原 `wind-oss-spring-boot-starter` 已退役，OSS 自动装配统一由公共 Starter 提供。

### OSS、DM 的配置位置

| 配置前缀 | 用途 | 对应 Bean |
| --- | --- | --- |
| `wind.alibaba.credentials` | 可选的全局默认身份，不包含产品 endpoint | `alibabaCloudDefaultCredential`：官方 `com.aliyun.credentials.Client` |
| `wind.oss.alibaba` | 阿里云 OSS endpoint 与可选独立身份 | `windAlibabaCloudOssProperties`、`alibabaCloudOssClient` |
| `wind.message.email.alibaba` | 邮件 endpoint 与可选独立身份 | `windAlibabaCloudEmailProperties`、`alibabaCloudEmailMessageSender` |
| `wind.oss.default-bucket-name` | OSS 通用默认 Bucket 名称，与认证无关 | 非空时创建 `defaultWindBucketTemplate` |

OSS、邮件的阿里云产品配置对象均为 `AlibabaCloudProductProperties`，按名称或 `@Qualifier` 注入。配置前缀由各自动配置维护；Java 类名和 Bean 名仍使用 `AlibabaCloud`，不随属性前缀调整。两个客户端的业务类型分别为 `com.wind.integration.oss.WindOssClient` 和 `com.wind.integration.message.email.alibabacloud.AlibabaCloudEmailMessageSender`。中间试用的 `wind.alibaba.cloud.*`、`spring.alibaba.cloud.*` 不提供兼容别名，迁移映射见[迁移指南](../wind-alibabacloud-spring-boot-starter/MIGRATION.md)。

公共 `application.yml` 放业务端点、可选默认 Bucket 和发件账号；只保留实际使用的产品：

```yaml
wind:
  oss:
    alibaba:
      endpoint: ${OSS_ENDPOINT}
    default-bucket-name: ${OSS_BUCKET:}
  message:
    email:
      alibaba:
        endpoint: ${MAIL_ENDPOINT}
app:
  mail:
    sender: ${MAIL_FROM_ADDRESS}
```

等价的 `application.properties`：

```properties
wind.oss.alibaba.endpoint=${OSS_ENDPOINT}
wind.oss.default-bucket-name=${OSS_BUCKET:}
wind.message.email.alibaba.endpoint=${MAIL_ENDPOINT}
app.mail.sender=${MAIL_FROM_ADDRESS}
```

`app.mail.sender` 是下方应用示例自定义的发件账号属性，不属于凭据模块。endpoint 沿用实际产品地域的地址；全局身份不提供 endpoint。`wind.oss.default-bucket-name` 沿用原键，用于 OSS 通用默认 Bucket 模板，不属于认证字段；未设置 `OSS_BUCKET` 时示例取空值，不创建模板。

### 第一阶段：统一使用全局 AK/SK

OSS 与邮件共用一组全局 AK/SK，分别读取自己的 endpoint。将这组凭据放入 `application-ak.yml`，产品配置保留上方公共文件中的 endpoint，默认 Bucket 和发件账号保持独立：

```yaml
wind:
  alibaba:
    credentials:
      type: access_key
      access-key: ${CLOUD_ACCESS_KEY_ID}
      secret-key: ${CLOUD_ACCESS_KEY_SECRET}
```

等价的 `application-ak.properties`：

```properties
wind.alibaba.credentials.type=access_key
wind.alibaba.credentials.access-key=${CLOUD_ACCESS_KEY_ID}
wind.alibaba.credentials.secret-key=${CLOUD_ACCESS_KEY_SECRET}
```

例如启用 `SPRING_PROFILES_ACTIVE=prod,ak`，保留原有业务 profile。统一使用的 AK/SK 应能访问目标 OSS 资源及邮件推送操作；从 `wind.oss.alibaba`、`wind.message.email.alibaba` 中移除原局部认证字段，让两个产品继承全局身份。

产品身份仍优先于全局身份。局部任一认证字段已设置就选择局部组，残缺配置直接报错，不从全局补字段。`read-timeout`、`sts-endpoint` 等认证选项也属于局部组；它们是获取凭据的参数，不是产品连接参数。不同 endpoint 不需要配置不同 AK/SK。

### 邮件自动装配需要发件账号

在应用组件扫描范围内加入：

```java
import com.wind.integration.message.email.alibabacloud.AlibabaCloudEmailAccountSupplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class AlibabaCloudMailConfiguration {

    @Bean
    public AlibabaCloudEmailAccountSupplier emailAccount(@Value("${app.mail.sender}") String account) {
        return () -> account;
    }
}
```

账号需是对应邮件产品已配置可用的发信地址。Spring 按 `AlibabaCloudEmailAccountSupplier` 类型装配，Bean 名称可自行定义；普通 `Supplier<String>` Bean 不会触发邮件自动装配。原应用使用动态 Supplier 时，可返回 `oldSupplier::get` 适配该接口，每次发送都会重新取账号。没有该类型 Bean 时不会自动创建邮件发送器；OSS 无此要求。

业务代码直接通过构造器注入 `WindOssClient`、`AlibabaCloudEmailMessageSender` 使用，不需要再自行 new 产品客户端。应用若保留自定义客户端或同名发送器 Bean，自动装配会退让，YAML 不会接管自定义实现的认证逻辑。

### 第二阶段：全局 AK/SK 切换 ACK OIDC

ACK 已启用 RRSA，完成角色信任、产品权限和 ServiceAccount 配置，并向 Pod 注入两个 ARN 与 Token 文件路径后，创建 `application-ack.yml`：

```yaml
wind:
  alibaba:
    credentials:
      type: oidc_role_arn
```

等价的 `application-ack.properties`：

```properties
wind.alibaba.credentials.type=oidc_role_arn
```

将认证 profile 从 `prod,ak` 换为 `prod,ack` 并重启。保留 endpoint、Bucket、发件账号；确认其他配置来源没有留下 `wind.oss.alibaba`、`wind.message.email.alibaba` 的局部认证字段，也没有全局 AK/SK。Spring 会合并配置，不能仅新增全局 type 或把旧字段改为空字符串。

两个 ARN 未显式配置时由 SDK 读取标准环境配置。Token 文件按显式 `oidc-token-file-path` → 真实 `ALIBABA_CLOUD_OIDC_TOKEN_FILE` → `/etc/secrets/alibaba_cloud_credential` 选择；标准 ACK 环境不需要重复填写路径。SDK 自动换取并刷新 STS，无需手工配置 `security-token`。

### 可选：产品独立身份

仅在个性化场景需要覆盖统一身份时，才在产品组中配置认证。例如在当前认证 profile 中追加邮件独立 AK/SK：

```yaml
wind:
  message:
    email:
      alibaba:
        type: access_key
        access-key: ${MAIL_ACCESS_KEY_ID}
        secret-key: ${MAIL_ACCESS_KEY_SECRET}
```

等价的 `.properties` 配置：

```properties
wind.message.email.alibaba.type=access_key
wind.message.email.alibaba.access-key=${MAIL_ACCESS_KEY_ID}
wind.message.email.alibaba.secret-key=${MAIL_ACCESS_KEY_SECRET}
```

这会让邮件优先使用局部身份，OSS 仍继承全局配置；它不是上述两阶段迁移的必需步骤。恢复统一身份时移除邮件整组局部认证字段。独立 OIDC 角色和静态 `sts` 也可显式配置；静态 STS 需完整的临时 AK/SK/Token，且不会自动续期。

### 自定义 SDK、连接配置与使用边界

应用自行创建其他产品 SDK 客户端时，可以在 `@Bean` 方法参数中注入 `@Qualifier("alibabaCloudDefaultCredential") com.aliyun.credentials.Client`，再通过产品 SDK 支持的凭据 Provider 或 `Config.credential` 接口传入；不要取一次三元组后固定到客户端。产品 endpoint 和连接参数由各产品决定。

全局 Client 只在实际使用时创建；显式替换同名 Client Bean 时由应用负责创建策略。普通 Java 的 `Factory.defaults()`、产品核心 `defaults()` 不读取 Spring YAML；其环境优先级与上述产品/全局配置选择是两个不同入口。

KMS 不接入这套 Spring 全局选择，但其无参 `defaults()` 仍会响应 ACK 的 OIDC 环境变量，部署时需核对实际调用链与 KMS 权限。

已有应用的字段映射、源码修改、验收和回退见[两阶段迁移指南](../wind-alibabacloud-spring-boot-starter/MIGRATION.md)。更多连接参数和客户端用法见 [OSS Spring 指南](../wind-oss/alibabacloud/README.md#spring-boot-集成)、[邮件 Spring 指南](../wind-message/email/README.md#spring-boot-集成)以及[公共 Starter](../wind-alibabacloud-spring-boot-starter/README.md)。以下章节介绍不依赖 Spring 自动装配的工厂 API。

## 能力与职责

`AlibabaCloudCredentialsFactory` 负责校验并创建官方凭据客户端；`create(Config)` 是通用入口，定向快捷方法方便指定认证模式，`defaults()` 提供推荐环境策略。SDK 负责临时凭据的获取、缓存与刷新。产品模块只负责接入凭据和自己的连接参数。

应用需要统一身份时，在装配处创建一次凭据并传给各产品的 `withCredential(...)`。个别资源需要其他身份时，为它单独创建凭据。无需产品属性引用或另一套配置文件：

```java
var sharedCredential = AlibabaCloudCredentialsFactory.defaults();
var archiveCredential = AlibabaCloudCredentialsFactory.accessKey(archiveAk, archiveSk);
// 将 sharedCredential 传给采用统一身份的产品；归档资源单独使用 archiveCredential。
```

只有相同身份和授权范围才复用一个客户端。统一切换发生在应用装配处：调整创建 sharedCredential 的方式，重新装配或重启应用；显式使用其他凭据的资源保持独立。SDK 的自动续期不会切换认证模式。

各产品的缺省行为保持独立：KMS 无参入口使用 OIDC/加密 AK 文件；OSS 和邮件核心 `defaults()` 使用公共推荐环境策略；Spring 产品装配选择产品身份或全局身份。应用可通过产品的显式凭据入口覆盖这些缺省。

## 普通 Java 引入依赖

使用 `wind-integration-dependencies` 管理版本时：

```xml
<dependency>
    <groupId>com.wind.integration</groupId>
    <artifactId>wind-alibabacloud-credentials</artifactId>
</dependency>
```

## 推荐快捷方式

```java
var credential = AlibabaCloudCredentialsFactory.defaults();
```

`defaults()` 是推荐的环境选择方式，固定顺序为：

1. 先检查 OIDC 的角色 ARN 和提供商 ARN；两项完整且非空白则使用 OIDC，部分设置或空白直接报错。Token 路径读取真实环境变量 `ALIBABA_CLOUD_OIDC_TOKEN_FILE`，未设置时使用 `/etc/secrets/alibaba_cloud_credential`；变量已设置但为空白时失败。
2. 两个 OIDC ARN 均未设置时，读取 `ALIBABA_CLOUD_ACCESS_KEY_ID`、`ALIBABA_CLOUD_ACCESS_KEY_SECRET`、`ALIBABA_CLOUD_SECURITY_TOKEN`。
3. AK/SK 完整且 Token 未设置时使用长期 AK；Token 已设置时必须是完整、非空白的 STS 三件套。空 Token 不会降级为长期 AK。
4. 所有来源均未设置时明确报错；不读取文件、不搜索 JVM 系统属性、CLI 配置或 ECS，也不进入 SDK 默认凭据链。

环境 STS 是外部已经取得的一组临时凭据，不自动续期。长期服务推荐 RRSA/OIDC 或显式选择可刷新的角色方式；一旦选择 OIDC，换证失败不会转用环境 AK/STS。

`create(Config)` 始终是最终通用入口。`defaults()` 和各定向快捷方法分别委托现有创建能力，其他入口无需经过 `defaults()`。应用需要固定使用 AK 时直接调用 `accessKey(...)`，不受 OIDC 环境优先级影响。

## RRSA/OIDC

```java
import com.wind.integration.alibaba.credentials.AlibabaCloudCredentialsFactory;

var credential = AlibabaCloudCredentialsFactory.oidcRoleArn();
```

## 常用快捷入口

常见场景无需创建 `Config`。所有快捷方法最终委托 `create(Config)`，共用校验、SDK 缺省解析和构建规则：

| 场景 | 调用 |
| --- | --- |
| 推荐环境策略：OIDC → AK/STS | `defaults()` |
| 长期 AK | `accessKey(accessKeyId, accessKeySecret)` |
| 源 AK 扮演 RAM 角色，自动获取 STS | `ramRoleArn(accessKeyId, accessKeySecret, roleArn)` |
| ECS 实例角色缺省配置 | `ecsRamRole()` |
| 指定 ECS 实例角色 | `ecsRamRole(roleName)` |
| RRSA/OIDC 缺省配置 | `oidcRoleArn()` |
| 显式 OIDC 身份参数 | `oidcRoleArn(roleArn, oidcProviderArn, oidcTokenFilePath)` |
| 外部凭据服务 | `credentialsUri(uri)` |
| 默认加密 AK 文件 | `withCredentialFile(decryptor)` |
| 自定义加密 AK 文件 | `withCredentialFile(path, decryptor)` |

策略、会话时长、源 STS、换证地址、超时、IMDS 降级控制等高级配置仍通过 `create(Config)` 提供。快捷入口不会自动将客户端缓存成全局实例。

## 凭据类型常量

凭据类型统一使用官方 `com.aliyun.credentials.utils.AuthConstant`，可直接传入 `Config.setType(...)`。本工厂当前支持以下六种类型：

| 常量 | 类型值 |
| --- | --- |
| `ACCESS_KEY` | `access_key` |
| `STS` | `sts` |
| `RAM_ROLE_ARN` | `ram_role_arn` |
| `ECS_RAM_ROLE` | `ecs_ram_role` |
| `OIDC_ROLE_ARN` | `oidc_role_arn` |
| `CREDENTIALS_URI` | `credentials_uri` |

## OIDC 配置与高级选项

在应用装配时创建一次并复用。运行环境需要提供：

- `ALIBABA_CLOUD_ROLE_ARN`：要扮演的 RAM 角色 ARN。
- `ALIBABA_CLOUD_OIDC_PROVIDER_ARN`：OIDC 身份提供商 ARN。

OIDC Token 路径按以下顺序选择：显式 `oidcTokenFilePath` → 真实环境变量 `ALIBABA_CLOUD_OIDC_TOKEN_FILE` → `DEFAULT_CREDENTIAL_FILE`（`/etc/secrets/alibaba_cloud_credential`）。只有前一项未设置（null）才检查下一项；显式路径或环境变量为空白时直接失败，不改用缺省文件。SDK 的进程内路径覆盖不会替换已选定的路径。

ACK RRSA 已提供两个 ARN 环境变量和 Token 文件路径时，直接调用 `oidcRoleArn()` 即可，不需要将路径再映射一次。运行平台负责挂载并轮换原始 OIDC Token 文件；显式路径仍可覆盖 ACK 环境变量。

指定 OIDC 参数时可直接使用快捷方法：

```java
var credential = AlibabaCloudCredentialsFactory.oidcRoleArn(
        roleArn, oidcProviderArn, tokenFilePath);
```

需要调整会话或连接参数时使用通用入口。角色 ARN、OIDC 提供商 ARN 及凭据 URI 为 `null` 时，缺省解析交给 SDK，包含其进程内覆盖值和对应环境变量。OIDC 文件路径由工厂按上述顺序解析后填入配置副本，输入配置保持不变：

```java
import com.aliyun.credentials.models.Config;
import com.aliyun.credentials.utils.AuthConstant;
import com.wind.integration.alibaba.credentials.AlibabaCloudCredentialsFactory;

var credential = AlibabaCloudCredentialsFactory.create(new Config()
        .setType(AuthConstant.OIDC_ROLE_ARN)
        .setRoleArn(roleArn)
        .setOidcProviderArn(oidcProviderArn)
        .setOidcTokenFilePath(tokenFilePath)
        .setRoleSessionExpiration(3600)
        .setConnectTimeout(3000));
```

创建客户端时不读取 Token 文件、不调用 STS。首次获取凭据时，由 SDK 读取 OIDC Token，调用 `AssumeRoleWithOIDC`，并在后续使用中缓存和刷新临时凭据。Token 文件由运行平台轮换。

OIDC 落到 `DEFAULT_CREDENTIAL_FILE` 时，该文件应存放原始 Token；AK 文件模式的同一路径则存放需要解密的 AK/SK 密文，两种内容不能混用。OIDC 不调用 AK 文件解密回调，也不会在换证失败后改用文件 AK。

## 加密 AK 文件

```java
var credential = AlibabaCloudCredentialsFactory.withCredentialFile(decryptor::decrypt);
// 自定义路径时：
var custom = AlibabaCloudCredentialsFactory.withCredentialFile(
        Path.of("/path/to/encrypted-credential"), decryptor::decrypt);
```

默认路径保持 `/etc/secrets/alibaba_cloud_credential`。工厂按 UTF-8 读取文件，密文整体 `trim()` 后交给回调，解密结果的前两条非空白行分别为 AK 和 SK。保留行中的原始字符，额外行忽略，不作为 STS Token；这与原 KMS 文件格式一致。

解密方式由应用决定。KMS 默认入口仍使用原 `WindKmsClientCredentialsDecryptor` SPI，将其方法引用传入本工厂；公共模块不反向依赖 KMS。明文文件仅在调用方显式传入 `UnaryOperator.identity()` 时使用，不会在解密失败后自动按明文尝试。

文件只在创建客户端时读取一次，返回静态 AK 客户端。读取失败抛出保留 I/O 原因的 `BaseException`；缺失参数或错误内容也通过统一断言报告；解密回调异常原样传播。

## 普通 Java 配置

`AlibabaCloudCredentialsProperties` 是不依赖 Spring 绑定的认证配置，全部字段默认 `null`。`AlibabaCloudProductProperties` 继承认证字段并增加 `endpoint`；端点由产品适配器使用，公共工厂不创建产品客户端。

```java
var properties = new AlibabaCloudCredentialsProperties();
properties.setAccessKey(accessKeyId);
properties.setSecretKey(accessKeySecret);
var credential = AlibabaCloudCredentialsFactory.fromProperties(properties);
```

`fromProperties` 将完整配置映射为官方 `Config` 后调用 `create`，不修改输入，不读取应用全局配置，也不逐字段拼接其他身份。显式 `type` 原样使用；仅当 `type == null` 时，有 `securityToken` 则选择 `sts`，否则存在 AK/SK 字段则选择 `access_key`。其他模式必须指定官方类型。空字符串也属于已配置值，空白类型或残缺 AK/STS 直接失败，不回退到环境身份。

角色、OIDC、URI、策略、会话时长、超时和 IMDS 控制参数均可通过普通配置传入。会话时长单位为秒，连接与读取超时单位为毫秒；未配置值沿用官方 SDK 和本模块的缺省规则。OIDC 路径采用显式值、ACK 环境变量、项目缺省文件的优先级，空白值失败。

Spring 应用建议把 AK、STS、ACK OIDC 的整组认证分别放入不同 profile，公共配置保留产品 endpoint 和可选默认 Bucket。只修改 `type` 不会清除 Spring 从其他配置文件继承的 AK/SK，应切换互斥的认证 profile 并重启；配置示例见 [Spring Starter](../wind-alibabacloud-spring-boot-starter/README.md)。

`hasCredentials()` 仅判断认证字段是否有任意非 null 值，空白及 `false` 也算已配置，供装配方防止残缺配置被其他身份覆盖；它不代表配置已经有效。仅设置产品 `endpoint` 不构成认证配置。配置对象的 `toString()` 不输出密钥、Token、策略或角色信息；认证父类只输出 `type`，产品子类额外输出端点。

## 显式来源及契约

`create(Config)` 必须指定类型，拒绝空配置、`default`、未知类型和冲突的身份参数，不隐式搜索默认凭据链或回退到其他身份。

本模块的显式配置及推荐环境策略校验失败统一抛出 `BaseException`；SDK 缺省配置解析、参数校验及取证异常保留原类型。显式 OIDC 配置缺失或推荐策略检测到残缺 OIDC 时均失败，不改用环境 AK。

`policy` 只允许用于 `ram_role_arn`、`oidc_role_arn`，`externalId` 只允许用于 `ram_role_arn`。其他模式下传入这些参数会报错，避免 SDK 静默忽略调用方期望的授权约束。

当前使用 `credentials-java 1.0.3`。该版本不消费 `Config.proxy`、`host`、`enableIMDSv2`，工厂拒绝显式设置这些字段；角色换证地址使用 `STSEndpoint`，禁止 IMDSv1 降级使用 `disableIMDSv1=true`。`metadataTokenDuration` 不在工厂中额外校验，交给 SDK 处理；当前 SDK 仍使用固定的 `21600` 秒，自定义该参数不会改变其行为。SDK 升级时需重新核对这些限制；`roleSessionExpiration` 的单位为秒。

未显式设置 `STSEndpoint` 时，SDK 1.0.3 可使用 `ALIBABA_CLOUD_STS_REGION` 和 `ALIBABA_CLOUD_VPC_ENDPOINT_ENABLED` 构造区域端点，例如区域为 `cn-hangzhou`、VPC 开关为 `true` 时使用 `sts-vpc.cn-hangzhou.aliyuncs.com`。它不会直接读取 `ALIBABA_CLOUD_STS_ENDPOINT`；需要自定义端点环境变量时，在 Spring 配置中显式映射已有属性 `sts-endpoint: ${ALIBABA_CLOUD_STS_ENDPOINT}`，或在 Java 中设置 `Config.setSTSEndpoint(...)`。这与 OSS、邮件等产品的 endpoint 独立。

| 类型 | 输入 | 生命周期 |
| --- | --- | --- |
| `access_key` | 长期 AK/SK | 静态凭据，应用负责轮换 |
| `oidc_role_arn` | 角色 ARN、OIDC 提供商 ARN、Token 文件 | SDK 自动获取和刷新 STS |
| `ram_role_arn` | 源 AK/SK、角色 ARN，可选源 SecurityToken | SDK 自动获取和刷新目标角色 STS；源身份须保持有效 |
| `ecs_ram_role` | 可选实例角色名称 | 官方提供者按需获取和刷新，关闭后台任务 |
| `credentials_uri` | 外部凭据 URI | SDK 重新请求该 URI；上游服务须返回有效凭据 |
| `sts` | 外部已获取的临时 AK/SK/Token | 仅接收一组固定临时凭据，不自动续期 |

长期服务优先使用可刷新的角色凭据。`sts` 只保留官方 SDK 的外部临时授权接入能力，不用于替代 RRSA。

ECS 显式使用官方 Builder 的同步刷新模式，避免当前 SDK 的 `Client` 没有关闭入口却持有后台任务。缓存失败策略仍由 SDK 决定：ECS 的 `ALLOW` 策略可能继续返回缓存，不能把 OIDC 的刷新失败传播行为推广到所有模式。

```java
var credential = AlibabaCloudCredentialsFactory.accessKey(accessKeyId, accessKeySecret);
```

工厂不修改输入配置。创建后修改原 Config 不会切换客户端身份；需要更换身份时由应用重新装配。必需字段和来源混配在创建时校验，ARN 合法性、信任策略、资源权限和网络可达性在实际调用时验证。初始化成功不表示 STS 或云产品鉴权成功。

## 多模块共享

- `findOidcRoleArnFromEnvironment()` 共用 OIDC 三态解析：仅两个 ARN 均未设置时返回 `Optional.empty()`；完整时使用该环境身份，按真实路径环境变量或项目缺省文件创建客户端，残缺时报错。它不读取 AK/STS，不提前读取 Token 文件或换证。
- 公共 `defaults()` 在没有 OIDC 时继续选择环境 AK/STS；KMS 为兼容旧用法，在没有 OIDC 时继续使用原文件。因此 KMS 复用上述 OIDC 查询，不强制经过公共 `defaults()`；文件策略仍由 KMS 负责。
- 同一身份和授权范围可以共享一个 `Client`，避免各模块独立换证；不同角色或权限策略使用独立实例。
- KMS 和邮件的集成层使用 Tea `Config.setCredential(credential)`，产品 endpoint、超时等仍归各模块管理。项目当前 Tea OpenAPI 版本会优先采用直接配置的 AK/SK，接入动态凭据时不能混填这些字段。
- OSS 集成层需适配为 OSS `CredentialsProvider`。每次通过一次 `getCredential()` 取得完整三件套，避免在启动时固定凭据或分别读取三个字段。
- 本模块不创建刷新线程、跨身份缓存、全局注册表，也不向业务代码提供独立的 Token DTO。

## 验证

使用项目要求的 Java 25，在仓库根目录运行：

```bash
mvn -o -pl wind-alibabacloud-credentials -am test \
  -Dmaven.test.skip=false -DskipTests=false -Dtest.ignore.failure=false
```

测试包含推荐策略的 OIDC/AK/STS 顺序、残缺和空白配置不降级、系统属性不参与选择、全未配置失败、来源校验、输入配置保持、身份隔离、无效 SDK 参数、隔离进程中的 OIDC 环境与 SDK 覆盖配置，以及官方 OIDC 提供者的首次换证、缓存、刷新重读 Token 文件和刷新失败传播。只替换外部 HTTP 通信，使用虚构凭据，不调用真实云服务。

AssumeRole 另验证授权参数的 HTTP 透传；ECS 验证无后台线程、按需刷新及 IMDSv2 降级控制；URI 验证请求协议、缓存、刷新及无效响应；文件路径验证真实临时文件及解密回调、旧格式兼容和失败传播。KMS 的 SDK 签名请求验证见其模块说明。真实 RRSA、ECS 元数据、AssumeRole、外部凭据服务及云端权限仍需环境验证。

参考：[阿里云 Java 凭据管理](https://help.aliyun.com/zh/sdk/developer-reference/v2-manage-access-credentials)、[ACK RRSA 方案](https://help.aliyun.com/zh/landing-zone/solution-241)。
