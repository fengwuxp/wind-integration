package com.wind.integration.oss.configuration;

import com.aliyun.oss.ClientConfiguration;
import com.aliyun.oss.OSSClient;
import com.aliyun.oss.common.auth.CredentialsProvider;
import com.aliyun.oss.common.auth.DefaultCredentialProvider;
import com.wind.integration.oss.DefaultBucketOperationsTemplate;
import com.wind.integration.oss.BucketOperationsTemplate;
import com.wind.integration.oss.WindOssClient;
import com.wind.integration.oss.alibaba.AlibabaCloudOssClient;
import com.wind.integration.oss.alibaba.AlibabaCloudOssProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author fanqingwei
 * @date 2023/10/16
 */
@Configuration
public class WindOssAutoConfiguration {

    public static final String ALIBABA_OSS_PREFIX = "wind.oss.alibaba";

    @Bean
    @ConfigurationProperties(prefix = ALIBABA_OSS_PREFIX)
    public AlibabaCloudOssProperties alibabaCloudOssProperties() {
        return new AlibabaCloudOssProperties();
    }

    @Bean
    @ConditionalOnMissingBean(ClientConfiguration.class)
    public ClientConfiguration alibabaOssClientConfiguration() {
        ClientConfiguration result = new ClientConfiguration();
        // 设置 OSSClient 允许打开的最大HTTP连接数，默认为 1024 个。
        result.setMaxConnections(256);
        // 设置 Socket 层传输数据的超时时间，默认为 50000 毫秒。
        result.setSocketTimeout(60000);
        // 设置建立连接的超时时间，默认为 50000 毫秒。
        result.setConnectionTimeout(50000);
        // 设置从连接池中获取连接的超时时间（单位：毫秒），默认不超时。
        result.setConnectionRequestTimeout(1000);
        // 设置连接空闲超时时间。超时则关闭连接，默认为 60000 毫秒。
        result.setIdleConnectionTime(60000);
        // 设置失败请求重试次数，默认为3次。
        result.setMaxErrorRetry(3);
        return result;
    }

    @Bean
    @ConditionalOnBean({AlibabaCloudOssProperties.class, ClientConfiguration.class})
    @ConditionalOnMissingBean(WindOssClient.class)
    public WindOssClient alibabaCloudOssClient(AlibabaCloudOssProperties properties, ClientConfiguration configuration) {
        CredentialsProvider credentialProvider = new DefaultCredentialProvider(properties.getAccessKey(), properties.getSecretKey());
        OSSClient ossClient = new OSSClient(properties.getEndpoint(), credentialProvider, configuration);
        return new AlibabaCloudOssClient(ossClient, properties.getEndpoint());
    }


    @Bean
    @ConditionalOnExpression(value = "#{T(org.springframework.util.StringUtils).hasText('${wind.oss.default-bucket-name}')}")
    @ConditionalOnBean(WindOssClient.class)
    public BucketOperationsTemplate defaultWindBucketTemplate(WindOssClient windOssClient, @Value("${wind.oss.default-bucket-name}") String defaultBucketName) {
        return new DefaultBucketOperationsTemplate(windOssClient, defaultBucketName);
    }
}
