# 指标公共能力与 DSL 分层重构

架构类型：系统架构，描述 Java 指标库的能力与实现边界。Wind 持有公共合同、DSL 通用数值与 JDBC 编译能力；Capte 持有查询编排、统计和存储实现。
依据：用户确认 WindMetricsValue 等是面向开发者的统一能力，DSL 只是定义方式；2026-09-15 当前源码及 Capte 消费范围回执。

## 重构准入与非目标

开发者应能以同一种方式读取代码、SQL 或 DSL 定义的单值和多字段指标，无须判断实时、快照或分段。
原公共能力拆分范围为 wind-metrics 源码、测试和文档。后续用户授权的历史组合接口渐进退役，
同时覆盖 Capte/nobe 的 SQL 字段工厂、具体转换、核心读取消费者与相关测试；根 POM、KMS 和共享依赖仓库不在写集内。
Definition JSON、显式定义修订和宿主事务职责保持。查询条件及聚合兼容入口按 QUERY_MIGRATION.md 统一。
后续物化全流程会商将 Plan JSON 升为 v3，目标以 objectTypeClassName 替代 valueMappings，详见下文。

当前问题与证据：原设计有独立能力价值，不能因未引用 DSL 而划为废弃实现。当前调用链中，Capte 的
DefaultMetricsFieldFactory、SqlSingleMetricsField、SqlMultipleMetricsField、
DefaultMetricsAggregatorFactory 和 OfflineMetricReportStatisticsExecutor 均是实际消费者。
已确认的缺口是：只读多字段访问绑定到条件求值接口；新查询结果与原具名值能力分离；
正式查询解析和发布派生模型位于 DSL 包；组合实现无条件 supports=true，嵌套选择时会抢占不支持的类型。

## 目标结构与职责

| 层级 | 类型 | 责任与边界 |
| --- | --- | --- |
| 公共取值 | WindMetricsValue、WindStructuredMetricsValue | 读取指标名称、单值或指标内部的具名字段，不关心来源、路由、版本管理和存储 |
| 取值能力工厂 | WindMetricsValueFactory | 按名称和 MetricQuery 创建数值或多字段读取能力，不提前读取、不承接重新求值或修改 |
| 维度键遍历 | WindMetricsDimensionKeyProvider | 由业务方按单维度或组合维度分页提供完整具名键，不带快照上下文 |
| 条件计算及对象组装 | WindMetricsEvaluator、WindMetricsAggregator、工厂 | 按约定条件求值，将多个值组装成开发者对象；Aggregator 不等于 SUM/COUNT 运算器 |
| 业务统计 | WindMetricsStatisticsExecutor | 按业务对象执行统计，不将结果读取等同修改能力 |
| 历史组合接口 | WindMetricsFieldFactory、SingleValueMetricsField、MultipleValueMetricsField | 渐进弃用；保留工厂、读取、求值和历史修改/转换签名以兼容旧消费者 |
| 查询能力 | query | 查询输入、带诊断信息的结果及值查询合同；计划执行和物化结果由宿主承接 |
| DSL 定义实现 | dsl.definition、dsl.materialization、filter、literal、codec | 描述、校验和规范化 Definition/Plan 语法，由宿主编译消费；Wind codec 本身不执行查询 |
| 受限表达式 | dsl.expression 的 Compiler、CompiledMetricExpression、MetricValueReference | 编译与验证 AST、提取结果引用、执行受限表达式，AST 不进入公开签名 |
| DSL 数值解释 | dsl.MetricValueCalculator | 直接读取原定义，精确合并 measure、归一类型、回调表达式并应用最终空值规则 |
| JDBC 编译 | jdbc.MetricJdbcSqlCompiler、MetricJdbcBinding、CompiledMetricSql、MetricSqlBinding | 用原 DSL、当前条件与冻结物理绑定生成 MySQL SQL、有序参数及 measure 投影；不执行 JDBC |
| 协议基础 | json.MetricJsonSupport | 查询与 DSL 共用的严格 JSON IO、JSON Pointer 与序列化，不决定业务规则 |
| 宿主实现 | Capte 或其他实现方 | 选定定义及路线，完成权限、取数、计算、结果转换、持久化、冻结绑定与事务 |

