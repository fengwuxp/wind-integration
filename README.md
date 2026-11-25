# wind-integration

Wind Integration 是面向业务应用的共享能力与外部系统接入组件库。它为服务提供对象存储、KMS 和通知渠道适配，也提供数据隔离、指标计算、流程路径、金额、文档处理及即时通信组件，减少这些能力在多个应用中的重复建设。

应用选用所需模块，提供可信主体、业务规则、数据源及运行资源。例如，审批服务用流程定义计算参与路径，由应用保存审批实例并执行任务；统计报表用指标定义统一口径，由应用决定事实映射、生效修订和快照存储。从 [开发者 Wiki](docs/README.md) 按任务进入相应指南。当前 4.1 分支使用 JDK 25，依赖以本分支的 POM 为准。

| 用途 | 文档 |
| --- | --- |
| 选择 OSS、KMS 或消息 API | [公共能力接口](abstractions/README.md) |
| 接入阿里云 OSS、KMS 或邮件推送 | [身份、装配与调用流程](docs/guides/alibabacloud.md) |
| 将存量应用从 AK/SK 迁到 ACK OIDC | [4.1.0 迁移步骤](wind-alibabacloud/spring-boot-starter/MIGRATION.md) |
| 向钉钉群发送机器人消息 | [钉钉群机器人](wind-alibabacloud/README.md#钉钉群机器人) |
| 在已有应用中引入 BOM、装配组件并验证第一次调用 | [应用接入](docs/getting-started/existing-project.md) |
| 使用业务锁、事务、模板通知和 Redis 序号 | [基础设施指南](docs/guides/infrastructure.md) |
| 接入 MyBatis-Flex 查询、字段填充和数据隔离 | [持久层扩展](extends/mybatis-flex-extends/README.md) |
| 构建指标查询与结果计算 | [指标指南](docs/metrics/README.md) |
| 根据条件确定审批参与路径 | [流程设计与接入](docs/guides/workflow.md) |
| 选用金额、Office 或 IM 等共享能力 | [模块能力与应用责任](docs/README.md#能力如何进入应用) |
| 查询模块坐标、运行测试和定位失败 | [模块参考](docs/reference/modules.md) · [开发与验证](docs/contributing.md) |

服务启动、Nacos、Trace、国际化和通用组件由 Wind Middleware 维护。用户认证、JWT、验证码、MFA 与访问控制由 Wind Security 维护。两项目的开发者入口均为仓库内的 `docs/README.md`。[Wind Middleware](https://github.com/fengwuxp/wind-middleware) · [Wind Security](https://github.com/fengwuxp/wind-security)

## 依赖迁移

对象存储和 KMS 继续使用 `wind-oss-api`、`wind-kms-api`。消息类移到 `wind-message-api` 后保留原包名。`wind-integration-core` 会传递引入这个 JAR。手工分发 JAR 的应用升级 core 时，需要一并提供同版本的 `wind-message-api`。

本版退役了 `wind-oss`、`wind-kms` 和 `wind-message` 三个旧父 POM。显式继承它们的项目升级时需调整 `<parent>`。Wind 4.1 在根 POM 统一管理 `com.aliyun:dm20151123`（1.9.3）。继承 `wind-integration` 或导入 `wind-integration-dependencies` BOM 的应用可省略该依赖的版本。

## 构建与验证

在仓库根目录用 Java 25 构建。父 POM 默认跳过测试，运行用例时请显式启用。

```bash
mvn clean package \
  -Dmaven.test.skip=false -DskipTests=false -Dtest.ignore.failure=false
```

按模块构建可用 `-pl :artifactId -am`。例如，`mvn -pl :wind-oss-alibabacloud -am package` 会同时构建 OSS 实现及其依赖。
