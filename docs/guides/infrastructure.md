# 基础设施组件接入

`wind-infra-commons` 把应用提供的事务管理器、锁工厂、消息发送器和 KMS 客户端接到 Wind 的公共接口。应用决定业务处理顺序，组件封装每一步的资源取得、调用和释放。

## 从一次业务处理看组件关系

以订单发货为例，业务流程先识别订单，再修改状态，最后生成通知。需要跨节点限制同一订单并发处理时，以订单 ID 构造锁键。数据库状态与事件记录放在同一事务回调内提交。提交成功后再调用通知发送入口，避免把已回滚的状态发给用户。若要求进程重启后仍能送达，业务在事务内保存待发送记录，由后续任务投递。

锁决定谁能同时进入处理逻辑，事务决定哪些数据库修改一起提交，模板工厂把业务事实转换为消息，渠道发送器完成外部调用。成功获得锁后仍需校验订单状态，防止前一次处理完成后再次执行业务动作。事务提交和渠道接受各有自己的成功时刻，调用方根据业务需要记录和处理两者之间的失败。

序号和 KMS 凭据服务于流程的输入。序号为对象提供标识，按名取密为访问外部资源提供凭据内容。应用启动时装配这些依赖，在业务调用时取得具体值。下面按互斥、事务、通知、凭据和取号的使用任务说明配置方式。

## 引入模块

项目已导入 Integration BOM 时，添加以下依赖。应用在启动阶段装配实际需要的组件。[模块 POM](../../extends/infra-commons/pom.xml) · [模块参考](../reference/modules.md)

```xml
<dependency>
    <groupId>com.wind.integration</groupId>
    <artifactId>wind-infra-commons</artifactId>
</dependency>
```

## 让同一资源的任务互斥执行

假设多个节点都会处理订单，要求同一订单在同一时刻只有一个处理者。使用同一套 Redis 和同一锁名规则构造 `LockTemplate`。以下 Spring 配置使用应用已有的 `RedissonClient`。

```java
import com.wind.integration.infrastructure.locks.LockTemplate;
import com.wind.integration.infrastructure.redisson.RedissonWindLock;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Bean;

@Bean
LockTemplate orderLocks(RedissonClient redis) {
    return new LockTemplate(name -> new RedissonWindLock(redis, name));
}
```

在业务方法中调用 `locks.tryLock(() -> processOrder(orderId), "order:" + orderId, 2, 30)`。此处 `processOrder` 是应用自己的处理方法。最多等待 2 秒，获得锁后固定租期为 30 秒。四参数的 `Runnable` 和 `Supplier<T>` 重载都按秒计时，有返回值的回调可直接取得结果。`Runnable` 的五参数重载可显式传入 `TimeUnit`。如果业务执行可能超过租期，应重新确定租期或缩短临界区，避免另一个节点提前进入。

争锁超时会抛出 `BaseException`，回调不会执行。等待被中断时，模板恢复线程中断标记后抛出异常。一旦工厂返回锁对象，退出时总会调用 `unlock()`。自定义工厂应能处理获取失败后的释放调用。`RedissonWindLock` 先检查当前线程是否持锁。相同业务键的并发执行受到约束，前一个任务释放锁后，后一个任务仍可再次处理同一资源。[锁模板](../../extends/infra-commons/src/main/java/com/wind/integration/infrastructure/locks/LockTemplate.java) · [Redisson 适配](../../extends/infra-commons/src/main/java/com/wind/integration/infrastructure/redisson/RedissonWindLock.java)

## 为一组数据库写入划定事务

传入管理目标数据源的 `PlatformTransactionManager`，将需要一起提交的操作放入同一回调。下面的方法体片段假设已有 `manager`、`jdbc`、订单编号和审计事件。`jdbc` 是使用该数据源的 `JdbcTemplate`。

```java
var transactions = new ProgrammaticTransactionTemplate(manager);
transactions.execute(() -> {
    jdbc.update("UPDATE orders SET status = ? WHERE id = ?", "SHIPPED", orderId);
    jdbc.update("INSERT INTO order_events (order_id, event_type) VALUES (?, ?)", orderId, eventType);
});
```

`ProgrammaticTransactionTemplate` 位于 `com.wind.integration.infrastructure.dal`。默认传播行为为 `REQUIRED`，调用方已有事务时，回调参与该事务。需要让一项写入独立提交时，可用 `execute(callback, Propagation.REQUIRES_NEW)`。它在外层回滚后仍可能保留，适用于有独立提交要求的记录。[H2 事务示例](../../tests/src/test/java/com/wind/integration/infrastructure/dal/ProgrammaticTransactionTemplateTests.java)

`Supplier<T>` 正常完成并请求提交后返回结果。回调或提交抛出异常时，模板尝试回滚。提供 `execute(supplier, fallback)` 时，随后调用 `fallback` 并返回其结果。未提供降级回调时重新抛出原异常。获取事务本身失败会直接抛给调用方。[事务模板实现](../../extends/infra-commons/src/main/java/com/wind/integration/infrastructure/dal/ProgrammaticTransactionTemplate.java)

## 按业务模板发送通知

应用负责注册渠道发送器和模板工厂。业务描述符通过工厂名选择工厂，再把模板标识、业务数据和额外变量交给它生成具体消息。下面以应用已创建的邮件发送器 `emailSender` 和实现了 `TemplateMessageDescriptor` 的订单通知描述符 `notice` 为例。

