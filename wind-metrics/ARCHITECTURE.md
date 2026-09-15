# 指标公共能力与 DSL 分层重构

架构类型：Java 指标库的能力与实现边界。Wind 持有公共合同；Capte 持有查询引擎、统计和存储实现。
依据：用户确认 WindMetricsValue 等是面向开发者的统一能力，DSL 只是定义方式；2026-09-15 当前源码及 Capte 消费范围回执。

## 重构准入与非目标

开发者应能以同一种方式读取代码、SQL 或 DSL 定义的单值和多字段指标，无须判断实时、快照或分段。
本次授权范围为 wind-metrics 源码、测试和文档；根 POM、KMS、Capte 源码和共享依赖仓库不在写集内。
不改变 Definition/Plan JSON、三字段快照目标、显式定义修订、查询服务与物化服务的方法及其事务职责。

当前问题与证据：原设计有独立能力价值，不能因未引用 DSL 而划为废弃实现。当前调用链中，Capte 的
DefaultMetricsFieldFactory、SqlSingleMetricsField、SqlMultipleMetricsField、
DefaultMetricsAggregatorFactory 和 OfflineMetricReportStatisticsExecutor 均是实际消费者。
已确认的缺口是：只读多字段访问绑定到条件求值接口；新查询结果与原具名值能力分离；
正式查询解析和发布派生模型位于 DSL 包；组合实现无条件 supports=true，嵌套选择时会抢占不支持的类型。

## 目标结构与职责

| 层级 | 类型 | 责任与边界 |
| --- | --- | --- |
| 公共取值 | WindMetricsValue、WindMetricsValueSet | 读取名称、单值或具名字段，不关心来源、路由、版本管理和存储 |
| 维度键遍历 | WindMetricsDimensionKeyProvider | 由业务方按单维度或组合维度分页提供完整具名键，不带快照上下文 |
| 条件计算及对象组装 | WindMetricsEvaluator、WindMetricsAggregator、工厂 | 按约定条件求值，将多个值组装成开发者对象；Aggregator 不等于 SUM/COUNT 运算器 |
| 可变统计 | SingleValueMetricsField、WindMetricsStatisticsExecutor | 显式提供修改或业务对象驱动的统计；不要求只读查询实现修改方法 |
| 查询与物化用例 | query、materialization | 查询输入、带诊断信息的结果、按固定版本执行的能力和物化派生合同 |
| DSL 定义实现 | dsl.definition、dsl.materialization、filter、literal、codec | 描述、校验和规范化 Definition/Plan 语法，由宿主编译消费；Wind codec 本身不执行查询 |
| 协议基础 | json.MetricJsonSupport | 查询与 DSL 共用的严格 JSON IO、JSON Pointer 与序列化，不决定业务规则 |
| 宿主实现 | Capte 或其他实现方 | 选定定义及路线，完成权限、取数、计算、结果转换、持久化、冻结绑定与事务 |

依赖方向：实现依赖公共能力；公共能力、query、materialization、json 均不导入 dsl。
既有 Jackson 注解绑定继续使用，但查询模型绑定自己的 query 解析器。
DSL 不是实时/分段查询策略本身，它描述规则；路线选择和执行是宿主对能力接口的实现。

## DSL 内容迁移裁决

