# Wind Integration 开发者 Wiki

Wind Integration 面向需要复用业务基础能力的后端应用。它既提供外部系统的访问接口与适配，也提供应用间共享的业务模型、计算规则和执行组件。应用把这些组件接入自己的业务入口、数据源与运行环境。当前文档对应本仓库 4.1 分支，使用 JDK 25。[项目首页](../README.md) · [模块参考](reference/modules.md)

## 能力如何进入应用

同一业务可以组合多个模块。例如一次申请审批先按业务事实计算参与路径，由应用保存实例与任务；数据库查询按可信主体限制可见范围；审批完成后调用渠道发送通知；报表再按定义好的口径统计业务结果。各模块分别维护自己的计算或调用契约，应用负责让整个流程按业务状态推进。

| 能力 | 模块负责的结果 | 应用提供的内容 |
| --- | --- | --- |
| 外部系统接入 | 存储、密钥、托管凭据及通知请求的转换与调用 | 云身份、资源权限、端点、消息内容和重试安排 |
| 基础设施执行 | 按键互斥、事务回调、模板通知、共享分时段计数 | 锁名、租期、数据源事务、时区及业务失败处理 |
| 数据隔离 | 作用域内的隔离维度、查询约束和插入归属 | 可信主体与范围、实体映射、实际业务查询入口 |
| 指标 | 版本化统计口径、SQL 生成、原始量合并及结果计算 | 事实映射、权限、生效修订、查询路线、快照和调度 |
| 流程与审批 | 按条件边计算审批和抄送节点顺序，提供参与者策略声明 | 候选人解析、流程实例、任务分派、动作执行、超时处理和保存 |
| 金额 | 带币种的金额表示、同币种运算及精度转换 | 金额输入校验、舍入、交易和账务规则 |
| 文档处理 | CSV/Excel 读写、行转换和导入导出任务模型 | 数据来源、业务校验、任务执行和结果保存 |
| 即时通信 | 连接与会话组件、节点间消息路由及广播 | 认证、会话业务、消息仓储、撤回规则和部署环境 |

接入时先确定应用需要的结果及责任，再选模块并装配运行依赖。例如指标模块的核心模型可以用于管理和校验统计口径，执行真实查询还要接入宿主数据源；流程模块可以直接计算路径，持久化审批过程需要应用自己的状态和事务。运行行为及失败边界在各任务指南中说明。

## 按任务阅读

| 要完成的任务 | 使用文档 |
| --- | --- |
| 在已有服务中选模块、管理版本并验证接入 | [应用接入](getting-started/existing-project.md) |
| 在业务层定义存储、加解密和通知调用 | [公共接口](../abstractions/README.md) |
| 配置共享身份，接入 OSS、KMS、DirectMail 和钉钉 | [阿里云接入](guides/alibabacloud.md) |
| 从 AK/SK 迁移到 ACK OIDC，或单独配置产品身份 | [凭据工厂](../wind-alibabacloud/credentials/README.md) · [Starter 迁移](../wind-alibabacloud/spring-boot-starter/MIGRATION.md) |
| 用回调加锁、执行事务、发模板消息或取序号 | [基础设施组件](guides/infrastructure.md) |
| 在数据库查询中应用可信访问范围 | [MyBatis-Flex 扩展](../extends/mybatis-flex-extends/README.md) |
| 定义指标、编译查询、解释和计算结果 | [指标指南](metrics/README.md) |
| 设计条件分支并计算审批参与路径 | [流程设计与接入](guides/workflow.md) |
| 选 Maven 坐标、查聚合模块与迁移关系 | [模块参考](reference/modules.md) |
| 从源码构建并确认测试实际执行 | [开发与验证](contributing.md) |

## 三个项目如何组合

| 项目 | 负责的内容 | 开发者入口 |
| --- | --- | --- |
| Middleware | 服务启动与 HTTP 通用处理、配置加载、Trace、i18n、日志、通用类型及 Nacos、Sentinel、RocketMQ、ElasticJob 适配 | 仓库内 `docs/README.md`，跨项目分工见 `docs/reference/wind-projects.md` |
| Security | 用户认证、JWT、验证码、MFA、权限和访问控制契约 | 仓库内 `docs/README.md` |
| Integration | 外部系统接入、基础设施、数据隔离，以及指标、流程、金额、Office 和 IM 等共享应用能力 | 本 Wiki |

