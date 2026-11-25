# 模块参考

下表列出当前 Maven reactor 中供应用使用的 JAR。它们的 `groupId` 均为 `com.wind.integration`。在应用的 `dependencyManagement` 中导入 `wind-integration-dependencies` BOM 后，可以按表中实际的 `artifactId` 声明依赖并省略版本。本仓库当前开发版本为 `4.1.0-SNAPSHOT`。[版本清单](../../dependencies/pom.xml) · [根 POM](../../pom.xml)

## 存储、密钥和消息

业务模块可只声明公共接口。负责启动的模块再选择服务实现、身份和连接配置。例如，使用阿里云 OSS 的服务，业务层依赖 `wind-oss-api`，启动模块添加 `wind-oss-alibabacloud`。采用 Spring Boot 属性装配时，再添加阿里云 Starter。[公共接口](../../abstractions/README.md) · [云产品接入](../../wind-alibabacloud/README.md)

| artifactId | 在什么情况下使用 |
| --- | --- |
| [wind-oss-api](../../abstractions/oss/pom.xml) | 通过对象存储接口上传、读取和管理文件。调用时提供所选实现的客户端。 |
| [wind-kms-api](../../abstractions/kms/pom.xml) | 按密钥标识加解密，或按名称、版本读取托管凭据。应用可直接提供客户端，也可通过 SPI 发现提供者。 |
| [wind-message-api](../../abstractions/message/pom.xml) | 定义邮件等消息、模板和发送器。在业务层定义通知模型时可单独引入。 |
| [wind-alibabacloud-credentials](../../wind-alibabacloud/credentials/README.md) | 用已有身份配置创建阿里云凭据客户端，供一个或多个产品 SDK 使用。 |
| [wind-oss-alibabacloud](../../wind-alibabacloud/oss/pom.xml) | 使用阿里云 OSS 实现对象存储 API，应用指定访问身份和服务端点。 |
| [wind-kms-alibabacloud](../../wind-alibabacloud/kms/pom.xml) | 使用阿里云 KMS 进行加解密和凭据读取，同时提供默认实现的 SPI 登记。 |
| [wind-message-emial-albabacloud](../../wind-alibabacloud/email/pom.xml) | 通过阿里云 DirectMail 发送邮件。坐标保留现有拼写，需提供访问身份和发信账号。 |
| [wind-message-dingtalk](../../wind-alibabacloud/dingtalk/pom.xml) | 向指定钉钉群的机器人发送消息，使用该机器人的 Webhook 和加签配置。 |
| [wind-alibabacloud-spring-boot-starter](../../wind-alibabacloud/spring-boot-starter/pom.xml) | 用 Spring 装配阿里云凭据。OSS 和邮件实现是可选依赖，由应用按产品添加。KMS 的默认客户端有独立的取凭据流程。 |

## 应用基础设施

| artifactId | 在什么情况下使用 |
| --- | --- |
| [wind-integration-core](../../core/pom.xml) | 服务之间共享租户、工作空间隔离模型与上下文，或使用任务、工作流参与者等公共契约。该构件传递引入 `wind-message-api`。 |
| [wind-operator](../../wind-operator/pom.xml) | 构造操作者身份和操作主体类型，匹配当前应用。 |
| [wind-infra-commons](../guides/infrastructure.md) | 按业务键加锁、为回调划定事务、组装模板消息、读取 KMS 凭据，或用 Redis 为多个实例共享分时段序号。 |
| [mybatis-flex-extends](../../extends/mybatis-flex-extends/README.md) | 在 MyBatis-Flex 读写中应用数据隔离、字段填充等规则，按需注册拦截器和监听器。 |

`wind-infra-commons` 使用 Wind Middleware 的 `WindLock`、`TemporalSequenceFactory` 等类型。应用接入 Integration 的 Redisson 适配即可复用这些契约。Spring Boot 服务骨架、请求追踪、配置中心等基础能力由 Middleware 提供。[基础设施模块依赖](../../extends/infra-commons/pom.xml)

## 文档、指标与业务模型

| artifactId | 在什么情况下使用 |
| --- | --- |
| [wind-office-core](../../office/core/pom.xml) | 定义文档导入导出任务、转换行数据，或使用 CSV 读写器。 |
| [wind-office-excel](../../office/excel/pom.xml) | 使用基于 EasyExcel 的 Excel 读写、导入任务和模板渲染。 |
| [wind-metrics](../metrics/README.md) | 定义指标、生成统计 SQL、计算结果。宿主应用提供事实表映射、查询执行与快照存储。 |
| [wind-money](../../wind-money/pom.xml) | 用整数金额和币种表达值，进行同币种运算及精度转换。应用定义输入校验、舍入及交易规则。 |
| [wind-im](../../wind-im/pom.xml) | 建立 Socket.IO 会话、发送和路由即时消息。业务应用接入聊天会话、消息存储等 SPI。 |
| [wind-workflow](../guides/workflow.md) | 根据工作流或审批定义解析执行路径。应用负责驱动和保存流程实例。 |

## 构建与旧工程迁移

`wind-integration` 是根聚合 POM。`wind-integration-abstractions`、`wind-extends`、`wind-office` 和 `wind-alibabacloud` 在 reactor 中聚合子模块。`wind-integration-dependencies` 是 BOM。`wind-integration-tests` 集中验证跨模块用例，POM 中设置了 `maven.deploy.skip=true`。按单个构件及其依赖构建时，可以在仓库根目录运行 `mvn -pl :wind-infra-commons -am package`。[模块声明](../../pom.xml) · [测试模块](../../tests/pom.xml)

4.1 已移出旧父 POM `wind-oss`、`wind-kms` 和 `wind-message`。旧工程若在 `<parent>` 中引用这些坐标，需更新父 POM 或改为导入 BOM，再按实际能力声明 JAR。OSS、KMS 的 API 坐标仍是 `wind-oss-api` 和 `wind-kms-api`。消息类型保留原有的 Java 包名，独立运行时需有 `wind-message-api` JAR。[迁移说明](../../README.md)
