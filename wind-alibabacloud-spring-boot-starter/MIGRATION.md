# 4.1.0 阿里云认证两阶段迁移

适用于 OSS 和邮件推送：第一阶段统一使用 `wind.alibaba.credentials` 下的一组 AK/SK，两个产品分别保留自己的 endpoint；验收通过后，第二阶段将同一全局配置切换为 ACK 的 `oidc_role_arn`。不需要经过静态 `sts` 模式。

本文以已有 Spring Boot 应用的自动装配为主，YAML 示例依赖应用正常引入的 Boot Starter 运行环境（包含 YAML 解析器）。非 Spring 或应用自建客户端见下方入口迁移表；它们不会自动读取 YAML。配置字段与扩展方式见 [使用指南](README.md)。

## 迁移前确认

- 当前源码构建版本为 `4.1.0-SNAPSHOT`，使用 Java 25、Spring Boot 4.1.0；应用应对齐实际发布版本、BOM 和工具链，不能混用旧产品模块与新凭据模块。本文不表示正式版本已发布。
- 统一引入 `wind-alibabacloud-spring-boot-starter`；OSS 另加 `wind-oss-alibabacloud`，邮件另加 `wind-message-emial-albabacloud`，两者都用时三个依赖均保留。产品模块是 Starter 的 optional 依赖，应用需显式声明。原 `wind-oss-spring-boot-starter` 已退役，应替换为公共 Starter 与 OSS 产品模块；邮件 artifactId 沿用现有拼写。
- 记录原配置和 Secret 引用，确定统一使用的 AK/SK，并核对它具备目标 OSS 资源和邮件推送权限。endpoint、Bucket、发件账号和连接设置保持各产品原值。
- 盘点应用中的自定义 `WindOssClient`、邮件发送器、官方 SDK Client、`alibabaCloudDefaultCredential` 及同名产品 Properties Bean。这些入口由应用掌握装配权，新增 YAML 不会自动接管。
- 保存旧构建产物、配置版本和 Secret 引用。两个旧 Properties 类及其构造入口已删除，下游需要修改源码并重新编译；旧配置文件不能单独作为新版本的回退方案。

## 第一步：将 AK/SK 统一到全局配置

### 1. 对照原配置迁移字段

| 原入口 | 新入口 | 操作 |
| --- | --- | --- |
| OSS 或邮件的原 AK | `wind.alibaba.credentials.access-key` | 填入统一选用的 AK，从产品组移除原 AK |
| OSS 或邮件的原 SK | `wind.alibaba.credentials.secret-key` | 引用该 AK 对应的 Secret，从产品组移除原 SK |
| `wind.oss.alibaba.endpoint` | 原键不变 | 保持原 endpoint，包括内外网选择 |
| `wind.oss.default-bucket-name` | 原键不变 | 保持原 Bucket；它属于 OSS 通用配置，未使用默认模板时可省略 |
| 邮件 `AlibabaCloudEmailProperties.endpoint` | `wind.message.email.alibaba.endpoint` | 原邮件类没有内置 Spring 前缀，先查应用自己的绑定或手工装配 |

原 OSS endpoint 和默认 Bucket 键继续使用。产品局部 AK/SK 的配置能力仍保留，但本次统一方案应移除它们及其他局部认证字段，让两个产品继承全局配置；另删除原 `wind.oss.alibaba.enabled`，它不作为新装配的开关。代码仍需迁移已删除的两个专用 Properties 类及旧构造入口。密钥继续来自 Secret 或配置中心，不写入仓库。

如果应用已经试用过中间版本的产品配置树，按下表移动字段；当前不读取 `wind.alibaba.cloud.*` 或 `spring.alibaba.cloud.*`，不提供兼容别名：

