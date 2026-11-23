# 4.1.0 阿里云认证两阶段迁移

先让 OSS 和邮件用原来的 AK/SK 跑通新版本，再把全局身份切到 ACK 的 `oidc_role_arn`。两次部署都沿用各自原来的 endpoint。

以下配置用于 Spring Boot 自动装配。非 Spring 应用和自建客户端按下文的接口对照表修改创建客户端的代码，凭据工厂用法见 [Java 凭据指南](../credentials/README.md)。日常使用参见 [Spring Boot](../README.md#spring-boot)、[OSS](../README.md#oss)和[邮件推送](../README.md#邮件推送)。

## 迁移前确认

- 替换原 `wind-oss-spring-boot-starter`，统一引入 `wind-alibabacloud-spring-boot-starter`。OSS 另加 `wind-oss-alibabacloud`，邮件另加 `wind-message-emial-albabacloud`；同时使用时三个依赖都要声明。产品模块是 Starter 的 optional 依赖，邮件 artifactId 保留现有拼写。
- 对齐 BOM、产品模块和工具链。本分支使用 `4.1.0-SNAPSHOT`、Java 25、Spring Boot 4.1.0。手工分发 JAR 时，升级 core 需一并提供同版本的 `wind-message-api`；OSS、KMS API 坐标和 Java 包名不变。
- 保存旧构建产物、配置版本和 Secret 引用。两个旧 Properties 类及相关构造入口已删除，需要修改应用源码并重新编译。
- 确定统一使用的 AK/SK，核对它的 OSS 资源和邮件推送权限，并记录各产品的 endpoint、Bucket、发件账号及自定义连接设置。
- 盘点自定义 `WindOssClient`、邮件发送器、官方 SDK Client、`alibabaCloudDefaultCredential` 及同名产品 Properties Bean。它们由应用装配，修改 YAML 不会自动替换这些入口。

显式继承旧父 POM 的项目另见[依赖迁移](../../README.md#依赖迁移)。

## 第一步：将 AK/SK 统一到全局配置

### 1. 对照原配置迁移字段

| 原入口 | 新入口 | 操作 |
| --- | --- | --- |
| OSS 或邮件的原 AK | `wind.alibaba.credentials.access-key` | 填入统一选用的 AK，从产品组移除原 AK |
| OSS 或邮件的原 SK | `wind.alibaba.credentials.secret-key` | 引用该 AK 对应的 Secret，从产品组移除原 SK |
| `wind.oss.alibaba.endpoint` | 原键不变 | 保持原 endpoint，包括内外网选择 |
| `wind.oss.default-bucket-name` | 原键不变 | 保持原 Bucket；它属于 OSS 通用配置，未使用默认模板时可省略 |
| 邮件 `AlibabaCloudEmailProperties.endpoint` | `wind.message.email.alibaba.endpoint` | 原邮件类没有内置 Spring 前缀，先查应用自己的绑定或手工装配 |

删除原 `wind.oss.alibaba.enabled`，它不再作为装配开关。密钥继续引用 Secret 或配置中心，不写入仓库。

试用过中间版本的应用还需迁移下列前缀；`wind.alibaba.cloud.*` 和 `spring.alibaba.cloud.*` 不再读取：

| 中间试用入口 | 当前入口 |
| --- | --- |
| `wind.alibaba.cloud.credentials.*` / `spring.alibaba.cloud.credentials.*` | `wind.alibaba.credentials.*` |
| `wind.alibaba.cloud.oss.*` / `spring.alibaba.cloud.oss.*` | `wind.oss.alibaba.*`，默认 Bucket 按下一行处理 |
| `wind.alibaba.cloud.oss.default-bucket-name` / `spring.alibaba.cloud.oss.default-bucket-name` | `wind.oss.default-bucket-name` |
| `wind.alibaba.cloud.dm.*` / `spring.alibaba.cloud.dm.*` | `wind.message.email.alibaba.*` |

本次统一身份时，产品组中的 AK/SK 也要移到全局凭据组。

### 2. 配置产品 endpoint 和全局 AK/SK

公共 `application.yml` 仅保留产品端点和可选默认 Bucket。只配置实际使用的产品，endpoint 替换为原值：

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
```

使用 properties 的应用可写为以下配置。

```properties
wind.oss.alibaba.endpoint=${OSS_ENDPOINT}
wind.oss.default-bucket-name=${OSS_BUCKET:}
wind.message.email.alibaba.endpoint=${MAIL_ENDPOINT}
```

`OSS_BUCKET` 未设置时不创建默认 `BucketOperationsTemplate`。

将统一选用的 AK/SK 放入 `application-ak.yml`：

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

从所有生效的配置来源移除 `wind.oss.alibaba`、`wind.message.email.alibaba` 下的整组认证字段，只保留各自 endpoint。局部身份优先于全局身份，残留产品 AK/SK 会使该产品继续使用原身份。默认 Bucket 和发件账号仍放在各自配置中。

保留应用现有业务 profile，只增加认证 profile。例如原来是 `SPRING_PROFILES_ACTIVE=prod`，第一阶段使用 `prod,ak`，不要丢掉 `prod`。

### 3. 更新创建客户端的代码

| 旧用法 | 4.1.0 用法 |
| --- | --- |
| `AlibabaCloudOssProperties` / `AlibabaCloudEmailProperties` | `AlibabaCloudProductProperties`；Spring 注入时按产品 Bean 名称限定 |
| 旧 OSS Properties 的 URL、默认连接配置方法 | `AlibabaCloudOssClient` 同名工具方法 |
| `new AlibabaCloudOssClient(ossClient)` | `defaults(...)` 或 `withCredential(...)` 工厂入口 |
| `new AlibabaCloudEmailMessageSender(sendAccount, properties)` | `AlibabaCloudEmailMessageSender.fromProperties(properties, () -> sendAccount)` |
| `new AlibabaCloudEmailMessageSender(accountSupplier, properties)` | `AlibabaCloudEmailMessageSender.fromProperties(properties, accountSupplier)` |

Spring 公共配置 Bean 名称分别为 `windAlibabaCloudOssProperties`、`windAlibabaCloudEmailProperties`，按 `@Qualifier` 注入，避免多个产品共用 Java 类型时产生歧义。

邮件自动装配按 `AlibabaCloudEmailAccountSupplier` 类型查找发件账号。已有动态 `Supplier<String>` 可用 `oldSupplier::get` 适配，继续返回原来的发信地址。普通 Java 构造器仍接受 `Supplier<String>`。[Spring 发件账号示例](../README.md#spring-boot)

迁入自动装配后，移除被替代的旧发送器 Bean；保留自定义发送器或 `WindOssClient` 时，自动装配会退让。

保留自建 Bean 时，让它读取公共产品配置或共享的官方凭据 Client，再调用 `withCredential(...)`。若固定调用 `Factory.accessKey(...)` 或直接把 AK/SK 填进产品 SDK Config，第二阶段修改 YAML 不会改变该客户端的身份。

### 4. 验证原账号并准备回退

重新编译应用，在测试环境用原来的账号验证以下操作。

1. 确认实际使用的 OSS/邮件 Bean 及凭据来源，两个产品均使用选定的全局 AK/SK，endpoint、Bucket 和发件账号正确。
2. 用专用测试对象验证 OSS 上传、读取及必要的删除操作；向指定测试收件人发送邮件，核对正文格式。这一步仍使用 AK/SK，无需新增 OIDC 环境或角色权限。
3. 留存脱敏请求 ID、结果和配置版本，不输出 AK/SK、Token 内容或完整签名头。凭据可能在首次请求时才获取，须完成实际操作后再验收。

失败时，恢复原构建产物、原配置和原 Secret 引用后重新部署。旧代码引用了已删除类型时，必须一起恢复旧依赖。第一阶段稳定后再进入第二阶段。

## 第二步：新 AK/SK 切换到 oidc_role_arn

### 1. 准备 ACK 身份与权限

启用集群 RRSA，为应用配置 ServiceAccount 与 RAM 角色，角色信任条件对应实际 namespace/ServiceAccount，并授予目标 OSS 资源、邮件推送操作所需权限。使用 `ack-pod-identity-webhook` 自动注入，或在 Pod 模板中手工配置等效的变量与投影卷。

在 Pod 中检查以下三个变量和挂载的文件路径。

| 环境变量 | 含义 |
| --- | --- |
| `ALIBABA_CLOUD_ROLE_ARN` | 应用要扮演的 RAM 角色 |
| `ALIBABA_CLOUD_OIDC_PROVIDER_ARN` | 集群对应的 OIDC 身份提供商 |
| `ALIBABA_CLOUD_OIDC_TOKEN_FILE` | 平台持续轮换的 OIDC Token 文件路径，应用进程需可读 |

确保 Pod 能访问 STS 换证端点及产品 endpoint。SDK 可读取 `ALIBABA_CLOUD_STS_REGION` 与 `ALIBABA_CLOUD_VPC_ENDPOINT_ENABLED`；若部署提供的是 `ALIBABA_CLOUD_STS_ENDPOINT`，需映射到 `wind.alibaba.credentials.sts-endpoint`，SDK 不直接读取该变量。[STS 端点配置](../credentials/README.md#oidc-token-文件与-sts-端点)

KMS 不读取 Spring 的全局或产品凭据配置。无参 `AlibabaCloudKmsCryptoClient.defaults()` 在两个 OIDC ARN 环境变量齐备时选择 OIDC，因此启用 ACK 注入可能同时改变 KMS 身份，即使 Spring 仍启用 `ak` profile。使用 KMS 的应用应先确认角色权限。

若 KMS 暂时保留原身份，通过 `AlibabaCloudCredentialsFactory.withCredentialFile(decryptor::decrypt)` 或显式 AK 配置创建凭据，再传入 KMS 的 `withCredential(...)`。同时替换实际调用链，例如使用 `new KmsTextEncryptor(kmsClient)`。`KmsTextEncryptor.defaults()/withChunk()` 仍走 ServiceLoader 默认 Provider，新增 Spring KMS Bean 不会覆盖它；继续使用默认 SPI 就需验证角色的 KMS 权限。非 Spring 的 `Factory.defaults()` 也优先选择 OIDC。详见 [KMS](../README.md#kms)和 [Java 凭据工厂](../credentials/README.md)。

### 2. 替换认证 profile

公共 `application.yml` 的 endpoint、Bucket、发件账号不变。为需要共享同一角色的产品创建 `application-ack.yml`：

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

将 `SPRING_PROFILES_ACTIVE=prod,ak` 改为 `prod,ack`，重新部署 Pod。不要同时激活 `ak` 和 `ack`。

Spring 会合并各配置来源：高优先级配置中不写某字段，并不等于删除低优先级字段。检查公共文件、业务 profile、环境属性、启动参数和配置中心，确保选中的认证组不再有 `access-key`、`secret-key`、`security-token`。填空字符串也不等于删除。

产品下也不能残留局部认证字段。除 AK/SK、Token、type 外，`role-arn`、`sts-endpoint`、`read-timeout` 等认证选项都会使该产品选择局部身份；共用身份时将这些选项放在全局凭据组。

OIDC Token 路径优先级为：显式 `oidc-token-file-path` → 真实 `ALIBABA_CLOUD_OIDC_TOKEN_FILE` → `/etc/secrets/alibaba_cloud_credential`。标准 ACK 注入完整时不用再写路径；需要覆盖时才显式设置。空白或不可读路径会报错，不会换用另一个文件或 AK 身份。

SDK 通过 `AssumeRoleWithOIDC` 获取并刷新 STS 凭据，应用无需配置 `security-token`。认证模式切换需要重启，运行期间只自动续期凭据。

### 3. 邮件需要保留独立 AK 时

若 OSS 使用全局 OIDC、邮件暂用独立 AK，在 `application-ack.yml` 中配置邮件身份：

```yaml
wind:
  alibaba:
    credentials:
      type: oidc_role_arn
  message:
    email:
      alibaba:
        type: access_key
        access-key: ${MAIL_ACCESS_KEY_ID}
        secret-key: ${MAIL_ACCESS_KEY_SECRET}
```

等价的 `application-ack.properties`：

```properties
wind.alibaba.credentials.type=oidc_role_arn
wind.message.email.alibaba.type=access_key
wind.message.email.alibaba.access-key=${MAIL_ACCESS_KEY_ID}
wind.message.email.alibaba.secret-key=${MAIL_ACCESS_KEY_SECRET}
```

邮件之后也迁 OIDC 时，移除其整组局部身份即可继承全局。若产品需要不同角色，将完整 `oidc_role_arn` 身份配置放在该产品下并显式指定 `role-arn`；每个角色都需信任该 ServiceAccount 并有对应权限。不同 ServiceAccount 隔离通常通过不同工作负载实现，不能假设同一个 Pod 会自动注入多套角色配置。

### 4. 验证角色权限和凭据刷新

先在测试环境或少量实例上验证角色。

1. 确认生效的认证 profile、产品覆盖配置和客户端来源，排除自定义 Bean 或旧环境属性继续使用 AK 的情况。
2. 完成首次换证，重复第一阶段的 OSS、邮件操作，核对角色和资源范围；遇到错误时保留脱敏错误码和请求 ID。
3. 在 STS 会话刷新及平台 OIDC Token 轮换后再次操作，确认服务持续可用。
4. 使用 KMS 时，一并验证启动解密和运行期操作。确认权限、网络和超时无误后再扩大部署。

需要回退时，恢复第一阶段验证过的完整 `application-ak.yml` 与 Secret 引用，将认证 profile 切回 `ak` 并重启；删除残留 OIDC 专用字段。单独把 `type` 改回 `access_key` 不够。KMS 或非 Spring `defaults()` 若仍在使用 ACK 环境路由，需要同时恢复对应的显式凭据装配或旧 Pod 身份注入方式。

回退窗口内保留原 AK，迁移稳定后再停用。

官方参考：[ACK RRSA 配置](https://help.aliyun.com/zh/ack/ack-managed-and-ack-dedicated/user-guide/use-rrsa-to-authorize-pods-to-access-different-cloud-services)、[Java 凭据管理](https://help.aliyun.com/zh/sdk/developer-reference/v2-manage-access-credentials)。