```java
import com.wind.integration.core.message.CompositeMessageSender;
import com.wind.integration.core.message.TemplateMessageFactory;
import com.wind.integration.core.message.definition.SimpleEmailMessage;
import com.wind.integration.infrastructure.message.TemplateMessageSender;
import java.util.List;
import java.util.Map;
import java.util.Set;

TemplateMessageFactory<String> orderEmails = (templateId, orderId, variables) ->
        List.of(SimpleEmailMessage.text(templateId, "订单 " + orderId + " 已发货",
                Set.of((String) variables.get("recipient"))));
TemplateMessageSender.of(new CompositeMessageSender(List.of(emailSender)), List.of(orderEmails));

// notice.getFactoryName() 返回 orderEmails.getName()，getTemplateId() 返回业务约定的模板标识。
TemplateMessageSender.send(notice, "A123", Map.of("recipient", "buyer@example.com"));
```

上例用工厂实例取得名称。要把名称写入长期配置，可在具名工厂类中重写 `getName()` 返回稳定标识。业务描述符的实现及双方的配对方式可参考[可运行的消息测试](../../tests/src/test/java/com/wind/integration/infrastructure/message/TemplateMessageSenderTests.java)。

`of` 替换当前类加载器中的发送器配置，工厂名称必须唯一。先发送后装配、或描述符引用了未知工厂，同步调用都会失败。`send` 在调用线程内执行，默认批量发送会逐条调用发送器，中途抛错时前面的投递可能已经完成。`asyncSend(descriptor, data, variables)` 将相同流程交给进程内线程池，返回类型为 `void`。需要投递结果的调用方可使用同步入口，进程重启后仍须送达的场景需由业务持久化待发送记录。[发送入口](../../extends/infra-commons/src/main/java/com/wind/integration/infrastructure/message/TemplateMessageSender.java) · [发送器接口](../../abstractions/message/src/main/java/com/wind/integration/core/message/MessageSender.java)

## 按名称读取 KMS 托管凭据

`wind-infra-commons` 通过 Java SPI 登记了 `WindCredentialsProvider` 的默认实现，首次读取时取得 `WindKmsClientProvider` 提供的凭据客户端。应用需在运行时引入一个可用的 KMS 实现。例如，添加 `com.wind.integration:wind-kms-alibabacloud` 并配置有权读取目标凭据的身份和 KMS 端点。阿里云默认 KMS 客户端的身份选择和端点解析见[阿里云 KMS 接入](../../wind-alibabacloud/README.md#kms)。

```java
import com.wind.core.WindCredentialsProvider;
import java.util.ServiceLoader;

WindCredentialsProvider credentials = ServiceLoader.load(WindCredentialsProvider.class)
        .findFirst().orElseThrow();
String secretText = credentials.getCredentials("orders-db");
```

`orders-db` 是示例托管凭据名。默认提供者复用客户端，每次调用按名称读取文本内容。需要指定版本或取得凭据详情时，使用 `WindCredentialsClient.getCredentials(name, version)`。运行时找不到 `WindKmsClientProvider` 会在首次取客户端时抛出 `IllegalStateException`。[凭据提供者](../../extends/infra-commons/src/main/java/com/wind/integration/infrastructure/kms/DefaultKmsCredentialsProvider.java) · [版本读取接口](../../abstractions/kms/src/main/java/com/wind/integration/kms/WindCredentialsClient.java)

加解密文本使用 `KmsTextEncryptor.defaults()`。应用也可把已有的 `WindCryptoClient` 传入构造器。请在加载 `KmsTextEncryptor` 前通过 JVM 属性 `-Dwind.kms.db.encrypt.id=<key-id>` 指定加密密钥标识。加解密使用这一标识，按名称读取托管凭据走独立的客户端方法。[KMS 文本加密器](../../extends/infra-commons/src/main/java/com/wind/integration/infrastructure/kms/KmsTextEncryptor.java)

## 多个实例共享分时段序号

在启动时以同一个 Redis 数据源装配 `RedissonTemporalSequenceSupport`。构造时它会注册并替换 `TemporalSequenceFactory` 当前的静态计数器。业务代码继续从工厂取完整编号。

```java
import com.wind.integration.infrastructure.redisson.RedissonTemporalSequenceSupport;
import com.wind.sequence.WindSequenceType;
import com.wind.sequence.time.TemporalSequenceFactory;

new RedissonTemporalSequenceSupport(redis);
WindSequenceType order = WindSequenceType.immutable("ORDER", "ORD", 6);
String number = TemporalSequenceFactory.dayNext(order);
```

类型名 `ORDER` 参与 Redis 键，前缀 `ORD` 和六位计数宽度用于拼装最终编号。例如，在 2026 年 11 月 25 日首次按天取号时，逻辑键为 `DAY@20261125_ORDER`，编号为 `ORD20261125000001`。多实例共享计数时需使用一致的时区、序列类型名和 Redis 键映射。若为 Redisson 配置了 `EnvIsolationNameMapper`，各环境的物理键会带上对应的环境前缀。[环境键映射](../../extends/infra-commons/src/main/java/com/wind/integration/infrastructure/redisson/EnvIsolationNameMapper.java)

年、月、日、时、分、秒各有自己的计数范围。`INCR` 失败会直接抛错。计数值小于等于 1 或是 512 的倍数时，组件异步提交 `expireIfNotSetAsync`，需要 Redis 7.0 或以上支持条件过期命令。目标 TTL 覆盖该本地时间段最后一次结束后的一秒，以便夏令时回拨时重复出现的时段继续沿用同一键。已有 TTL 的键保留原过期时间。提交 TTL 时抛出的异常会记日志，异步完成结果也不影响已取得的序号。若设置失败且此后取号量未达到补设时机，键可能长期保留。排查时请查看 Redis 的键值和实际 TTL。[序列适配](../../extends/infra-commons/src/main/java/com/wind/integration/infrastructure/redisson/RedissonTemporalSequenceSupport.java) · [时间边界测试](../../tests/src/test/java/com/wind/integration/infrastructure/redisson/RedissonTemporalSequenceSupportTests.java)
