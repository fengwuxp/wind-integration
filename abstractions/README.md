# 通用集成能力

业务代码按需引入下表中的 JAR。`wind-integration-abstractions` 负责一起构建这三个模块。

| 目录 / artifactId | 主要入口 | 用途 |
| --- | --- | --- |
| `oss` / `wind-oss-api` | [WindOssClient](oss/src/main/java/com/wind/integration/oss/WindOssClient.java)、[BucketOperationsTemplate](oss/src/main/java/com/wind/integration/oss/BucketOperationsTemplate.java) | 存储桶操作，对象上传、下载、删除、复制、列举及元数据读取。默认 Bucket 模板封装固定桶名 |
| `kms` / `wind-kms-api` | [WindCryptoClient](kms/src/main/java/com/wind/integration/kms/WindCryptoClient.java)、[WindCredentialsClient](kms/src/main/java/com/wind/integration/kms/WindCredentialsClient.java) | 使用密钥标识加解密数据，按名称及版本读取托管凭据。`WindKmsClientProvider` 提供这两类客户端 |
| `message` / `wind-message-api` | [MessageSender](message/src/main/java/com/wind/integration/core/message/MessageSender.java)、[TemplateMessageFactory](message/src/main/java/com/wind/integration/core/message/TemplateMessageFactory.java) | 单条及批量消息投递、按消息类型选择发送者、将业务数据转换为模板消息。提供文本和 HTML 邮件模型 |

## 声明依赖

三个 JAR 的 groupId 都是 `com.wind.integration`。导入项目 BOM 后，消息模块的依赖写法如下。[wind-integration-dependencies](../dependencies/pom.xml)

```xml
<dependency>
    <groupId>com.wind.integration</groupId>
    <artifactId>wind-message-api</artifactId>
</dependency>
```

原先通过 `wind-integration-core` 使用消息接口的应用可以继续使用原包名 `com.wind.integration.core.message`。core 会传递引入 `wind-message-api`。

## 调用与实现装配

发送欢迎邮件的代码只需要 `MessageSender<SimpleEmailMessage>`。应用启动时传入邮件发送器即可。

```java
import com.wind.integration.core.message.MessageSender;
import com.wind.integration.core.message.definition.SimpleEmailMessage;
import java.util.Set;

public class WelcomeNotice {
    private final MessageSender<SimpleEmailMessage> sender;

    public WelcomeNotice(MessageSender<SimpleEmailMessage> sender) {
        this.sender = sender;
    }

    public void send(String receiver) {
        sender.sendMessage(SimpleEmailMessage.text("欢迎", "欢迎使用我们的服务", Set.of(receiver)));
    }
}
```

`CompositeMessageSender` 会把消息交给每一个支持其类型的发送器。只需要发送一封邮件时，直接使用对应的邮件发送器即可。

邮件的发件账号和阿里云凭据由应用装配。[阿里云接入指南](../wind-alibabacloud/README.md#邮件推送)

向钉钉群发送消息可使用同一套消息接口。[钉钉群机器人](../wind-alibabacloud/README.md#钉钉群机器人)
