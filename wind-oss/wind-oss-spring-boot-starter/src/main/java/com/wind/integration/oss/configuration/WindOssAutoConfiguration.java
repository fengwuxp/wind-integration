package com.wind.integration.oss.configuration;

import com.aliyun.credentials.Client;
import com.wind.integration.alibaba.configuration.AlibabaCloudCredentialsAutoConfiguration;
import com.wind.integration.alibaba.credentials.AlibabaCloudCredentialsFactory;
import com.wind.integration.alibaba.credentials.AlibabaCloudProductProperties;
import com.wind.integration.alibaba.credentials.AlibabaCloudProducts;
import com.wind.integration.oss.BucketOperationsTemplate;
import com.wind.integration.oss.DefaultBucketOperationsTemplate;
import com.wind.integration.oss.WindOssClient;
import com.wind.integration.oss.alibaba.AlibabaCloudOssClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * @author wuxp
 * @date 2024/12/27
 */
@Configuration
@ConditionalOnClass(name = {"com.aliyun.oss.OSSClient", "com.wind.integration.oss.alibaba.AlibabaCloudOssClient"})
@Import(AlibabaCloudCredentialsAutoConfiguration.class)
public class WindOssAutoConfiguration {

    /**
     * OSS 新配置前缀，身份整组覆盖全局配置，端点独立选择。
     */
    public static final String WIND_ALIBABA_OSS_PREFIX = AlibabaCloudProducts.CONFIG_PREFIX + "." + AlibabaCloudProducts.OSS;

    /**
     * @return OSS 产品配置，身份不与全局配置逐字段合并
     */
    @Bean
    @ConditionalOnMissingBean(name = "windAlibabaCloudOssProperties")
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
    @Bean(name = "alibabaCloudOssClient")
    @ConditionalOnMissingBean(WindOssClient.class)
    public WindOssClient configuredAlibabaCloudOssClient(
            @Qualifier("windAlibabaCloudOssProperties") AlibabaCloudProductProperties properties,
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