| 中间试用入口 | 当前入口 |
| --- | --- |
| `wind.alibaba.cloud.credentials.*` / `spring.alibaba.cloud.credentials.*` | `wind.alibaba.credentials.*` |
| `wind.alibaba.cloud.oss.*` / `spring.alibaba.cloud.oss.*` | `wind.oss.alibaba.*`，默认 Bucket 按下一行处理 |
| `wind.alibaba.cloud.oss.default-bucket-name` / `spring.alibaba.cloud.oss.default-bucket-name` | `wind.oss.default-bucket-name` |
| `wind.alibaba.cloud.dm.*` / `spring.alibaba.cloud.dm.*` | `wind.message.email.alibaba.*` |

上表是配置能力的位置映射；执行本次统一方案时，产品组中的 AK/SK 应归并到全局凭据组，不能按原样保留局部身份覆盖。

### 2. 分离业务端点与身份配置

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

等价的 `application.properties`，两种格式选择一种：

```properties
wind.oss.alibaba.endpoint=${OSS_ENDPOINT}
wind.oss.default-bucket-name=${OSS_BUCKET:}
wind.message.email.alibaba.endpoint=${MAIL_ENDPOINT}
```

`OSS_BUCKET` 未设置时默认空值，不创建默认 `BucketOperationsTemplate`。仅试用过中间配置树的应用需要移除其对应旧键，不保留双份配置。

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

从所有实际生效的配置来源移除 `wind.oss.alibaba`、`wind.message.email.alibaba` 下的整组认证字段，只保留各自 endpoint。默认 Bucket 和发件账号保持各自配置。局部身份会优先于全局身份，因此残留产品 AK/SK 会让该产品继续使用局部身份。

保留应用现有业务 profile，只增加认证 profile。例如原来是 `SPRING_PROFILES_ACTIVE=prod`，第一阶段使用 `prod,ak`，不要丢掉 `prod`。

### 3. 迁移代码装配入口

| 旧用法 | 4.1.0 用法 |
| --- | --- |
| `AlibabaCloudOssProperties` / `AlibabaCloudEmailProperties` | `AlibabaCloudProductProperties`；Spring 注入时按产品 Bean 名称限定 |
| 旧 OSS Properties 的 URL、默认连接配置方法 | `AlibabaCloudOssClient` 同名工具方法 |
| `new AlibabaCloudOssClient(ossClient)` | `defaults(...)` 或 `withCredential(...)` 工厂入口 |
| `new AlibabaCloudEmailMessageSender(sendAccount, properties)` | `AlibabaCloudEmailMessageSender.fromProperties(properties, () -> sendAccount)` |
| `new AlibabaCloudEmailMessageSender(accountSupplier, properties)` | `AlibabaCloudEmailMessageSender.fromProperties(properties, accountSupplier)` |

Spring 公共配置 Bean 名称分别为 `windAlibabaCloudOssProperties`、`windAlibabaCloudEmailProperties`，按 `@Qualifier` 注入，避免多个产品共用 Java 类型时产生歧义。

希望第二阶段由配置驱动的标准应用，优先使用 Starter 自动装配。邮件需提供 `AlibabaCloudEmailAccountSupplier` 类型的 Bean，名称不限，账号沿用原值或原动态来源：

```java
import com.wind.integration.message.email.alibabacloud.AlibabaCloudEmailAccountSupplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class MailAccountConfiguration {

    @Bean
    public AlibabaCloudEmailAccountSupplier emailAccount(@Value("${app.mail.sender}") String account) {
        return () -> account;
    }
}
```

配置类放在组件扫描范围内；`app.mail.sender` 是应用自有配置示例，不是公共模块新增的属性。原来是动态 `Supplier<String>` 时，返回 `oldSupplier::get` 适配新接口；仅声明普通 Supplier Bean 不再触发自动装配。普通 Java 的发送器工厂和 SDK 注入构造仍接受 `Supplier<String>`。

邮件应用仅加 YAML 不会生成发送器；需要同时有邮件依赖、公共 Starter 和上述类型的账号提供者。采用自动装配时，移除被替代的旧发送器 Bean；若保留自定义发送器，新自动装配会退让。OSS 自定义 `WindOssClient` 同理。