| 原内容 | 裁决 | 理由及兼容影响 |
| --- | --- | --- |
| dsl.MetricQueryJsonParser | 移至 query，同名 | 正式查询是公共输入合同，不是 Definition/Plan 语法；消费者改 import，parse/parseBatch 行为保持 |
| dsl.materialization.MetricMaterializationDependencyDsl | 移至 materialization.MetricMaterializationDependency | 服务端从固定定义展开的依赖，不是用户填写的 Plan 字段；移除旧类型 |
| dsl.materialization.MetricMaterializationMeasureDsl | 移至 materialization.MetricMaterializationMeasure | 发布派生的值字段与充分合并状态；字段、构造语义保持，消费者改类型名 |
| MetricDslJson 的严格读取、序列化、路径 | 提取到 json.MetricJsonSupport | 查询和 DSL 复用同一实现，保留精确数值、重复字段、尾随内容和原错误码 |
| MetricDslJson 的 required/optional/type/enum 等 | 留在 DSL，包内可见 | 它们解释 DSL 关闭世界语法，不属于开发者指标能力 |
| DefinitionSpec、Value/Measure/Expression、Subject/Time、selection、filter、literal | 留在 DSL | 表达定义结构及字段引用，既不是指标值，也不是执行服务；上移将使公共层依赖 DSL 语法 |
| Plan、Reference、Segment、SnapshotTarget/Mapping | 留在 DSL | 物化计划的逻辑配置语法，不能用运行结果或存储身份替代 |
| enums、MetricQuery/Result、物化入口与结果 | 保持 metrics 下既有子包 | 已是共用语义或能力合同，不需要平铺到根包 |

上述三类旧路径直接迁移，不保留两套同义模型或回退解析入口。这是 Java 源码/二进制包名变化，需消费者随制品重新编译；JSON 无变化。
WindMetricsValue、field、factory 原方法保留，不标废弃。新增只读父接口保留
List<WindMetricsValue<Object>> 与 findByName 的泛型形状，旧 SqlMultipleMetricsField 不新增必需方法。

## 对象设计、行为不变量与公共契约不变量

- WindMetricsValue 只约定读取。getValue 允许定义认可的正常空值；执行错误必须传播，不能用 null/0 隐藏。
- WindMetricsValue.of 固定名称和值引用。WindMetricsValueSet.of 复制字段容器并保留顺序、null；不声称任意业务对象深度不可变。
- WindMetricsValueSet 表达“是一个多字段值”；MultipleValueMetricsField 在此基础上增加条件求值。
  只读结果不继承 mutable field，避免为满足接口而实现抛异常的 increase/setValue。
- MetricResult.toMetricsValue 只从已计算结果派生值视图，无 IO、无重新查询、无依赖重选。
  SCALAR 返回具名 Number，FIELD_SET 返回具名字段集；Java 数值类型、精度和字段存在性保持。
- 顶层 name 是指标编码，子字段名只在所属指标内唯一。A/value 与 B/value 是两个所属指标的字段，不合并。
  值视图不承担存储身份；定义修订、时间、覆盖和执行路由保留在 MetricResult 及宿主冻结上下文。
- MetricResult 构造器、record 访问器及 HTTP JSON 结构保留。toMetricsValue 不是 Bean getter，不增加响应字段。
- Resolver 是按顺序选一个支持者，StatisticsExecutor 是执行全部匹配者。supports 与真实能力一致，执行失败不自动换实现。
  本次不把“多个主体汇总”和 MetricBatchQuery 的“同一条件多个指标”混为一种请求。

## 调用方式与实现交接

```java
// queries 是宿主实现的 MetricValueQueryService；调用者不依赖 DSL 类型。
MetricResult detailed = queries.query(request);
WindMetricsValue<?> value = detailed.toMetricsValue();
display(value.getName(), value.getValue());

// 只根据结果形态访问具名字段，不根据实时/快照/分段路线写分支。
if (value instanceof WindMetricsValueSet<?> fields) {
    Optional<WindMetricsValue<Long>> count = fields.findByName("count");
    // Optional 为空表示字段不存在；对象存在且 getValue() 为空表示正常空结果。
}

// 代码或 SQL 实现可以直接提供同一个能力；无需构造 Definition DSL。
WindMetricsValue<Long> count = WindMetricsValue.of("transactionCount", 3L);
WindMetricsValueSet<Map<String, Object>> summary =
        WindMetricsValueSet.of("summary", Map.of("count", 3L, "amount", new BigDecimal("12.5000")));
```

