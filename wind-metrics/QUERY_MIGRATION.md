# 指标查询条件统一与兼容迁移

通用条件统一使用 MetricQuery。当前七字段模型由 MetricQueryCriteria 改名而来，不携带指标编码或定义修订。历史同名 MetricQuery 的带编码请求职责已退出，不能按旧构造器或旧 JSON 使用当前类型。MetricBatchQuery 及服务三个旧入口保持退役；MetricResult 继续返回实际值、修订和执行摘要，业务仅取值时使用 toMetricsValue()。

本次配套改名为 MetricQueryJsonParser、MetricQueryValidator，JSON 字段、构造器参数、校验和查询行为保持。没有 Criteria 类型别名；源码消费者更新 import 后须与新制品一起重新编译，类型名进入方法描述符的旧字节码不能直接混用。外部配置若引用旧类型全路径，也需随消费者更新。

## 通用条件与正式查询

| 字段 | 通用计算 | 正式 DSL 查询和实时试算 |
| --- | --- | --- |
| subjectId | Object，可为单主体或集合；全局为空 | null 或非空白 String，不能将集合转成字符串 |
| subjectType | 可空的主体类型，对应旧 dimensions | 非空时必须与已选定义一致，由宿主核对 |
| startTime / endTime | 可空；沿实际计算或旧模板的边界语义 | 必填半开窗口 [startTime, endTime)，开始必须早于结束 |
| dimensionValues | 独立的具名维度容器 | 非空 Map、完整已声明维度、受支持标量 |
| parameterValues | 任意业务变量，包括宿主内已计算的物化上下文 | 非空 Map，非空白名称和 Integer 值，再核对声明及范围 |
| searchTags | 查询标签，保留其具体对象 | 不允许非空标签集合，不能忽略后查询 |

五参数构造器可用于未指定主体类型和标签的调用；七参数构造器补齐通用条件。subjectId 访问器现在返回 Object，使用旧五字段条件制品的消费者也须重新编译和适配。构造器不再执行 DSL 资格校验。

Map、主体集合和标签集合复制其外层容器，Map 中 Date/Timestamp 同时复制。任意业务对象、嵌套可变值及标签对象保持引用，由宿主持有生命周期；本模型不是深度冻结或跨网络传输运行上下文的保证。为保持旧聚合构造器语义，显式 null 容器也可承载；DSL 入口明确拒绝 null 维度或参数。

MetricQueryJsonParser.parse(String) 与 Jackson 注解绑定解析七字段通用条件，严格拒绝指标身份、服务端字段、重复键和非法 JSON；parse(String) 还拒绝根尾随。维度、变量和标签缺省为空，显式 null 保留；标签 JSON 只接受 name/value，其他 Java 标签子类型的额外属性不做猜测绑定。数值保持精确小数，日期兼容 ISO 和空格分隔格式。

正式查询协议适配使用 parseDsl(String)；直接 Java 服务入口调用 MetricQueryValidator.validateDsl(criteria)，然后按已选定义校验主体类型、维度与参数。Jackson 通用绑定或 @Valid 不能代替该入口校验。parseDsl 保持非法参数容器、非整数和越界值的 METRIC_PARAMETER_TYPE_MISMATCH 及 JSON Pointer。

```java
MetricResult query(String metricCode, MetricQuery query);
MetricResult query(String metricCode, int definitionRevision, MetricQuery query);
List<MetricResult> batchQuery(List<String> metricCodes, MetricQuery query);
MetricResult previewRealtime(String metricCode, int definitionRevision, MetricQuery query);
```

实现方校验独立编码非空白、修订为正、批量编码列表非空且不重复，并固定列表副本。批量当前必须拒绝非空参数，保持输入顺序、整批预检和整体失败语义，不用逐项单查替代一致性边界。正式精确修订与保存修订实时试算仍按各自资格执行。

## 聚合接口已经接入的能力

Evaluator 新入口 evaluateWithCriteria，Aggregator 新入口 aggregateWithCriteria，工厂只读入口为 value 和 fieldValues，均直接接收 MetricQuery。不同名称避免原 evaluate(null)、aggregate(null)、single(name, null) 产生源码歧义。

本次只改条件类型及配套解析、校验类型；WithCriteria、toCriteria/fromCriteria 方法名保留，继续区分旧聚合外观和通用查询条件入口。

后续工厂退役已增加根包 WindMetricsValueFactory，只读消费者使用 value(name, query) 或
fieldValues(name, query)。WindMetricsFieldFactory 标记过时但不继承它，仅保留历史 single/multiple 入口；
新工厂直接实现 WindMetricsValueFactory，具体实现和调用点另行处理。
新工厂可以只实现两个取值能力入口。继续需要历史 Field/evaluate 的调用方保留旧契约，
不将求值或修改方法搬进新工厂。旧工厂与新工厂相互独立，不通过继承补足默认实现。

WindMetricsAggregationQuery 标记 Deprecated 并承担兼容外观，保留原六字段构造器、builder/of、getter、JSON 和旧调用方法描述符。旧 getter 的既有可变容器行为也保留。toCriteria() 将 dimensions 映射为 subjectType、dimensionsId 映射为 subjectId、时间按原边界值传递、queryVariables 放入 parameterValues、标签保持；dimensionValues 为空，不根据变量名猜测 DSL 维度。

