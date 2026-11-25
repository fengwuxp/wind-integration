# 在已有应用中接入 Integration

本文以已有 Spring Boot 应用增加 OSS 对象读取为例。业务层使用 `WindOssClient`，应用启动层装配阿里云客户端。新建服务、Nacos、HTTP 入口和日志初始化可从 Wind Middleware 仓库内的 `docs/README.md` 进入。[Middleware 仓库](https://github.com/fengwuxp/wind-middleware)

## 版本与依赖

当前分支使用 JDK 25。先运行 `mvn -version`，确认 Maven 实际使用的 Java 版本。企业仓库需已能解析同一版本线的 Wind SNAPSHOT 构件；私有仓库地址和访问凭据沿用团队的 Maven settings。父 POM 或 BOM 的依赖管理会引入 Middleware 和 Security 的版本约束。[本仓库版本和管理依赖](../../pom.xml)

保留应用已有父 POM，在 `dependencyManagement` 导入 Integration BOM。BOM 管理版本，运行依赖由下面的 `dependencies` 显式选择。

多模块应用把公共接口放入业务模块，把供应商实现和 Starter 放入启动模块。下例集中列出一次 OSS 接入所需的三个依赖，按这个关系分配到自己的工程即可。

~~~xml
<properties>
    <wind.version>4.1.0-SNAPSHOT</wind.version>
</properties>
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>com.wind.integration</groupId>
            <artifactId>wind-integration-dependencies</artifactId>
            <version>${wind.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
<dependencies>
    <dependency>
        <groupId>com.wind.integration</groupId>
        <artifactId>wind-oss-api</artifactId>
    </dependency>
    <dependency>
        <groupId>com.wind.integration</groupId>
        <artifactId>wind-oss-alibabacloud</artifactId>
    </dependency>
    <dependency>
        <groupId>com.wind.integration</groupId>
        <artifactId>wind-alibabacloud-spring-boot-starter</artifactId>
    </dependency>
</dependencies>
~~~

邮件、KMS 和其他模块的实际坐标见模块参考。DirectMail 构件的现有名称是 `wind-message-emial-albabacloud`，按这个拼写声明。[模块参考](../reference/modules.md)

## 配置访问身份和目标服务

在 `application.properties` 中配置以下属性。示例变量由启动环境提供，角色需要有读取目标 Bucket 中对象元数据的权限。

~~~properties
wind.alibaba.credentials.type=access_key
wind.alibaba.credentials.access-key=${CLOUD_ACCESS_KEY_ID}
wind.alibaba.credentials.secret-key=${CLOUD_ACCESS_KEY_SECRET}
wind.oss.alibaba.endpoint=${OSS_ENDPOINT}
app.documents.bucket=${OSS_BUCKET}
~~~

`app.documents.bucket` 是本示例的业务属性。Starter 检测到 OSS 适配器和 SDK、容器中缺少 `WindOssClient` 时创建默认客户端。该客户端采用共享身份，endpoint 归 OSS 产品配置。业务在每次调用时传入自己的 Bucket；配置 `wind.oss.default-bucket-name` 后也可注入绑定默认桶的 `BucketOperationsTemplate`。

已完成 ACK RRSA、ServiceAccount Token 挂载和 RAM 信任配置时，可将全局三项静态身份替换为 `wind.alibaba.credentials.type=oidc_role_arn`，其余 OSS 配置保留。角色信任和 Token 投影由部署环境提供，相关字段和运行条件见凭据指南。[凭据配置](../../wind-alibabacloud/credentials/README.md)

## 发起第一次只读调用

将下面的类放到已有应用的组件扫描范围内。它复用容器管理的客户端，由现有业务入口调用 `describe`。

~~~java
import com.wind.integration.oss.WindOssClient;
import com.wind.integration.oss.model.WindOssFile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class DocumentStorage {
    private final WindOssClient oss;
    private final String bucket;

    public DocumentStorage(WindOssClient oss,
                           @Value("${app.documents.bucket}") String bucket) {
        this.oss = oss;
        this.bucket = bucket;
    }

    public WindOssFile describe(String objectKey) {
        return oss.statFile(bucket, objectKey);
    }
}
~~~

用目标环境中已知存在的测试对象键调用 `describe`，核对返回的元数据是否对应这个对象。抛异常时依次核对对象键、Bucket、凭据来源、endpoint、资源权限和服务端请求 ID。构造客户端成功证明本地装配完成，第一次请求才能验证实际服务访问。业务接口还应检查当前调用者有权读取这个对象。

需要上传下载时，沿用同一客户端。下载返回的流由每次调用关闭，长生命周期客户端由容器在退出时关闭。普通 Java 应用自行创建并释放客户端，见云产品指南。[上传下载及资源管理](../guides/alibabacloud.md#读写对象存储)

## 常见接入问题

| 现象 | 定位方式 |
| --- | --- |
| 找不到 `WindOssClient` Bean | 检查启动模块是否同时带有实现 JAR、Starter 和 OSS SDK，是否排除了自动配置 |
| 启动时提示凭据字段缺失 | 检查全局和 `wind.oss.alibaba` 产品级配置。产品级出现认证字段后，会按该组身份校验完整性 |
| endpoint 已填却没有 Bucket 模板 | `BucketOperationsTemplate` 还需要非空白的 `wind.oss.default-bucket-name`，使用 `WindOssClient` 时可直接传 Bucket |
| 编译成功却报类或方法不存在 | 核对运行时依赖树中的 Wind 版本、供应商 SDK 和重复 JAR，消息接口需带 `wind-message-api` |
| KMS SPI 首次访问失败 | 核对 `wind-kms-alibabacloud` 及所需凭据解密 SPI，KMS 默认入口的取凭据流程见云产品指南 |
| 数据隔离未生效 | 按 MyBatis 文档注册拦截器、可信范围和监听器，并验证实际 Mapper 路径 |

本仓库的组件测试与示例验证方法见开发指南。接入方仍需对自己的身份、网络、真实资源和业务授权做联调。[开发与验证](../contributing.md) · [数据隔离](../../extends/mybatis-flex-extends/README.md)