需要保留自定义连接参数或业务扩展时，可以继续自建 Bean，但应让装配读取公共产品配置或共享的官方凭据 Client，再调用 `withCredential(...)`。若直接写死 `Factory.accessKey(...)` 或把 AK/SK 填进官方产品 SDK 的 Config，第二阶段只改 YAML 不会改变该客户端。

### 4. 第一阶段验收与回退

重新编译并部署使用新配置的应用。在测试环境确认：

1. 应用实际使用预期的 OSS/邮件 Bean；没有已删除类的引用、中间配置树残留或多余的同类客户端。原 OSS 配置前缀继续有效。
2. OSS 和邮件均使用统一选定的全局 AK/SK，同时保留各自 endpoint、Bucket 和发件账号。用原业务操作验证 OSS 上传、读取及必要的删除权限；仅在专用测试对象上操作。邮件向指定测试收件人验证发送和正文格式。
3. 无需新增 OIDC 环境或修改角色权限即可正常工作。只启动成功不算鉴权成功，因为凭据可能在首次请求时才获取。
4. 留存脱敏请求 ID、成功结果和配置版本，不输出 AK/SK、Token 文件内容或完整签名头。

这一阶段失败时，恢复原构建产物、原配置和原 Secret 引用后重新部署；不要把已删除类型的旧代码与新依赖混用。第一阶段稳定后再进入第二阶段。

## 第二步：新 AK/SK 切换到 oidc_role_arn

### 1. 准备 ACK 身份与权限

启用集群 RRSA，为应用配置 ServiceAccount 与 RAM 角色，角色信任条件对应实际 namespace/ServiceAccount，并授予目标 OSS 资源、邮件推送操作所需权限。使用 `ack-pod-identity-webhook` 自动注入，或在 Pod 模板中手工配置等效的变量与投影卷。

部署后应具备以下配置；无需打印 Token 内容：

| 环境变量 | 含义 |
| --- | --- |
| `ALIBABA_CLOUD_ROLE_ARN` | 应用要扮演的 RAM 角色 |
| `ALIBABA_CLOUD_OIDC_PROVIDER_ARN` | 集群对应的 OIDC 身份提供商 |
| `ALIBABA_CLOUD_OIDC_TOKEN_FILE` | 平台持续轮换的 OIDC Token 文件路径，应用进程需可读 |

