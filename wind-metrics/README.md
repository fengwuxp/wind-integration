# 指标物化 Plan DSL

指标公共能力与 DSL 的职责、迁移清单和调用示例见 [架构说明](ARCHITECTURE.md)。
`WindMetricsValue` / `WindMetricsValueSet` 提供策略无关的单值及多字段读取，
`MetricResult.toMetricsValue()` 将已查询结果转为该只读能力；不改变原详细查询响应。

`MetricMaterializationPlanDslCodec` 负责 schemaVersion 2 的关闭世界解析、基础校验和 canonical JSON。
`MetricMaterializationPlanDsl` 的直接及嵌套 Jackson 绑定使用同一入口。

快照执行与指标值查询的公共接口、使用示例及 Capte 实现流程见
[物化和查询接口接入说明](EXECUTION_INTERFACES.md)。
`MetricSnapshotMaterializer` 按固定计划修订推进快照；`MetricValueQueryService` 提供生效版本单查、
指定修订单查及 `MetricBatchQuery` 批量查询。Wind 提供合同与结果模型，具体运行和存储由宿主实现。

## 计划成员的固定定义版本

`metrics` 非空且指标编码唯一，每项 `definitionRevision` 在创建、更新草稿时就必须显式提供正整数。
缺失或 null 使用 `DSL_FIELD_REQUIRED`，非整数使用 `DSL_FIELD_TYPE_INVALID`，0 或负数使用 `DSL_PLAN_INVALID`；
错误路径为 `/metrics/{index}/definitionRevision`。canonical 按指标编码排序并保留每项指定版本，不补默认值。

Capte 保存时核对所选定义版本存在，复制保留来源版本，普通保存不追新；发布按既有资格规则校验并冻结这个精确版本，
不合格则拒绝，不替换为最新版本。草稿选择不因此被限制为只能选已发布版本。Wind 不解析实际定义或判定发布资格，
这一约束也不修改其他 Definition 依赖及查询的版本选择合同。

## 查询拓扑、保存形态与物化范围

这三者描述不同问题，不能把 `SNAPSHOT` 命名为“全量物化”：

| 维度 | 合同 | 决定什么 |
| --- | --- | --- |
| 查询数据来源 | SNAPSHOT / SEGMENTED | 获准区间全部读取快照，或按分段组合数据来源 |
| 结果保存形态 | METRIC_VALUE_TABLE / WIDE_TABLE | 多个指标结果如何组织为逻辑保存行 |
| 物化覆盖与进度 | Capte 的覆盖范围、目标截止时间、Checkpoint | 从哪里开始、此次推进到哪里、实际上已连续完成到哪里 |

`snapshotTarget` 可用于历史区间物化和持续按水位推进；改变一次执行的截止时间不需要改变逻辑保存合同。
如果“全量”指从声明的覆盖起点处理到当前有效截止，则它是同一时间范围模型中截止时间取当前有效边界的情形。
单独把目标设为当前时间，不代表已经完成该范围，也不表示必须重新计算已提交历史。

初次物化的水位 W 从配置覆盖起点 S 初始化，后续从已提交 W 推进下一个完整时间桶；请求目标 T 可以是过去的时间。
本次新增的是 W 之后的完整桶，累计覆盖是 `[S, 新W)`，正常追赶不重算已提交桶。
可推进上界受桶粒度、安全延迟、分段近期窗口、请求目标和计划覆盖截止约束；日、月桶使用业务时区的自然边界。
例如 Asia/Shanghai 的 DAY 桶在 2026-09-14 15:30、安全延迟为 0 时，最晚只关闭到 9 月 14 日 00:00，
不包含当天尚未关闭的部分。这个例子是边界推导，不是 Capte 运行验证。

结果行的 `bucketTimeField` 只是桶时间字段名，请求目标 T 与 Checkpoint 实际进度 W 各自独立。
查询覆盖必须依据可读 Checkpoint：`SNAPSHOT` 要求获准区间已被覆盖；当前 Capte 的 `SEGMENTED`
按实际 W 切历史快照与近期实时，不能用预计 recentWindow 边界冒充完成水位，也不能用缺失或不可读 Checkpoint 绕过准入。
GET 观察到水位达到目标只证明覆盖；未达到目标不证明原请求已停止，未知状态不能据此自动重提。

宿主聚合、时间规范化、调度与回填必须使用同一固定业务时区；未指定 ZoneId 的 `LocalDateTime.now()`
不能用于解释业务桶或时间型维度。JVM 默认时区与业务时区不同的行为需由消费端验证。

## 快照逻辑保存合同

`SNAPSHOT` 使用根级 `snapshotGranularity` 和 `snapshotTarget`；`SEGMENTED` 的快照分段使用同样的字段。
实时分段禁止声明这两个快照字段。`snapshotTarget` 只允许以下三个必填字段：

