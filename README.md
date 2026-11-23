# wind-integration

Wind 提供对象存储、加解密、消息投递等 Java 组件。业务代码可以单独依赖接口，应用再引入阿里云、钉钉等具体实现。

| 用途 | 文档 |
| --- | --- |
| 选择 OSS、KMS 或消息 API | [公共能力接口](abstractions/README.md) |
| 接入阿里云 OSS、KMS 或邮件推送 | [阿里云接入指南](wind-alibabacloud/README.md) |
| 将存量应用从 AK/SK 迁到 ACK OIDC | [4.1.0 迁移步骤](wind-alibabacloud/spring-boot-starter/MIGRATION.md) |
| 向钉钉群发送机器人消息 | [钉钉群机器人](wind-alibabacloud/README.md#钉钉群机器人) |

## 依赖迁移

对象存储和 KMS 继续使用 `wind-oss-api`、`wind-kms-api`。消息类移到 `wind-message-api` 后保留原包名。`wind-integration-core` 会传递引入这个 JAR。手工分发 JAR 的应用升级 core 时，需要一并提供同版本的 `wind-message-api`。

本版退役了 `wind-oss`、`wind-kms` 和 `wind-message` 三个旧父 POM。显式继承它们的项目升级时需调整 `<parent>`。原来通过 `wind-message` 继承邮件 SDK 版本管理且直接声明 `com.aliyun:dm20151123` 的项目，需自行管理该依赖版本。

## 构建与验证

在仓库根目录用 Java 25 构建。父 POM 默认跳过测试，运行用例时请显式启用。

```bash
mvn clean package \
  -Dmaven.test.skip=false -DskipTests=false -Dtest.ignore.failure=false
```

按模块构建可用 `-pl :artifactId -am`。例如，`mvn -pl :wind-oss-alibabacloud -am package` 会同时构建 OSS 实现及其依赖。
