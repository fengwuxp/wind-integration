# 计算与结果处理

客户 C-17 的 9 月美元授权通过率，需要先取得整个 9 月的批准笔数和总笔数，再计算一次比率。这个顺序决定了快照保存哪些数据、实时查询从哪里接续，以及前端能够展示什么。

## 合并历史与实时数据

假设该客户 USD 维度的两个计数都已连续保存到 9 月 21 日零点，正式读取路线允许其后使用实时事实。查询窗口为 9 月 1 日零点至 10 月 1 日零点，所有区间都含起点、不含终点。

| 实际覆盖 | 来源 | 批准笔数 | 总笔数 |
| --- | --- | ---: | ---: |
| [9 月 1 日，9 月 21 日) | 已提交快照 | 72 | 80 |
| [9 月 21 日，10 月 1 日) | 实时事实 | 12 | 20 |
| [9 月 1 日，10 月 1 日) | 按定义合并 | 84 | 100 |

84 除以 100 的数值结果是 0.84，报表按百分比显示为 84%。如果把两段的 90% 和 60% 直接取平均，会得到 75%，因为两段分母不同。COUNT 和 SUM 跨段相加，MIN 和 MAX 分别取极值。比率和平均值应保留其可合并的组成量，汇总之后再执行表达式、精度、舍入和空值规则。

不同来源可以有不同的实际水位。宿主要按每个 RAW 修订和完整业务键分别核对连续覆盖，再对齐请求范围求派生值。缺片、重叠、覆盖不足或缺少必需字段时应拒绝该次计算。如果走全量快照路线，宿主按完整累计对象可提供的实际范围读取。若报表允许任意选择日期，宿主还需提供能回答该区间的路线和数据。[物化范围及分段规则](../../wind-metrics/src/main/java/com/wind/integration/metrics/dsl/materialization/MetricMaterializationPlanDsl.java)

## 将新增事实并入下一版快照

对于上述两项计数，宿主可以在 schema 4 计划中登记两个精确 RAW 修订，指定客户和币种的业务键来源及保存目标。若采用同一宽表行保存计数，二者要覆盖相同区间并在同一保存组内提交。值表也可作为目标，行到对象的映射和分组事务由仓储实现。

1. **读实际状态。** 按目标、版本、主体和完整维度读已提交快照。设其范围为 `[B,W)`，本例 B 为 9 月 1 日，W 为 9 月 21 日。读取历史时按实际覆盖末端升序核对所有相关记录。增量续算只需取该键覆盖末端最新的完整记录。确无记录时，列表为空、最新记录为空值，读取失败则抛出异常。
2. **取本次增量。** 物化任务固定业务时区和目标末端 E。假设本次 E 为 9 月 23 日，对每个成员调用 `queryMeasures(reference, query)` 读取 `[W,E)` 的精确原始量。声明字段缺失、COUNT 为空或该指标只有不可合并的最终值时应报错。
3. **保存合并结果。** 将旧计数加上增量，生成覆盖 `[B,E)` 的完整状态。保存请求依次携带 `snapshotValue`、`subjectType`、`subjectId`、`dimensionValues`、`coverageStartTime`、`watermarkTime`，本例最后两项分别是 9 月 1 日和 9 月 23 日。仓储按读取侧的规则把原始维度转换成存储键。
4. **确认水位。** 仓储在同一事务中校验并发版本，提交该组的值、覆盖和版本。只有提交成功，E 才是下次读取增量的起点。不同业务键可以独立提交。遇到部分提交、并发冲突或应答不明，先按键回读实际状态，再决定尚需处理的区间。

计划中的刷新粒度决定任务目标。查询只使用实际提交的覆盖。完整快照写入的字段和提交职责可查阅[保存请求](../../wind-metrics/src/main/java/com/wind/integration/metrics/repository/MetricSnapshotSaveRequest.java)和[快照仓储](../../wind-metrics/src/main/java/com/wind/integration/metrics/repository/MetricSnapshotRepository.java)。

## 把结果交给报表

单值通过率从结果的 value 读取，多字段指标通过 fields 读取具名字段。结果还给出实际编码和修订、值形态、主体、执行模式、起止时间和直接组成结果 segmentValues。混合来源的顶层 executionMode 可以为空，报表需要解释来源时可继续查看组成结果。正常空值按定义展示，调用失败和缺字段应保留为错误。[结果字段](../../wind-metrics/src/main/java/com/wind/integration/metrics/query/MetricResult.java)

页面同时需要交易笔数与通过率时，可用主体视图将已选指标映射到 DTO。视图固定主体类型、维度及字段映射。按完整条件组装时需明确主体类型，且恰有一个实现支持目标 DTO。匹配歧义、返回空对象或已选实现取数失败均应报错。使用已有宽表对象时一次读取所需完整记录，保留其版本和覆盖。[主体视图契约](../../wind-metrics/src/main/java/com/wind/integration/metrics/WindMetricsValueResolver.java)

同一请求多处展示通过率，可复用一次求出的结果。确需延迟取值时，把查询绑定为求值回调，在读取字段时调用。回调的每次执行会再次进入其实现，缓存有效期与跨字段一致性由调用方安排。[条件求值契约](../../wind-metrics/src/main/java/com/wind/integration/metrics/WindMetricsValueEvaluator.java)

迁移已有报表时，旧单 ID 和 ID 集合入口仍按首个匹配的视图执行。旧视图收到完整查询条件时默认抛出 UnsupportedOperationException。遇到按币种统计的报表，应让视图直接支持完整条件，保留 currency。若通过旧求值器兼容桥转换条件，独立维度映射必须显式为空映射。[视图分派测试](../../wind-metrics/src/test/java/com/wind/integration/metrics/CompositeMetricsValueResolverTests.java) · [条件转换测试](../../wind-metrics/src/test/java/com/wind/integration/metrics/WindMetricsCriteriaCompatibilityTests.java)
