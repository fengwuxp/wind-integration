# 阿里云 Spring 凭据配置

本模块将普通 Properties 交给 `AlibabaCloudCredentialsFactory` 创建凭据。全局配置是可选默认，OSS 和邮件可以分别配置自己的身份；没有具名注册表、额外配置文件或运行时模式切换。

从旧版本升级请先阅读[两阶段迁移指南](MIGRATION.md)：第一阶段统一使用全局 AK/SK，OSS 和邮件分别配置 endpoint；第二阶段将全局身份切换为 ACK OIDC，分别验证并保留回退方案。

KMS 独立配置，不接入本模块，也不受这些 Spring 属性影响。但其无参 `defaults()` 会读取 OIDC 环境变量；给 Pod 启用 ACK 身份注入时，需同时检查 KMS 角色权限或显式凭据装配。

配置按能力归属：全局凭据为 `wind.alibaba.credentials`，阿里云 OSS 为 `wind.oss.alibaba`，阿里云邮件为 `wind.message.email.alibaba`，OSS 通用默认 Bucket 为 `wind.oss.default-bucket-name`。中间试用的 `wind.alibaba.cloud.*`、`spring.alibaba.cloud.*` 不提供兼容别名。

## 依赖与配置入口

统一使用本 Starter，产品模块按需显式引入。以下为同时使用 OSS 和邮件的依赖，版本由应用 BOM 管理：

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

只使用 OSS 时，保留本 Starter 与 `wind-oss-alibabacloud`；只使用邮件时，保留本 Starter 与 `wind-message-emial-albabacloud`。两个产品依赖在 Starter 中均为 optional，不会自动传递到应用。原 `wind-oss-spring-boot-starter` 已退役，OSS 由本模块的 `AlibabaCloudOssAutoConfiguration` 装配；未引入相应产品模块时，其自动配置退让，公共凭据配置仍可使用。

公共 `application.yml` 放产品端点和可选默认 Bucket：

```yaml
wind:
  oss:
    alibaba:
      endpoint: oss-cn-hangzhou.aliyuncs.com
    default-bucket-name: ${OSS_BUCKET:}
  message:
    email:
      alibaba:
        endpoint: dm.us-east-1.aliyuncs.com
```

等价的 `application.properties`，两种格式选择一种：

```properties
wind.oss.alibaba.endpoint=oss-cn-hangzhou.aliyuncs.com
wind.oss.default-bucket-name=${OSS_BUCKET:}
wind.message.email.alibaba.endpoint=dm.us-east-1.aliyuncs.com
```

`wind.oss.default-bucket-name` 沿用原键，属于 OSS 通用配置，不属于阿里云认证字段。示例在 `OSS_BUCKET` 未设置时使用空值，不创建默认 `BucketOperationsTemplate`；需要模板时提供实际 Bucket。

`credentials` 仅包含身份字段，产品 endpoint 不从全局继承。类型值使用 SDK 的 `AuthConstant`，如 `access_key`、`sts`、`oidc_role_arn`。全部字段初值为 null；未写 type 时由 Token 的存在推断 STS，否则由 AK/SK 推断 access_key。空白 Token 仍会报错。

## 第一阶段：统一使用全局 AK/SK

将统一的 AK/SK 放入 `application-ak.yml`；OSS 和邮件共用该身份，但各自使用上方产品 endpoint：

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

启用 `prod,ak` 等包含认证 profile 的组合。移除产品下原有的 AK/SK、Token、type 等局部认证字段，否则局部身份仍会优先，不能达到统一使用全局凭据的效果。统一 AK 需具备两个产品所需权限，endpoint、默认 Bucket 和发件账号仍分别配置。

## 第二阶段：切换全局 ACK OIDC

ACK 部署端需先启用 RRSA，配置角色信任关系和产品权限，为 Pod 指定对应 ServiceAccount，并提供 `ALIBABA_CLOUD_ROLE_ARN`、`ALIBABA_CLOUD_OIDC_PROVIDER_ARN`、`ALIBABA_CLOUD_OIDC_TOKEN_FILE` 及投影 Token 文件。环境就绪后，`application-ack.yml` 的最简认证配置为：

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

其中 `ALIBABA_CLOUD_OIDC_TOKEN_FILE` 是文件路径，文件内容是 OIDC Token。SDK 用它调用 `AssumeRoleWithOIDC` 换取 STS 三元组，缓存并在到期前刷新；投影 Token 文件由平台轮换，无需人工填写或轮换 `security-token`。Pod 还需能够访问 STS 及相应产品端点。