Evaluator 与 Aggregator 新入口的默认方法通过 fromCriteria() 调用原实现。这样已有源码与旧字节码可以接收通用条件中的主体集合、无界时间、标签和任意上下文。转换不经 JSON，也不自行查询。工厂的 value/fieldValues 须由宿主显式实现；Capte 已原生接入 MetricQuery，不再依赖旧工厂桥接方法。

独立 dimensionValues 在旧对象中没有等价属性。默认兼容实现遇到 null 或非空维度容器时明确拒绝，不把维度偷偷合并进 queryVariables，也不覆盖同名参数。需要使用独立维度的新实现必须原生覆盖 WithCriteria 入口，并将旧入口作为兼容转发。此边界保护完整条件，不代表宿主已实现所有新的计算组合。

```java
// 新实现内保留旧调用兼容；新方法原生消费完整条件。
@Override
public Output aggregate(WindMetricsAggregationQuery query) {
    return aggregateWithCriteria(query == null ? null : query.toCriteria());
}

@Override
public Output aggregateWithCriteria(MetricQuery criteria) {
    // 按宿主真实取值或已算上下文组装对象。
}
```

## Capte 迁移与退役

Capte 的 JdbcMetricValueQueryServiceImpl 已接入值服务四方法，统一调用 validateDsl 并按正式 DSL 查询约束处理 Object 类型的 subjectId。指标相关生产代码及测试中已无 MetricBatchQuery、MetricQueryCriteria 引用。MetricQueryApplicationService 继续单向组合委托，不实现 Wind 接口、不形成反向调用；实际值入口持有只读一致性事务，纯委托不开第二次独立事务。

既有 HTTP 的 POST /platform/api/v2/metrics/values 保持六字段平铺请求及 ApiResp<MetricResult>，由 Web 请求承接指标编码并提取通用条件。协议仍应拒绝原未声明字段；新增通用字段和 Java 方法不自动进入既有 HTTP。严格解析、参数错误、事务和业务结果须由宿主验证。

旧聚合/SQL 实现可以先通过默认兼容入口使用旧能力；需要独立维度的实现原生覆盖新方法。物化实现保持已计算上下文，不重查定义或追随新修订。未知 SQL 模板的时间包含性不能因为新字段名而改为半开。所有运行中消费者和配置完成迁移后，才退役旧聚合外观与方法。

当前候选不再兼容被删除的正式查询类型；旧正式服务字节码探针的历史通过结论不适用于本版。回退应配套库、消费者代码和模板版本，不安装到共享 Maven 仓库或发布。

## 数值计算与物化校验收口

MetricValueCalculator 保留，统一承担精确 Number 校验、分段合并、声明类型归一、表达式结果处理和最终 orElse。它不读取定义或数据库，也不解释表达式语言；MetricExpressionDsl 与默认 Spring Expression 能力继续保留。MetricResult 承载查询身份及实际执行摘要，只取业务值时调用 toMetricsValue()，不再查询。

Capte 物化先将原始 JDBC Number 交给 calculate 校验，随后保存未经最终精度舍入的 STATE；OUT 使用归一后的结果。COUNT 的 null、浮点数、自定义 Number、小数截断及整数溢出显式失败，不在宿主中预先转零或转成 BigDecimal 掩盖。跨桶完成原始状态合并后才做最终舍入，存储 decimal(24,10) 容量限制仍由 Capte 检查。

MetricMergeState 和 MetricMaterializationMeasure 随旧物化结果协议退出。Capte 发布计划复用 validateMergeable，删除仅用于存在性检查的状态 Map 和递归转换；不另建替代枚举或模型。通过定义校验的 AVG、rowSelection 在发布时仍返回 MATERIALIZATION_PLAN_INVALID，并保留 Wind 校验原因；无原始 measure 的事实定义由 DSL 校验提前拒绝。发布失败不得修改草稿、成员、生效选择或写入快照。

## 条件统一阶段的历史验证

Java 21 隔离构建：185 项模块测试通过，0 失败、0 错误、0 跳过。测试覆盖通用条件、DSL 入口拒绝、严格 JSON、旧对象转换、旧 JSON 形状、上下文引用、维度不丢失及原生条件求值。PMD 检查有 EntityTag/WindTag 的 4 条 HEAD 既有问题，本次新增 0 条。

真实 Capte 的 SqlSingleMetricsField、SqlMultipleMetricsField、DefaultMetricsAggregatorFactory 源码重新编译通过；旧源码编译产物在新库上运行通过。新 WithCriteria 客户端对旧编译产物和重新编译产物均完成字段取值、重新求值、多个字段创建和对象聚合验证，保持集合主体、空窗口及任意上下文。

这些检查没有执行生产 SQL 模板、数据库事务或 HTTP 宿主，不能代替 Capte 全链路迁移验收。完整命令、摘要和匹配候选 JAR 见 /private/tmp/wind-metrics-query-unification-20260915/。

## 结构化指标值命名

WindMetricsFieldValues<M> 统一改名为 WindStructuredMetricsValue<M>，仍位于 metrics 根包，
表示一个指标的整体值及其具名字段读取能力。getName/getValue/asFieldValues、of 和工厂
fieldValues(name, query) 的行为保持；整体值可以是 Map 或业务对象，正常 null 与字段缺失继续区分。
WindMetricsValueSet 删除，旧字段视图由 MultipleValueMetricsField 直接继承 WindStructuredMetricsValue 承接。

不保留 WindMetricsFieldValues 同义类型。直接引用该类型的源码、反射配置和方法描述符
须同步改名并随新制品重新编译；旧制品上的字节码不能据此前行为回归直接认定兼容。