三个项目的源码与文档入口分别位于对应仓库。[Wind Middleware](https://github.com/fengwuxp/wind-middleware) · [Wind Security](https://github.com/fengwuxp/wind-security) · [Wind Integration](https://github.com/fengwuxp/wind-integration)

Integration 根 POM 继承 Middleware 的构建与依赖约定，并导入 Security BOM 管理版本。运行依赖随所选模块确定，例如 IM 引入 Security 认证组件。BOM 版本管理和应用实际加载的 JAR 需分别核对。[根 POM](../pom.xml) · [IM 依赖](../wind-im/pom.xml)

### 配置中心和托管凭据

Middleware 负责读取配置及执行 `@{CRE('orders-db', 'password')}` 表达式，`WindCredentialsProvider` 接口也归 Middleware。Integration 的 `DefaultKmsCredentialsProvider` 通过 KMS 实现读取托管凭据。配置中心的 Group、Data ID 和共享规则查 Middleware 的 `docs/guides/nacos-config.md`，KMS 的访问身份、默认 SPI 和端点查本项目指南。用于获取 KMS 凭据的初始身份应由运行环境独立提供，避免用尚未取得的凭据初始化同一个 KMS 客户端。[KMS 接入](guides/alibabacloud.md#使用-kms-加解密和读取托管凭据)

### 验证码和消息投递

Security 决定验证码的生成、存储、有效期、验证次数和校验结果。应用把需要投递的内容交给 Integration 的消息发送器，并选择渠道、收件人和发送身份。渠道接受请求和用户收到通知是不同阶段，应用按所需送达保证保存发送记录和处理结果。[消息接口](../abstractions/README.md) · [渠道调用](guides/alibabacloud.md#发送邮件与钉钉群通知)

### 权限判断和 SQL 隔离

Security 和业务域确定可信主体及其允许访问的资源范围。Integration 的上下文和 MyBatis 扩展把这个范围应用到查询条件与字段填充。应用需在请求或任务入口建立范围，在退出时恢复上下文，并验证其 SQL、Mapper 和缓存路径都进入预期约束。[数据隔离接入](../extends/mybatis-flex-extends/README.md#71-最小接入)

## 语雀主题与当前文档

语雀知识库按整个 Wind 技术栈组织内容。本 Wiki 按当前 POM 与实现整理接入方式。早期文中的仓库名、坐标及示例类型需要结合对应版本核对。

| 资料 | 当前维护位置 |
| --- | --- |
| [框架介绍](https://www.yuque.com/suiyuerufeng-akjad/wind/uo9kf5s1277vi3cg)、[Features](https://www.yuque.com/suiyuerufeng-akjad/wind/frg18n25vz07swqu) | 三项目分工见本页，Integration 构件见模块参考 |
| [Quick Start](https://www.yuque.com/suiyuerufeng-akjad/wind/wua5a6g571qpmk9m) | 应用生成和启动由 Middleware 维护，已有应用接入 Integration 见本 Wiki |
| [配置中心](https://www.yuque.com/suiyuerufeng-akjad/wind/lb2kacr9ch1l70td) | Middleware 的配置加载文档；KMS 提供者见本项目基础设施指南 |
| [Wind-Tracer](https://www.yuque.com/suiyuerufeng-akjad/wind/mrzvfd5pz4e8fgd3) | Middleware 的 `docs/guides/request-tracing.md` |
| [SpringBoot 国际化](https://www.yuque.com/suiyuerufeng-akjad/wind/vzoygfi6ehphzhvh) | Middleware 的 `docs/guides/i18n.md` |

语雀的 Quick Start 和配置中心旧例中出现过 `com.wind.configcenter`、`com.wind.configcenter.core` 两种 Nacos groupId。当前 Middleware 的入门文档使用 `com.wind.middleware`。依赖声明以所选源码版本的 POM 为准，Integration 的供应商配置也应使用本项目文档给出的当前前缀。
