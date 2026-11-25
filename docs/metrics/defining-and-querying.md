# 指标定义与查询

先定义“某客户、某币种、某段时间内的授权总笔数”。每条事实代表一次授权，按授权发生时间落入窗口。数据源允许同一授权出现多行时，接入方须先确定去重规则，否则直接 COUNT 会放大笔数。

## 写出可独立验证的定义

下面的 schema 6 定义以逻辑事实 VccTransaction 为来源，客户和币种由查询条件传入。SCALAR 指定单值输出，LONG/COUNT 声明基础计数。宿主将事实、主体、时间及维度映射到实际表和列后即可执行。

```json
{
  "schemaVersion": 6,
  "definition": {
    "code": "VCC_TOTAL_COUNT",
    "revision": 1,
    "valueShape": "SCALAR",
    "subject": {"type": "CUSTOMER", "field": "customerId"},
    "dimensions": ["currency"],
    "parameters": {},
    "valueQuery": {
      "type": "DSL",
      "fact": "VccTransaction",
      "joins": [],
      "time": {"field": "authTime"}
    },
    "value": {
      "valueType": "LONG",
      "measure": {"aggregation": "COUNT"},
      "orElse": {"mode": "ZERO"}
    },
    "fields": {},
    "dependencies": []
  }
}
```

再定义批准笔数时，沿用同一套主体、维度和时间口径，在计数的过滤条件中限定授权状态 APPROVED。通过率定义的 valueQuery 为空，在 dependencies 中为批准笔数和总笔数各登记一组编码与修订。若产品约定「零笔授权时通过率为空」，可以在表达式中先判断分母，同时将结果的 orElse 设为 NULL。

```text
metric('VCC_TOTAL_COUNT', 'value') == 0
    ? null
    : ratio(metric('VCC_APPROVED_COUNT', 'value'), metric('VCC_TOTAL_COUNT', 'value'))
```

批准笔数为零且总笔数为正时，结果为零。零笔授权则返回空，报表可以分别展示这两种情况。ratio 的精度与舍入方式由 DECIMAL 值声明确定。[精确引用测试](../../wind-metrics/src/test/java/com/wind/integration/metrics/spec/MetricVersionReferenceTests.java)

RAW 也可使用 SQL 模板。DSL 根据已配置的物理映射编译，SQL 通过宿主可信的模板生成参数化查询。两者都沿用共同的主体、维度、类型和空值口径。读取旧协议时，RAW DSL 支持 schema 1—4，DERIVED DSL 支持 schema 4，旧 SQL 可沿用原布局。旧 SQL 缺少原始量声明时，接入方须先补齐口径才能用于累计。[定义格式与兼容边界](../../wind-metrics/src/main/java/com/wind/integration/metrics/spec/MetricDefinitionSpec.java)

## 查询一个已发布的指标

假定宿主已装配 queryService、发布上述修订，并允许业务调用方访问客户 C-17。以下代码统计业务时区内整个 9 月的美元授权笔数。9 月 1 日零点计入本次，10 月 1 日零点归入下个窗口。

```java
MetricQuery september = MetricQuery.builder()
        .subjectType("CUSTOMER")
        .subjectId("C-17")
        .dimension("currency", "USD")
        .timeRange(LocalDateTime.of(2026, 9, 1, 0, 0),
                   LocalDateTime.of(2026, 10, 1, 0, 0))
        .build();

MetricResult count = queryService.query("VCC_TOTAL_COUNT", september);
```

查询条件中的 `subjectId`、`subjectType` 标识主体，`startTime`、`endTime` 限定窗口，`dimensionValues`、`parameterValues` 分别承载维度和业务参数。指标身份随查询方法传入。DSL 事实查询要求单个非空白字符串主体，GLOBAL 指标则省略主体 ID。已声明的维度和参数须逐项给全，未声明的容器可以省略或传空映射。上例缺少 currency、多传一个维度，或在 JSON 中拼错顶层字段名，都会被拒绝。应用在入口校验 C-17 的归属和调用资格。[查询条件](../../wind-metrics/src/main/java/com/wind/integration/metrics/query/MetricQuery.java) · [DSL 编译校验](../../wind-metrics/src/main/java/com/wind/integration/metrics/jdbc/MetricJdbcSqlCompiler.java)

## 按用途选择取数入口

日常报表读取生效口径。运营核对已保存的草稿或修订时，可指定修订号做实时试算，所有依赖一并走实时路线。如果要复现某个已发布修订的正式报表，则使用精确引用查询，让该修订沿获准的快照或分段路线取数。派生指标也按精确引用读取依赖，并复用根查询的视图。

批查适用于共同条件下的多项当前指标。默认实现对空输入返回空列表，按输入顺序保留重复编码。任一项失败时整次调用抛错。报表需要跨项一致时，由宿主安排同一读取视图。物化任务单独请求可合并的原始量，以便续算历史状态。

精确引用与原始量查询需要宿主实现，缺少实现时默认抛出 UnsupportedOperationException。依赖修订失效、快照路线找不到唯一已发布计划或覆盖不足时，应向调用方报告失败。[查询服务契约与方法签名](../../wind-metrics/src/main/java/com/wind/integration/metrics/query/MetricValueQueryService.java)

取得计数后，按实际覆盖组合原始量，再生成报表所需的最终值。[计算与结果处理](calculation-and-results.md)