两个 ARN 未显式配置时从 SDK 标准环境配置取得。Token 路径按 `oidc-token-file-path` 显式值 → 真实环境变量 `ALIBABA_CLOUD_OIDC_TOKEN_FILE` → `/etc/secrets/alibaba_cloud_credential` 选择；ACK 的路径无需重复映射。显式路径或环境变量为空白时直接失败，不换用固定文件。需要覆盖 ACK 路径时显式配置 `oidc-token-file-path` 即可。

例如将 `SPRING_PROFILES_ACTIVE=prod,ak` 改为 `prod,ack` 并重启，保留原有业务 profile。认证 profile 不要同时启用；公共配置不要留下 AK/SK。Spring 会合并配置文件，只改 `type` 不会删除另一层配置中的旧字段，残留 AK/SK 与 OIDC 混配会报错。配置切换通过重启或重新装配生效，不是热切换；SDK 自动续期只更新当前身份的临时凭据。

切换全局身份时，检查 `wind.oss.alibaba`、`wind.message.email.alibaba` 下是否仍配置独立的 AK/SK、Token 或 type；这些局部配置会继续优先，需删除后才继承全局 OIDC。应用自行创建的客户端或同名全局凭据 Bean 由应用控制，不随上述属性自动切换。

## STS 换证端点

当前 `credentials-java 1.0.3` 可根据 `ALIBABA_CLOUD_STS_REGION` 与 `ALIBABA_CLOUD_VPC_ENDPOINT_ENABLED` 构造区域 STS 端点，例如 `cn-hangzhou` 和 `true` 对应 `sts-vpc.cn-hangzhou.aliyuncs.com`。SDK 不直接读取 `ALIBABA_CLOUD_STS_ENDPOINT`。如果部署约定了单独的端点变量，应将其映射到已有配置，例如在认证 profile 的 `credentials` 下设置：

```yaml
sts-endpoint: ${ALIBABA_CLOUD_STS_ENDPOINT}
```

显式 `sts-endpoint` 优先于 SDK 的区域端点构造；它只影响换证请求，产品 endpoint 仍独立配置。

