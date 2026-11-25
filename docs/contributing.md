# 开发、测试与维护

从仓库根目录执行构建。当前分支的父 POM 使用 Java 25，MyBatis 上下文用到了正式版 `ScopedValue`。先用 `mvn -version` 确认实际 JDK，再运行目标模块的命令。[根 POM](../pom.xml)

## 构建模块

~~~sh
mvn -pl :wind-infra-commons -am compile
~~~

`-pl` 选择模块，`-am` 同时构建 reactor 中的依赖。根 POM 默认跳过测试，验证时应显式启用并核对实际测试报告。

仓库聚合全部模块时使用 `mvn package`。只有依赖已缓存时才加 `-o`；首次构建通过团队配置的 Maven 仓库解析依赖。

## 运行定向测试

下面同时运行基础设施模块的五组行为用例。用例集中在 `tests` 模块，运行命令以该模块为入口。

~~~sh
mvn -pl tests -am test \
  -Dmaven.test.skip=false -DskipTests=false \
  -Dtest.ignore.failure=false -Dmaven.test.failure.ignore=false \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dtest=LockTemplateTests,TemplateMessageSenderTests,ProgrammaticTransactionTemplateTests,RedissonWindLockTests,RedissonTemporalSequenceSupportTests
~~~

父 POM 的 Surefire 识别 `*Test`、`*Tests`，排除 `Abstract*` 和 `Base*`。核对目标模块 `target/surefire-reports/TEST-*.xml` 的实际用例、失败、错误及跳过数。使用 `verify` 时还需按范围指定 `-Dit.test=...`，并检查 `target/failsafe-reports`，因为父 POM 同时配置了集成测试阶段。

JDK 25 环境如拒绝 Mockito 动态挂载，在测试 JVM 启动时指定项目实际解析的 `mockito-core` JAR 为 Java agent。当前父 POM 的 Surefire/Failsafe 使用 `jacoco-coverage-agent` 属性传递 JVM 参数，可在上述命令中追加以下参数；这里的路径需替换为自己本机解析到的文件。

~~~sh
-Djacoco-coverage-agent="-javaagent:/absolute/path/mockito-core-5.23.0.jar"
~~~

此参数示例用于定向行为测试。需要同时收集覆盖率时，保留 JaCoCo 的 agent 参数并追加 Mockito agent。构建工具与依赖版本发生变化后，先确认有效 POM 的 `argLine`。

## 各类测试证明什么

| 测试范围 | 仓内依据 | 消费方还需验证 |
| --- | --- | --- |
| 事务模板 | 真实 Spring 事务管理器和 H2 检查提交、回滚、传播 | 目标数据库及业务事务边界 |
| Redis 锁与序列 | Mockito 隔离 Redis，固定时间检查边界和失败行为 | 实际 Redis 版本、网络、部署拓扑、数据保留和负载 |
| 模板消息 | 描述符选厂、生成内容、同步/异步入口 | 渠道可达性、发送权限和最终送达 |
| MyBatis 查询隔离 | SQL 改写及 H2 执行、上下文与缓存路径 | 生产数据库方言、业务可信范围及所有查询入口 |
| 云身份和产品适配 | 装配、模拟端点、签名及响应处理 | 真实云账号信任、RAM/资源策略、地域与网络 |

MyBatis 的完整测试集合和 `verify` 命令维护在模块 README。指标测试按指标指南选择。云产品测试目录按产品分布，运行带真实外部资源依赖的测试前先查看该用例的配置要求。[MyBatis 测试](../extends/mybatis-flex-extends/README.md#构建与测试) · [指标指南](metrics/README.md) · [阿里云测试入口](guides/alibabacloud.md#身份边界与接入验证)

## 维护接入说明

修改公开 API、属性名、默认值、自动配置条件或资源生命周期时，更新相应指南及可编译示例。模块坐标以本分支 POM 为准，复核相对链接、代码围栏和示例中由应用提供的变量。凭据使用环境占位符或示例名称。

本 Wiki 的相对链接指向同一仓库。跨项目使用仓库首页并注明文档路径，开发者可按自己使用的版本打开对应说明。当前 `.gitignore` 包含 `docs`，新增 Markdown 文件需要显式加入 Git 跟踪，提交前核对 `git status` 与暂存文件清单。[忽略规则](../.gitignore)
