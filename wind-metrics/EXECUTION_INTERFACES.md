# 物化和指标值查询接口接入说明

架构类型：Wind 公共 Java 能力合同，Capte 提供执行实现，技术架构复用宿主既有计算和存储链。
背景与目标：调用方需要通过完整能力入口生成快照或查询指标值，由宿主隐藏定义解析、Provider、计算、
存储布局、状态合并及事务细节。

## 接口契约与职责

| 接口 | 能力 | 实现归属 |
| --- | --- | --- |
| `com.wind.integration.metrics.materialization.MetricSnapshotMaterializer` | 按固定计划修订推进快照，返回各 SNAPSHOT 分段的实际覆盖 | Capte 既有物化应用服务与 Runner |
| `com.wind.integration.metrics.query.MetricValueQueryService` | 按生效或指定定义修订查询最终指标值，支持共同条件批量查询 | Capte 既有查询应用服务与执行链 |

```java
MetricMaterializationResult materialize(String planCode, int planRevision, Instant targetTime);

MetricResult query(MetricQuery query);
MetricResult query(MetricQuery query, int definitionRevision);
List<MetricResult> batchQuery(MetricBatchQuery query);
```

编码及修订确定对应 DSL，调用方不重复提供 Plan/Definition DSL。`MetricQuery` 已包含指标编码，接口不再另收编码。
公开合同不携带数据库、表、列、私有绑定、用户配置的 rowKeys 或内部状态对象。
Wind 不提供默认引擎、存储实现、Spring Bean、任务调度或新 HTTP 路由；不新增值行 Writer/Reader SPI。

接口上的 Jakarta 约束注解和包级 JSpecify 表达调用前置条件。宿主的实际 Controller、定时任务或其他适配入口
负责使参数验证生效，并完成授权、可信操作者及主体/维度数据归属检查；接口声明本身不执行这些检查。
计划/定义是否存在、生命周期是否允许本次操作，以及物理绑定和覆盖是否可用，仍由承责服务判断。

## 使用方式

下例中的两个接口实例由 Capte 装配提供，示例代码运行在已完成授权和参数校验的应用边界内。
业务时区必须取自宿主选定的固定上下文；下面的 Asia/Shanghai 仅用于展示这一配置下的调用。

```java
ZoneId businessZone = ZoneId.of("Asia/Shanghai");
LocalDateTime start = LocalDateTime.of(2026, 9, 13, 0, 0);
LocalDateTime end = LocalDateTime.of(2026, 9, 14, 0, 0);

// 推进已发布的 P@5；首次起点和已有水位均由实现方恢复。
MetricMaterializationResult materialized = materializer.materialize(
        "P", 5, end.atZone(businessZone).toInstant());
for (MetricMaterializationSegmentResult segment : materialized.segments()) {
    // 每段分别呈现实际水位、粒度和有效目标，不合成一个全局水位。
    display(segment.segmentCode(), segment.watermarkTime(), segment.effectiveTargetTime());
}

MetricQuery request = new MetricQuery(
        "USER_WALLET_INCOME_TOTAL", "2001", start, end,
        Map.of("currency", "CNY"), Map.of());
MetricResult servingResult = queries.query(request);
WindMetricsValue<?> servingValue = servingResult.toMetricsValue();
// 仅读取指标值时不需要判断执行路线；诊断和版本信息仍从 servingResult 获取。
MetricResult fixedResult = queries.query(request, 2);

MetricBatchQuery batch = new MetricBatchQuery(
        List.of("USER_WALLET_INCOME_TOTAL", "USER_WALLET_TRANSACTION_COUNT"),
        "2001", start, end, Map.of("currency", "CNY"));
List<MetricResult> results = queries.batchQuery(batch);
// results.get(0) 对应第一个编码，results.get(1) 对应第二个编码。
```

`display` 代表调用方已有展示逻辑，不是 Wind API。物化与查询是独立调用能力，正式查询不要求每次先调用 materialize。
不要在捕获“快照未覆盖”后自动触发物化；是否推进计划由既有业务流程决定。

## 物化的入参和返回合同

`planCode` 非空白，`planRevision` 为正整数且必须精确定位具有发布记录、满足宿主既有执行资格的修订。
发布、启停、草稿创建与复制不属于本接口。`targetTime` 必填，表示本次请求的时间上界，可以在过去。
宿主不能改选当前最新计划、补选最新定义或重新解析当前物理目标。

```java
public record MetricMaterializationResult(
        List<MetricMaterializationSegmentResult> segments) {}

public record MetricMaterializationSegmentResult(
        String segmentCode,
        SnapshotGranularity snapshotGranularity,
        ZoneId timeZone,
        Instant queryableStartTime,
        Instant watermarkTime,
        Instant effectiveTargetTime) {}
```