官方参考：[ACK RRSA](https://help.aliyun.com/zh/ack/ack-managed-and-ack-dedicated/user-guide/use-rrsa-to-authorize-pods-to-access-different-cloud-services)、[Java 凭据管理](https://help.aliyun.com/zh/sdk/developer-reference/v2-manage-access-credentials)。

## 可选：产品独立身份与选择顺序

上述两阶段使用统一身份；有个性化需求时才增加产品覆盖。例如在 ACK profile 中让邮件单独使用 AK，产品端点仍放公共配置：

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

此时邮件使用独立 AK；没有局部身份的产品使用全局 OIDC。选择时以整组身份为单位，不拼接局部 AK 和全局 SK。任一身份字段已设置，包括空白值，都构成局部选择；残缺配置报错，不回退。

OSS 顺序为：自定义 `WindOssClient` → `wind.oss.alibaba` 身份 → 全局凭据。endpoint 必须由 OSS 产品配置提供，只有 endpoint 时继承全局身份。未配置任何身份时明确失败。

邮件顺序为：自定义发送器 → `wind.message.email.alibaba` 身份 → 全局凭据。endpoint 必须由邮件产品配置提供。普通 Java 的 `fromProperties`、`defaults`、`withCredential` 不读取 Spring 全局属性。

凭据配置中的 `connect-timeout`、`read-timeout` 是换证或凭据服务请求的超时，单位毫秒；它们不是 OSS/邮件产品请求超时。需要自定义产品连接参数时继续使用客户端的显式 SDK 配置入口。

## 可选：静态 STS

静态 STS 不是上述迁移的必经步骤。短期使用外部签发凭据时，在互斥的 `application-sts.yml` 中提供同一次签发的三元组：

```yaml
wind:
  alibaba:
    credentials:
      type: sts
      access-key: ${CLOUD_STS_ACCESS_KEY_ID}
      secret-key: ${CLOUD_STS_ACCESS_KEY_SECRET}
      security-token: ${CLOUD_STS_SECURITY_TOKEN}
```

等价的 `application-sts.properties`：

```properties
wind.alibaba.credentials.type=sts
wind.alibaba.credentials.access-key=${CLOUD_STS_ACCESS_KEY_ID}
wind.alibaba.credentials.secret-key=${CLOUD_STS_ACCESS_KEY_SECRET}
wind.alibaba.credentials.security-token=${CLOUD_STS_SECURITY_TOKEN}
```

SDK 不自动续期静态 STS；外部系统或人工更换三元组后，需要重新装配客户端或重启。

## 邮件发件账号与客户端覆盖

应用提供 `AlibabaCloudEmailAccountSupplier` 类型的 Bean 后，才自动创建邮件发送器。Bean 名称不限，每次发送读取账号；配置类放在组件扫描范围内：

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

`app.mail.sender` 由应用配置为实际发信地址。没有该类型 Bean 时仅绑定配置；普通 `Supplier<String>` 即使沿用以前的 Bean 名称，也不会触发自动装配。已有动态 Supplier 可通过 `oldSupplier::get` 适配该接口。已有 `AlibabaCloudEmailMessageSender` Bean，或名为 `alibabaCloudEmailMessageSender` 的自定义 Bean 时，自动创建退让；其他消息通道的 Sender 不受影响。

全局凭据 Bean 名为 `alibabaCloudDefaultCredential`，采用惰性单例，仅在实际继承全局身份时创建。各产品通过带同名限定符的 `ObjectProvider<Client>` 获取同一实例。应用也可以提供同名的官方凭据 Client Bean；其他名称的 Client 不会压掉全局默认。默认实例不由产品模块关闭。

## OSS 默认 Bucket、自定义客户端与关闭

`wind.oss.default-bucket-name` 未配置或为空白时，不创建 `BucketOperationsTemplate`，不影响 `WindOssClient`；配置非空值时创建模板。该键不参与身份选择。只配置 OSS endpoint 和默认 Bucket 时仍可继承全局身份。

应用提供 `WindOssClient` Bean 时，OSS 自动装配退让，不选择或创建额外凭据来源，也不创建第二个 OSS 客户端。产品属性仍按 Spring 规则绑定，字段类型等绑定错误仍会报错。自定义连接参数与静态 STS 示例见 [OSS 接入指南](../wind-oss/alibabacloud/README.md#spring-boot-集成)。

默认最大连接数为 256，签名版本沿用 OSS SDK 现有默认。Spring 容器关闭时调用 OSS 客户端的 `close()`，释放底层 SDK 连接池；共享凭据客户端不会随产品客户端关闭。应用不要在每次业务请求后关闭这个单例。

## 配置模型与验证

公共模块提供 `AlibabaCloudCredentialsProperties` 和带 endpoint 的 `AlibabaCloudProductProperties`。它们不依赖 Spring Boot 或产品 SDK，配置前缀由各自动配置维护。配置对象的字符串表示不输出 AK/SK、Token 等身份值。

4.1.0 已删除两个产品专属 Properties 类及其兼容入口；原 OSS endpoint 和 `wind.oss.default-bucket-name` 配置继续使用。`wind.oss.alibaba` 的局部 AK/SK 能力仍可用于个性化覆盖，本次统一方案将其移到全局凭据组。邮件原类没有内置 Spring 配置前缀，新自动装配使用 `wind.message.email.alibaba`。程序中使用公共产品类型并按名称注入：OSS 为 `@Qualifier("windAlibabaCloudOssProperties")`，邮件为 `@Qualifier("windAlibabaCloudEmailProperties")`，避免多个产品之间按类型注入产生歧义。Java 类名和 Bean 名保持不变。

上述产品配置 Bean 名由 `AlibabaCloudOssAutoConfiguration.OSS_PROPERTIES_BEAN_NAME`、`AlibabaCloudEmailAutoConfiguration.EMAIL_PROPERTIES_BEAN_NAME` 维护，应用可在 `@Qualifier` 中引用相应常量。

OSS 的 URL 和默认连接配置方法使用 `AlibabaCloudOssClient`，需要自定义连接配置时显式创建客户端 Bean。邮件普通 Java 调用使用 `AlibabaCloudEmailMessageSender.fromProperties(productProperties, accountSupplier)`。

测试保留真实 Spring 绑定和 SDK 签名，只替换网络/Token 文件读取，覆盖全局与局部 AK/SK、STS、残缺拒绝、惰性初始化、自定义 Bean、可选邮件依赖及 OIDC 换证。凭据模块另验证 Token 文件轮换后的 STS 刷新。测试不发送真实邮件或访问真实云资源。

使用 Java 25 在仓库根目录验证自动装配，已有离线依赖时可运行：

```bash
mvn -o -pl wind-alibabacloud-spring-boot-starter -am test \
  '-Dtest=AlibabaCloud*Tests' -Dsurefire.failIfNoSpecifiedTests=false \
  -Dmaven.test.skip=false -DskipTests=false -Dtest.ignore.failure=false
```
