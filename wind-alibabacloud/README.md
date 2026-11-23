# Wind 阿里云接入指南

| 要使用的功能 | Maven artifactId | 用法 |
| --- | --- | --- |
| 对象存储 | `wind-oss-alibabacloud` | [OSS](#oss) |
| 邮件推送（DirectMail） | `wind-message-emial-albabacloud` | [邮件推送](#邮件推送) |
| 钉钉群机器人 | `wind-message-dingtalk` | [钉钉群机器人](#钉钉群机器人) |
| 加解密、读取托管凭据 | `wind-kms-alibabacloud` | [KMS](#kms) |
| 在 Spring Boot 中装配 OSS 或邮件 | `wind-alibabacloud-spring-boot-starter` | [Spring Boot](#spring-boot) |
| 在 Java 中创建或共享凭据 | `wind-alibabacloud-credentials` | [凭据工厂](credentials/README.md) |

这些 JAR 的 groupId 都是 `com.wind.integration`。导入 `wind-integration-dependencies` BOM 后可以省略版本号。只需要接口的模块可以直接依赖对应的 API。[公共能力接口](../abstractions/README.md)

## Spring Boot

引入 Starter，再加上需要的产品 JAR。只用 OSS 就加 `wind-oss-alibabacloud`，同时发邮件就再加 `wind-message-emial-albabacloud`。KMS 单独创建客户端。

**用同一组 AK/SK 访问 OSS 和邮件**时，两个产品分别配置 endpoint，身份只配一次。下面是 `application.properties` 的写法。只接入其中一个产品时，可以省去另一个 endpoint。

```properties
wind.alibaba.credentials.type=access_key
wind.alibaba.credentials.access-key=${CLOUD_ACCESS_KEY_ID}
wind.alibaba.credentials.secret-key=${CLOUD_ACCESS_KEY_SECRET}

wind.oss.alibaba.endpoint=${OSS_ENDPOINT}
wind.message.email.alibaba.endpoint=${MAIL_ENDPOINT}
```

有默认 Bucket 时，加上 `wind.oss.default-bucket-name=${OSS_BUCKET}`，即可注入 `BucketOperationsTemplate`。直接使用 `WindOssClient` 时，在每次调用中传入桶名。

邮件发送器还需要一个 `AlibabaCloudEmailAccountSupplier` Bean。把下面的方法放进应用的 `@Configuration` 类，并在 `app.mail.sender` 中填写 DirectMail 发件地址。发送器每次发信时调用这个 Bean，已有应用也可以返回按业务选出的发件账号。

```java
@Bean
AlibabaCloudEmailAccountSupplier emailAccount(@Value("${app.mail.sender}") String address) {
    return () -> address;
}
```

**在 ACK 上使用 RRSA** 时，为 Pod 的 ServiceAccount 配好角色和权限，用下面一行替换全局的三行 AK/SK 配置。保留产品 endpoint、Bucket 和发件账号。应用重启后，SDK 读取 Pod 投影的 OIDC Token，向 STS 换取并刷新临时凭据。

```properties
wind.alibaba.credentials.type=oidc_role_arn
```

如果邮件需要自己的身份，就在 `wind.message.email.alibaba` 下配置完整的一组 `type` 和对应凭据字段。OSS 同理，使用 `wind.oss.alibaba`。产品只配置 endpoint 时会继承全局身份，局部凭据缺字段会报错。使用现成的 STS 临时凭据时，配置 `type=sts` 和同一次签发的 `access-key`、`secret-key`、`security-token`。这种方式需要应用自行更换到期凭据。[旧配置迁移、YAML 和分环境 properties 示例](spring-boot-starter/MIGRATION.md)

应用可以注册自己的 `WindOssClient` 或 `AlibabaCloudEmailMessageSender` Bean 来设置产品 SDK 的连接参数。需要沿用全局身份时，用 `@Qualifier("alibabaCloudDefaultCredential")` 注入 `com.aliyun.credentials.Client`。OSS 和邮件的配置 Bean 都是 `AlibabaCloudProductProperties`，可分别用 `AlibabaCloudOssAutoConfiguration.OSS_PROPERTIES_BEAN_NAME`、`AlibabaCloudEmailAutoConfiguration.EMAIL_PROPERTIES_BEAN_NAME` 限定注入。

## OSS

在普通 Java 应用中，`AlibabaCloudOssClient.defaults(endpoint)` 从环境中选择凭据。已有凭据客户端时，传给 `withCredential` 即可。下面的例子使用 OIDC 和 V4 签名，地域需要填写 Bucket 所在地。

```java
import com.aliyun.oss.common.comm.SignVersion;
import com.wind.integration.alibaba.credentials.AlibabaCloudCredentialsFactory;
import com.wind.integration.oss.alibaba.AlibabaCloudOssClient;

var credential = AlibabaCloudCredentialsFactory.oidcRoleArn();
var connection = AlibabaCloudOssClient.getDefaultClientConfiguration();
connection.setSignatureVersion(SignVersion.V4);

try (var oss = AlibabaCloudOssClient.withCredential(
        credential, System.getenv("OSS_ENDPOINT"), "cn-hangzhou", connection)) {
    boolean exists = oss.isBucketExists(System.getenv("OSS_BUCKET"));
}
```

V4 需要签名版本和地域两个参数。对于 SDK 无法识别地域的 endpoint，请显式传入地域。`getDefaultClientConfiguration()` 每次返回一份连接配置，默认最大连接数为 256，可以按实际并发量调整。

长期运行的应用通常复用一个 OSS 客户端。下载完成后关闭返回的流，应用退出时调用 `oss.close()` 释放连接池。共享凭据仍可供其他客户端使用。`getFileUrl` 生成的地址没有预签名授权。使用自定义域名时请填写实际 endpoint，内网地址转换方法只处理 `aliyuncs.com` 的域名规则。

## 邮件推送

普通 Java 应用创建发送器时可以传入现有凭据和发件账号。下面的代码从环境变量读取发件地址，用 `SimpleEmailMessage.text` 发送纯文本邮件。HTML 正文使用 `SimpleEmailMessage.html`。

```java
import com.wind.integration.alibaba.credentials.AlibabaCloudCredentialsFactory;
import com.wind.integration.core.message.definition.SimpleEmailMessage;
import com.wind.integration.message.email.alibabacloud.AlibabaCloudEmailMessageSender;

import java.util.Set;
import java.util.function.Supplier;

var credential = AlibabaCloudCredentialsFactory.accessKey(
        System.getenv("CLOUD_ACCESS_KEY_ID"), System.getenv("CLOUD_ACCESS_KEY_SECRET"));
var connection = new com.aliyun.teaopenapi.models.Config()
        .setEndpoint(System.getenv("MAIL_ENDPOINT"))
        .setConnectTimeout(3000)
        .setReadTimeout(10000);
Supplier<String> account = () -> System.getenv("MAIL_SENDER");
var sender = AlibabaCloudEmailMessageSender.withCredential(credential, connection, account);

sender.sendMessage(SimpleEmailMessage.text(
        "通知", "邮件正文", Set.of("recipient@example.com")));
```

每次发送都会调用一次 `account.get()`。手里已有 DirectMail SDK 客户端时，可以用 `new AlibabaCloudEmailMessageSender(sdkClient, account)`。若按产品属性创建，使用 `fromProperties(properties, account)`，并在属性中提供邮件 endpoint 和完整身份。

传给 `withCredential` 的 Tea `Config` 只填写 endpoint、超时等连接参数。方法会复制这份配置，若其中还带有 AK、SK、Token 或另一个凭据客户端，创建就会失败。发送失败时可从 `BaseException` 的 cause 读取 SDK 的 `TeaException` 和请求 ID。发送器不会额外重试。

## KMS

KMS 独立创建客户端。需要指定身份时，可以用 `defaults(credentialConfig, connection)` 传入凭据配置和 KMS 连接配置，也可以用 `withCredential(credential, connection)` 与其他产品共享已有凭据。后者同样要求 Tea 连接配置只含连接参数。

```java
import com.aliyun.credentials.utils.AuthConstant;
import com.wind.integration.alibaba.AlibabaCloudKmsCryptoClient;

var credentialConfig = new com.aliyun.credentials.models.Config()
        .setType(AuthConstant.OIDC_ROLE_ARN);
var connection = new com.aliyun.teaopenapi.models.Config()
        .setEndpoint(System.getenv("KMS_ENDPOINT"))
        .setConnectTimeout(3000)
        .setReadTimeout(10000);
var kms = AlibabaCloudKmsCryptoClient.defaults(credentialConfig, connection);
```

示例中的 `KMS_ENDPOINT` 由应用提供。省略显式端点时，客户端先尝试用 `ALIBABA_CLOUD_K_ID` 构造实例 VPC 地址，再尝试 `ALIBABA_CLOUD_K_ENDPOINT`。这两项均优先读取同名 JVM 系统属性，其次读取环境变量。

无参 `AlibabaCloudKmsCryptoClient.defaults()` 按 Pod 或进程中的两个环境变量选择身份。Spring 的全局凭据配置不会改变这个选择。

| `ALIBABA_CLOUD_ROLE_ARN` 和 `ALIBABA_CLOUD_OIDC_PROVIDER_ARN` | KMS 使用的凭据 |
| --- | --- |
| 两项都有非空值 | OIDC 角色凭据，SDK 用 Token 换取 STS |
| 两项均未设置 | 从 `/etc/secrets/alibaba_cloud_credential` 读取加密 AK 文件 |
| 仅设置一项，或其中一项为空白 | 创建失败，报告配置错误 |

使用加密 AK 文件的应用需实现 `WindKmsClientCredentialsDecryptor`，在 `META-INF/services/com.wind.integration.kms.WindKmsClientCredentialsDecryptor` 中登记实现类的全限定名。解密结果的前两条非空行依次是 AK 和 SK，凭据客户端在创建时读一次。要固定使用另一个文件，可调用 `AlibabaCloudCredentialsFactory.withCredentialFile(path, decryptor::decrypt)`，把返回的凭据传给 `withCredential`。

通过 `KmsTextEncryptor.defaults()` 或 `withChunk()` 使用 KMS 的应用会经由 `WindKmsClientProvider` 创建客户端。要用应用已有的客户端，可直接构造 `new KmsTextEncryptor(kmsClient)`。[凭据来源与 OIDC Token 文件](credentials/README.md)

## 钉钉群机器人

引入 `com.wind.integration:wind-message-dingtalk`。钉钉和阿里云邮件都实现 `MessageSender`。向钉钉群发消息时，使用该群机器人的 Webhook 中的 `access_token` 和加签密钥。下面的示例通过应用自定义的环境变量读取这两个值。

```java
import com.wind.integration.message.dingtalk.DingTalkRobotMessage;
import com.wind.integration.message.dingtalk.DingTalkRobotMessageSender;
import org.springframework.web.client.RestClient;
import java.util.List;

var sender = new DingTalkRobotMessageSender(RestClient.create());
var message = DingTalkRobotMessage.text(
        System.getenv("DINGTALK_ROBOT_ACCESS_TOKEN"), System.getenv("DINGTALK_ROBOT_SECRET"),
        "发布通知", "订单服务已发布", List.of(), List.of(), false);
sender.sendMessage(message);
```

末尾两个列表分别填写要 @ 的用户 ID 和手机号，最后一个参数控制是否 @ 所有人。发送 Markdown 消息可使用 `DingTalkRobotMessage.markdown(...)`。[钉钉消息模块](dingtalk/pom.xml)