结果只包含应执行的 SNAPSHOT 分段，顺序与固定计划一致、编码不重复。根 SNAPSHOT 返回 `snapshot` 一项；
SEGMENTED 使用实际快照分段的 `archive`/`recent`。REALTIME 分段不出现在物化结果中。
这些字符串沿既有 Capte 根分段和 Wind 分段编码，不扩展 Plan DSL 枚举。

每段覆盖为 `[queryableStartTime, watermarkTime)`。起点等于水位表示初始空覆盖，不能当作已完成一个桶。
`effectiveTargetTime` 是该段应用关闭桶及范围规则后的有效目标；正常返回必须满足本段水位已达到它。
目标早于已有水位时，仍返回实际水位，不回退或裁剪历史覆盖。

各段的粒度、时区、目标和水位均独立返回。上海 DAY 桶在 9 月 14 日 15:30，安全延迟为 0 且无更早范围限制时，
最晚只关闭至当日 00:00；HOUR 段可能关闭至 15:00。不能选择一个分段或取 max/min 水位代表整个计划。
Wind DSL 已允许 archive、recent 都是 SNAPSHOT；该表达能力不代表 Capte 多分段运行已经实现。

结果构造器校验非空字段、非空且无重复的分段列表、覆盖先后及达到目标，返回防御性复制的列表。
失败使用已有 `MetricValidationException`、`RESULT_INVALID` 和字段路径。
Wind 不从结果对象证明真实提交、桶对齐或分段集合与 Plan 一致；这些必须由执行方验证。

## Capte 物化实现流程与事务边界

沿现有 `MetricMaterializationApplicationService` 和 `MetricMaterializationRunner` 实现同一条执行链，
不另建第二套分页、桶循环或后台任务。可让承责服务实现公共接口，复用其内部流程；管理方法保留可信操作者等宿主参数，
新增接口不成为绕过授权或生命周期的旁路。

1. 读取精确计划修订及其发布记录，加载成员的精确定义、固定时间/维度解释和持久冻结的各段绑定。
   校验本次执行资格与全部所需存储能力；缺失绑定、旧规则不可恢复或不支持的计划组合明确失败。
2. 一次捕获参考时刻。对每个 SNAPSHOT 分段，按固定粒度/时区、请求上界、安全延迟、覆盖截止及本段适用的近期窗口规则
   计算有效目标。历史段的 recentWindow 截止不能机械套给近期段。后续循环不持续追逐“现在”。
3. 首次按配置覆盖起点初始化各段 Checkpoint，后续恢复已提交水位。起点必须满足各段桶对齐，不能静默取整改变覆盖起点。
4. 在一个分段的一个完整桶内，沿既有锁序锁定计划及 Checkpoint 并重读 W；枚举覆盖所有成员的共同键页，
   计算全部必要结果和内部状态，使用同一冻结绑定写入、回读，再 CAS 推进该段水位。
5. 全部应执行分段达到各自目标后组装结果。任一桶或分段失败则抛异常，已提交桶保留；不能返回部分结果冒充整次成功。

一个分段的完整桶是事务边界，包含全部成员、全部键页、业务结果、必要状态和 Checkpoint CAS。
跨分段或跨桶的一次调用不承诺总事务；目标存储必须实际参与既有桶事务，不能只因存在 `@Transactional` 就认定跨数据源原子。
执行预算继续沿宿主既有配置约束，超过限制按原准入/异常合同处理。

响应丢失后，GET Checkpoint 只证明已提交覆盖。水位未达目标不证明原调用已停止。
使用同一计划修订和目标续进时，应在相同计划/Checkpoint 事务锁内重读 W；原调用仍执行时等待锁或返回冲突/超时，
已提交桶跳过，未提交桶重算并原子写回。恢复不增加 Run 实体、运行 ID、租约或自动后台重提。

## 唯一键、时间和冻结绑定的实现归属

两种保存形态都由固定定义的主体、完整维度、时间桶、计划修订及分段上下文推导唯一身份，查询与写回使用同一份解释。
Provider 提供实际值；Normalizer 校验完整维度并规范化；适配器将派生身份映射到真实物理键。
GLOBAL 无业务键时沿内部规范化规则定位；带维度的 GLOBAL 仍保留所有真实维度。
指标值表还按指标修订和结果字段区分值位置；宽表共同成行时，各成员修订和同名输出仍须独立定位。

Capte 私有持久化的建议落点是 `MetricMaterializationPlanRevision.snapshotBindingContent`，按各段保存实际绑定，
并承接固定 timeContext、完整主体/维度及 codec/normalization 解释、成员必要字段描述和输出/状态的物理位置。
逻辑 Plan DSL 仍在已有 planContent，成员修订仍由 Member 与固定 Definition Revision 承接。
这不是 Wind 新增字段，也不是 Web 用户配置；具体私有 JSON 格式和宽表内部 SPI 由 Capte Owner 实现。

发布应用服务生成并核验完整绑定，由现有 `publishPlan` 在同一个 DRAFT/version 条件更新及事务中保存绑定、成员、发布状态
和根选择指针。发布后不可原位重绑；相同发布意图重试返回原发布事实。读取通过精确修订恢复绑定，不按当前 Bean 重选目标。
Checkpoint 继续单独承担各段可变进度，绑定不能塞进 Checkpoint 或恢复已退出的 Run。

