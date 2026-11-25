# 接入阿里云 OSS、邮件、KMS 和钉钉机器人

应用访问对象存储、密钥和通知渠道时，需要同时确定“访问哪项资源”和“使用哪个身份”。比如对账任务可以用同一 RAM 角色读取 OSS 报表、通过 DirectMail 发送完成通知，两个服务仍各自使用自己的 endpoint。业务模块使用存储、加解密和消息接口，启动模块选择阿里云实现并配置运行身份。钉钉群机器人使用群专属的 token 和签名密钥。

## 身份如何进入产品请求

一次云服务调用从部署环境提供的身份开始。凭据客户端按配置读取静态凭据，或换取并刷新临时凭据。产品适配器用这个客户端为请求提供身份，同时采用自己的 endpoint、连接参数和目标资源。云服务再按工作负载身份及资源策略决定是否接受请求。

接入时先确定身份来源和授权范围，再创建或选择凭据客户端，然后装配具体产品客户端。业务层通过存储、加解密和消息接口调用。需要复用身份时，OSS、邮件和显式创建的 KMS 客户端可以共享一个凭据客户端，各自保留产品连接配置。

Spring Boot 的共享身份主要供 OSS 和邮件自动装配使用。KMS 默认入口通过独立的 SPI 和环境配置取得身份，显式传入凭据时可加入共享装配。钉钉群机器人采用群专属 token 和签名密钥，应用按目标群选择配置。下文分别说明这些装配方式与生命周期。

## 选择依赖和装配方式

