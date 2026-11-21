package com.wind.integration.alibaba.configuration;

import com.aliyun.credentials.Client;
import com.wind.integration.alibaba.credentials.AlibabaCloudCredentialsFactory;
import com.wind.integration.alibaba.credentials.AlibabaCloudProductProperties;
import com.wind.integration.oss.BucketOperationsTemplate;
import com.wind.integration.oss.DefaultBucketOperationsTemplate;
import com.wind.integration.oss.WindOssClient;
import com.wind.integration.oss.alibaba.AlibabaCloudOssClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * OSS 产品的 Spring 自动装配，复用应用凭据并允许产品独立身份。
 *
 * @author wuxp
 * @date 2024/12/27
 */
@AutoConfiguration(after = AlibabaCloudCredentialsAutoConfiguration.class)
@ConditionalOnClass(name = {"com.aliyun.oss.OSSClient", "com.wind.integration.oss.alibaba.AlibabaCloudOssClient"})
public class AlibabaCloudOssAutoConfiguration {

    /**
     * OSS 公共产品配置的 Bean 名称。
     */
    public static final String OSS_PROPERTIES_BEAN_NAME = "windAlibabaCloudOssProperties";

    /**
     * OSS 客户端的 Bean 名称。
     */
    public static final String OSS_CLIENT_BEAN_NAME = "alibabaCloudOssClient";

    /**
     * OSS 阿里云配置前缀，身份整组覆盖全局配置，端点由产品提供。
     */
    public static final String WIND_ALIBABA_OSS_PREFIX = "wind.oss.alibaba";

    /**
     * @return OSS 产品配置，身份不与全局配置逐字段合并
     */
    @Bean(OSS_PROPERTIES_BEAN_NAME)
    @ConditionalOnMissingBean(name = OSS_PROPERTIES_BEAN_NAME)
    @ConfigurationProperties(prefix = WIND_ALIBABA_OSS_PREFIX)
    public AlibabaCloudProductProperties windAlibabaCloudOssProperties() {
        return new AlibabaCloudProductProperties();
    }

    /**
     * 产品配置了身份时使用该组凭据，否则使用应用默认身份；端点只读取产品配置。
     *
     * @param properties OSS 产品配置
     * @param defaultCredential 仅在产品没有身份配置时使用的共享凭据
     * @return 由 Spring 管理关闭的 OSS 客户端
     */
    @Bean(OSS_CLIENT_BEAN_NAME)
    @ConditionalOnMissingBean(WindOssClient.class)
    public WindOssClient configuredAlibabaCloudOssClient(
            @Qualifier(OSS_PROPERTIES_BEAN_NAME) AlibabaCloudProductProperties properties,
            @Qualifier(AlibabaCloudCredentialsAutoConfiguration.DEFAULT_CREDENTIAL_BEAN_NAME) ObjectProvider<Client> defaultCredential) {
        Client credential;
        if (properties.hasCredentials()) {
            credential = AlibabaCloudCredentialsFactory.fromProperties(properties);
        } else {
            credential = defaultCredential.getObject();
        }
        return AlibabaCloudOssClient.withCredential(credential, properties.getEndpoint(), AlibabaCloudOssClient.getDefaultClientConfiguration());
    }

    @Bean
    @ConditionalOnExpression(value = "#{T(org.springframework.util.StringUtils).hasText('${wind.oss.default-bucket-name:}')}")
    @ConditionalOnBean(WindOssClient.class)
    public BucketOperationsTemplate defaultWindBucketTemplate(WindOssClient client, @Value("${wind.oss.default-bucket-name}") String bucketName) {
        return new DefaultBucketOperationsTemplate(client, bucketName);
    }
}