桶规则、时区规则、安全延迟以及时间型维度的解释必须可按该发布版本恢复；规则版本必须能定位真实可用的旧实现。
Instant 返回值不证明已有 LocalDateTime 持久化能区分重叠本地小时。支持范围和旧快照回读需要 Capte 实测，
不能因 Java enum 出现某粒度就宣称其时区/日历语义全部可用。

## 查询入参、结果与一致性

`query(query)` 按宿主当前生效选择一次固定根定义、依赖及适用路线；不等于取最大修订号。
`query(query, revision)` 按编码和指定正整数修订加载已发布定义，不切换生效选择，不读其他修订快照。
草稿及指定候选计划的预览继续使用 Capte 原有 preview 方法；不把预览伪装成正式查询。

`MetricQuery` 已有参数：metricCode、subjectId、`[startTime, endTime)`、dimensionValues、parameterValues。
GLOBAL 可不提供业务 subjectId；维度必须与定义完整匹配。LocalDateTime 沿既有合同按宿主固定业务时区解释，
不能使用请求者或 JVM 偶然的默认时区。`MetricResult.timeZone` 说明实际解释时区。

`MetricResult` 沿现有合同返回 SCALAR 的 value/valueType 或 FIELD_SET 的 fields，以及真实定义修订和取数信息。
业务正常 null 与查询失败不同，遵守定义的空值规则；快照保存别名、物理列名和 SUM/COUNT 等内部状态不变成查询字段。

| 取数路线 | 实现责任 |
| --- | --- |
| REALTIME | 编译固定定义和依赖，取事实并计算最终值 |
| SNAPSHOT | 核验全部获准区间的实际覆盖，按同版本绑定读快照及必要状态，再求最终值 |
| SEGMENTED | 按计划声明及各段实际覆盖执行，合并必要状态后求值；历史快照/近期实时组合按实际 W 切分 |

Capte 应复用 `MetricQueryApplicationServiceImpl` 的定义选择、Compiler、QueryEngine、SnapshotReader、
MeasureMerger、ValueResolver 和结果组装。取数、跨桶/分段状态合并必须在求最终表达式之前完成；例如比率要合并分子、分母，
不能平均各桶比率。覆盖不足或某段失败时明确抛错，不自动触发物化、切换路线或返回部分数值。

实现方须在既有一致性读取边界内固定本次定义、依赖、计划与快照覆盖，避免执行中重新读取变化的指针。
跨数据源的观察一致性不能由 Java 接口或单个数据库事务自动保证，宿主须明确实际能力和限制。

## 批量查询

`batchQuery(MetricBatchQuery)` 共用主体、半开时间区间和完整维度，指标编码非空且不重复。
各指标分别选择自己的实际生效修订、依赖及路线，先完成全部条件校验再执行；不共用一个任取的 revision。
返回数量和顺序与 metricCodes 完全一致，每项保留独立值结构及实际来源，不包含 null 项。
任一项失败，整次抛出异常，不返回部分成功；批量不保证单条 SQL。

当前 MetricBatchQuery 没有 parameterValues 和逐项修订，不猜参数也不扩展为统一修订号。
需要这些条件时使用单查方法。Capte 现有批量链使用实时路径，并限制参数化/rowSelection 等组合；
接入接口时不能将它描述为所有快照与分段组合都已支持。不支持的请求明确拒绝，后续扩展沿相同公共语义验证。

## 验证方案、测试与接入风险

Wind 契约测试验证公共模型的基础约束、不可变性、各段独立覆盖及 JSON 往返，并编译接口和使用示例。
这些检查不证明宿主版本解析、授权、Spring 装配、读写事务或运行恢复。

Capte 的实施验收至少覆盖：

- 通过实际注入的公共接口调用；权限/非法参数在既有入口被拒绝，没有绕过调用路径。
- 固定修订加载、发布后配置变化与进程重启、绑定恢复；不追当前定义/当前目标。
- 两种保存形态同键写读，跨计划/修订/分段及完整维度隔离，GLOBAL、多指标、FIELD_SET、跨指标同名字段和重复映射。
- 完整桶的结果与状态事务提交；第二成员/键页/分段失败、并发续进及响应丢失恢复；不重复累计。
- 各分段粒度和水位独立返回，安全延迟、近期窗口、有限截止及业务/JVM 时区差异，不提交未关闭桶。
- 生效与指定修订单查、批量顺序/独立修订/任一失败不返回部分成功，快照覆盖不足和跨桶充分状态合并。

接口是完整能力合同，实现可以明确拒绝尚不支持的计划或查询组合；不能返回看似成功的降级结果。
Capte 行式作用域的局部验证不替代冻结绑定、宽表共同成行或多分段执行；Wind 制品构建不替代 Capte 消费签收。