| 字段 | 类型 | 含义 |
| --- | --- | --- |
| storageType | METRIC_VALUE_TABLE 或 WIDE_TABLE | 逻辑保存形态 |
| bucketTimeField | 字符串 | 结果行的逻辑时间字段，例如 bucketEndTime |
| valueMappings | 非空对象数组 | 指标结果到逻辑结果字段的映射，每项只有 metricCode 和 fieldName |

指标值表允许不同 SCALAR 指标共用逻辑保存名 `value`：

```json
{
  "snapshotTarget": {
    "storageType": "METRIC_VALUE_TABLE",
    "bucketTimeField": "bucketEndTime",
    "valueMappings": [
      {"metricCode": "USER_WALLET_INCOME_TOTAL", "fieldName": "value"},
      {"metricCode": "USER_WALLET_TRANSACTION_COUNT", "fieldName": "value"}
    ]
  }
}
```

宽表同一统计主体、维度和时间桶的一行承载多个指标结果。以下两个 SCALAR 结果分别保存为
`incomeTotal`、`transactionCount`；FIELD_SET 指标的固定定义包含 `incomeAmount`、`incomeCount` 两个业务输出：

```json
{
  "schemaVersion": 2,
  "executionMode": "SNAPSHOT",
  "snapshotKeyProviderCode": "WALLET_KEYS",
  "metrics": [
    {"metricCode": "USER_WALLET_INCOME_TOTAL", "definitionRevision": 2},
    {"metricCode": "USER_WALLET_TRANSACTION_COUNT", "definitionRevision": 7},
    {"metricCode": "USER_WALLET_SUMMARY", "definitionRevision": 3}
  ],
  "snapshotGranularity": "DAY",
  "snapshotTarget": {
    "storageType": "WIDE_TABLE",
    "bucketTimeField": "bucketEndTime",
    "valueMappings": [
      {"metricCode": "USER_WALLET_INCOME_TOTAL", "fieldName": "incomeTotal"},
      {"metricCode": "USER_WALLET_TRANSACTION_COUNT", "fieldName": "transactionCount"},
      {"metricCode": "USER_WALLET_SUMMARY", "fieldName": "incomeAmount"},
      {"metricCode": "USER_WALLET_SUMMARY", "fieldName": "incomeCount"}
    ]
  }
}
```

## 校验与规范化

- 每个映射的 `metricCode` 必须存在于计划的 `metrics`；允许同一指标映射多个不同字段。基础校验不要求每个计划指标都出现在每个目标中。
- 指标编码沿用 `[A-Za-z][A-Za-z0-9_]*`、最长 100 字符；逻辑字段沿用同一格式、最长 64 字符。
- Wind 不额外要求映射组合或字段名唯一，也不把逻辑名称相同直接判定为物理列覆盖。
  重复声明保留；消费端须结合实际结果和冻结绑定校验保存关系，不能静默覆盖结果。
- 未知字段、重复 JSON 成员、错误容器类型、显式 null 均被拒绝，返回既有 `MetricErrorCode` 和 JSON Pointer 路径。必填字段缺失或 null 使用 `DSL_FIELD_REQUIRED`，显式 null 的 `snapshotTarget` 使用 `DSL_FIELD_TYPE_INVALID`。
- canonical 固定输出 `storageType`、`bucketTimeField`、`valueMappings`；映射按 `metricCode`、`fieldName` 排序。原对象及集合不随规范化改变。
- Wind 基础校验不解析指标定义；结果来源由消费端依据已冻结定义解析。映射不声明 SUM、COUNT 等内部计算状态。

## 业务结果的映射来源

`metricCode` 定位计划中的固定指标成员，`fieldName` 指定逻辑保存字段名。两种值结构的来源解释如下：

| 固定定义的值结构 | 业务结果来源 | fieldName 的作用 |
| --- | --- | --- |
| SCALAR | `value` 定义计算得到的唯一业务结果 | 为该结果声明逻辑保存名称，例如 value 或 incomeTotal，来源由指标唯一确定 |
| FIELD_SET | `fields[fieldName]` 定义计算得到的同名业务结果 | 同时定位来源业务输出和声明逻辑保存名称；本合同没有另一份输出别名对照 |

这不增加来源字段、映射唯一性或 Wind 解析拒绝规则。消费端须核对固定定义的真实输出，不能将任意合法字符串视为已存在的结果。
不同指标的同名字段仍以 `(metricCode, fieldName)` 区分，宽表私有绑定须保留这个结果身份；重复声明不会在 Wind canonical 中丢失。
同一 `(metricCode, fieldName)` 的重复声明在 Capte 执行时对应一个有效结果位置，复用固定来源和绑定。
例如同一结果为 100，声明两次仍保存 100，不能重复累计为 200 或多写一条值记录。
同一结果身份存在不一致的实际绑定时，由消费端报告真实冲突，不能将旧 Writer 的重复 measure 异常作为 Wind 拒绝声明的理由。

