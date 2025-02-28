package com.wind.integration.oss.configuration;

import com.aliyun.oss.OSSClient;
import com.aliyun.oss.common.auth.CredentialsProvider;
import com.aliyun.oss.common.auth.DefaultCredentialProvider;
import com.wind.integration.oss.BucketOperationsTemplate;
import com.wind.integration.oss.DefaultBucketOperationsTemplate;
import com.wind.integration.oss.WindOssClient;
import com.wind.integration.oss.alibaba.AlibabaCloudOssClient;
import com.wind.integration.oss.alibaba.AlibabaCloudOssProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author wuxp
 * @date 2024/12/27
 */
@Configuration
public class WindOssAutoConfiguration {

    public static final String ALIBABA_OSS_PREFIX = "wind.oss.alibaba";

    @Bean
    @ConfigurationProperties(prefix = ALIBABA_OSS_PREFIX)
    @ConditionalOnClass(value = AlibabaCloudOssProperties.class)
    public AlibabaCloudOssProperties alibabaCloudOssProperties() {
        return new AlibabaCloudOssProperties();
    }

    @Bean
    @ConditionalOnBean({AlibabaCloudOssProperties.class})
    @ConditionalOnMissingBean(WindOssClient.class)
    public WindOssClient alibabaCloudOssClient(AlibabaCloudOssProperties properties) {
        CredentialsProvider credentialProvider = new DefaultCredentialProvider(properties.getAccessKey(), properties.getSecretKey());
        OSSClient ossClient = new OSSClient(properties.getEndpoint(), credentialProvider, properties.getDefaultClientConfiguration());
        return new AlibabaCloudOssClient(ossClient);
    }

    @Bean
    @ConditionalOnExpression(value = "#{T(org.springframework.util.StringUtils).hasText('${wind.oss.default-bucket-name}')}")
    @ConditionalOnBean(WindOssClient.class)
    public BucketOperationsTemplate defaultWindBucketTemplate(WindOssClient client, @Value("${wind.oss.default-bucket-name}") String bucketName) {
        return new DefaultBucketOperationsTemplate(client, bucketName);
    }
}