确保 Pod 能访问 STS 换证端点及产品 endpoint。当前 SDK 可消费 `ALIBABA_CLOUD_STS_REGION` 与 `ALIBABA_CLOUD_VPC_ENDPOINT_ENABLED`；若部署仅提供 `ALIBABA_CLOUD_STS_ENDPOINT`，将其映射到已有 `sts-endpoint` 属性，见 [STS 端点说明](README.md#sts-换证端点)。

**KMS 需单独检查：**它不读取 Spring 的全局或产品凭据配置，但无参 `AlibabaCloudKmsCryptoClient.defaults()` 会在两个 OIDC ARN 环境变量齐备时选择 OIDC。给 Pod 启用 ACK 注入，可能同时改变 KMS 的身份，即使 Spring 的认证 profile 仍是 `ak`。使用 KMS 的应用应先确认该角色有 KMS 权限。

若本阶段 KMS 仍须使用原身份，通过公共 `AlibabaCloudCredentialsFactory.withCredentialFile(decryptor::decrypt)` 或显式 AK 配置创建凭据，再传入 KMS 的 `withCredential(...)`；KMS 自身的包级文件方法不是应用公开入口。还要替换实际调用链：例如将客户端传给 `new KmsTextEncryptor(kmsClient)`。`KmsTextEncryptor.defaults()/withChunk()` 仍走 ServiceLoader 默认 Provider，新增一个 Spring KMS Bean 不会覆盖它。继续使用默认 SPI 时，必须验证新角色的 KMS 权限或恢复原 Pod 注入方式。非 Spring 的 `Factory.defaults()` 同样会优先选择 OIDC。详见 [KMS 使用说明](../wind-kms/alibabacloud/README.md)。

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

第一步已经使用全局 AK/SK，第二步将其整组替换为全局 OIDC。再次检查产品下没有局部认证字段，避免它们遮蔽全局切换；endpoint 和默认 Bucket 保留。除 AK/SK、Token、type 外，`role-arn`、`sts-endpoint`、`read-timeout` 等任一认证选项都会使该产品选择局部身份；需要这些认证选项时放在全局凭据组。

OIDC Token 路径优先级为：显式 `oidc-token-file-path` → 真实 `ALIBABA_CLOUD_OIDC_TOKEN_FILE` → `/etc/secrets/alibaba_cloud_credential`。标准 ACK 注入完整时不用再写路径；需要覆盖时才显式设置。空白或不可读路径会报错，不会换用另一个文件或 AK 身份。

SDK 通过 `AssumeRoleWithOIDC` 获取完整 STS 三元组并刷新，应用不配置 `security-token`。这是重建客户端后的模式切换，SDK 自动续期不等于认证模式热切换。

### 3. 可选：个性化产品覆盖

以下是统一方案之外的可选用法。若确有个性化需求，例如 OSS 使用全局 OIDC、邮件暂用独立 AK，可在 `application-ack.yml` 中明确配置邮件覆盖：

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

### 4. 第二阶段验收与回退

先在测试环境或少量实例完成：

1. 确认实际生效的认证 profile、产品覆盖配置和客户端来源，确保未被应用自定义 Bean 或旧环境属性绕过。
2. 完成首次真实换证及第一阶段同样的 OSS、邮件操作，记录脱敏错误码和请求 ID，核对预期角色与资源范围。
3. 在实际会话刷新及平台 Token 轮换后再次执行操作，确认服务持续可用；本地测试或一次成功请求不能替代该验证。
4. 如应用使用 KMS，验证启动解密和运行期操作。观察 STS/产品权限、网络和超时错误，再扩大部署。

需要回退时，恢复第一阶段验证过的完整 `application-ak.yml` 与 Secret 引用，将认证 profile 切回 `ak` 并重启；删除残留 OIDC 专用字段。单独把 `type` 改回 `access_key` 不够。KMS 或非 Spring `defaults()` 若仍在使用 ACK 环境路由，需要同时恢复对应的显式凭据装配或旧 Pod 身份注入方式。

回退窗口内保持原 AK 可恢复使用；迁移确认稳定后再按实际密钥管理流程停用旧 AK。本文仅给步骤，不执行云资源变更、真实邮件发送或密钥停用。

## 本地交付验证与边界

仓库内可用 Java 25 执行以下命令。已有离线依赖时可加 `-o`；测试开关必须显式开启：

```bash
mvn -pl wind-alibabacloud-spring-boot-starter,wind-kms/alibabacloud -am clean package \
  -Dmaven.test.skip=false -DskipTests=false -Dtest.ignore.failure=false \
  '-Dtest=AlibabaCloud*Tests' \
  -Dsurefire.failIfNoSpecifiedTests=false
```

本地覆盖配置绑定、产品与全局选择、AK/STS 签名、OIDC 换证与刷新、Token 文件更新、失败不换身份、自定义 Bean、生命周期和 KMS 路由；外部 HTTP 由测试替身提供，原真实发信测试禁用。它们不能证明应用已迁移、ACK 信任关系及权限正确、网络可达或生产刷新成功。

官方参考：[ACK RRSA 配置](https://help.aliyun.com/zh/ack/ack-managed-and-ack-dedicated/user-guide/use-rrsa-to-authorize-pods-to-access-different-cloud-services)、[Java 凭据管理](https://help.aliyun.com/zh/sdk/developer-reference/v2-manage-access-credentials)。