display 是调用者已有展示逻辑。query 的正常返回保证其正式查询合同；toMetricsValue 仅转换结果，不构成新的取数路由。
Capte 的值执行服务仍实际承责，MetricQueryApplicationService 保持组合委托及自己的预览/解释职责。
Wind 不在本轮增加 DSL 引擎、注册表、公共 Writer/Reader SPI 或统计平台。

用户在 Capte 统一任务进一步明确维度遍历归 Wind 公共能力，本批纳入 WindMetricsDimensionKeyProvider：

```java
List<Map<String, Serializable>> queryDimensionKeys(Set<String> dimensions, int queryPage, int querySize);
```

例如 Set.of("userId") 或 Set.of("cardId", "currency")。每行必须给出完整具名组合，按真实业务关系生成，
不自动做笛卡尔积。页码从 1 开始，页大小按组合计数，非空短页仍继续，空页才表示结束；
不支持组合抛异常，不能返回空页冒充成功。业务实现负责排序、权限和稳定分页，Set 的迭代顺序不定义排序。
本接口没有 Plan、bucket、fact keySpec、有效期或一致性 token，不宣称历史视图及跨源一致性；
变化数据集的稳定遍历和历史范围资格仍由宿主及数据提供方验证。注解表达约束，不等于 Java 直接调用自动校验。

## MIG 切片、验证与回退

前置条件：用户已确认公共能力归属，Capte 已确认可见消费者及迁移责任。写入范围由本轮差量清单限定。
MIG-001：公共值能力和结果转换。保留旧 field/factory，验证三个查询模式下两种值形态、空值、精度、同名字段隔离及原 HTTP JSON。
MIG-002：query parser、物化派生类型迁移。Capte Owner 承接 MetricMaterializationPlanApplicationServiceImpl 和
MetricDslDocumentationContractTests 两个文件的 import/类型调整；无 Capte 可见的 DependencyDsl 直接 Java 消费。
MIG-003：组合能力与依赖守卫。验证嵌套选择、所有匹配者执行、错误传播，以及公共层不导入 DSL 的源码约束。

验证在独立目录使用 Java 21 编译当前源码并运行模块测试；基线和输入摘要、日志、XML、兼容探针置于
/tmp/wind-metrics-architecture-20260915。具体执行结果以 validation.json 为准。
旧 SQL 字段的源码编译与已编译客户端链接兼容单独验证；不将纯值视图测试当作 Capte 真实 SQL、物化或生产验收。
特征测试固定原 SQL 字段消费形状；契约测试验证公共值和查询 JSON；回归测试覆盖完整模块。
PMD 和 Wind 约规检查须区分本次新增与既有告警。

主写方与发布迁移责任：Wind 交付新源码/制品与路径清单，Capte 迁移 import 后再独立编译；未核实的仓外消费者仍须升级确认。
不安装或覆盖共享 SNAPSHOT，不将包迁移静默发布为二进制兼容变更。
回滚使用本次源码差量和迁移前配套制品/消费者版本成组恢复，根 POM 和无关脏文件不进入回滚范围。
本次不引入持久化数据迁移或新的外部副作用，不需要双写或回填；重启、重放、响应不明继续遵守原物化接口的固定版本、完整桶事务和 Checkpoint 合同。

## Engineering Handoff 与停止条件

Wind 为执行 owner，Capte 统一任务负责消费确认；制品和源码验证证据分别交接。
遇到旧全限定类名反射/持久化消费者或 JSON 差异时暂停交付并扩充迁移清单，不用兼容别名掩盖缺口。

## 证据索引

- 当前 Wind 原接口、字段接口、query/DSL 源码及新增能力测试。
- Capte 消费范围回执：/tmp/metric-wind-value-contract-20260915/capte-consumer-receipt.md。
- Capte 当前消费者摘要：同目录 consumer-inputs.json；仅代表 Capte 可见工作树。
- 原根 POM 和无关修改基线：/tmp/wind-metrics-architecture-20260915/before.json。
