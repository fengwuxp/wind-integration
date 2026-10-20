# mybatis-flex-extends：使用与设计

本模块封装 Wind 项目中常用的 MyBatis-Flex 操作：查询分页、MySQL 条件、树对象处理、字段转换与加密、插入字段填充，以及数据读取隔离。

| 要做的事 | 入口 |
| --- | --- |
| 把 Wind 查询请求转换为分页结果 | [查询与分页](#query) |
| 构造 FIND_IN_SET 或全文搜索条件 | [MySQL 查询函数](#mysql-functions) |
| 查询子树、补父名称、把树拉平 | [树对象处理](#tree) |
| 插入时填充环境、工作空间和测试标记 | [插入监听器](#insert-listeners) |
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

版本由宿主 Wind BOM/依赖管理确定。构建基线为 Java 21、MyBatis 3.5.19、MyBatis-Flex 1.11.5 和 JSQLParser 4.3。MyBatis-Flex Starter 按应用的 Spring Boot 版本选择；仓内集成测试使用 Spring Boot 4.1.0、Flex Boot 4 Starter 和 H2 2.4.240。

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

三个监听器通过 Flex 的 AbstractInsertListener 检查实体是否实现对应接口：

| 监听器 | 写入内容 |
| --- | --- |
| EnvIsolationFieldInsertListener | DataIsolationContextHolder.requireEnv()，缺失、空字符串或仅空白时拒绝写入 |
| TestDataIsolationFieldInsertListener | DataIsolationContextHolder.isTestData()，未绑定时为 false |
| WorkspaceIsolationFieldInsertListener | DataIsolationContextHolder.getWorkspaceId()，未绑定时填 null |

环境值的权威来源是 Spring profile。应用入口解析环境并绑定上下文，core 不读取 Spring 配置；读插件和插入监听器使用同一个上下文值。

可以挂到实体的 onInsert：

```java
@Table(value = "t_order", onInsert = {
        EnvIsolationFieldInsertListener.class,
        TestDataIsolationFieldInsertListener.class,
        WorkspaceIsolationFieldInsertListener.class
})
public class OrderEntity implements EnvIsolationObject, TestDataIsolationObject, WorkspaceIsolationObject<String> {
    // 保留业务字段，并实现各接口的 getter/setter。
}
```

或在现有 Flex 初始化入口中全局注册到目标接口，选择一种装配方式即可：

```java
FlexGlobalConfig globalConfig = FlexGlobalConfig.getDefaultConfig();
globalConfig.registerInsertListener(new EnvIsolationFieldInsertListener(), EnvIsolationObject.class);
globalConfig.registerInsertListener(new TestDataIsolationFieldInsertListener(), TestDataIsolationObject.class);
globalConfig.registerInsertListener(new WorkspaceIsolationFieldInsertListener(), WorkspaceIsolationObject.class);
```

注册应在业务写入前完成，具体生命周期沿用项目的 MyBatisFlexCustomizer。onInsert 和全局配置方式参考 [Flex 实体监听器](https://github.com/mybatis-flex/mybatis-flex/blob/main/docs/zh/core/table.md)。

监听器会覆盖对应字段的已有值。查询忽略不会关闭这些监听器，也不会改变当前租户、工作空间或环境。它们只在 Flex 实体插入监听链上执行；手写 XML/注解 INSERT、直接 JDBC 和 UPDATE/DELETE 不经过该链路。

租户填充可使用原有 Flex TenantFactory 与 @Column(tenantId = true)。写入归属校验仍由业务和 Flex 配置负责，读插件不会为更新、删除补写权限条件。工作空间监听器允许填 null；如果业务要求每条记录必须有工作空间，需要入口校验或数据库约束，否则可能写入一条默认查询不可见的记录。

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
## 7. 数据读取隔离

### 7.1 一次注册，普通查询默认过滤

需要保护的实体应在同一 SqlSessionFactory 中有已注册的 BaseMapper，并实现相应归属接口。插件按实际 SQL 表匹配，不依赖查询返回类型，因此列表、详情、COUNT、DTO 查询都可受保护。

使用 Flex Spring Boot Starter 自动创建工厂时，在应用扫描范围内声明一次：

```java
import com.wind.mybatis.plugin.DataIsolationQueryInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class DataIsolationConfiguration {

    @Bean
    public DataIsolationQueryInterceptor dataIsolationQueryInterceptor() {
        return new DataIsolationQueryInterceptor();
    }
}
```

Flex Boot 4 Starter 1.11.5 会将 Interceptor Bean 安装到工厂，不应重复调用 addInterceptor。自建 SqlSessionFactory 时，将插件显式加入各工厂的插件列表，并保留其他插件。

无参构造直接读取 DataIsolationContextHolder：

| 维度 | 实体接口 | 默认 Java 属性 | 默认读取条件 |
| --- | --- | --- | --- |
| TENANT | TenantIsolationObject | tenantId | 等于 getTenantId()；缺失时无可见数据 |
| WORKSPACE | WorkspaceIsolationObject | workspaceId | 等于 getWorkspaceId()；缺失时无可见数据 |
| ENVIRONMENT | EnvIsolationObject | env | 等于 getEnv()；缺失时无可见数据 |
| TEST_DATA | TestDataIsolationObject | testData | 等于 isTestData()；未绑定时只查 false |

普通请求默认只读正式数据，测试请求只读测试数据。需要同时读取两类数据时，可在授权后临时忽略 TEST_DATA，或为该维度配置明确的多值范围。

### 7.2 绑定请求上下文

| 类型 | 职责与位置 |
| --- | --- |
| DataIsolationContextHolder | core；绑定、读取当前执行的租户、工作空间、环境和测试标记，基于 WindTracer |
| DataIsolationQueryIgnoreHolder | core；控制当前线程哪些查询维度暂时不生效，不修改归属值 |
| DataIsolationDimensions | core.enums；四个枚举值、归属接口和 trace 属性名的映射 |
| DataIsolationScope | mybatis-flex-extends；表达一个维度允许读取的值集合或无限制 |
| DataIsolationQueryInterceptor.Rule | 读插件内部类型；绑定维度、Java 属性和 scopeSupplier |

上下文值表达当前归属，Scope 表达读取范围。跨工作空间读取可以扩大范围，同时让插入继续使用当前工作空间；各归属接口按需组合。

在应用已有的请求入口中，一次提供本次需要的维度。以下示例中的 environment 应由 Spring profile 确定，租户与工作空间来自已认证的业务上下文：

```java
import com.wind.common.exception.AssertUtils;
import com.wind.integration.core.context.DataIsolationContextHolder;

import java.util.Map;

import static com.wind.integration.core.enums.DataIsolationDimensions.ENVIRONMENT;
import static com.wind.integration.core.enums.DataIsolationDimensions.TENANT;
import static com.wind.integration.core.enums.DataIsolationDimensions.TEST_DATA;
import static com.wind.integration.core.enums.DataIsolationDimensions.WORKSPACE;

public final class IsolationRequestExample {

    public static void handleRequest(Long tenantId, String workspaceId, String environment,
                                     boolean testData, Runnable handler) {
        AssertUtils.hasText(environment, "应用入口必须提供环境值");
        DataIsolationContextHolder.runWith(Map.of(
                TENANT, tenantId,
                WORKSPACE, workspaceId,
                ENVIRONMENT, environment,
                TEST_DATA, testData), handler);
    }
}
```

只传入本次需要的维度，Map 的键和值均不可为 null。普通业务查询直接调用 Mapper；单维度绑定可用 runWithTenant、callWithTenantId、runWithWorkspace、callWithWorkspace，需要返回值时使用 callWith。

嵌套绑定继承外层上下文，只覆盖本次传入的维度。例如切换工作空间时，租户、环境和测试标记继续有效。已有 trace 时创建一个子 span，保持 traceId，parentSpanId 指向外层 span；没有 trace 时创建 root。正常或异常退出都会恢复外层上下文和旧租户值，原本没有旧租户值时会清理。

| 读取方法 | 未绑定时的行为 |
| --- | --- |
| getTenantId / getWorkspaceId / getEnv | 返回 null；getTenantId 另有旧租户兼容回退 |
| requireTenantId / requireWorkspaceId | 抛出异常 |
| requireEnv | 缺失、空字符串或仅空白时抛出异常 |
| isTestData | 返回 false |

租户绑定虽接收 Serializable，为兼容 TenantContextHolder 要求 Long；工作空间可使用 String 等 Serializable 类型。getTenantId 优先读取 trace，缺失时回退旧 TenantContextHolder。直接调用旧 setTenantId 的代码仍须管理自己的清理；新回调负责恢复进入前的旧值。

请求、消息、定时任务和线程池任务分别绑定各自身份。省略维度会继承外层值，不能用来清除外层身份。callWith 会把回调的受检异常包装成运行时异常并保留 cause；callWithTenantId、callWithWorkspace 的兼容签名仍声明 throws Exception。

### 7.3 实体映射与读取范围

WorkspaceIsolationObject 声明 getWorkspaceId/setWorkspaceId，与租户接口和记录主键独立。以下实体同时实现四种接口，工作空间使用 String，记录与租户 ID 使用 Long：

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
}
```

只实现业务需要的接口，随后由现有扫描机制注册 BaseMapper<OrderEntity>。默认工作空间范围来自上下文，无需另配 Supplier。需要多个工作空间的读取范围时，才覆盖 WORKSPACE 规则；以下配置替换前面的无参 Bean：

```java
import com.wind.mybatis.isolation.DataIsolationScope;
import com.wind.mybatis.plugin.DataIsolationQueryInterceptor;
import com.wind.mybatis.plugin.DataIsolationQueryInterceptor.Rule;
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
            @Qualifier("workspaceIsolationScopeSupplier") Supplier<DataIsolationScope> workspaceScopeSupplier) {
        return new DataIsolationQueryInterceptor(List.of(
                new Rule(WORKSPACE, "workspaceId", workspaceScopeSupplier)));
    }
}
```

应用提供 workspaceIsolationScopeSupplier Bean，每次查询返回本次已计算好的授权范围。Rule 使用 Java 持久化属性名：workspaceId 可映射到 organization_id；若 Java 字段本身叫 organizationId，则 Rule 配 organizationId。单独添加 getter 别名不会建立 Flex 字段映射。

| Supplier 结果 | 查询行为 |
| --- | --- |
| of(value) | column = ? |
| ofValues(多个值) | IN 条件 |
| of(null) 或 ofValues(空集合) | 1 = 0，无可见数据 |
| unrestrictedScope() | 不追加该维度条件 |
| Supplier 本身返回 null 或抛异常 | 查询失败，不回退为无限制 |

默认上下文缺失与 Supplier 返回 null 是两种情况：前者经 of(null) 转为空范围，后者表示没有提供必要的范围结果。范围列表不可修改且不含 null，元素本身不做深拷贝；新增参数按值的 Java 类型选择 TypeHandler，不自动复用实体字段上的专属加密处理器。

各维度以 AND 组合。只允许 (T1,W1)、(T2,W2) 时，不能拆成两个独立 IN 集合，否则会产生交叉组合；应限定一个租户后提供其工作空间集合。Supplier 应读取稳定的请求授权结果，不在单例初始化时捕获某个请求，也不要每条 SQL 都调用权限服务。

### 7.4 临时忽略查询维度

```java
import com.wind.integration.core.context.DataIsolationQueryIgnoreHolder;

import static com.wind.integration.core.enums.DataIsolationDimensions.ENVIRONMENT;
import static com.wind.integration.core.enums.DataIsolationDimensions.TEST_DATA;
import static com.wind.integration.core.enums.DataIsolationDimensions.WORKSPACE;

DataIsolationQueryIgnoreHolder.runIgnoring(() -> {
    processOrders(orderMapper.selectListByQuery(query));
}, ENVIRONMENT, TEST_DATA);

List<OrderEntity> acrossWorkspaces = DataIsolationQueryIgnoreHolder.callIgnoring(
        () -> orderMapper.selectListByQuery(query), WORKSPACE);
```

runIgnoring 接受 Runnable，callIgnoring 接受 Callable 并声明 throws Exception。回调在前，维度在后；多个维度合并，重复值只算一次，空参数不新增忽略项。正常或异常退出都恢复外层状态。clear() 只清理查询忽略，不清理 DataIsolationContextHolder 或旧租户上下文。

被忽略的维度不调用 Supplier，其余维度继续过滤。忽略不会改变当前归属，不关闭插入监听器，也不会删除业务 WHERE 或 Flex TenantFactory 已生成的条件。因此，忽略 TENANT 后仍可能受原生租户条件约束；调整读取配置时应保留写入保护。

### 7.5 异步任务

隔离上下文和查询忽略都不会自动传播到新线程。需要延续归属上下文时，在提交任务时使用项目已有的 ContextPropagationTaskDecorator：

```java
import com.wind.trace.task.ContextPropagationTaskDecorator;

executor.execute(ContextPropagationTaskDecorator.of().decorate(() -> {
    processOrders(orderMapper.selectListByQuery(query));
}));
```

装饰器传递捕获时的 trace 和归属值，任务结束后恢复执行线程的原上下文。查询忽略保存在独立的 ThreadLocal 中，不随 trace 传播；异步任务恢复正常隔离，需要放宽范围时由任务自身显式调用 runIgnoring/callIgnoring。

callIgnoring 原样抛出 Callable 的受检异常。不要将查询忽略标记存入普通 trace 变量，否则任务可能在原回调结束后仍携带忽略状态。

### 7.6 执行过程与支持边界

插件构造时组装规则，首次 SELECT 才读取当前 Configuration 的 Mapper 注册表，建立实体索引。各工厂独立缓存，Mapper 数量变化时刷新。同实体的多个 Mapper 去重；同名表规则冲突、属性缺失或列映射不受支持时报错。

```mermaid
flowchart LR
    A[Mapper 查询] --> B[Executor 拦截]
    B --> C[解析 SQL 和匹配实体表]
    C --> D[检查忽略并读取范围]
    D --> E[追加谓词和重排参数]
    E --> F[继续插件链与查询缓存]
    F --> G[JDBC 执行]
```

原 WHERE 保留括号分组，新增条件放在聚合、分页之前。插件创建新的 BoundSql/MappedStatement，按占位符顺序排列参数；四参数查询和游标继续原重载，六参数查询同步替换 SQL 与 CacheKey。MyBatis 一级、二级缓存按隔离值区分；业务自建缓存需自行把租户、工作空间等范围纳入缓存键。

内部 SelectIsolationRewriter 复用一次解析的完整语法树检查查询结构，再改写 SQL；MybatisFlexReadParameters 仅在参数中实际存在 SQL_ARGS 时适配 Flex 参数，保留 TypeHandlerObject 和 null 行为。接入方不用另行注册参数适配器，也无需调用这些内部协作方法。

| 场景 | 行为 |
| --- | --- |
| 单表详情、列表、COUNT/SUM、别名、OR、GROUP BY/HAVING、排序、LIMIT/OFFSET、foreach、游标 | 追加隔离条件；保持原查询与参数语义 |
| 受管 JOIN、子查询、UNION、CTE、SELECT INTO、部分非 LIMIT 分页 | 拒绝执行；忽略维度不会扩大语法支持范围 |
| 嵌套 select/懒加载 ResultMap，包括 discriminator | 一律拒绝，查询入口为公共表也一样 |
| JSQLParser 4.3 无法解析的 SELECT | 报错，未受管 SELECT 也会先解析 |
| 没有已注册 BaseMapper 或实体未实现维度接口 | 不增加隔离条件，不报漏配错误 |
| 动态表名/分表、视图/同义词、仅标量 SQL 无实体映射 | 不在保护契约内，实际物理名可能无法匹配而放行 |
| 不同 schema 的同名表 | 按简单表名共用规则，配置冲突时报错，不支持按 schema 分权 |
| INSERT、UPDATE、DELETE | 直通，不填字段、不校验写入归属 |
| 直接 JDBC、其他 ORM、存储过程、StatementType.STATEMENT | 不在已验证保护范围内 |

子查询无论出现在排序、分组、关联条件还是函数表达式中，都按上述边界检查。CTE 名称与受管物理表同名时保守拒绝，即使该 CTE 的定义只访问公共表；不涉及受管名称的公共 JOIN、UNION、CTE 和子查询仍然直通。

JSQLParser 4.3 对部分 TABLE 查询简写与数据库的理解不同，因此以 TABLE 关键字开头的括号表达式统一拒绝，包括只访问公共表的情况。字符串、注释和带引号的标识符中的同名文字不受这一检查影响。

未知表会放行，因此“缺失范围拒绝”不能理解为“所有漏配都拒绝”。应在 Mapper 注册完成后开放流量；元数据错误常在首次查询暴露，仅检查 Bean 创建成功不够。已有游标和已返回数据不会因范围后续变化重新过滤，应在原授权上下文内消费并关闭游标。

每次 SELECT 都会解析 SQL，包括 MyBatis 缓存命中的查询；语法树不缓存。Supplier 宜读取请求内结果，不应每条 SQL 都调用权限服务或递归执行受管查询。IN 集合没有自动分批与大小上限，应结合数据库参数限制和索引评估。

<a id="verification"></a>
## 8. 生产接入与维护

### 接入检查

1. **工厂与实体**：确认每个 SqlSessionFactory 已安装插件，受保护实体有已注册的 BaseMapper 并实现对应归属接口。动态表名、视图和未登记实体需单独处理，不能依赖此插件自动识别。
2. **身份与写入**：请求、消息和任务入口绑定必要维度。环境来自 Spring profile；工作空间必填要求由入口校验和数据库约束落实。更新、删除及手写插入另行校验归属。
3. **真实 SQL 与负载**：在目标数据库验证实际详情、列表、统计、分页和游标查询，覆盖两租户、两工作空间、缺失维度、忽略恢复及历史 null 字段。使用实际插件顺序、数据量和范围集合评估延迟与资源开销。
4. **发布与回退**：固定制品和依赖版本，重新编译消费应用并执行冒烟。观察空结果、权限失败和 SQL 异常比例；保留上一套已验证的制品及配置作为回退目标。

### 常见问题

| 现象 | 检查位置 |
| --- | --- |
| 查询没有隔离 | 当前工厂插件、Mapper 注册、实体接口、实际表名、忽略和无限制范围 |
| Missing isolation scope | 自定义 Rule 的 Supplier 是否返回 null；默认维度缺失会得到空范围 |
| 缺失维度时查询为空 | 入口是否绑定租户、工作空间、环境；测试标记未绑定时按 false 处理 |
| 环境字段插入失败 | Spring profile 是否已绑定到 ENVIRONMENT，值是否非空白 |
| 复用线程时出现旧租户数据 | 当前制品版本、入口身份绑定，以及直接调用旧 setTenantId 的位置是否清理 |
| Missing or unsupported isolation column mapping | Rule 是否使用 Java 属性名，@Column 映射和字段忽略配置 |
| Conflicting isolation mappings for table | 同工厂同名表是否映射为不一致的维度或列 |
| Read isolation supports single-table SELECT only | 查询结构或分页方言是否超出支持范围 |
| Cannot parse query / Unsupported parameter placement | 用脱敏 SQL 与参数映射复现，核对支持范围和插件组合 |
| Nested selects/lazy loading are not supported | ResultMap 和 discriminator 是否依赖嵌套查询 |
| 忽略后仍有过滤 | 原 SQL、Flex TenantFactory 和其他未忽略维度 |
| 分页统计或转换失败 | counter、resultQueryFunc、converter 和 querySize；游标结果的 id |
| 树字段没有填充 | TreeResources 的 children/parentName 字段、parent_id 映射及数据环 |
| TextEncryptor not init / 解密失败 | 初始化时机、处理器、密钥、密文格式和 SQL NULL |

排查时记录 statement ID、必要的维度标识和异常类型，避免输出完整授权集合、明文和密钥。出现跨归属结果时，暂停受影响入口，核对上下文及实际 SQL；不要通过移除插件或重试无隔离 SQL 恢复服务。修复后重新验证两租户、缺失上下文与写权限场景。

### 构建与测试

在仓库根目录使用 JDK 21 执行：

```bash
WIND_MYBATIS_TESTS='MybatisQueryMethodHelperTests,MybatisTreeObjectHelperTests,TenantContextHolderTests,DataIsolationContextHolderTests,DataIsolationQueryIgnoreHolderTests,DataIsolationQueryInterceptorIntegrationTests,DataIsolationDefaultsIntegrationTests,DataIsolationMetadataIntegrationTests,WorkspaceIsolationIntegrationTests,DataIsolationReadWriteIntegrationTests'

mvn -o -pl tests -am clean verify \
  -Dmaven.test.skip=false -DskipTests=false -Dtest.ignore.failure=false \
  "-Dtest=$WIND_MYBATIS_TESTS" "-Dit.test=$WIND_MYBATIS_TESTS" \
  -Dsurefire.failIfNoSpecifiedTests=false -Dfailsafe.failIfNoSpecifiedTests=false
```

`-o` 要求依赖已缓存；首次构建通过项目配置的 Maven 仓库解析依赖后再执行。父 POM 在 Surefire 和 Failsafe 阶段都配置了测试执行，因此分别通过 test 和 it.test 限定同一范围。verify 生成各模块 JAR，不发布制品。

| 测试入口 | 覆盖内容 |
| --- | --- |
| [DataIsolationContextHolderTests](../../core/src/test/java/com/wind/integration/core/context/DataIsolationContextHolderTests.java) | root/子 span、维度继承、异常恢复、线程复用、必填值和 Long 兼容 |
| [DataIsolationQueryIgnoreHolderTests](../../core/src/test/java/com/wind/integration/core/context/DataIsolationQueryIgnoreHolderTests.java) | 多维度忽略、嵌套恢复、归属不变和显式传播边界 |
| [查询隔离集成测试](../../tests/src/test/java/com/wind/mybatis/isolation/DataIsolationQueryInterceptorIntegrationTests.java) | SQL 支持边界、参数绑定、插件顺序、缓存和游标 |
| [默认规则、元数据与工作空间测试](../../tests/src/test/java/com/wind/mybatis/plugin) | 四个维度的默认值、Mapper 自动发现、多工厂与字段映射 |
| [读写集成测试](../../tests/src/test/java/com/wind/mybatis/plugin/DataIsolationReadWriteIntegrationTests.java) | 插入与查询归属一致、线程复用、忽略不改变写入和手写 SQL 边界 |
| [查询与树工具测试](src/test/java/com/wind/mybatis/flex) | MySQL 条件参数化、树拉平顺序和副本独立性 |

数据库用例使用 H2 验证组件契约。真实 MySQL 全文检索、生产方言、完整业务流程和吞吐需在消费应用中验证；加密字段还需覆盖所选 TextEncryptor、历史密文及密钥变更。

<a id="source"></a>
## 9. 源码索引

- 查询与树：[MybatisQueryHelper](src/main/java/com/wind/mybatis/flex/MybatisQueryHelper.java)、[MybatisQueryMethodHelper](src/main/java/com/wind/mybatis/flex/MybatisQueryMethodHelper.java)、[MybatisTreeObjectHelper](src/main/java/com/wind/mybatis/flex/MybatisTreeObjectHelper.java)。
- 插入监听：[EnvIsolationFieldInsertListener](src/main/java/com/wind/mybatis/flex/EnvIsolationFieldInsertListener.java)、[TestDataIsolationFieldInsertListener](src/main/java/com/wind/mybatis/flex/TestDataIsolationFieldInsertListener.java)、[WorkspaceIsolationFieldInsertListener](src/main/java/com/wind/mybatis/flex/WorkspaceIsolationFieldInsertListener.java)。
- 类型处理：[LocaleTypeHandler](src/main/java/com/wind/mybatis/convert/LocaleTypeHandler.java)、[AbstractEncryptBaseTypeHandler](src/main/java/com/wind/mybatis/encrypt/AbstractEncryptBaseTypeHandler.java)、[StringEncryptTypeHandler](src/main/java/com/wind/mybatis/encrypt/StringEncryptTypeHandler.java)、[LocalDateEncryptTypeHandler](src/main/java/com/wind/mybatis/encrypt/LocalDateEncryptTypeHandler.java)。
- 读隔离：[DataIsolationQueryInterceptor](src/main/java/com/wind/mybatis/plugin/DataIsolationQueryInterceptor.java)、[SelectIsolationRewriter](src/main/java/com/wind/mybatis/plugin/SelectIsolationRewriter.java)、[MybatisFlexReadParameters](src/main/java/com/wind/mybatis/flex/MybatisFlexReadParameters.java)。
- 隔离范围：[DataIsolationScope](src/main/java/com/wind/mybatis/isolation/DataIsolationScope.java)。
- 公共维度与上下文：[DataIsolationDimensions](../../core/src/main/java/com/wind/integration/core/enums/DataIsolationDimensions.java)、[DataIsolationContextHolder](../../core/src/main/java/com/wind/integration/core/context/DataIsolationContextHolder.java)、[DataIsolationQueryIgnoreHolder](../../core/src/main/java/com/wind/integration/core/context/DataIsolationQueryIgnoreHolder.java)。
- 测试：[模块测试](src/test/java/com/wind/mybatis)、[隔离集成测试](../../tests/src/test/java/com/wind/mybatis)。
