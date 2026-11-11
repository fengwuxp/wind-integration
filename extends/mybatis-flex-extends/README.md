# mybatis-flex-extends：使用与设计

本模块封装 Wind 项目中常用的 MyBatis-Flex 操作：查询分页、MySQL 条件、树对象处理、字段转换与加密、插入字段填充，以及数据读取隔离。

| 要做的事 | 入口 |
| --- | --- |
| 把 Wind 查询请求转换为分页结果 | [查询与分页](#query) |
| 构造 FIND_IN_SET 或全文搜索条件 | [MySQL 查询函数](#mysql-functions) |
| 查询子树、补父名称、把树拉平 | [树对象处理](#tree) |
| 插入时填充租户、环境、工作空间和测试标记 | [插入监听器](#insert-listeners) |
| 保存 Locale、字符串密文或日期密文 | [字段转换与加密](#type-handlers) |
| 按租户、工作空间等维度过滤查询 | [数据读取隔离](#read-isolation) |
| 部署检查、排障和运行测试 | [生产接入与维护](#verification) |

<a id="module"></a>
## 1. 模块边界与接入

本模块沿用 MyBatis-Flex 的 Mapper、实体元数据和监听机制。读隔离在 MyBatis Executor 执行前介入，业务负责提供身份与授权范围。

### 职责与边界

| 层次 | 负责什么 |
| --- | --- |
| 应用层 | 计算权限、绑定请求上下文、定义事务边界、校验写入归属，管理数据库索引和加密密钥 |
| 公共模型与上下文 | Wind 依赖提供 WindQuery、分页类型和 TreeResources；core 提供隔离归属接口、DataIsolationContextHolder 和 DataIsolationQueryIgnoreHolder |
| 本模块公开接口 | 提供查询和树 helper、字段 TypeHandler、插入监听器、读隔离插件及范围配置 |
| 插件内部实现 | SelectIsolationRewriter 改写 SQL，MybatisFlexReadParameters 适配 Flex 参数，接入方无需直接调用 |

各能力按需使用，查询 helper 不依赖读隔离插件。组件不创建业务事务，数据一致性沿用应用的事务和数据库隔离级别。

### 引入与初始化

```xml
<dependency>
    <groupId>com.wind.integration</groupId>
    <artifactId>mybatis-flex-extends</artifactId>
</dependency>
```

版本由宿主 Wind BOM/依赖管理确定。本分支使用 JDK 25 正式版 ScopedValue，构建与运行均须使用 JDK 25 或更高版本，见第 8 节。当前依赖基线为 MyBatis 3.5.19、MyBatis-Flex 1.11.5 和 JSQLParser 5.4。MyBatis-Flex Starter 按应用的 Spring Boot 版本选择；仓内集成测试使用 Spring Boot 4.1.0、Flex Boot 4 Starter 和 H2 2.4.240。

各能力按需接入：静态 helper 直接调用；TypeHandler 配在需要转换的字段上；插入监听器挂到实体或 Flex 全局配置；读隔离插件单独注册。仅添加依赖不会自动启用这些行为。

<a id="query"></a>
## 2. 查询与分页

### 常用接口

`MybatisQueryHelper` 把 Wind 的查询契约接到 Flex。它创建或修改 QueryWrapper，实际查询通过调用方提供的 Mapper 方法执行。

| 方法 | 行为 |
| --- | --- |
| `of(PageBasedQuery)` | 创建 Flex Page，大小取 querySize 与 maxQuerySize 的较小值 |
| `from(WindQuery[, tableAlias])` | 根据排序字段和方向创建 QueryWrapper，不自动填业务 WHERE、FROM 或分页 |
| `getQueryOrderBy(...)` | 转为 QueryOrderBy 数组；字段/方向缺失或长度不匹配时返回空数组 |
| `convert(Page, query, converter/records)` | 将 Flex Page 转成 Wind Pagination |
| `convert([total,] List, cursorQuery, converter)` | 将已查询列表转成 CursorPagination；不访问数据库，省略 total 时使用 -1 |
| `cursorConditionWithNumId/WithTextId(column, query)` | 为指定列构造数值或文本游标比较条件 |
| `query(wrapper)` / `query(wrapper, entityType, resultType)` | 创建 WindQueryExecutor；带 Class 参数的重载只辅助类型表达，不会自动转换对象 |

`DEFAULT_TABLE_ALIAS` 的值为 `t`。传给 from 的别名只用于排序表达式，FROM 中的别名仍由调用方配置。排序字段应来自项目定义的 QueryOrderField，不能直接信任请求传入的字段或 SQL 片段。

### 页码分页

在应用的查询方法中设置回调：

```java
import com.mybatisflex.core.BaseMapper;
import com.mybatisflex.core.query.QueryWrapper;
import com.wind.common.query.supports.AbstractPageQuery;
import com.wind.common.query.supports.Pagination;
import com.wind.mybatis.flex.MybatisQueryHelper;

import java.util.function.Function;

public final class PaginationExample {

    public static <E, R> Pagination<R> queryPage(
            BaseMapper<E> mapper, AbstractPageQuery<?> options, Function<E, R> converter) {
        QueryWrapper wrapper = MybatisQueryHelper.from(options);
        // 在此追加本次查询的业务条件。
        return MybatisQueryHelper.<E, R>query(wrapper)
                .counter(mapper::selectCountByQuery)
                .resultQueryFunc(mapper::selectListByQuery)
                .converter(converter)
                .pagination(options);
    }
}
```

实际转换可以传入已有 MapStruct Converter；实体直接作为内部结果时使用 Function.identity()。查询回调的方法名为 `resultQueryFunc`。

执行器根据 shouldCountTotal/shouldQueryResult 决定是否统计和取数。统计得到零行后直接返回空分页。count 使用克隆的 QueryWrapper 并清除排序，取数则给原 wrapper 追加 limit。超出 maxQuerySize 会报错，不同于 of(PageBasedQuery) 的取较小值行为。

需要统计时必须提供 counter，resultQueryFunc 总是必填；需要取数时也必须提供 converter。resultEnricher 在转换之后收到结果列表，可补充 DTO 属性；列表来自 Stream.toList()，不适合在回调中增删元素。

### 游标分页

```java
CursorPagination<OrderDTO> page = MybatisQueryHelper.<OrderEntity, OrderDTO>query(wrapper)
        .counter(orderMapper::selectCountByQuery)
        .resultQueryFunc(orderMapper::selectListByQuery)
        .converter(converter)
        .cursor(options);
```

wrapper 应先通过 from(options) 等方式设置稳定排序，options 为 AbstractCursorQuery。执行器固定使用数值 `id` 构造边界，并按 querySize 取数；转换后的 DTO 也须保留 Wind 游标生成所需的 id。它不是任意字段或复合键的通用游标实现。

需要文本列时，单独使用 cursorConditionWithTextId 构造条件，再显式查询并转换结果。文本按数据库字符串规则比较，字符串数字不一定按数值排序。向前翻页需要调用方按 Wind 游标约定反转查询排序；执行器不替调用方翻转 ORDER BY，返回列表由 CursorPagination.withQuery 恢复方向。不要把 `gmt_create, id` 组合排序直接等同于完整的复合游标谓词。

query(options) 会根据 AbstractPageQuery/AbstractCursorQuery 分派，其他 WindQuery 实现会报错。执行器和 QueryWrapper 都是一次查询的可变对象，不应作为跨请求共享对象反复使用。count 与列表是两次查询，是否共享一致快照取决于业务事务。

<a id="mysql-functions"></a>
## 3. MySQL 查询函数

### FIND_IN_SET

`MybatisQueryMethodHelper` 返回可组合的 QueryCondition，查询值通过参数绑定传入。

```java
QueryCondition one = MybatisQueryMethodHelper.findInSet(tagsColumn, "MEMBER");
QueryCondition excluded = MybatisQueryMethodHelper.notFindInSet(tagsColumn, "TEST");
QueryCondition any = MybatisQueryMethodHelper.findInSetCondition(
        tagsColumn, Set.of("MEMBER", "OPENAPI"));
wrapper.and(any);
```

集合版本以 OR 组合，表示匹配任意一个值。**空集合返回空条件，会省略这一过滤项**，不表示无可见数据；权限范围为空时应提前返回空结果或交由 DataIsolationScope 处理。

单值支持 String 和 Enum。Enum 取 name()，null Enum 返回空条件；String 重载会将 null 作为参数传入，按数据库 NULL 语义处理。

旧 `findInSet(QueryColumn, Set<String>)` 返回拼接后的 String，已标记废弃且限制字符。新代码使用 findInSetCondition。旧方法对空集合返回 `( )`，迁移时一并检查空集合分支。

### 全文搜索

```java
wrapper.and(MybatisQueryMethodHelper.matchFullText(titleColumn, keyword));
wrapper.and(MybatisQueryMethodHelper.matchFullTextWithBoolean(titleColumn, keyword));
```

自然语言模式与布尔模式均绑定关键词。null 或空白关键词返回空条件。布尔模式默认将包含操作符的关键词包成短语，并替换换行、制表符；需要保留 MySQL 布尔操作符时显式传入第三个参数 true。

列参数应来自可信实体字段定义，并携带表名。函数读取 QueryColumn 的表名和列名，使用 MySQL 反引号输出，不适用于任意表达式或用户输入标识符。示例中的 tagsColumn/titleColumn 应由调用方传入项目生成的字段引用或固定元数据。

接口支持单列全文搜索。实际使用需配置匹配的 FULLTEXT 索引、分词器并验证检索效果；函数 helper 只构造 SQL，不创建索引。参考 [MySQL 全文搜索](https://dev.mysql.com/doc/refman/8.0/en/fulltext-search.html)和 [FIND_IN_SET](https://dev.mysql.com/doc/refman/8.0/en/string-functions.html#function_find-in-set)。

<a id="tree"></a>
## 4. 树对象处理

### 查询树和补充父节点信息

```java
MybatisTreeObjectHelper.fillChildren(roots, mapper, converter);
MybatisTreeObjectHelper.buildTree(roots, mapper, converter);
MybatisTreeObjectHelper.fillParentName(children, mapper);
```

| 方法 | 输入与结果 |
| --- | --- |
| fillChildren | 批量查询 roots 的直接子节点，写回 children，并填充子节点的 parentName |
| buildTree | 从已提供的根节点开始逐层查询，直到没有子节点；不会替调用方查询根节点 |
| fillParentName | 收集 parentId，批量查询父节点，再写回 parentName |
| flattenTree | 只处理内存中的一棵树，不访问 Mapper |

查询依赖固定数据库字段 parent_id。树对象实现 TreeResources，Mapper 实体实现 WindResources；children 和 parentName 通过同名字段反射写入。fillChildren 没有子节点时写空列表，已有 children 会被替换；参与父名称映射的节点 ID 应唯一、name 应非空。

buildTree 按层查询，一层一次批量查询，树很深或很宽时仍需控制数据规模。数据应是无环树，节点 ID 唯一；实现没有循环检测、深度上限或子节点排序。需要固定展示顺序时，在查询或结果处理中明确排序。读隔离插件启用后，每一层 Mapper 查询也受其范围约束。

### 拉平树

```java
List<TreeResources<Long>> rows = MybatisTreeObjectHelper.flattenTree(root);
```

flattenTree 接受非空根节点，返回包含根节点的深度优先前序列表，保持每个 children 列表原顺序。它用 WindDeepCopyUtils 复制整棵树一次，再遍历副本，将每个节点的 children 设为 null；原树和节点的可变属性不受修改。

遍历开销随节点数线性增长，整体开销还包括深拷贝的对象图。节点与属性必须可被 WindDeepCopyUtils 复制，children 应为可写的同名字段，树中不得有环或 null 子节点。输入是森林时，逐个根调用；多个根之间的重复节点由调用方处理。

<a id="insert-listeners"></a>
## 5. 插入监听器

四个监听器通过 Flex 的 AbstractInsertListener 检查实体是否实现对应接口：

| 监听器 | 实体须实现的接口 | 写入内容 |
| --- | --- | --- |
| TenantIsolationFieldInsertListener | SettableTenantIsolationObject | DataIsolationContextHolder.getTenantId()，未绑定时填 null |
| EnvIsolationFieldInsertListener | EnvIsolationObject | DataIsolationContextHolder.requireEnv()，缺失、空字符串或仅空白时拒绝写入 |
| TestDataIsolationFieldInsertListener | SettableTestDataIsolationObject | DataIsolationContextHolder.isTestData()，未绑定时为 false |
| WorkspaceIsolationFieldInsertListener | SettableWorkspaceIsolationObject | DataIsolationContextHolder.getWorkspaceId()，未绑定时填 null |

环境值的权威来源是应用的 Spring profile。`DataIsolationContextHolder.getEnv()` 通过 `ServiceInfoUtils.getSpringProfilesActive()` 读取它，读插件和插入监听器使用同一来源；在 trace 中绑定 ENVIRONMENT 不会覆盖应用 profile。

租户、工作空间和测试数据的查询隔离分别识别 `TenantIsolationObject`、`WorkspaceIsolationObject` 和 `TestDataIsolationObject`。需要插入时填充的实体应实现表中的 Settable 子接口，并装配对应监听器；子接口同时满足查询隔离的识别条件。只有 setter 而未实现子接口的对象不会被这些监听器填充。迁移已有实体时，需要同时调整接口声明和全局监听器注册；仅保留原读取接口会停止对应的自动填充。

租户监听器直接调用 `SettableTenantIsolationObject.setTenantId`；使用 Lombok `@Setter` 的 tenantId 字段可自动提供该方法。物理列可用 `@Column` 重命名；采用其他 Java 属性存储租户时，由 getTenantId/setTenantId 委托到该属性，读取规则另按第 7.3 节配置。当前租户上下文仍采用 Long 兼容约定。

可以挂到实体的 onInsert：

```java
@Table(value = "t_order", onInsert = {
        TenantIsolationFieldInsertListener.class,
        EnvIsolationFieldInsertListener.class,
        TestDataIsolationFieldInsertListener.class,
        WorkspaceIsolationFieldInsertListener.class
})
public class OrderEntity implements SettableTenantIsolationObject<Long>, EnvIsolationObject,
        SettableTestDataIsolationObject, SettableWorkspaceIsolationObject<String> {
    // 保留业务字段，并实现各接口的 getter/setter。
}
```

或在现有 Flex 初始化入口中全局注册到目标接口，选择一种装配方式即可：

```java
FlexGlobalConfig globalConfig = FlexGlobalConfig.getDefaultConfig();
globalConfig.registerInsertListener(new TenantIsolationFieldInsertListener(), SettableTenantIsolationObject.class);
globalConfig.registerInsertListener(new EnvIsolationFieldInsertListener(), EnvIsolationObject.class);
globalConfig.registerInsertListener(new TestDataIsolationFieldInsertListener(), SettableTestDataIsolationObject.class);
globalConfig.registerInsertListener(new WorkspaceIsolationFieldInsertListener(), SettableWorkspaceIsolationObject.class);
```

注册应在业务写入前完成，具体生命周期沿用项目的 MyBatisFlexCustomizer。onInsert 和全局配置方式参考 [Flex 实体监听器](https://github.com/mybatis-flex/mybatis-flex/blob/main/docs/zh/core/table.md)。

监听器会覆盖对应字段的已有值。查询忽略不会关闭这些监听器，也不会改变当前租户、工作空间或环境。它们只在 Flex 实体插入监听链上执行；手写 XML/注解 INSERT、直接 JDBC 和 UPDATE/DELETE 不经过该链路。

退役 Flex 原生租户能力时，可用 TenantIsolationFieldInsertListener 承接实体插入的租户填充，无需启用 TenantFactory 或 `@Column(tenantId = true)`。已有 tenantId 会被当前上下文覆盖，缺少租户时也会覆盖为 null，不保留预填值。更新、删除仍须校验并限定目标归属，插入监听器和读插件都不会补写这些权限条件。

租户和工作空间监听器都允许填 null；如果业务要求每条记录必须有归属，需要入口校验或数据库约束，否则可能写入一条默认查询不可见的记录。

`TestDataIsolationObject.isTestData()` 保留默认 false 的行为；实际存储测试标记的实体应返回自身字段值，使用 Lombok `@Getter` 可生成对应方法。

<a id="type-handlers"></a>
## 6. 字段转换与加密

### Locale

LocaleTypeHandler 将 Locale 写成语言标签，例如 zh-CN，读取时调用 Locale.forLanguageTag；SQL NULL 返回 null。

```java
@Column(typeHandler = LocaleTypeHandler.class)
private Locale locale;
```

也可以在项目现有 TypeHandler 扫描/注册中注册 LocaleTypeHandler，它声明了 MappedTypes(Locale.class)。字段级配置影响范围更明确。语言标签转换不负责业务允许值校验。

### 字符串与日期加密

StringEncryptTypeHandler 写入时加密字符串，读取时解密。LocalDateEncryptTypeHandler 先格式化为 yyyy-MM-dd 再加密，读取时解密并还原日期；数据库列应保存密文字符串，而不是 DATE。

```java
@Column(typeHandler = StringEncryptTypeHandler.class)
private String contactNumber;

@Column(typeHandler = LocalDateEncryptTypeHandler.class)
private LocalDate birthday;
```

两个处理器共用 AbstractEncryptBaseTypeHandler 中的 TextEncryptor。应用在使用这些字段前完成初始化：

```java
AbstractEncryptBaseTypeHandler.setTextEncryptor(textEncryptor);
```

textEncryptor 由应用的密码模块提供；这里不选择算法或保存密钥。未初始化会报 `TextEncryptor not init`。Spring 初始化的一个写法是：

```java
import com.wind.mybatis.encrypt.AbstractEncryptBaseTypeHandler;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.encrypt.TextEncryptor;

@Configuration(proxyBeanMethods = false)
public class FieldEncryptionConfiguration {

    @Bean
    public InitializingBean initializeFieldEncryption(TextEncryptor textEncryptor) {
        return () -> AbstractEncryptBaseTypeHandler.setTextEncryptor(textEncryptor);
    }
}
```

初始化期间如有其他 Bean 提前访问数据库，需要建立相应启动依赖，不能仅凭声明顺序判断先后。处理器使用同一类加载器内的共享静态引用，不按工厂或租户隔离；应用不应在请求中反复替换它。

字段加密的使用边界：

- 密文长度由 TextEncryptor 决定，按实际输出规划列长度，并保留历史数据的解密能力。
- 读取时会把数据库返回值直接传给 decrypt，包括 null；所选 TextEncryptor 不接受 null 时，SQL NULL 会读失败。可空字段须验证这一行为。
- 日期密文解开后仍须是约定格式，明文旧数据和密文不能直接混用。
- 不承诺密文支持等值、LIKE、范围或排序。查询能力取决于实际加密方案，不能通过固定随机量换取可查询性。
- 本模块不提供密钥轮换、多密钥路由、脱敏展示或密码哈希；密钥来源、算法与迁移由密码模块负责。

加密实现与安全属性参考 [Spring Security Crypto 文档](https://docs.spring.io/spring-security/reference/features/integrations/cryptography.html)。项目应按实际依赖版本与所选 TextEncryptor 做往返、空值、历史数据和密钥变更验证。

<a id="read-isolation"></a>
## 7. 数据隔离使用指南

`DataIsolationQueryInterceptor` 在 MyBatis 查询执行前追加行级约束。配置后，业务仍正常调用 Mapper；受支持的列表、详情、计数、聚合和游标查询使用同一套隔离规则。

查询隔离按应用显式配置的策略和规则启用，不自动选择租户或其他维度。MyBatis-Flex 继续提供 Mapper、实体映射和参数生成；保留 Flex 租户能力时，Wind 可只配置工作空间、环境、测试数据或业务范围。完整由 Wind 承担读取隔离时，需另行完成租户能力迁移与写侧承接。

本模块消费已经确定的上下文与允许范围。身份认证、功能权限、引用有效性、必要的前后置校验和写控制由权限及业务层负责。查询没有命中记录，只表示本次条件下没有结果，不能直接解释为授权通过或拒绝。

| 需求 | 使用方式 |
| --- | --- |
| 按当前租户、工作空间、环境或测试标记读取 | 显式配置所需维度规则，实体实现对应接口，再绑定上下文 |
| 实体未实现隔离接口，或改用其他持久化属性 | 显式配置 `FieldRule`，由元数据解析物理列 |
| 读取多个获准的租户、工作空间或其他字段值 | Supplier 提供本次 `DataIsolationScope`；不改变请求的当前归属 |
| 临时忽略某些内置查询维度 | 在已授权操作中使用 `DataIsolationQueryIgnoreHolder` |
| 多个字段取 OR，或按关系表确定范围 | 实现 `DataIsolationStrategy`，返回完整的参数化条件 |
| 将业务数据权限统一应用到 Mapper 查询 | 由权限策略消费已校验范围，与基础隔离共同执行，见第 7.6 节 |
| 插入时填充租户、环境、工作空间、测试标记 | 单独注册[插入监听器](#insert-listeners) |

保护范围以实际工厂、已登记实体和适用策略为准。统一承担读取隔离时，应使用下文的严格模式及启动校验，让未知表、未分类实体明确报错。普通构造保留原有未受管查询直通行为；SQL 支持范围见第 7.8 节。

### 7.1 最小接入

接入包含实体映射、显式策略配置、插件注册和入口上下文。仅添加依赖、实现隔离接口或设置上下文都不会自行启用规则。

**实体与 Mapper**

实体按需要实现隔离接口。下面的订单实现四个维度，`workspaceId` 和 `testData` 使用自定义物理列名：

```java
import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.Table;
import com.wind.integration.core.model.EnvIsolationObject;
import com.wind.integration.core.model.TenantIsolationObject;
import com.wind.integration.core.model.TestDataIsolationObject;
import com.wind.integration.core.model.WorkspaceIsolationObject;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Table("t_order")
public class OrderEntity implements TenantIsolationObject<Long>,
        WorkspaceIsolationObject<String>, EnvIsolationObject, TestDataIsolationObject {

    @Id
    private Long id;
    private Long tenantId;
    @Column("organization_id")
    private String workspaceId;
    private String env;
    @Column("is_test")
    private boolean testData;
    private Long ownerId;
}
```

通过应用已有的 Mapper 扫描注册 `BaseMapper<OrderEntity>`，并确保它与插件属于同一个 `SqlSessionFactory`。插件依据实际 SQL 的表和 Mapper 实体元数据匹配规则，查询返回 DTO、Map 或计数值不会改变这种匹配。

**注册插件**

使用 MyBatis-Flex Spring Boot Starter 创建工厂时，在应用配置中声明策略和插件。以下示例明确选择全部四个内置维度：

```java
import com.wind.mybatis.isolation.FieldDataIsolationStrategy;
import com.wind.mybatis.plugin.DataIsolationQueryInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration(proxyBeanMethods = false)
public class DataIsolationConfiguration {

    @Bean
    public DataIsolationQueryInterceptor dataIsolationQueryInterceptor() {
        FieldDataIsolationStrategy fields = FieldDataIsolationStrategy.withDefaults(List.of());
        return new DataIsolationQueryInterceptor(List.of(fields));
    }
}
```

仓内 Flex Boot Starter 集成测试已覆盖 `Interceptor` Bean 的自动安装。自建工厂时，须将插件加入每个需要隔离的工厂，并保留其他插件；同一个工厂不要重复安装。

两个组件均没有无参构造。上例中的 `withDefaults(List.of())` 是显式选择以下四维约定；仅需部分维度时使用第 7.3 节的显式规则构造。拦截器要求传入非空策略集合，不自动补充字段策略。

| 维度 | 实体接口 | 默认 Java 属性 | 上下文缺失时 |
| --- | --- | --- | --- |
| `TENANT` | `TenantIsolationObject` | `tenantId` | 空范围，无可见记录 |
| `WORKSPACE` | `WorkspaceIsolationObject` | `workspaceId` | 空范围，无可见记录 |
| `ENVIRONMENT` | `EnvIsolationObject` | `env` | 空范围，无可见记录 |
| `TEST_DATA` | `TestDataIsolationObject` | `testData` | 按 `false` 查询正式数据 |

一个实体可以实现多个接口，但只有已配置且匹配的规则才生效，多个条件取 AND。只声明同名字段或 `@Column(tenantId = true)` 不会自动启用 Wind 规则；不采用接口约定的实体可配置第 7.3 节的普通字段规则。

**接口识别流程**

`FieldRule.forDimension` 将维度映射到对应接口，例如 WORKSPACE 对应 `WorkspaceIsolationObject`。插件从实际工厂的 BaseMapper 取得实体元数据，字段策略使用规则类型的 `isAssignableFrom` 匹配实体，支持直接实现、父类继承和 Settable 子接口。匹配后，再把规则中的 Java 持久化属性解析为物理列；每次查询才读取范围并生成条件。

例如只配置工作空间规则，即使实体同时实现租户和工作空间接口，也只追加工作空间条件。接口匹配不依赖当前用户或上下文值，未命中的规则不会调用范围供应者。普通模式下未匹配策略的实体不追加隔离条件，但仍受第 7.8 节的全局 SQL 检查约束；严格模式下需明确分类。

**在可信入口绑定上下文**

租户与工作空间来自服务端确认的业务上下文。环境由 ServiceInfoUtils 读取应用 profile，入口应确认其已配置。

```java
import com.wind.integration.core.context.DataIsolationContextHolder;

import java.util.Map;

import static com.wind.integration.core.enums.DataIsolationDimensions.TENANT;
import static com.wind.integration.core.enums.DataIsolationDimensions.TEST_DATA;
import static com.wind.integration.core.enums.DataIsolationDimensions.WORKSPACE;

public final class IsolationRequestExample {

    public static void execute(Long tenantId, String workspaceId,
                               boolean testData, Runnable query) {
        DataIsolationContextHolder.requireEnv();
        DataIsolationContextHolder.runWith(Map.of(
                TENANT, tenantId,
                WORKSPACE, workspaceId,
                TEST_DATA, testData), query);
    }
}
```

回调内直接执行正常 Mapper 查询。默认生成的约束相当于：

```sql
原业务条件
AND tenant_id = ?
AND organization_id = ?
AND env = ?
AND is_test = ?
```

**严格覆盖与启动校验**

完整接入时，用严格模式替换上面的普通插件 Bean。它复用当前工厂的 BaseMapper 元数据和策略匹配结果，不要求手工维护表名映射：

```java
@Bean
public DataIsolationQueryInterceptor dataIsolationQueryInterceptor(
        List<DataIsolationStrategy> strategies) {
    return DataIsolationQueryInterceptor.strict(strategies, Set.of());
}

@Bean
public SmartInitializingSingleton validateDataIsolation(
        List<SqlSessionFactory> factories, DataIsolationQueryInterceptor interceptor) {
    return () -> {
        if (factories.isEmpty()) {
            throw new IllegalStateException("缺少业务 SqlSessionFactory");
        }
        for (SqlSessionFactory factory : factories) {
            interceptor.validate(factory.getConfiguration(), OrderEntity.class);
        }
    };
}
```

示例使用 `org.springframework.beans.factory.SmartInitializingSingleton` 和 `org.apache.ibatis.session.SqlSessionFactory`；List、Set 来自 java.util。strategies 是完整策略集合，须单独声明 FieldDataIsolationStrategy 及所需业务策略 Bean。明确使用四个内置维度时，可将 `List.of(FieldDataIsolationStrategy.withDefaults(List.of()))` 传给 strict。

| 检查 | 严格模式行为 |
| --- | --- |
| 当前工厂已登记实体无适用策略 | 拒绝，除非实体在 publicEntities 中明确声明为公共对象 |
| 公共实体同时匹配隔离策略，或没有登记 Mapper | 拒绝；公共声明不能覆盖保护规则 |
| 实际 SQL 涉及未知表 | 查询拒绝，包括嵌套位置引用；不能因没有规则而直通 |
| schema 限定映射/表名、CTE | 严格模式拒绝，避免同名表及 CTE 别名被误认为已登记物理表 |
| `validate(configuration, requiredEntities...)` | 检查本实例已安装；预热静态绑定；要求指定实体已登记且匹配策略，不读取请求范围 |

确实无需隔离的字典等实体可通过 `Set.of(PublicDictionaryEntity.class)` 声明；它们仍须有 BaseMapper。纯字面量 SELECT 不需要实体。公共声明按插件实例固定，多工厂若分类不同，应分别组装插件。requiredEntities 可直接从应用已有资源登记中提取，不必再维护一份平行清单。

严格模式证明查询对象已分类、要求实体有策略，不能判断任意策略是否写对了租户或团队条件。普通实体只有 ownerId 规则时，不会自动补 tenantId；基础边界仍须由策略负责。实体、属性、安装问题可在启动时暴露；动态 SQL 支持形态还需通过真实查询验证。

**与 Flex 原生租户隔离配合**

保留 Flex 租户能力时，使用 `new FieldDataIsolationStrategy(rules)` 显式配置非租户维度，保留原 TenantFactory 与租户列元数据。同一实体的租户填充选择一种来源，不重复注册 Wind 租户监听器。Flex 的租户忽略与 Wind 的维度忽略分别生效；Wind 不会因为保留原生租户就跳过 SQL 支持范围检查。

需要明确迁移到 Wind 承担租户读取隔离时，再执行以下步骤。

显式选择的字段规则和自定义策略可在原生 TenantFactory 未启用时独立执行。迁移在应用装配处完成：

1. 核对各 SqlSessionFactory 中的受保护实体和查询入口，包括生成 SQL、XML/注解 SQL、计数、详情及导出。启用严格模式和启动校验；仅有 `@Column(tenantId = true)` 的实体需要补 Wind 接口或显式字段规则。
2. 为每类实体配置独立的租户、工作空间等边界，再追加业务权限策略；`noBinding` 只能省略 RBAC 条件。字段规则示例见第 7.3 节。
3. 在应用启动配置中停用 TenantFactory Bean 及 `TenantManager.setTenantFactory(...)` 等原生注册入口，确认初始化结束后原生租户工厂未启用。不要在每次请求中切换全局工厂，也不要让 Wind 重写器识别、删除旧租户条件。原有租户填充及 UPDATE/DELETE 约束须同时有明确承接，见第 5 节。
4. 在原生租户过滤关闭的配置下，验证生成 SQL 与手写 SQL 的可见范围一致，并覆盖缺上下文、无 DATA、空范围、多租户切换和临时忽略。检查方法见第 8 节。

同一租户维度同时由两侧过滤时，条件取交集：原 SQL 已限制租户 10，Wind 再允许租户 20，结果为空。Wind 的忽略 API 不会移除原条件，应在装配时明确该维度的责任。

### 7.2 上下文的生命周期

`DataIsolationContextHolder` 保存当前归属，`DataIsolationScope` 表达某条字段规则允许读取的值。两者可以不同，例如请求归属工作空间 A，但获准读取 A、B；插入仍使用当前归属 A。

| API | 用途 |
| --- | --- |
| `runWith(values, action)` / `callWith(values, action)` | 同时绑定多个维度，执行无返回值或有返回值的回调 |
| `runWithTenant` / `callWithTenantId` | 只覆盖租户 |
| `runWithWorkspace` / `callWithWorkspace` | 只覆盖工作空间 |
| `getTenantId` / `getWorkspaceId` | 读取请求归属，缺失时返回 null |
| `getEnv` | 从应用 profile 读取环境，不使用 trace 中的 ENVIRONMENT 值 |
| `requireTenantId` / `requireWorkspaceId` | 缺失时抛异常 |
| `requireEnv` | 缺失、空字符串或仅空白时抛异常 |
| `isTestData` | 缺失时返回 false，非 Boolean 值拒绝 |

租户、工作空间和测试标记的嵌套绑定只覆盖传入维度，其余继承外层；正常或异常退出均恢复外层值。环境值保持由应用 profile 决定。已有 trace 时在子 span 中执行，没有 trace 时建立 root。Map 的键和值应非 null，省略维度表示继承，不能用于清空外层身份。

租户参数虽然声明为 `Serializable`，当前为兼容 `TenantContextHolder` 仍要求 `Long`；工作空间可以使用 String 等可序列化类型。`getTenantId()` 优先读取 trace，缺失时回退旧租户 Holder。新回调会恢复进入前的旧租户值，直接调用旧 `setTenantId` 的代码仍需负责清理。

`callWith`、`callWithTenantId`、`callWithWorkspace` 沿用 WindTracer 的异常包装，受检异常转换为运行时异常并保留原因；这些方法不声明 `throws Exception`。查询忽略的 `callIgnoring` 仍声明受检异常，见第 7.4 节。

请求、消息、定时任务都应在自身入口建立可信上下文。需要在线程池中延续同一上下文时，在提交任务处装饰任务：

```java
import com.wind.trace.task.ContextPropagationTaskDecorator;

executor.execute(ContextPropagationTaskDecorator.of().decorate(() -> {
    orderMapper.selectListByQuery(query);
}));
```

装饰器传递捕获时的 trace 和归属值，任务结束后恢复执行线程原上下文。查询忽略状态绑定在独立的 ScopedValue 作用域中，仅对创建该作用域的线程生效，不随 trace 传播；异步任务需要放宽范围时，由任务入口重新确认并显式使用忽略 API。

业务权限快照不属于这两个 Holder 的自动传播内容。延迟任务应在实际执行时重新确认主体与范围；传递了租户、工作空间不表示原授权一直有效。

### 7.3 配置字段与允许范围

`FieldDataIsolationStrategy` 提供显式规则构造，以及主动选择内置四维的工厂方法：

| 构造方式 | 生效规则 |
| --- | --- |
| `FieldDataIsolationStrategy.withDefaults(customRules)` | 明确启用四个内置维度，自定义维度规则优先；普通字段规则额外追加 |
| `new FieldDataIsolationStrategy(rules)` | 仅使用显式规则，不补默认维度；可用第二个构造参数指定顺序 |

`FieldRule(entityType, property, scopeSupplier, dimension)` 中，`property` 是 **Java 持久化属性名**，由 Flex 元数据解析物理列。上例应填写 `workspaceId`，不能填写 `organization_id`。单独添加 getter 别名不会建立持久化映射。

`dimension` 可省略。标注内置维度时，目标类型必须实现该维度接口，规则参与维度覆盖与临时忽略；省略时是一条普通字段约束，不要求实现内置接口，也不受内置维度忽略影响。

**修改内置维度的读取范围**

例如，应用提供 `workspaceIsolationScopeSupplier` Bean，读取请求内已经确定的工作空间集合。以下配置替换第 7.1 节的插件 Bean：

```java
import com.wind.mybatis.isolation.DataIsolationScope;
import com.wind.mybatis.isolation.DataIsolationStrategy.FieldRule;
import com.wind.mybatis.isolation.FieldDataIsolationStrategy;
import com.wind.mybatis.plugin.DataIsolationQueryInterceptor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.function.Supplier;

import static com.wind.integration.core.enums.DataIsolationDimensions.WORKSPACE;

@Configuration(proxyBeanMethods = false)
public class WorkspaceIsolationConfiguration {

    @Bean
    public DataIsolationQueryInterceptor dataIsolationQueryInterceptor(
            @Qualifier("workspaceIsolationScopeSupplier") Supplier<DataIsolationScope> scopes) {
        FieldDataIsolationStrategy fields = FieldDataIsolationStrategy.withDefaults(List.of(
                FieldRule.forDimension(WORKSPACE, "workspaceId", scopes)));
        return new DataIsolationQueryInterceptor(List.of(fields));
    }
}
```

**修改实体属性名**

假设 `InvoiceEntity` 实现 `WorkspaceIsolationObject`，实际持久属性为 `organizationId`，接口 getter 委托给该属性；需要插入填充时实现 `SettableWorkspaceIsolationObject`，并让 setter 委托到同一属性。可以在全局工作空间规则之外指定实体规则：

```java
FieldDataIsolationStrategy fields = FieldDataIsolationStrategy.withDefaults(List.of(
        FieldRule.forDimension(WORKSPACE, "workspaceId", workspaceScopes),
        new FieldRule(InvoiceEntity.class, "organizationId", workspaceScopes, WORKSPACE)));
```

InvoiceEntity 选择 organizationId，其他工作空间实体选择 workspaceId。自定义规则按类型选择唯一最具体的实体、父类或接口；两个互不包含的类型同时匹配会报歧义，不能靠顺序解决。只有完全没有适用的自定义规则，才使用默认约定。

同一实体类型重复配置相同维度或属性会拒绝。覆盖的是整条 FieldRule，属性与 Supplier 一起选择；属性不存在、物理列不是简单标识符或 Supplier 失败，都不会回退默认规则。

**追加普通字段约束**

```java
FieldDataIsolationStrategy fields = FieldDataIsolationStrategy.withDefaults(List.of(
        new FieldRule(OrderEntity.class, "ownerId", ownerScopes)));
```

`ownerScopes` 是应用提供的 `Supplier<DataIsolationScope>`，读取本次可信范围。该规则与默认维度取 AND，不受内置维度忽略影响。

已有实体没有实现隔离接口时，可直接配置它的持久属性。例如 `LegacyOrderEntity` 保存 tenantId 和 organizationId：

```java
FieldDataIsolationStrategy fields = FieldDataIsolationStrategy.withDefaults(List.of(
        new FieldRule(LegacyOrderEntity.class, "tenantId",
                () -> DataIsolationScope.of(DataIsolationContextHolder.requireTenantId())),
        new FieldRule(LegacyOrderEntity.class, "organizationId",
                () -> DataIsolationScope.of(DataIsolationContextHolder.requireWorkspaceId()))));
```

此处用 require 方法，缺少可信归属时直接拒绝。两条普通字段规则始终取 AND，不响应内置 TENANT/WORKSPACE 忽略；需要该忽略语义时，实体须实现相应接口，并给规则标注维度。若实体已实现接口且只想改属性，使用前面的维度覆盖方式，避免无意叠加两条归属规则。

| 范围结果 | 生成条件 |
| --- | --- |
| `DataIsolationScope.of(100L)` | `column = ?` |
| `DataIsolationScope.ofValues(List.of(100L, 200L))` | `column IN (?, ?)` |
| `DataIsolationScope.of(null)` 或空集合 | `1 = 0` |
| `DataIsolationScope.unrestrictedScope()` | 本条规则不追加条件，其他规则继续执行 |
| Supplier 返回 null 或抛异常 | 拒绝查询 |

默认上下文缺失经 `of(null)` 转为空范围；Supplier 返回 null 则表示必要的范围结果缺失，两种情况不同。范围集合不可修改、不允许 null 元素，元素本身不做深拷贝。新增参数按值的 Java 类型选择 TypeHandler，不自动复用实体字段的专属加密处理器。

Supplier 在每次查询中调用，应读取请求内已准备好的结果；不要在单例初始化时固定某个用户的范围，也不要在 Supplier 中再查权限库或递归调用受管 Mapper。IN 集合不自动分批，没有内置数量上限，接入方需按数据库限制和数据规模控制。

平台操作获准读取多个目标租户时，可用 `FieldRule.forDimension(TENANT, "tenantId", targetTenantScopes)` 覆盖默认租户规则，让 targetTenantScopes 返回已校验的 `ofValues(...)`；保留其他维度。仅某类实体允许此范围时，使用实体级 FieldRule。目标实体未实现 TenantIsolationObject 时使用普通字段规则。账号归属、权限来源租户和目标数据租户应分别确定，不能用“忽略租户”代替一个有限的目标集合。

多个独立字段取 AND。若只允许 `(租户 A, 工作空间 X)`、`(租户 B, 工作空间 Y)`，分别配置两个 IN 集合会产生未授权的交叉组合；应固定单租户范围，或由自定义策略保留成对条件。

### 7.4 临时忽略查询维度

在已经授权的管理操作中，可临时忽略一个或多个内置维度：

```java
import com.wind.integration.core.context.DataIsolationQueryIgnoreHolder;

import static com.wind.integration.core.enums.DataIsolationDimensions.ENVIRONMENT;
import static com.wind.integration.core.enums.DataIsolationDimensions.TEST_DATA;
import static com.wind.integration.core.enums.DataIsolationDimensions.WORKSPACE;

DataIsolationQueryIgnoreHolder.runIgnoring(() -> {
    orderMapper.selectListByQuery(query);
}, ENVIRONMENT, TEST_DATA);

List<OrderEntity> rows = DataIsolationQueryIgnoreHolder.callIgnoring(
        () -> orderMapper.selectListByQuery(query), WORKSPACE);
```

回调在前，维度在后。嵌套调用合并忽略项，重复值只算一次，空参数不新增忽略项；正常或异常退出都恢复外层状态。`callIgnoring` 原样抛出 Callable 的受检异常，调用处须处理或声明 `throws Exception`。忽略集合在作用域内不可变，绑定由 ScopedValue 自动恢复；不再提供 `clear()`，旧的手动清理调用应删除。需要恢复隔离的查询放到忽略回调之外。

FieldDataIsolationStrategy 对被忽略维度跳过 Supplier 与条件生成，但仍检查实体映射。普通字段规则、其他策略和原业务 WHERE 继续生效；迁移期间残留的 Flex 原生租户条件也不会删除。忽略同样作用于关系内表上的字段策略，也不会扩大 SQL 语法支持范围。

忽略不改变当前归属，不关闭插入监听器，也不自动传播到新线程。平台线程、虚拟线程和结构化子任务都须自行显式开启忽略作用域；虽然结构化子任务会继承 ScopedValue 绑定，本组件会核对创建线程，避免把父线程的忽略状态带入子任务。需要同时读正式和测试数据时，可以在授权后忽略 TEST_DATA，或配置显式多值范围；“忽略”本身不授予访问权限。

### 7.5 DataIsolationStrategy 的抽象与扩展

#### 定性与职责

[DataIsolationStrategy](src/main/java/com/wind/mybatis/isolation/DataIsolationStrategy.java) 是 DAL 读取约束的扩展接口：针对适用实体，将本次可信上下文或已校验范围转换为参数化查询条件。它回答“这次读取要追加哪些行级约束”。租户归属、工作空间边界、业务数据权限都可以通过这一接口提供条件。

接口以实体元数据和查询目标为输入，没有要求资源类型、角色模型或内置隔离接口。`FieldDataIsolationStrategy` 只是其中的通用实现：通过接口约定或显式配置，把字段允许值转换为等值/IN 条件。业务需要权限并集或关系范围时，可以实现另一个策略，无需继承字段策略，也无需修改拦截器。

策略属于可信应用代码。它可以生成受支持的 SQL 谓词，但不承担身份认证、功能授权、角色解析、前后置业务校验、写控制、列权限或脱敏；这些能力由各自负责的模块提供。将权限范围转成查询条件，是完整权限流程中的读取执行环节。

| 参与者 | 持有的职责 |
| --- | --- |
| 权限及业务模块 | 确认主体、动作、授权作用域和引用有效性，准备可用范围；执行必要前后置校验和写控制 |
| 上下文与范围提供者 | 提供本次归属或已解析范围，管理其生命周期，不在 SQL 拦截过程中重做授权查询 |
| `DataIsolationStrategy` 实现 | 决定适用实体，将范围解释为读取条件，明确根表与关系内表的语义 |
| `DataIsolationQueryInterceptor` 及内部重写器 | 发现实体、选择和排序策略、检查 SQL 形态、组合条件、绑定参数并处理查询缓存键 |

`DataIsolationContextHolder` 保存内置归属值；`DataIsolationQueryIgnoreHolder` 保存临时忽略的内置维度。两者都不承载业务角色与完整权限范围。自定义策略是否消费这些上下文，由自身契约决定，拦截器不会因设置了忽略状态就跳过全部策略。

#### 静态匹配与本次求值

| 方法 | 契约 |
| --- | --- |
| `supports(EntityMetadata)` | 只依据实体类型及列映射判断适用性；结果可缓存，不读取请求范围 |
| `resolve(QueryContext)` | 为当前根表返回参数化条件；空列表不追加，异常拒绝 |
| `resolveRelation(QueryContext)` | 为可信策略生成的关系内表提供标量约束；默认拒绝，见第 7.7 节 |
| `getOrder()` | Spring Ordered 顺序，越小越早；默认 0，同值保持输入顺序 |

工厂建立实体索引时调用 supports，固定该实体适用的策略；每次读取再调用这些策略的 resolve。因此不能根据当前登录人、有无权限或临时忽略来返回 supports=false，否则请求状态会混入静态匹配结果。实体受本策略保护时应保持匹配，具体空范围或错误在求值阶段表达。

UNION 的各分支分别调用根查询策略，同一实体出现多次也会分别求值；每个 QueryContext 使用所在分支的实体及别名。供应者应读取入口准备好的同一份范围快照，不应因调用次数改变返回值。派生表外层只保留原计数、过滤和分页，隔离条件加在内层实际读取物理表的位置。

根表与内表分开求值，是因为两种查询承担不同责任。例如读取员工列表时，部门表可以只用于证明员工的部门归属；它仍须满足租户和团队边界，但不一定适用“用户可以浏览哪些部门资料”的独立权限。直接复用内表的全部根表策略会错误缩小员工范围，直接跳过内表策略又可能丢失基础边界。resolveRelation 要求每个适用策略明确声明这一行为；未实现时默认拒绝，不能把不明确的处理当作放行。

| 策略结果 | 含义 |
| --- | --- |
| `supports` 返回 false | 实体不属于本策略的保护对象，不是本次请求无权限 |
| 返回空 `List<Condition>` | 本策略在当前场景不追加条件，其他策略继续执行 |
| 返回 `Condition.of("1 = 0")` | 有效空范围，本次读取没有可见记录 |
| 返回多个 Condition | 每一项都必须成立 |
| 返回 null 或抛异常 | 查询失败，不能回退为无条件查询 |

#### 输入模型与条件组合

接口内的几个类型分别承载静态事实、查询目标和执行结果，不要求调用方统一转换成某一种 Rule：

| 类型 | 内容与用途 |
| --- | --- |
| `EntityMetadata` | 实体类型与 Java 属性到物理列的映射，用于静态适用性与字段校验 |
| `QueryContext` | statement ID、物理表、当前别名/限定名及实体元数据；`column(property)` 生成当前表的列引用 |
| `Condition` | SQL 谓词与按占位符顺序排列的参数值，是策略与执行器之间的结果契约 |
| `FieldRule` | 可复用的字段规则值对象：目标类型、Java 属性、范围 Supplier、可选内置维度；不是所有策略的必需输入 |

FieldRule 本身不决定 AND/OR、覆盖或忽略，消费它的策略负责这些语义。FieldDataIsolationStrategy 的规则按 AND 执行；业务权限策略也可以复用字段条件生成，再自行组成完整并集。元数据映射及 Condition 参数列表会复制为不可修改集合，但参数对象本身不做深拷贝。

策略与其返回的多个 Condition 都取 AND，优先级不覆盖已有条件。需要 OR 时，应在一个 Condition 中保留完整分组：

```java
return List.of(Condition.of(
        "(" + context.column("ownerId") + " = ? OR " + context.column("id") + " = ?)",
        currentUserId, selectedOrderId));
```

最终查询为 `原业务条件 AND 基础隔离 AND (owner_id = ? OR id = ?)`。不要将两个 OR 分支拆成两个策略或两个返回元素。`Condition.sql` 由可信代码构造，业务值按占位符顺序放入 values；不能拼入前端提供的表名、属性名或 SQL。

`QueryContext.column(property)` 使用当前查询别名和实体列映射，适合生成根表列引用。参数只使用 `?`，数量须与值一致，禁止命名参数、固定索引参数和尾随语句。

#### 如何增加一个策略

先判断现有字段规则是否足够。只是属性名不同、允许值不同或追加一个独立字段约束，配置 FieldRule 即可。出现不同的条件组合、关系解释或范围模型时，再实现 DataIsolationStrategy。

扩展实现先用 supports 明确实体覆盖，再在 resolve 中读取本次可信范围、检查必要输入并返回条件。若实体还会出现在其他策略生成的关系内表中，须同时定义 resolveRelation；只用于根表时可以保留默认拒绝。最后通过真实 Mapper 验证正确结果、有效空范围、缺少输入、范围切换与查询缓存，不只断言生成的 SQL 字符串。

有多个策略时，在装配处显式传入完整集合：

```java
@Bean
public DataIsolationQueryInterceptor dataIsolationQueryInterceptor(
        List<DataIsolationStrategy> strategies) {
    return new DataIsolationQueryInterceptor(strategies);
}
```

应用将所需的字段策略、自定义策略分别声明为 Bean，拦截器只使用传入的完整集合。内置接口识别既可由显式 `FieldRule.forDimension` 启用，也可由 `withDefaults` 一次选择四个维度。原无参构造调用须迁移到这些显式入口；同一维度不要重复配置多份字段策略，否则条件会叠加。

策略通常是共享单例，配置应不可变、实现应线程安全；请求范围只在 resolve/resolveRelation 阶段获取，不保存在全局变量中。SQL 解析、参数重排和缓存键处理由插件负责，策略无需修改 BoundSql 或替换 Executor。

### 7.6 数据权限查询隔离

数据权限策略消费已经确定的资源可见范围，将约束落实到普通 Mapper 查询。业务查询无需逐处拼权限条件；权限策略保留上游已经裁决的完整分支，与独立基础边界共同执行：

```text
原业务条件
AND 租户、工作空间等基础边界
AND（同一资源、同一授权作用域内的有效权限分支并集）
```

范围合并规则由权限产品及其裁决层定义，Wind 负责执行最终条件。完整分支内部可以取 AND，合法分支之间可以取 OR；不能先把各维度分别合并再组合，否则会形成原授权不存在的交叉配对。多个角色的结果应先由上游裁决，再交给权限策略；不能为每个角色安装一个策略，否则会被 AND 成交集。

查询链路分为三个责任明确的步骤：权限入口确认本次操作并准备范围；权限策略通过供应契约取得范围、转换条件；拦截器在列表、计数、聚合和分页之前执行这些约束。供应者读取已经准备好的结果，不在 resolve 中查询角色表、调用远程权限服务或重新解析部门树。查到记录仍不代表已经取得修改、删除或其他动作权限。

**常见业务场景如何落地**

| 场景 | 范围准备与查询表达 | 必须保留的边界 |
| --- | --- | --- |
| 指定租户、用户、团队、员工、发卡行或工单记录 | 校验稳定引用后，按该资源的 ID 属性生成等值/IN | 指定对象不能跨出可信目标范围；资源与实体映射由所属模块登记 |
| 本人处理或指定用户处理的工单 | 将可信用户 ID 映射到当前 handlerId | 协作人、候选人不等于当前处理人 |
| 本人提交或指定用户提交的工单 | 将实际提交账号 ID 映射到 creatorId | 代客对象、登记员工不能替换实际提交账号 |
| 指定租户内的工单 | 将合法目标租户集合映射到 tenantId | 该 DATA 分支仍与独立目标租户边界取 AND |
| 本人所在部门或指定部门的员工 | IAM 解析部门 ID 集合，权限策略生成当前任职 EXISTS；可与指定员工 ID 取 OR | 员工读取另有独立租户、当前团队约束，无 DATA 时也要执行 |
| 部门仅当前节点或包含后代 | 请求准备阶段按当前树解析 NODE_ONLY/SUBTREE，保留根引用与层级定义 | 不包含兄弟节点，不保存后代快照；下次请求重新解析才能反映树变化 |
| 无适用 DATA、多个角色贡献 DATA | 按权限产品合同明确拒绝或裁决完整范围；Fincone 受控类型无适用 DATA 时拒绝 | 不能将缺少授权转换为“不追加过滤”；不混合不同团队的角色范围 |

这些是查询表达示例，不是产品允许配置的白名单。哪些资源、模式和维度组合合法，由权限产品及其解析入口决定；非法、退役或未知定义必须在那里拒绝，不能静默丢弃后变成无绑定。Wind 不需要为每种业务条件新增策略接口。

原业务筛选会继续取 AND。例如工单列表原先要求“我是处理人、协作人或候选人”，再加“本人提交”的 DATA 条件后，只会得到两者交集。接入方须按该列表的业务用途确认结果，插件不会自动取消原筛选。

**与 Capte 权限能力对接**

Capte/IAM 负责主体、作用域、授权来源与 READ/WRITE 用途的裁决，业务域提供可信对象归属及关系事实；Capte 的读取适配把结果转换成 Wind Condition。Wind 不依赖 Capte 资源目录，也不在 SQL 拦截过程中查询角色或重做授权。

例如两条已裁决读取分支为“T1 的合规工单”与“T2 的充值工单”，应整体传入：

```sql
(tenant_id = ? AND ticket_type = ?) OR (tenant_id = ? AND ticket_type = ?)
```

参数保持对应分支的顺序；不能改成两个独立的 IN 集合，也不能把分支内字段分别 OR。实际条件中的列限定名由 QueryContext.column 取得，值始终参数化绑定。

Fincone 当前要求完整 DATA 配对、包含择小、只读收紧及无适用 DATA 拒绝，这些规则由 Capte/IAM 承接。消费者若使用 `noBinding` 或返回空条件列表，其技术含义仅是本策略不追加过滤，不能据此把“无 DATA”或显式 ALL 当成无条件放行。合法范围没有匹配记录才是普通空结果；范围缺失、失效或无法解析应由授权入口或策略明确拒绝。

读取插件和插入字段填充不承担写入鉴权。修改、删除、创建及转派由原业务用例消费 WRITE 结果，在必要的事务、版本和前后置检查中执行；不能把 SELECT 可见性作为写权限证明。

员工部门、工单处理人等关系由数据所属模块按真实字段、有效期和可信目标边界登记，不能依赖默认员工关系配置。字段归属存在也不能代替关系有效性校验。业务注册、完整范围模型、供应者及 Spring 装配由 capte-domain 的 `docs/系分设计/权限与访问控制-数据权限与数据隔离-系分设计.md` 维护，权限政策同时以 Fincone 最新产品合同为准。组件存在与 H2 用例通过，不代表这些生产消费者已经接通。

### 7.7 用关系表约束读取

可信策略生成的关系条件与手写 SQL 的支持范围不同。下面以“本人负责的订单，或本人参与的订单”为例：

```java
import com.wind.mybatis.isolation.DataIsolationStrategy;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

public final class OrderVisibilityStrategy implements DataIsolationStrategy {

    private final Supplier<Long> userIds;

    public OrderVisibilityStrategy(Supplier<Long> userIds) {
        this.userIds = Objects.requireNonNull(userIds);
    }

    @Override
    public boolean supports(EntityMetadata entity) {
        return OrderEntity.class.equals(entity.type());
    }

    @Override
    public List<Condition> resolve(QueryContext context) {
        Long userId = Objects.requireNonNull(userIds.get(), "缺少可信用户");
        String sql = "(" + context.column("ownerId") + " = ? OR EXISTS ("
                + "SELECT 1 FROM t_order_member member WHERE member.order_id = "
                + context.column("id") + " AND member.user_id = ?))";
        return List.of(Condition.of(sql, userId, userId));
    }
}
```

该示例要求应用登记订单实体和订单成员实体的 BaseMapper；成员实体的物理表为 t_order_member，包含 order_id、user_id 及业务需要的归属列。成员实体应通过接口或显式字段规则配置独立租户、工作空间等边界。固定表名、列名来自数据所属模块，不能来自运行时用户输入；成员关系是否有效、是否有状态或时间约束，由该模块在条件中补齐。

插件校验关系结构后，对每张内表的全部适用策略调用 resolveRelation，将返回条件 AND 到该 EXISTS 的 WHERE 中。原策略参数与内表参数最后按完整 SQL 的占位符顺序绑定。

| 内表策略 | 关联读取行为 |
| --- | --- |
| `FieldDataIsolationStrategy` | 复用字段规则，包括当前范围及显式忽略语义 |
| 自定义策略未覆写 `resolveRelation` | 拒绝关联查询 |
| 自定义策略返回标量条件 | 条件全部 AND 到内表 WHERE |
| 自定义策略明确返回空列表 | 仅该策略在关系场景不追加条件 |
| 返回 null、抛异常，或返回条件再次含子查询 | 拒绝查询 |

例如，业务权限策略可以明确声明“内表只是参与目标资源判断的关系事实，不作为独立资源再次应用 RBAC”：

```java
@Override
public List<Condition> resolveRelation(QueryContext context) {
    return List.of();
}
```

只有该策略的责任确实如此时才采用。其他基础边界仍须执行，不能为消除异常让所有策略一律返回空。上例 OrderVisibilityStrategy 没有覆写该方法，因此订单本身被其他策略用作关系内表时会拒绝。

策略生成的关系条件限于：

- 单层正向 `EXISTS (SELECT 1 ...)`，位于 AND、OR、括号组成的布尔表达式中；不能用 NOT、CASE 或比较表达式反转结果。
- 内表须有已登记 BaseMapper、明确且不遮蔽外层的别名；列须带表限定并匹配登记元数据，不支持跨 schema。
- 内层 WHERE 必须有独立成立的“外层列 = 内层列”条件。只在 OR 分支里出现的等式不算有效关联。
- 子查询最多一个 JOIN/INNER JOIN，且有一个 ON 条件。不支持外连接、嵌套/标量子查询、UNION、CTE、分组、排序、分页或锁。

插件核对的是 SQL 结构和已登记策略，不推断完整业务归属。表已登记、SQL 能执行，都不能代替关系提供者对租户、团队、有效期等必要条件的定义。

### 7.8 查询覆盖范围与失败行为

下表中的“受管表”指当前工厂有 BaseMapper 元数据、且至少一个策略适用的表。查询返回实体、DTO 或标量不改变保护对象；通过受支持查询导出数据，也使用相同约束。

| 查询形态 | 行为 |
| --- | --- |
| 受管单表的列表、详情、COUNT/SUM、DTO、别名、原 WHERE 中的 OR | 在业务条件之外追加隔离，保留原分组 |
| 单表 GROUP BY/HAVING、排序、LIMIT/OFFSET、foreach、游标 | 在聚合、分页前应用隔离，保留参数映射 |
| 单表叶查询组成的 UNION、UNION ALL、UNION DISTINCT | 每个分支独立隔离，保留去重、重复记录及外层排序/分页；其他集合运算拒绝 |
| `FROM (SELECT ...) alias` 派生包装，包括内层 UNION 和多层计数包装 | 隔离内层物理表叶查询，外层计数、过滤、聚合、排序、分页保持；派生表须有别名 |
| 可信策略生成的相关 EXISTS | 按第 7.7 节执行内表约束 |
| 原业务 SQL 中的有限 WHERE EXISTS | 按下文规则执行，关系边界由原 SQL 提供 |
| 受管的顶层 JOIN、投影/排序等表达式子查询、CTE、INTERSECT/EXCEPT、LATERAL、SELECT INTO、PREWHERE/QUALIFY 或 LIMIT BY | 拒绝；忽略所有维度也不放宽语法限制 |
| ResultMap 的嵌套 select、懒加载，包括 discriminator 分支 | 一律拒绝，入口表未受管也一样 |
| 未登记表或所有策略均不匹配的表 | 普通模式保留直通；严格模式要求已登记且匹配策略，或明确声明为公共实体 |
| INSERT、UPDATE、DELETE | 插件直通，写控制另行执行 |

原业务 SQL 的 EXISTS 只允许 SELECT 1，最多一个 JOIN/INNER JOIN。内表须在同一工厂登记且没有任何适用的 Wind 隔离策略，不支持 schema、重复别名、内部嵌套、分组、排序、分页或锁。带此类子查询时，外层 WHERE 中的 NOT 表达式也会被拒绝。业务负责相关条件及内表边界；此路径不会调用 resolveRelation，不能照搬策略生成条件的能力。

派生与 UNION 组合中的物理表叶节点都须有实体映射；混合公共与受管分支时，公共分支保留原结果，受管分支各自过滤。允许的原 SQL EXISTS 可以位于叶查询的 WHERE；不能将受管表藏在投影、外层 WHERE 子查询、排序表达式或未支持的 JOIN 中。所有原始及新增参数在改写结束后统一按 SQL 顺序绑定。

例如 `SELECT COUNT(*) FROM (SELECT id FROM t_order WHERE state = ?) orders` 会先在 t_order 查询中追加隔离，再计数；无需把 tenantId 投影到外层。UNION 两个不同实体时，各自使用自己的字段映射和策略，不能只给首个分支加条件。

以下是 `SelectIsolationRewriterTests` 中的改写示例。实体的 tenantId 映射到 tenant_id，本次允许的租户为 10：

```sql
-- 输入 SQL，参数依次为 [1, 3, 5, 0]
SELECT r.id FROM protected_records r
WHERE r.id = ? OR r.id = ?
ORDER BY r.id LIMIT ? OFFSET ?

-- 改写后的 SQL，参数依次为 [1, 3, 10, 5, 0]
SELECT r.id FROM protected_records r
WHERE (r.id = ? OR r.id = ?) AND ((r.tenant_id = ?))
ORDER BY r.id LIMIT ? OFFSET ?
```

原 OR 条件作为整体保留，租户值用占位符绑定，分页参数仍对应原值。允许多个租户时生成 `IN (?, ...)`，空范围生成 `1 = 0`，不限范围则不追加该维度条件。该测试类直接比较完整 SQL 和按占位符顺序解析出的参数值，覆盖无 WHERE、OR 与分页、多值、空范围、不限范围、UNION ALL 及派生 COUNT；集成测试另在 H2 中验证实际查询结果。

普通模式的未受管查询、严格模式的已登记公共查询通常直通，但仍经过语法解析和全局结构检查：JSQLParser 5.4 无法解析的 SELECT、嵌套查询映射、TABLE 查询简写、管道式 FROM 查询及含 INSERT/UPDATE/DELETE 的 CTE 会报错。严格模式还拒绝所有 CTE 和未知表。已确认无需隔离的单表相同 SQL 可命中解析跳过缓存；SQL 或 Mapper 数量变化会重新检查。

原 SQL 和策略条件都检查完整词法 token，包括分号后的尾注释。当前拒绝 MySQL/MariaDB 可执行注释（`/*! ... */`、`/*M! ... */`）、嵌套块注释、`//` 注释、不满足空白要求的 `--text`，以及字符串和引用标识符之外的 `#`。这些形式在解析器与数据库之间可能有不同含义，不能原样放行，也不会通过删除内容来改变查询语义。公共查询、不限制范围和临时忽略同样执行此检查。

普通块注释、合法 `-- ` 行注释和优化器提示继续支持；字符串、反引号或双引号标识符中的注释标记不按注释处理。单条语句可以带结尾分号及普通尾注释；分号后出现第二条语句或其他 SQL token 会拒绝。MySQL 的 `#` 行注释应改用 `-- ` 或普通块注释，不要依赖框架猜测数据库方言。

单引号或双引号 token 中，紧邻引号的奇数个反斜杠会被拒绝：数据库 SQL mode 可能将该引号解释为转义字符，而解析器把它当作字符串边界。请优先使用参数绑定，SQL 中固定文本采用标准双写引号；不紧邻引号的普通反斜杠内容不受此检查影响。

动态表名、分表、视图、同义词、无实体映射的标量 SQL，以及直接 JDBC、其他 ORM、存储过程不属于保护契约。表名按简单名称匹配，不支持按 schema 分权；部署时固定 schema/search path，避免同名表混淆。StatementType.STATEMENT 不在已验证范围内。

每个工厂独立建立 BaseMapper 实体索引；同实体多个 Mapper 去重，同名表映射冲突或属性错误会报错。元数据按 Mapper 数量变化刷新，不用于运行时热替换策略配置。新增受保护实体、工厂或查询入口时，应重新核对登记与策略覆盖。

### 7.9 读写配合、缓存与性能

**读取和写入使用同一归属来源**

插件只处理 SELECT；租户、环境、测试标记和工作空间的插入填充见[第 5 节](#insert-listeners)。监听器会覆盖对应字段的已有值，查询忽略不会停止填充。环境缺失或为空白时拒绝插入；租户、工作空间未绑定时允许填 null，业务必填要求应在入口及数据库约束落实。

应用通过租户插入监听器填充当前归属，并继续负责写权限校验，无需为保留填充而启用原生租户过滤。读取一个获准的多租户集合，不表示允许向该集合中的任意租户写入；写入目标须由具体操作的可信上下文确定。读插件对 INSERT/UPDATE/DELETE 直通，即使更新前曾读到对象，实际更新或删除仍须带必要的归属和业务条件。

**范围变化与查询缓存**

隔离检查发生在 MyBatis 查询缓存命中前，新增条件与参数进入缓存键；六参数 Executor 查询也会重建 CacheKey。不同的隔离参数可以区分缓存，但关系表变化、权限撤销或 CURRENT_TIMESTAMP 推移未必改变参数，不能自动使旧结果失效。

对于任职、成员关系、到期时间或撤权会影响可见范围的应用，建议先采用以下配置，再验证是否有必要引入结果缓存：

```yaml
mybatis-flex:
  configuration:
    local-cache-scope: statement
    cache-enabled: false
```

自建工厂时，在创建 SqlSession 前对其 Configuration 设置 `setLocalCacheScope(LocalCacheScope.STATEMENT)` 和 `setCacheEnabled(false)`。上述配置作用于整个工厂；多工厂应用须分别核对，不能只修改未被业务使用的默认工厂。

STATEMENT 限制一级缓存跨查询复用，cache-enabled=false 关闭 MyBatis 二级缓存。只关闭其中一个仍可能得到旧结果：SESSION 会话可以继续命中本地缓存；二级缓存则可能在新会话中复用旧关系判断。需要保留其他 Mapper 的二级缓存时，须保证受影响的读取不使用二级缓存，并验证关系表更新、到期及撤权的失效机制。只设 TTL 仍会留下过期前的旧权限窗口。

应用还须更新请求范围快照，并管理 Redis 等业务结果缓存；这两个 MyBatis 配置不会处理它们。元数据缓存和“无需隔离 SQL”的解析跳过缓存不保存受管查询结果，不必因关系变化清空。

关闭查询缓存不改变数据库事务的快照语义。使用短生命周期 SqlSession 与适当的事务范围；长事务、已打开游标和已返回结果不会因关系变化自动撤回。要求执行时重新确认权限的操作，应在业务入口重判，不能把“每次查数据库”理解为任何时刻都能立即撤权。游标须在原授权范围内消费并关闭。

**查询成本**

原 SQL 解析使用模块内共享执行器，CPU 少于 4 时核心线程数为 1，否则为 CPU 数的一半；CPU 为 1 或 2 时最大线程数为 4，否则等于 CPU 数。等待队列最多 256 项；工作线程不继承请求上下文，空闲 60 秒可回收，daemon 线程不阻止 JVM 退出。Mapper 元数据刷新不重建执行器。队列满时拒绝解析，不在调用线程兜底执行，也不退回未隔离 SQL。

JSQLParser 5.4 的原 SQL 解析超时覆盖排队与执行时间；超时或调用线程中断会取消任务，中断标志保留。策略条件的 parseCondExpression 沿用同步解析方式，不承诺与原 SQL 共享同一超时。Parser 和 AST 每次独立创建，不在线程间共享。

原参数的根属性名在每次查询中建立索引，字段和关系条件共用，避免每个新增参数重复扫描所有原参数；点号、数组下标、additionalParameters 与 Value 后缀的冲突避让保持一致。

受管查询每次解析并改写 SQL，语法树不跨请求缓存；静态实体绑定可以复用。增加 IN 值、OR 分支和 EXISTS 会增加数据库的参数与执行成本。用实际数据量核对索引和查询计划，重点覆盖关联键、归属列与范围列；H2 行为验证不能替代目标数据库的性能验证。

<a id="verification"></a>
## 8. 接入验证与排障

### 接入时验证什么

先在实际工厂中确认 Wind 插件、实体规则和各维度的执行责任，再用两个租户、两个工作空间的数据验证结果。完整迁移租户隔离到 Wind 时确认原生 TenantFactory 未启用；保留 Flex 租户时，Wind 显式配置其他维度，并分别验证手写 SQL 和写入边界。生成 SQL 与 XML/注解 SQL 都应覆盖，不能只检查 Bean 或正常列表。

| 验证场景 | 预期结果 |
| --- | --- |
| 同一范围查询列表、详情、计数、聚合、分页、导出 | 返回记录、总数与统计使用相同边界；越界 ID 不可见 |
| 普通实体仅有 tenantId 字段或 Flex 租户注解 | 补显式 Wind 规则后，生成与手写 SQL 均隔离 |
| 无 DATA、有效空范围、缺失或非法权限范围 | 按当前权限合同验证；Fincone 受控类型无 DATA 明确拒绝，合法空范围无记录，缺失或非法范围拒绝 |
| 多个合法完整分支 | 保留分支内部的字段/关系配对和 AND，分支间按已裁决语义组合；基础边界与原业务条件继续 AND |
| 单个/多个目标租户、嵌套上下文、异常退出、线程复用 | 只返回获准范围，退出后恢复，不残留前次身份 |
| 忽略一个或多个内置维度 | 只移除对应字段条件，普通字段和权限策略仍生效；退出后恢复 |
| 根记录合法、关系记录跨租户或团队 | 不能通过越界关系分支获得访问 |
| 工单转派、部门移动、新增下级或任职到期 | 在约定的范围重算与缓存策略下反映变化，提交人含义保持不变 |
| 不支持的 SQL、范围供应失败或解析失败 | 查询失败，不回退为无隔离 SQL |
| 租户字段填充及 UPDATE/DELETE | 原生租户能力退役后，独立写入机制仍限制合法目标 |

目标数据库上的插件组合、类型处理器、关系时效和查询计划由消费应用验证。字段范围能正确转换成 SQL，不代表主体资格、引用有效性或写操作已经通过授权。

### 诊断日志

排查隔离是否生效、查询为什么被拒绝时，可为下面两个类临时开启 DEBUG：

```yaml
logging:
  level:
    com.wind.mybatis.plugin.DataIsolationQueryInterceptor: DEBUG
    com.wind.mybatis.plugin.SelectIsolationRewriter: DEBUG
```

同一次查询用 `statement`（Mapper 语句 ID）定位；并发请求的关联沿用应用已有的 traceId 日志配置。

| 日志 | 用途 |
| --- | --- |
| `metadata ready` | 确认 Mapper、表、受保护实体数量及是否开启严格模式；元数据建立或刷新时记录 |
| `skipped` / `unchanged` | 区分缓存命中的非隔离查询、没有匹配策略、策略未追加条件 |
| `prepared` | 确认 SQL 文本是否改变（`sqlChanged`）、绑定参数总数及准备耗时；不把参数适配导致的 BoundSql 对象变化算作 SQL 改写 |
| `rejected` | WARN 记录拒绝查询的阶段、异常类型和抛出位置；原异常继续向调用方抛出 |

失败阶段由拦截器内部的 `PreparationStage` 枚举定义：结果映射检查（`RESULT_MAPPING`）、取得 SQL（`BOUND_SQL`）、元数据建立（`METADATA`）、SQL 改写（`REWRITE`）、语句复制（`STATEMENT_COPY`）和缓存键重建（`CACHE_KEY`）。数据库执行异常由原调用链处理，这两类日志不重复记录。

需要比较改写前后 SQL 时，将拦截器临时调整为 TRACE：

```yaml
logging:
  level:
    com.wind.mybatis.plugin.DataIsolationQueryInterceptor: TRACE
```

`Read isolation SQL` 在同一个日志事件中提供 `statement`、`sqlChanged`、`before` 和 `after`。前后内容来自本次查询已经生成的 BoundSql，不会为了记录日志再次求值动态 SQL、范围供应者或策略。SQL 未改变时复用同一份展示文本。需要逐策略和逐叶的内部诊断时，再将 SelectIsolationRewriter 调整为 TRACE，查看 `Resolving read isolation` 和 `rewritten`。

SQL 展示文本保留查询结构与绑定占位符，隐藏字面量及注释，保留简单反引号表名与列名，其他复杂引号标识符隐藏；含反斜杠转义或无法确定边界的输入保守省略。它用于结构对照，不是可复制执行的原始 SQL。每段输入最多 16,384 个字符，超出后直接省略正文，只报告长度及省略原因；展示输出也受长度限制。无法可靠处理的词法形式会省略正文，不回退输出原文。

INFO 不记录逐次查询；DEBUG 和 WARN 不输出 SQL、参数值、授权集合或异常消息/堆栈。TRACE 同样不展开参数值。调用方仍会收到查询原始异常，应用统一异常日志也须遵循脱敏要求。新增对照诊断发生运行时异常时舍弃诊断，不改变查询结果，也不覆盖原查询异常。

准备耗时不含数据库执行和 SQL 展示处理；关闭 DEBUG 时不采集该耗时。关闭 TRACE 时不进行诊断词法扫描或展示字符串构建；仅开启 DEBUG 时会比较 SQL 文本以确定 sqlChanged。TRACE 开启后，短 SQL 的展示处理采用现有 JSQLParser 词法能力，不构建完整语法树，也不增加解析执行器或 SQL 日志缓存。

生产排查应限定实例和开启时段，完成后恢复原日志级别。日志文件的异步写入、滚动与保留期限沿应用已有日志配置；异步输出仍有队列、分配和磁盘成本。本组件不新增观测 SPI、采样器或语句白名单，SQL 诊断也不承担权限审计。

### 常见问题

| 现象或诊断 | 优先检查 |
| --- | --- |
| 查询没有隔离 | 插件是否在实际工厂；表是否有 BaseMapper；实体是否匹配接口或显式规则 |
| `Entity has no isolation strategy or public declaration` | 为实体补隔离策略；确实无需隔离时才声明为公共实体 |
| `Query table has no isolation mapping` | 实际 SQL 是否涉及未登记表或严格模式不支持的 schema 限定名 |
| `Required entity has no protected mapper` | 当前工厂是否登记了该实体的 BaseMapper，是否有适用策略 |
| 缺少上下文时返回空结果 | 默认规则把 null 转为空范围；入口是否绑定需要的维度 |
| `Missing isolation scope` | 自定义字段 Supplier 是否返回 null |
| `Missing or unsupported isolation column mapping` | 配置的是 Java 属性还是物理列；属性是否真实持久化 |
| `Ambiguous isolation rules` | 是否有互不包含的接口规则同时匹配同一维度 |
| `Conflicting isolation mappings for table` | 同工厂同名表的实体与规则是否一致 |
| `Isolation strategy does not support relation queries` | 内表适用策略是否明确实现 resolveRelation |
| `Unsupported isolation relation predicate` | 正向 EXISTS、别名、列映射、AND 关联键和子查询形态 |
| `Read isolation supports single-table SELECT only` | 原查询是否有不受支持的 JOIN、子查询或分页结构 |
| `Cannot parse query` / `Unsupported parameter placement` | 脱敏 SQL、参数映射及实际插件顺序 |
| `Unsupported SQL comment syntax` | 是否包含可执行、嵌套或歧义注释，以及未引用的 `#`；改用受支持的注释形式 |
| `Unsupported SQL statement tail` | 结尾分号之后是否还有另一条 SQL；每次只执行一条受支持查询 |
| `Unsupported SQL quoted literal` | 固定 SQL 文本是否用反斜杠转义引号；改用参数绑定或标准双写引号 |
| 忽略后仍有过滤 | 原 WHERE、普通字段及其他策略；迁移是否残留 Flex 原生租户过滤 |
| 生成查询与手写 SQL 结果不同 | 原生 TenantFactory 是否已停用，两类查询是否使用同一工厂、上下文和 Wind 规则 |
| 新增关系或任职到期后结果未变 | 一级/二级缓存、业务缓存及范围快照是否更新 |
| 环境字段插入失败 | ServiceInfoUtils 是否能读到非空白的应用 profile |
| 分页、树或加密字段异常 | counter/converter、children/parent_id 映射、TextEncryptor 初始化与密钥 |

诊断保留 statement ID、异常类型和必要的上下文标识，不输出完整授权集合或敏感明文。发现跨归属结果时先停止受影响入口，核对实际执行 SQL 与上下文；回退使用验证过的应用、依赖和配置组合，不以关闭隔离恢复查询。

### 构建与测试

在仓库根目录先用 `mvn -version` 核对实际使用 JDK 25。当前 4.1 分支的查询忽略组件依赖正式版 ScopedValue，无须启用预览功能。JDK 21 应使用对应的兼容分支，不能仅修改 revision 将当前源码降到 4.0 构建。

```bash
WIND_MYBATIS_TESTS='MybatisQueryMethodHelperTests,MybatisTreeObjectHelperTests,TenantContextHolderTests,DataIsolationContextHolderTests,DataIsolationQueryIgnoreHolderTests,IsolationSqlDiagnosticsTests,SelectIsolationRewriterTests,SelectIsolationRelationTests,SelectIsolationExecutionTests,DataIsolationQueryInterceptorIntegrationTests,DataIsolationDefaultsIntegrationTests,DataIsolationMetadataIntegrationTests,WorkspaceIsolationIntegrationTests,DataIsolationReadWriteIntegrationTests,DynamicSqlIsolationIntegrationTests,RelationshipPermissionIntegrationTests,DataIsolationRelationIntegrationTests,FieldDataIsolationStrategyIntegrationTests,FieldDataIsolationStrategyConfigurationTests,DataIsolationSpringIntegrationTests,DataIsolationCoverageIntegrationTests,CompositeSelectIsolationIntegrationTests,SqlCommentIsolationIntegrationTests'

mvn -o -pl tests -am clean verify \
  -Dmaven.test.skip=false -DskipTests=false -Dtest.ignore.failure=false \
  "-Dtest=$WIND_MYBATIS_TESTS" "-Dit.test=$WIND_MYBATIS_TESTS" \
  -Dsurefire.failIfNoSpecifiedTests=false -Dfailsafe.failIfNoSpecifiedTests=false
```

`-o` 适用于依赖已缓存的环境；首次构建先通过项目配置的 Maven 仓库解析依赖。父 POM 在 Surefire 和 Failsafe 阶段均有测试执行，分别用 test 和 it.test 限定范围，并从两类报告核对实际数量、失败和跳过项。`verify` 生成制品，不发布。

| 测试入口 | 核对内容 |
| --- | --- |
| [上下文](../../core/src/test/java/com/wind/integration/core/context/DataIsolationContextHolderTests.java)、[查询忽略](../../core/src/test/java/com/wind/integration/core/context/DataIsolationQueryIgnoreHolderTests.java) | 嵌套、异常恢复、线程复用、传播边界及租户 Long 兼容 |
| [查询隔离](../../tests/src/test/java/com/wind/mybatis/isolation/DataIsolationQueryInterceptorIntegrationTests.java) | 查询形态、参数、插件链、缓存、分页与游标 |
| [字段策略](../../tests/src/test/java/com/wind/mybatis/plugin/FieldDataIsolationStrategyIntegrationTests.java)、[字段配置](../../tests/src/test/java/com/wind/mybatis/plugin/FieldDataIsolationStrategyConfigurationTests.java) | 显式规则、覆盖选择、实体属性、并集及忽略边界 |
| [覆盖校验](../../tests/src/test/java/com/wind/mybatis/plugin/DataIsolationCoverageIntegrationTests.java) | 严格模式、公共声明、未知表、启动校验及旧构造兼容 |
| [注释边界](../../tests/src/test/java/com/wind/mybatis/plugin/SqlCommentIsolationIntegrationTests.java) | 原 SQL/策略条件、完整尾注释、JDBC 前拒绝及字面量兼容 |
| [复合查询](../../tests/src/test/java/com/wind/mybatis/plugin/CompositeSelectIsolationIntegrationTests.java) | 派生计数、UNION、参数顺序、不同分支的范围和拒绝形态 |
| [关系结构](src/test/java/com/wind/mybatis/plugin/SelectIsolationRelationTests.java)、[关系执行](../../tests/src/test/java/com/wind/mybatis/isolation/DataIsolationRelationIntegrationTests.java) | 受控 EXISTS、内表策略、拒绝路径及实际 JDBC 参数顺序 |
| [解析执行器](src/test/java/com/wind/mybatis/plugin/SelectIsolationExecutionTests.java) | 执行器复用、饱和拒绝、排队超时、中断恢复及并发范围隔离 |
| [读写配合](../../tests/src/test/java/com/wind/mybatis/plugin/DataIsolationReadWriteIntegrationTests.java) | 租户、工作空间和测试数据的读取与赋值接口区分、插入与读取的归属来源、忽略不改变填充 |
| [Spring 装配](../../tests/src/test/java/com/wind/mybatis/spring/DataIsolationSpringIntegrationTests.java) | Starter 安装、策略集合、Mapper 扫描及并发读取 |
| [查询与树工具](src/test/java/com/wind/mybatis/flex) | MySQL 条件参数化、树拉平顺序和副本独立性 |

仓内数据库测试使用 H2。生产数据库方言、完整权限流程、真实范围供应、业务入口覆盖和容量需要在消费应用验收，不能从组件测试通过推导为全局生产准入。

<a id="source"></a>
## 9. 源码索引

- 查询与树：[MybatisQueryHelper](src/main/java/com/wind/mybatis/flex/MybatisQueryHelper.java)、[MybatisQueryMethodHelper](src/main/java/com/wind/mybatis/flex/MybatisQueryMethodHelper.java)、[MybatisTreeObjectHelper](src/main/java/com/wind/mybatis/flex/MybatisTreeObjectHelper.java)。
- 插入监听：[TenantIsolationFieldInsertListener](src/main/java/com/wind/mybatis/flex/TenantIsolationFieldInsertListener.java)、[EnvIsolationFieldInsertListener](src/main/java/com/wind/mybatis/flex/EnvIsolationFieldInsertListener.java)、[TestDataIsolationFieldInsertListener](src/main/java/com/wind/mybatis/flex/TestDataIsolationFieldInsertListener.java)、[WorkspaceIsolationFieldInsertListener](src/main/java/com/wind/mybatis/flex/WorkspaceIsolationFieldInsertListener.java)。
- 类型处理：[LocaleTypeHandler](src/main/java/com/wind/mybatis/convert/LocaleTypeHandler.java)、[AbstractEncryptBaseTypeHandler](src/main/java/com/wind/mybatis/encrypt/AbstractEncryptBaseTypeHandler.java)、[StringEncryptTypeHandler](src/main/java/com/wind/mybatis/encrypt/StringEncryptTypeHandler.java)、[LocalDateEncryptTypeHandler](src/main/java/com/wind/mybatis/encrypt/LocalDateEncryptTypeHandler.java)。
- 读隔离：[DataIsolationQueryInterceptor](src/main/java/com/wind/mybatis/plugin/DataIsolationQueryInterceptor.java)、[SelectIsolationRewriter](src/main/java/com/wind/mybatis/plugin/SelectIsolationRewriter.java)、[RelationIsolationPredicate](src/main/java/com/wind/mybatis/plugin/RelationIsolationPredicate.java)、[MybatisFlexReadParameters](src/main/java/com/wind/mybatis/flex/MybatisFlexReadParameters.java)。
- 隔离策略：[DataIsolationStrategy](src/main/java/com/wind/mybatis/isolation/DataIsolationStrategy.java)、[FieldDataIsolationStrategy](src/main/java/com/wind/mybatis/isolation/FieldDataIsolationStrategy.java)。
- 隔离范围：[DataIsolationScope](src/main/java/com/wind/mybatis/isolation/DataIsolationScope.java)。
- 公共维度与上下文：[DataIsolationDimensions](../../core/src/main/java/com/wind/integration/core/enums/DataIsolationDimensions.java)、[DataIsolationContextHolder](../../core/src/main/java/com/wind/integration/core/context/DataIsolationContextHolder.java)、[DataIsolationQueryIgnoreHolder](../../core/src/main/java/com/wind/integration/core/context/DataIsolationQueryIgnoreHolder.java)。
- 测试：[模块测试](src/test/java/com/wind/mybatis)、[隔离集成测试](../../tests/src/test/java/com/wind/mybatis)。