依赖方向：实现依赖公共能力；公共能力、query、json 均不导入 dsl 或 jdbc。
JDBC 编译层按职责依赖 dsl 与 query；数值计算位于 dsl，二者都不依赖宿主 Compiled 模型。
既有 Jackson 注解绑定继续使用，但查询模型绑定自己的 query 解析器。
DSL 不是实时/分段查询策略本身，它描述规则；路线选择和执行是宿主对能力接口的实现。

## DSL 内容迁移裁决

| 原内容 | 裁决 | 理由及兼容影响 |
| --- | --- | --- |
| query.MetricQueryJsonParser | 保留通用条件解析 | 当前解析七字段 MetricQuery，由原 Criteria 解析器改名；历史同名解析器承载的带编码请求已退出，指标身份由服务参数或宿主协议接收 |
| materialization.MetricMaterializationDependency、MetricMaterializationMeasure、enums.MetricMergeState | 删除 | 不再包装原定义的 measure 或建立临时合并状态映射；发布校验直接复用 MetricValueCalculator.validateMergeable |
| materialization.MetricSnapshotMaterializer、MetricMaterializationResult、MetricMaterializationSegmentResult | 删除 | 固定计划执行、实际覆盖与提交水位属于宿主；Capte 使用既有应用入口、执行器和结果 DTO，不新增同义替代类型 |
| MetricDslJson 的严格读取、序列化、路径 | 提取到 json.MetricJsonSupport | 查询和 DSL 复用同一实现，保留精确数值、重复字段、尾随内容和原错误码 |
| MetricDslJson 的 required/optional/type/enum 等 | 留在 DSL，包内可见 | 它们解释 DSL 关闭世界语法，不属于开发者指标能力 |
| DefinitionSpec、Value/Measure/Expression、Subject/Time、selection、filter、literal | 留在 DSL | 表达定义结构及字段引用，既不是指标值，也不是执行服务；上移将使公共层依赖 DSL 语法 |
| Plan、Reference、Segment、SnapshotTarget | 归属调整为 Capte；消费迁移前暂留 | 计划配置与编解码随宿主物化能力迁出；模型本身仍有职责，不能直接破坏现有消费 |
| 共用 enums、MetricQuery/MetricResult | 保持 metrics 下既有子包 | 已是共用语义或能力合同，不需要平铺到根包 |

上述三类旧路径直接迁移，不保留两套同义模型或回退解析入口。这是 Java 源码/二进制包名变化，需消费者随制品重新编译；这些包迁移本身不改变 JSON。Plan v3 是后续单独确认的契约变化。
WindMetricsValue 的取值方法保持。field、factory 的旧聚合参数入口保留兼容并逐步迁移至 WithCriteria。
WindMetricsValueSet 直接删除，旧 getMetricsFields/findByName/asValues 不再保留；
MultipleValueMetricsField 直接继承 WindStructuredMetricsValue，以 asFieldValues 提供字段映射。
新旧工厂共用 ReadOnlyStructuredMetricsValue 一个实现，它仅实现 WindStructuredMetricsValue。
Capte 的物化聚合器、物化执行器及 Capte/nobe 的 SQL 字段实现仍有旧接口消费；待消费者迁移及公共制品兼容核对后再裁决移除。
仅识别旧接口的宿主入口应继续接收工厂结果或旧接口实现；直接实现 WindStructuredMetricsValue 的自定义结果，
需要宿主先将类型判断迁移至新接口。

## 对象设计、行为不变量与公共契约不变量

- WindMetricsValue 只约定读取。getValue 允许定义认可的正常空值；执行错误必须传播，不能用 null/0 隐藏。
- WindMetricsValue.of 固定名称和值引用。WindStructuredMetricsValue.of 复制字段容器并保留顺序、null；
  getValue 与 asFieldValues 返回同一个只读 Map。字段值保留原引用，业务对象的后续修改仍可见，不承诺深度不可变。
