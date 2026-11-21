# 阿里云邮件凭据装配

已有应用按[两阶段迁移指南](../../wind-alibabacloud-spring-boot-starter/MIGRATION.md)升级：先保持原账号与 AK/SK 身份迁移装配，再切换 OIDC。

## Spring Boot 集成

下方 YAML 与 `.properties` 为等价写法，选择一种使用；同一组配置不要在两种文件中重复维护。使用 `.properties` 时，认证 profile 文件对应为 `application-ak.properties`、`application-ack.properties`。

同时引入公共 Starter 和邮件模块；邮件是公共 Starter 的可选依赖，需显式声明。以下版本对应当前源码，应用应替换为实际可用且一致的发布版本；已由项目 BOM 管理时可省略版本：

```xml
<dependency>
    <groupId>com.wind.integration</groupId>
    <artifactId>wind-alibabacloud-spring-boot-starter</artifactId>
    <version>4.1.0-SNAPSHOT</version>
</dependency>
<dependency>
    <groupId>com.wind.integration</groupId>
    <artifactId>wind-message-emial-albabacloud</artifactId>
    <version>4.1.0-SNAPSHOT</version>
</dependency>
```

在 `application.yml` 中配置邮件自己的身份、实际邮件服务 endpoint 和发件账号，环境变量由应用部署提供。`app.mail.sender` 是本示例的应用配置，不是公共模块的内置属性：

```yaml
wind:
  message:
    email:
      alibaba:
        endpoint: ${MAIL_ENDPOINT}
        type: access_key
        access-key: ${MAIL_ACCESS_KEY_ID}
        secret-key: ${MAIL_ACCESS_KEY_SECRET}
app:
  mail:
    sender: ${MAIL_SENDER}
```

等价的 `application.properties`：

```properties
wind.message.email.alibaba.endpoint=${MAIL_ENDPOINT}
wind.message.email.alibaba.type=access_key
wind.message.email.alibaba.access-key=${MAIL_ACCESS_KEY_ID}
wind.message.email.alibaba.secret-key=${MAIL_ACCESS_KEY_SECRET}
app.mail.sender=${MAIL_SENDER}
```

添加以下配置类，放入应用组件扫描范围。Spring 按 `AlibabaCloudEmailAccountSupplier` 类型装配，Bean 名称不限：

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

业务服务通过构造器注入 `com.wind.integration.message.email.alibabacloud.AlibabaCloudEmailMessageSender`，调用其 `sendMessage(message)`；自动创建的 Bean 名为 `alibabaCloudEmailMessageSender`，无需再次手动创建发送器。每次发送都会读取账号提供者；已有动态 `Supplier<String>` 可通过 `oldSupplier::get` 适配该接口。缺少 `AlibabaCloudEmailAccountSupplier` Bean 时不会创建发送器，普通 Supplier Bean 即使使用旧约定名称也不会触发自动装配。

ACK 已配置 RRSA、角色权限并注入两个 ARN 和 `ALIBABA_CLOUD_OIDC_TOKEN_FILE` 后，仅切换邮件时，将上述 `wind.message.email.alibaba` 认证组替换为下面配置；`app.mail.sender` 保持原值：

```yaml
wind:
  message:
    email:
      alibaba:
        endpoint: ${MAIL_ENDPOINT}
        type: oidc_role_arn
```

等价的 `application.properties`：

```properties
wind.message.email.alibaba.endpoint=${MAIL_ENDPOINT}
wind.message.email.alibaba.type=oidc_role_arn
```

必须从实际生效的全部配置来源移除原邮件 AK/SK、静态 SecurityToken，不能只覆盖 `type`。另一种方式是按[迁移指南](../../wind-alibabacloud-spring-boot-starter/MIGRATION.md)使用互斥认证 profile。重启或重新装配后生效；SDK 负责 OIDC 换证与 STS 刷新。该配置只选择邮件身份，OSS 和 KMS 的配置保持独立；同一 Pod 的 OIDC 环境注入对 KMS 默认路由的影响见迁移指南。

应用已经提供 `AlibabaCloudEmailMessageSender` Bean，或名为 `alibabaCloudEmailMessageSender` 的 Bean 时，自动装配会退让；仅修改 YAML 不会替换自建客户端。需要多个产品共享身份时，再使用 `wind.alibaba.credentials`，并移除邮件产品下整组认证字段；规则见[公共 Spring 配置](../../wind-alibabacloud-spring-boot-starter/README.md)。

## 公共产品配置

非 Spring 或需要手工装配时，使用 `AlibabaCloudProductProperties` 与 `fromProperties`。4.1.0 已删除邮件专用的 `AlibabaCloudEmailProperties` 及其两个构造器，不提供旧类型兼容。已有官方邮件 SDK 客户端仍可通过 `(dm.Client, Supplier<String>)` 构造器注入。

```java
import com.wind.integration.alibaba.credentials.AlibabaCloudProductProperties;
import com.wind.integration.message.email.alibabacloud.AlibabaCloudEmailMessageSender;

var properties = new AlibabaCloudProductProperties();
properties.setEndpoint("dm.us-east-1.aliyuncs.com");
properties.setAccessKey(mailAccessKeyId);
properties.setSecretKey(mailAccessKeySecret);
var sender = AlibabaCloudEmailMessageSender.fromProperties(
        properties, () -> "sender@example.com");
```