业务输出映射不替代内部可合并状态。跨桶比率等结果需要的 SUM、COUNT 等充分状态，仍由 Capte 内部保存、恢复和合并；
不能平均各桶的最终比率，也不能把内部状态伪装成新业务映射项。本节描述逻辑合同，不代表消费端已实现业务投影与状态的完整读写。

## 从冻结上下文推导唯一键

`METRIC_VALUE_TABLE` 和 `WIDE_TABLE` 都必须推导完整唯一键，用于查询与写回。Plan DSL 不再声明 `rowKeys`；
统计身份由固定指标定义中的主体和完整维度决定，Provider 提供实际值，Normalizer 生成稳定的维度键。
适配器结合时间桶、粒度以及冻结的指标、定义版本、计划、分段和目标作用域，确定完整的保存与寻址关系。
这份关系必须在查询、写回、重试和恢复时保持一致，不能为宽表省掉必要键，也不能把删除重复配置理解成取消唯一约束。

| 保存形态 | 上下文必须确定的唯一定位关系 |
| --- | --- |
| METRIC_VALUE_TABLE | 在目标作用域内，以完整统计身份、时间桶以及指标、定义版本、逻辑结果字段区分每个值项 |
| WIDE_TABLE | 在冻结成员及目标作用域内，以共同统计身份和时间桶定位唯一行；同时保留每个指标、定义版本、逻辑结果字段到实际值位置的唯一对应 |

上述含义不要求每项上下文都新增一个物理主键列；实际库表、分区或唯一键怎样保证隔离，由 Capte 私有绑定承接。
多个成员共用业务行之前，须核对主体、完整维度、类型和时间语义一致；相同的 `subjectId / dimensionKey` 字段名不能证明口径相容。
例如同一用户的 USD 和 CNY 必须保持不同统计身份，不能因保存映射少带维度而合并或覆盖。
GLOBAL 没有业务主体或维度时也必须从桶和版本等上下文确定唯一位置，内部规范化占位值由既有适配链处理，不要求业务造键。
GLOBAL 定义包含维度时，仍须保留全部真实维度；GLOBAL 不代表可以省略统计身份。

发布时核对输出身份和实际绑定的完整性、类型及唯一约束；发布后保存固定定义引用和物理绑定，运行时不得重新解析当前目标。
Writer 与 Reader 必须依据同一冻结关系寻址；仅有确定性键仍不等于幂等保证，完整桶结果和必要内部状态写回、回读以及 Checkpoint CAS
需要在既有事务边界内一致提交。失败重试不得重复累计或留下半行。维度、类型、规范化规则或存储关系的不兼容变化须通过新版本和明确的重建/迁移承接，不能静默改变旧快照的解释。

计划成员的定义修订号可以不同。宽表不能任取一个成员的 `definitionRevision` 表示整行，也不能直接删掉旧版本键来放开多成员。
旧 Writer 的 A@2/B@7 拆行或同号 value 冲突，是绕开单成员守卫后直接扩用旧逻辑的结构反例；当前守卫已拒绝多成员，并无已发生串写的证据。

Wind 提供逻辑合同的解析与规范化；两种保存形态完整的唯一键推导、冻结绑定和实际读写由 Capte 承接。
其行为验收须覆盖两种形态的同键查询/写回、不同维度互不覆盖、独立版本隔离、GLOBAL、同桶失败与重试以及历史回读；Wind codec 测试不代这些运行证据。

## Wind 与 Capte 边界

本合同采用“Wind 逻辑保存目标 + Capte 存储适配器私有物理绑定”的方案 B。Capte 按
`planCode + planRevision + segmentCode` 保存并冻结实际物理绑定；运行、重试和恢复读取冻结绑定，不重新解析当前物理目标。
`bucketTimeField` 不是运行水位；水位由 Capte `Checkpoint.watermarkTime` 承接。

Wind 不接收目标编码、数据源、表名、物理列名、SQL 类型、凭据、`targetConfig`、`storageCode` 或私有 binding。
已移除的 `rowKeys`、旧 `snapshotTargetCode` 与未提交草稿的 `targetCode / targetType / resultMappings` 均不受支持；关闭世界解析拒绝这些字段，不提供兼容桥。
消费端须同步更新 Java 构造调用与 Plan JSON 后才能使用本合同；Wind 的测试不代表 Capte 存储适配器、服务事务或生产验证。

设计依据：Capte 会商回执 `METRIC-WIND-20260910` 第 3.1、8.1、13.6、14.2～14.8 节，以及
2026-09-14 用户转交的最终设计，以及用户后续明确同意移除 `rowKeys`、要求两种存储形态均从上下文推导查询与写回唯一键的决定。
此前四字段候选已被本次三字段合同替代。
查询拓扑、行身份及时间边界补充依据为 `WIND-CAPTE-20260914-SNAPSHOT-SEMANTICS-R2`；该语义回执不等于最终提交确认。
Plan 成员显式版本依据为 Capte D 转交的用户纠正及 B21.84～21.85；此前省略版本并在发布时选择最新版本的合同退出。