- WindStructuredMetricsValue 表达“一个指标的结构化值及其具名字段读取能力”；历史 MultipleValueMetricsField 还混合了求值和对象转换。
  只读结果不继承 mutable field，避免为满足接口而实现抛异常的 increase/setValue。
- MetricResult.toMetricsValue 只从已计算结果派生值视图，无 IO、无重新查询、无依赖重选。
  SCALAR 返回具名 Number，FIELD_SET 返回具名字段集；Java 数值类型、精度和字段存在性保持。
- getName 返回指标名称（DSL 查询结果使用指标编码），Map 的 Key 是该指标内部的输出字段名，Value 是实际字段结果。
  字段名只在所属指标内唯一，不表示维度键，也不直接表示物理列名。A/value 与 B/value 分别属于两个指标。
  asFieldValues 直接返回字段映射，不再将字段包装为 WindMetricsValue。
  值视图不承担存储身份；定义修订、时间、覆盖和执行路由保留在 MetricResult 及宿主冻结上下文。
- MetricResult 构造器、record 访问器及 HTTP JSON 结构保留。toMetricsValue 不是 Bean getter，不增加响应字段。
- 通用 MetricQuery 保留多主体、可空时间、任意变量和标签；正式 DSL 执行入口显式校验其受限形状。
  聚合/求值/字段工厂已增加 WithCriteria 入口；旧外观只适配可无损表达的条件，不合并独立维度和参数。
- Resolver 是按顺序选一个支持者，StatisticsExecutor 是执行全部匹配者。supports 与真实能力一致，执行失败不自动换实现。
  本次不把“多个主体汇总”和 batchQuery 的“同一条件多个指标”混为一种请求。

## 历史组合接口渐进退役

根包 WindMetricsValueFactory.value(name, query) 返回 WindMetricsValue，fieldValues(name, query)
返回 WindStructuredMetricsValue。单值沿用 Number 范围，多字段整体值可为 Map 或业务对象，不新增类型转换合同。
旧 WindMetricsFieldFactory 标记 Deprecated，forRemoval=false，不继承新接口，仅保留历史 single/multiple 入口。
Capte 工厂通过 value/fieldValues 直接创建原生消费 MetricQuery 的 SQL 读取对象；single/multiple 保留旧调用兼容。
只读指调用方仅依赖读取能力，不代表实际对象不可变或查询结果被缓存；SQL getValue 每次仍执行查询。
新入口仍按实现能力解释通用条件：Capte 原生消费 MetricQuery，nobe 的旧适配不能表达独立维度时明确拒绝。
这些入口没有将旧 SQL 配置桥接到正式 DSL 查询服务。

SingleValueMetricsField 和 MultipleValueMetricsField 标记 Deprecated，forRemoval=false。
旧字段接口的 evaluate 及修改方法保持；需要重求值的调用方继续将旧工厂结果
赋给 WindMetricsEvaluator。只读消费者依赖 WindMetricsValue/WindStructuredMetricsValue，三个修改方法不进入公共只读值接口。
尚未确认真实可变实现，因此不新增可变值接口。当前工厂实现仍可保留旧组合接口作为兼容实现；
新工厂可以直接实现 WindMetricsValueFactory 的两个方法，无需实现 single/multiple、evaluate 或修改方法。
新接口不引用 fields、DSL 或 JDBC，不为旧接口迁包复制同义工厂实现。旧接口继承新只读能力用于渐进兼容，
不改变宿主应用服务组合委托和事务归属。

Capte/nobe 的 SQL 多字段实现显式承担对象到 Map 的转换，每次 asFieldValues 读取一次。
旧接口的默认转换暂留，保证未迁移的已编译实现继续运行；新公共多字段能力不依赖 JSON。
SQL 默认工厂的 setValue 从空操作改为明确抛出 UnsupportedOperationException，与其不支持增减的能力一致。
这是一项有意的行为修正：以前无声成功的写调用现在失败，查询错误仍向上传播；本批没有存储写回流程。