认证字段由公共工厂 `fromProperties` 校验并创建凭据：AK/SK 可推断 `access_key`，增加 SecurityToken 可推断静态 `sts`，其他模式显式设置 `type`。发送器只使用传入的认证和 endpoint，不修改配置，也不隐式继承 Spring 全局身份；只有端点或残缺身份时会报错。发件账号在每次发送时读取，构造时不调用 Supplier。

从旧 API 迁移时，将配置对象替换为 `AlibabaCloudProductProperties`，并将 `new AlibabaCloudEmailMessageSender(sendAccount, properties)` 改为 `fromProperties(properties, () -> sendAccount)`。AK/SK 字段与端点含义保持；已有显式 SDK 客户端或共享凭据使用下方对应入口。

后续改为 ACK 的 OIDC 换证时，整组替换认证字段：设置 `type=oidc_role_arn`、`roleArn`、`oidcProviderArn`，清除原 AK/SK 和静态 SecurityToken。Token 路径优先采用显式 `oidcTokenFilePath`，其次采用 ACK 注入的真实环境变量 `ALIBABA_CLOUD_OIDC_TOKEN_FILE`，两者均未设置时才使用 `/etc/secrets/alibaba_cloud_credential`；已设置的空白路径会报错，不回退。SDK 的进程内路径覆盖值不参与此选择。凭据 SDK 负责换取、缓存和刷新 STS，应用重启或重新装配后使用新身份。Spring 全局配置及产品覆盖示例见 Starter 文档。

## 推荐快捷入口

```java
var sender = AlibabaCloudEmailMessageSender.defaults(
        "dm.us-east-1.aliyuncs.com", () -> "sender@example.com");
```

此入口委托 `AlibabaCloudCredentialsFactory.defaults()`：OIDC 两个 ARN 优先，Token 采用 `ALIBABA_CLOUD_OIDC_TOKEN_FILE` 指定的文件，未设置该变量时使用 `/etc/secrets/alibaba_cloud_credential`；没有 OIDC 配置时选择环境 AK/STS。配置残缺、已设置路径为空白或全部未配置时失败。

需要显式选择认证模式或设置连接参数时，使用完整入口：

```java
import com.aliyun.credentials.utils.AuthConstant;

var credentialConfig = new com.aliyun.credentials.models.Config()
        .setType(AuthConstant.RAM_ROLE_ARN)
        .setAccessKeyId(sourceAccessKeyId)
        .setAccessKeySecret(sourceAccessKeySecret)
        .setRoleArn(roleArn);
var connection = new com.aliyun.teaopenapi.models.Config()
        .setEndpoint("dm.us-east-1.aliyuncs.com")
        .setReadTimeout(10000);
var sender = AlibabaCloudEmailMessageSender.defaults(
        credentialConfig, connection, () -> "sender@example.com");
```

显式凭据配置调用公共工厂 `create`，不受推荐环境策略影响；只有 `credentialConfig` 为 null 时才调用公共工厂 `defaults`。两个入口最终使用 `withCredential`，不在邮件模块重新实现凭据类型选择。发件账号 Supplier 在每次发送时取值，构造时不调用。

## 应用统一凭据

在应用装配处通过公共工厂创建一次凭据客户端，同一身份的多个产品可复用它：

```java
import com.wind.integration.alibaba.credentials.AlibabaCloudCredentialsFactory;
import com.wind.integration.message.email.alibabacloud.AlibabaCloudEmailMessageSender;

var credential = AlibabaCloudCredentialsFactory.defaults();
var sender = AlibabaCloudEmailMessageSender.withCredential(credential,
        new com.aliyun.teaopenapi.models.Config()
                .setEndpoint("dm.us-east-1.aliyuncs.com")
                .setConnectTimeout(3000)
                .setReadTimeout(10000),
        () -> "sender@example.com");
```

公共工厂 `defaults()` 按明确的环境规则选择 OIDC 或 AK/STS，具体行为参见[公共凭据模块](../../wind-alibabacloud-credentials/README.md)。邮件模块只使用传入的客户端，不再次解析默认来源。

## 邮件独立凭据

需要独立身份时，使用公共工厂的定向方法或显式配置创建另一个客户端，再传给同一 `withCredential` 入口。例如：

```java
var mailCredential = AlibabaCloudCredentialsFactory.accessKey(mailAccessKeyId, mailAccessKeySecret);
var sender = AlibabaCloudEmailMessageSender.withCredential(mailCredential,
        new com.aliyun.teaopenapi.models.Config().setEndpoint("dm.us-east-1.aliyuncs.com"),
        () -> "sender@example.com");
```

连接配置不能混入 AK、SK、SecurityToken、BearerToken 或另一个 credential。`withCredential` 复制连接配置，构造时不取证；每次发送通过同一个官方凭据客户端获取当前身份，实例由调用方持有。角色凭据刷新由官方 SDK 负责；静态 STS 三件套不会自动续期。

## 验证边界

文本和 HTML 分别映射到 `TextBody`、`HtmlBody`。捕获到 `TeaException` 时封装为 `BaseException` 并保留 cause，可读取 SDK 错误码、状态码和请求 ID；其他异常维持原有传播行为。发送器不增加自动重试。

测试保留真实邮件 SDK 的请求构造与签名，只替换外部 HTTP 通信，覆盖公共产品配置的 AK/STS、SDK 客户端注入、推荐环境入口、显式配置优先、STS 轮换、连接配置保持、身份混配拒绝、正文映射、动态发件账号和异常原因。原有真实发送测试继续禁用，不实际发送邮件。