各产品 artifact 均属于 `com.wind.integration`，版本由 Integration BOM 管理。Spring Boot 应用添加 `wind-alibabacloud-spring-boot-starter`，再按所用产品加入实现 JAR。OSS 和邮件是 Starter 的可选依赖，KMS 和钉钉按下文显式装配。产品坐标和能力入口集中在模块参考。[模块参考](../reference/modules.md#存储密钥和消息) · [BOM](../../dependencies/pom.xml)

### 在 Spring Boot 使用共享身份

下面的配置适用于已经通过 ServiceAccount 投影 OIDC Token、绑定 RAM 角色和信任关系的 ACK 工作负载。占位符由应用环境提供。OSS 和邮件只需配置启用的产品。

```properties
wind.alibaba.credentials.type=oidc_role_arn
wind.oss.alibaba.endpoint=${OSS_ENDPOINT}
wind.oss.default-bucket-name=${OSS_BUCKET}
wind.message.email.alibaba.endpoint=${MAIL_ENDPOINT}
app.mail.sender=${MAIL_SENDER}
```

需要邮件时，在应用的 `@Configuration` 类中提供发件账号。这里的 `app.mail.sender` 属于示例应用。

```java
@Bean
AlibabaCloudEmailAccountSupplier mailAccount(@Value("${app.mail.sender}") String sender) {
    return () -> sender;
}
```

| 配置 | 省略时的行为 | 使用条件 |
| --- | --- | --- |
| `wind.alibaba.credentials.*` | 字段初值均为 `null`。默认凭据 Bean 延迟创建；实际使用时需要有效来源 | OSS 和邮件没有产品级身份时借用同一个 `alibabaCloudDefaultCredential`，也可以按这个 Bean 名称提供自己的 `com.aliyun.credentials.Client` |
| `wind.oss.alibaba.endpoint`、`wind.message.email.alibaba.endpoint` | Starter 创建相应客户端时要求非空白 | endpoint 只属于相应产品；单独填写 endpoint 会继承共享身份 |
| `wind.oss.alibaba.*`、`wind.message.email.alibaba.*` 中的认证字段 | 全部省略时继承共享身份 | 任一认证字段有值就整组选用产品配置，包括空白串、显式 `false` 或仅填凭据超时。缺字段时检查该产品完整身份 |
| `wind.oss.default-bucket-name` | 省略或空白时只有客户端 | 非空白且存在 `WindOssClient` 时可注入 `BucketOperationsTemplate`，操作绑定的桶 |

只有 `type` 缺省时，`fromProperties` 才推断静态凭据类型。有 `security-token` 时选 `sts`，有 AK/SK 字段时选 `access_key`，两种情况都要求提供完整的一组字段。各来源属性、旧配置迁移和精确的 Bean 名称见现有模块说明。[凭据属性](../../wind-alibabacloud/credentials/README.md) · [Starter 配置迁移](../../wind-alibabacloud/spring-boot-starter/MIGRATION.md)

## 在普通 Java 中明确选择身份

`AlibabaCloudCredentialsFactory` 返回官方 `com.aliyun.credentials.Client`。同一授权范围内可把一个实例传给 OSS、邮件和 KMS 的 `withCredential`。SDK 通过该实例取当前凭据。Tea 产品连接配置只填写 endpoint、超时等连接参数，再带 AK、Token 或另一个 credential 会在创建时失败。

| 来源 | 创建方法 | 凭据有效期与使用条件 |
| --- | --- | --- |
| `access_key` | `accessKey(ak, sk)` | 使用指定 AK/SK。轮换后重新提供凭据 |
| `sts` | `create(new com.aliyun.credentials.models.Config().setType("sts").setAccessKeyId(ak).setAccessKeySecret(sk).setSecurityToken(token))` | 三件套须来自同次签发，到期由调用方更换 |
| `ram_role_arn` | `ramRoleArn(ak, sk, roleArn)` | SDK 使用有效源身份扮演角色并刷新会话 |
| `ecs_ram_role` | `ecsRamRole()` 或 `ecsRamRole(roleName)` | 从实例元数据按需获取，参数为角色名称 |
| `oidc_role_arn` | `oidcRoleArn()` 或带角色 ARN、提供商 ARN、Token 路径的重载 | SDK 读取 Token 换取 STS 并刷新，部署环境须可访问 STS |
| `credentials_uri` | `credentialsUri(uri)` | SDK 从指定凭据服务取得并刷新临时身份 |

对普通 Java 的 OSS 和邮件，`defaults` 使用凭据工厂的环境策略。先看 `ALIBABA_CLOUD_ROLE_ARN` 和 `ALIBABA_CLOUD_OIDC_PROVIDER_ARN`。两项齐全且非空白才选 OIDC，仅配一项或任一空白会失败。两项均未设置时读 `ALIBABA_CLOUD_ACCESS_KEY_ID` 和 `ALIBABA_CLOUD_ACCESS_KEY_SECRET`；同时设置 `ALIBABA_CLOUD_SECURITY_TOKEN` 时使用静态 STS。全部缺失会报错。显式 OIDC Token 路径优先，其次为 `ALIBABA_CLOUD_OIDC_TOKEN_FILE`，最后才是默认文件 `/etc/secrets/alibaba_cloud_credential`。[凭据来源和文件格式](../../wind-alibabacloud/credentials/README.md)

## 读写对象存储

以下代码在有 OIDC 工作负载身份的普通 Java 任务中下载对象，`objectKey` 为桶内键，`output` 为调用方持有的输出流。

```java
var credential = AlibabaCloudCredentialsFactory.oidcRoleArn();
var config = AlibabaCloudOssClient.getDefaultClientConfiguration();
try (var oss = AlibabaCloudOssClient.withCredential(credential, System.getenv("OSS_ENDPOINT"), config);
     var data = oss.downloadFile(System.getenv("OSS_BUCKET"), objectKey)) {
    data.transferTo(output);
}
```

默认 OSS 连接配置的最大连接数为 256。长生命周期服务复用客户端，每次下载后关闭数据流，退出时关闭客户端释放连接池。Spring 创建的客户端由容器关闭，共享凭据继续归应用持有。采用 V4 签名时在连接配置指定签名版本；若 SDK 从 endpoint 解析不到地域，使用带 region 的重载。

`uploadFile` 向指定 key 覆盖写入，写入后再次读取元数据并返回 `WindOssFile`。若读取元数据失败，先确认对象状态再决定是否重传。`listFiles(bucket, prefix, maxKeys)` 返回一页对象键。`getFileUrl` 生成对象地址，私有对象的访问授权需另外处理。[OSS 适配器](../../wind-alibabacloud/oss/src/main/java/com/wind/integration/oss/alibaba/AlibabaCloudOssClient.java)

## 使用 KMS 加解密和读取托管凭据

显式传入身份可以让 KMS 和其他客户端共享角色。下面的 `encodedPlaintext` 表示调用方已按 KMS 请求格式准备好的明文；适配器将字符串直接放入请求，解密也直接返回 KMS 的 plaintext 字段。加解密须使用匹配的加密上下文。

```java
var credential = AlibabaCloudCredentialsFactory.oidcRoleArn();
var connection = new com.aliyun.teaopenapi.models.Config().setEndpoint(System.getenv("KMS_ENDPOINT"));
var kms = AlibabaCloudKmsCryptoClient.withCredential(credential, connection);
Map<String, Object> context = Map.of("purpose", "archive");
String ciphertext = kms.encrypt(keyId, encodedPlaintext, context);
String recovered = kms.decrypt(ciphertext, context);
String secretValue = kms.getCredentialsAsText(secretName);
```

`WindCryptoClient` 承担加解密，`WindCredentialsClient` 按凭据名称读取内容。指定版本或需要完整返回值时调用 `getCredentials(secretName, version)`。`decrypt(keyId, ciphertext, context)` 的阿里云实现使用密文和上下文发起请求，调用方传入的 keyId 不参与解密校验。KMS 操作失败沿 `WindKmsException` 传播。[KMS 客户端](../../wind-alibabacloud/kms/src/main/java/com/wind/integration/alibaba/AlibabaCloudKmsCryptoClient.java)

KMS 无参 `defaults()` 及默认 SPI 路径独立于 Spring 配置。两个 OIDC ARN 均未设置时，KMS 从默认文件读取加密 AK，调用应用通过 Java SPI 登记的 `WindKmsClientCredentialsDecryptor`。解密结果前两条非空白行分别为 AK 和 SK，客户端创建时读取一次。只设置一个 ARN 或填入空白值会在 OIDC 选择阶段失败。OIDC 模式中同一路径存放原始 Token，两种文件内容依部署时选定的模式准备。未显式设置 KMS endpoint 时，先用 `ALIBABA_CLOUD_K_ID` 构造实例 VPC 地址，再查 `ALIBABA_CLOUD_K_ENDPOINT`。两项均优先取同名 JVM 系统属性，其次为环境变量。[KMS 初始化与 SPI 示例](../../wind-alibabacloud/README.md#kms)

## 发送邮件与钉钉群通知

以下示例的 `email` 为 Starter 装配的 `AlibabaCloudEmailMessageSender`，`recipient` 为应用选定的收件地址。每次发送时读取一次发件账号。DirectMail 返回成功表示服务端接受了本次调用；最终投递状态需要邮件服务的回执。

```java
email.sendMessage(SimpleEmailMessage.text("报表就绪", "本期报表已生成。", Set.of(recipient)));
```

钉钉使用目标群机器人的 token 和加签密钥，应用自行载入这两项配置。发送时生成签名，使用 Spring `RestClient` 发起请求，HTTP 或机器人业务响应失败会抛出异常。HTTP 日志中含 token、签名的请求 URL 需要脱敏。下面的两个空列表表示本次通知不点名用户，最后一个参数控制是否提醒全群。

```java
var robot = new DingTalkRobotMessageSender(RestClient.create());
var message = DingTalkRobotMessage.text(robotToken, signingSecret,
        "报表就绪", "本期报表已生成。", List.of(), List.of(), false);
robot.sendMessage(message);
```

发信出现超时时，调用方可能无法判断对方是否已接受通知。需要重试或确保业务消息只处理一次时，应用负责保存发送意图、关联标识与处理结果。Integration 负责把消息转成各渠道请求、返回或传播调用结果。[邮件适配器](../../wind-alibabacloud/email/src/main/java/com/wind/integration/message/email/alibabacloud/AlibabaCloudEmailMessageSender.java) · [钉钉适配器](../../wind-alibabacloud/dingtalk/src/main/java/com/wind/integration/message/dingtalk/DingTalkRobotMessageSender.java)

## 身份边界与接入验证

Wind Security 和业务应用负责用户身份、操作权限及业务资源范围。RAM、Bucket Policy、KMS 密钥策略等决定应用工作负载可以对云资源执行哪些动作。Middleware 提供 `WindCredentialsProvider` 等通用契约；Integration 的 `DefaultKmsCredentialsProvider` 将按名读取凭据的请求交给 KMS SPI。部署方提供角色信任、Token 挂载、网络路由、资源名称和应用所需的云上授权。[KMS 凭据桥接](../../extends/infra-commons/src/main/java/com/wind/integration/infrastructure/kms/DefaultKmsCredentialsProvider.java)

排查时可先按失败发生的阶段缩小范围。

| 现象 | 先核对 |
| --- | --- |
| 找不到客户端或模板 Bean | 产品 JAR、邮件账号 Supplier、默认桶名，以及应用替换的 Bean 名称或类型 |
| 创建产品 Bean 时报凭据残缺 | 产品前缀下是否意外配置了局部认证字段，是否同时向 Tea 连接配置传入第二套身份 |
| 启动成功，首次调用才换证失败 | OIDC Token 文件及轮换、角色信任、STS 端点和网络。构造客户端成功只说明本地装配完成 |
| 已能换证，产品操作仍报错 | 核对使用的 endpoint、请求目标资源、RAM/资源策略及云端请求 ID |
| 收件人没有收到通知 | 分别核对发送器异常、渠道接受记录及投递结果，再决定是否重发 |

仓库提供 Spring 上下文装配测试、针对本地模拟 HTTP 服务的凭据刷新和请求测试，以及钉钉 Webhook 签名测试。实际账号的授权、服务可达性与投递结果需在使用方的目标环境验证。[Starter 测试](../../wind-alibabacloud/spring-boot-starter/src/test/java/com/wind/integration/alibaba/configuration) · [凭据测试](../../wind-alibabacloud/credentials/src/test/java/com/wind/integration/alibaba/credentials) · [钉钉签名测试](../../wind-alibabacloud/dingtalk/src/test/java/com/wind/integration/message/dingtalk/DingTalkRobotMessageSenderTests.java)