首批收窄 Capte/nobe 核心聚合器、nobe Vcc Resolver 的显式旧类型依赖；旧查询分支保留原工厂路径，
保持查询变量修改的可见性。其他业务域的内联 single/multiple 读取暂由兼容签名承接。
删除旧接口、旧工厂返回签名和默认转换的条件是生产消费者迁移、求值入口接替及公共制品兼容核对完成。
本批单独验证 Wind 合同、宿主真实 SQL/模板/对象组装和旧字节码运行；这些结果不代表全部外部制品已确认。

## 调用方式与实现交接

```java
// queries 是宿主实现的 MetricValueQueryService；调用者不依赖 DSL 类型。
MetricResult detailed = queries.query(metricCode, criteria);
WindMetricsValue<?> value = detailed.toMetricsValue();
display(value.getName(), value.getValue());

// 只根据结果形态访问具名字段，不根据实时/快照/分段路线写分支。
if (value instanceof WindStructuredMetricsValue<?> fields) {
    Map<String, Object> fieldValues = fields.asFieldValues();
    if (fieldValues.containsKey("transactionCount")) {
        Object count = fieldValues.get("transactionCount");
        // containsKey 为 true 时，null 表示字段存在且结果为空。
    }
}

// 代码或 SQL 实现可以直接提供同一个能力；无需构造 Definition DSL。
WindMetricsValue<Long> count = WindMetricsValue.of("transactionCount", 3L);
WindStructuredMetricsValue<Map<String, Object>> summary = WindStructuredMetricsValue.of(
        "TRADE_SUMMARY", Map.of("transactionCount", 10L, "transactionAmount", new BigDecimal("1250.00")));
// summary.getName() 返回 "TRADE_SUMMARY"；summary.getValue() 是整个字段 Map。
// summary.asFieldValues().get("transactionCount") 返回 10L。
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
多个实现由宿主按 `getProviderCode()` 返回的编码选择；Plan 的 `dimensionKeyProviderCode` 指向该编码。
编码确定来源及范围，dimensions 确定完整键形状；同形键不能作为自动选择不同业务来源的充分条件。
本接口没有 Plan、bucket、fact keySpec、有效期或一致性 token，不宣称历史视图及跨源一致性；
变化数据集的稳定遍历和历史范围资格仍由宿主及数据提供方验证。注解表达约束，不等于 Java 直接调用自动校验。

## 物化快照调度、构建和保存

依据：WIND-CAPTE-SNAPSHOT-FLOW-20260915-R1 双边会商，用户要求先确认完整流程再重构。
复用既有 WindMetricsAggregatorFactory、WindMetricsAggregator 和 WindMetricsValue/WindStructuredMetricsValue，
不增加 SnapshotAggregator、ValueRef、公共物化上下文或保存 SPI。

Plan v3 的 snapshotTarget 只包含 storageType、bucketTimeField、objectTypeClassName。
最后一项是 factory(Class<T> objectType) 的目标类型全限定二进制类名，允许使用 Class.getName() 得到的嵌套类名；
不是工厂实现类。Codec 只校验格式和规范化，不加载宿主类。
删除 MetricSnapshotTargetMappingDsl/valueMappings，不提供 v2 兼容执行、自动迁移或回退解析。
旧持久化计划不能改写为新结构后沿用原冻结摘要；实际历史数据处理不在本次代码重构范围。

固定成员确定指标及定义修订。宽表标量属性与 metricCode 同名，字段集使用定义内 fieldKey 同名属性；
不从 value 猜 incomeTotal，不隐式驼峰转换，不自动加前缀。宿主验证属性、数值类型及成员间/身份字段冲突，
不存在或重名必须报告具体来源。多个 FIELD_SET 可以组合；字段冲突不等于该值形态整体被禁止。
行式快照仍按单成员、单值字段构造记录，一个 record 不能代表多指标宽表行。

完整运行时协作：

1. 宿主既有周期任务触发 Scheduler。按 Plan 修订主键分页扫描已发布且手动开启物化的计划，
   本轮共享 targetTime；单计划失败继续其他计划，扫描完报告错误。查询启用状态与物化开关分开。
2. Task 复核计划及 Checkpoint 状态，由宿主既有物化执行器承接计划执行。
   宿主恢复固定成员和分段，按 HOUR/DAY/自然 MONTH、时区、覆盖、安全延迟及桶数预算确定各段目标。
   只执行 SNAPSHOT 分段；返回值的实际覆盖不能被输入 targetTime 代替。
3. Runner 对一个分段完整桶开启事务。公共维度 Provider 按完整主体/维度组合分页；非空短页继续，空页结束。
   无维度 GLOBAL 由宿主执行一次，不向要求非空 dimensions 的 Provider 发空维度请求。
4. 同一页 Key 对全部固定版本成员计算，按每个成员的类型归一后必须得到同一业务行身份。
   将已计算值表现为 WindMetricsValue/WindStructuredMetricsValue，正常 null 与缺失字段区分。
5. 宿主注入实现现有 Factory 的物化工厂，按目标 Class 创建 Aggregator、自动 named，再 aggregate(query)。
   queryVariables 中的宿主私有只读上下文提供当前键、固定版本、桶、已算值和原始状态；
   不放入 DSL/HTTP/Provider 参数或共享可变缓存。工厂不重查旧 SQL、不追新、不回读正在生成的快照。
6. 工厂返回完整保存对象。Bean 可在返回前填齐；record 使用规范构造器一次传齐，不能返回后 setter 补身份。
   行式记录交既有快照基础服务；宽表对象由宿主现有 BaseMapper<T> 元数据找到实际保存者，经 Writer 写入并读回。
   对象类可加载不表示存在 Mapper；零个或多个匹配保存者、目标字段/唯一键不符均拒绝。
7. 全部键页、全部成员、业务输出及必要合并状态成功保存和校验后，才 CAS 推进本桶 Checkpoint 并提交。
   任一步失败回滚当前桶；之前已提交桶保留，恢复时重读水位。重试不依赖新增 Run/Batch 系统。

页面是 SQL/内存批量边界；完整桶才是事务边界。必要 STATE 与 OUT 的读写解释继续由冻结定义和宿主绑定保持，
均值、比率等不能只保存最终展示数再跨桶累加。真实目标缺少必要状态存储时拒绝执行，不自动建表。
上述用户范围保留普通权限和审计，不引入审批；物化任务本身不等于实际宿主调度已部署。

Wind 验证覆盖 v3 JSON 直读/嵌套绑定、严格字段校验、目标类名、分段目标与固定成员；
Capte 验证真实分页计算、工厂、Entity/record、Mapper/Reader、事务失败和重放恢复，不能用 Wind 契约测试替代。
会商和两仓差量证据分别位于 /tmp/WIND-CAPTE-SNAPSHOT-FLOW-20260915-R1 与
/tmp/wind-capte-snapshot-flow-20260915，后者的基线测试不代表新流程已验证。

## R3：SQL 编译、通用数值与宿主执行收缩

依据：CAPTE-WIND-JDBC-20260915-R3；Capte 原任务已确认 JDBC 四个公开类型和 MetricValueCalculator
四个方法。此次修正旧 SQL 全留宿主的包位，保持 R2 的 DSL 唯一声明来源。

MetricJdbcSqlCompiler.compile(definition, criteria, binding) 直接消费原 MetricDefinitionSpec。
MetricJdbcBinding 的 tableName、columnName、javaType、jdbcType、toJdbcValue 读取宿主同次验证冻结的索引；
主事实引用为空字符串，关联事实引用为 DSL join alias。宿主可由 CompiledMetricDefinition 直接实现该接口，
不复制 Fact/Field 描述类，不重新 parse、扫描 Entity 或查询数据库元数据。
编译产物保留 SQL 占位符次序；JOIN、measure、维度与投影按声明的确定顺序生成。
renderValidatedFilter 为实时、有限行集和物化页复用同一过滤语义；列引用转换由受信宿主代码提供。

2026-09-16 SQL 后端改用 jOOQ 3.21.8，仅构造、渲染 SQL，不执行 JDBC 或生成实体代码。
原构造器默认 MYSQL；新增 (ZoneId, int, SQLDialect) 构造器显式选择 MYSQL、POSTGRES、H2。
SQLDialect 仅为编译配置，第三方 AST 不进入 Definition、Binding 或 CompiledMetricSql。
最终渲染时按每个参数实际出现的顺序收集原 MetricSqlBinding，并输出问号，避免再次转换宿主编码值。
SQL 文本允许 AS、OUTER、括号等方言渲染差异；参数值、JDBC 类型及投影合同保持。
renderValidatedFilter 的列引用 SQL 仍由宿主提供，必须匹配当前方言；已编码物化键仍不得重编码。
这次后端替换没有扩展 Definition v1：全量时间、单侧截止、skip 和派生参数传递仍需配套 DSL/宿主变更。
原生 jOOQ 的 CTE、窗口、分页探针证明后端能力，不等于公共指标 DSL 已支持这些声明。

Wind 使用显式 ZoneId、精确数值和字段 Java 类型归一每个实际参数，再调用一次宿主 codec。
此次修正旧 String、UUID、时间路径绕过 codec 的行为；Character 使用单字符合同。
已由物化键逻辑编码的 JDBC 值不再次传入 codec。SQL 值使用占位符，裸表列名独立校验。
MetricSqlBinding 构造和读取时复制 byte[]、Timestamp（保留纳秒）及 Date；其他 codec 值须不可变。
冻结定义修订、计划、水位和物理解释是宿主职责，不能只固定一个 SQL String 便宣称冻结成立。

MetricValueCalculator.validateMergeable 在数据加载前拒绝 AVG 缺充分状态和 rowSelection 分桶。
merge 使用实际 Map 精确合并 COUNT/SUM/MIN/MAX，不舍入、不算表达式、不应用 orElse。
calculate 先归一全部 measure，再把只读 measure Map 交给宿主已编译表达式回调，最后统一应用 orElse。
normalize 可用于宿主有明确类型合同的单值归一，不带默认值行为。COUNT null、缺字段、溢出、浮点数和
表达式错误均失败；正常 null 保留。Wind dsl.expression 持有受限 AST 编译、依赖提取与 ratio 求值；Capte 持有依赖闭包及修订/结果加载，不再重复数值或表达式规则。
本类不创建 Definition/Value/Measure 的声明副本，不新增 execution 包或 RawValues 包装。

用户明确要求 Capte 旧 dsl.execution 12 类及 package-info、旧 dsl.expression 整包退出。Wind 承接上述通用能力；
Capte 将真实查询行为收敛到服务普通查询及 preview/explain 的内部查询实现，快照读取和键归一归既有
storage/materialization。不得换名保留 Engine/Executor 转发链。普通权限、事务、同次修订闭包、分段覆盖、
完整桶提交与异常传播保持。最终退出与宿主行为验收由 Capte 原任务回执确认，Wind 单元测试不替代它。

表达式句柄仅保存真实编译增量：私有 Spring AST、已确认的 localValueFields、metricValueReferences 和 ratio 标识。
原始 DSL 不复制到句柄；canonicalAst 使用 toStringAST 保持发布比较语义。evaluate 直接接收原 MetricValueDsl、
实际 measure/依赖值和错误路径，不引入 Context/CompiledValue，宿主无法构造任意 AST。
默认直接使用 Spring Expression：句柄通过 SimpleEvaluationContext 求值，只读字段访问器读取本次引用值，
方法白名单仅向 Spring DataBindingMethodResolver 开放 Root 的 metric/ratio；禁用赋值，类型、构造器和 Bean
引用由 SimpleEvaluationContext 限制。Root 仅承接预载值读取与 ratio 精度，不再设置独立 Evaluator 或手写方法执行器。
wind-script 当前封装会替换传入上下文的方法解析器，且不提供指标依赖提取，因此这里沿用已有 spring-expression
依赖。简单优先：复用现成解析和求值能力，只保留有实际调用语义的指标规则，不预设多引擎接口或额外缓存。
MetricExpressionDsl 保留 type/value 结构及既有 SPEL JSON 值。当前 wind-script 的 OperandType、LogicalOp、Op
分别描述操作数类型和运算符，不是表达式语言枚举；没有同义公共枚举可复用时，保留 MetricExpressionType，
不为减少一个本地枚举新增跨模块模型或依赖。语言类型声明不意味着开放其他脚本执行能力。
保留 2048 字符、32 层深度、AST/方法/属性白名单及常量溢出预检；运行时同样限制实际引用集合。
本地/依赖字段缺失、非精确输入与表达式错误显式失败；常量及 measure 运算的最终类型由 Calculator 统一归一。
新增 spring-expression 直接依赖，沿现有父 BOM 受管版本；不依赖 Capte 或动态查库函数。

提供者源码与输入清单位于 /tmp/capte-wind-jdbc-20260915/wind-implementation；测试使用独立构建目录和
Java 21。交付制品供 Capte 独立临时仓库消费，不安装至共享 Maven 仓库。无 Git 或发布操作。

## MIG 切片、验证与回退

前置条件：用户已确认公共能力归属，Capte 已确认可见消费者及迁移责任。写入范围由本轮差量清单限定。
MIG-001：公共值能力和结果转换。保留旧 field/factory，验证三个查询模式下两种值形态、空值、精度、同名字段隔离及原 HTTP JSON。
MIG-002：query parser 归位；退出物化公共包装。Capte 发布校验直接调用 MetricValueCalculator.validateMergeable，
将提供者校验异常转换为既有 MATERIALIZATION_PLAN_INVALID；保留 AVG、rowSelection、无原始 measure 的拒绝规则。
Wind 删除 materialization 包及孤立的 MetricMergeState；不改 dsl.materialization 的计划声明及编解码。
这是 Java 源码和二进制不兼容删除，消费者必须配套重新编译；已发布的仓外消费者兼容尚未核实。
MIG-003：组合能力与依赖守卫。验证嵌套选择、所有匹配者执行、错误传播，以及公共层不导入 DSL 的源码约束。

验证在独立目录使用 Java 21 编译当前源码并运行模块测试；基线和输入摘要、日志、XML、兼容探针置于
/tmp/wind-metrics-architecture-20260915。具体执行结果以 validation.json 为准。
旧 SQL 字段的源码编译与已编译客户端链接兼容单独验证；不将纯值视图测试当作 Capte 真实 SQL、物化或生产验收。
特征测试固定原 SQL 字段消费形状；契约测试验证公共值和查询 JSON；回归测试覆盖完整模块。
PMD 和 Wind 约规检查须区分本次新增与既有告警。

主写方与发布迁移责任：Wind 交付新源码/制品与路径清单，Capte 迁移 import 后再独立编译；未核实的仓外消费者仍须升级确认。
不安装或覆盖共享 SNAPSHOT，不将包迁移静默发布为二进制兼容变更。
回滚使用本次源码差量和迁移前配套制品/消费者版本成组恢复，根 POM 和无关脏文件不进入回滚范围。
本次不引入持久化数据迁移或新的外部副作用，不需要双写或回填；重启、重放、响应不明继续遵守宿主的固定版本、完整桶事务和 Checkpoint 合同。

## Engineering Handoff 与停止条件

Wind 为执行 owner，Capte 统一任务负责消费确认；制品和源码验证证据分别交接。
遇到旧全限定类名反射/持久化消费者或 JSON 差异时暂停交付并扩充迁移清单，不用兼容别名掩盖缺口。

## 证据索引

- 当前 Wind 原接口、字段接口、query/DSL 源码及新增能力测试。
- Capte 消费范围回执：/tmp/metric-wind-value-contract-20260915/capte-consumer-receipt.md。
- Capte 当前消费者摘要：同目录 consumer-inputs.json；仅代表 Capte 可见工作树。
- 原根 POM 和无关修改基线：/tmp/wind-metrics-architecture-20260915/before.json。
