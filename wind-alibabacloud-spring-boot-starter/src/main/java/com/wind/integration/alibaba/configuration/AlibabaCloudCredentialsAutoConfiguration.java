package com.wind.integration.alibaba.configuration;

import com.aliyun.credentials.Client;
import com.wind.integration.alibaba.credentials.AlibabaCloudCredentialsFactory;
import com.wind.integration.alibaba.credentials.AlibabaCloudCredentialsProperties;
import com.wind.integration.alibaba.credentials.AlibabaCloudProducts;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;

/**
 * 绑定应用默认身份；只有产品实际继承全局身份时才创建凭据客户端。
 * 非 Spring 入口和 KMS 启动阶段独立使用各自的配置。
 */
@AutoConfiguration
public class AlibabaCloudCredentialsAutoConfiguration {

    public static final String GLOBAL_PROPERTIES_BEAN_NAME = "alibabaCloudCredentialsProperties";

    public static final String DEFAULT_CREDENTIAL_BEAN_NAME = "alibabaCloudDefaultCredential";

    @Bean(GLOBAL_PROPERTIES_BEAN_NAME)
    @ConditionalOnMissingBean(name = GLOBAL_PROPERTIES_BEAN_NAME)
    @ConfigurationProperties(prefix = AlibabaCloudProducts.CONFIG_PREFIX + ".credentials")
    public AlibabaCloudCredentialsProperties alibabaCloudCredentialsProperties() {
        return new AlibabaCloudCredentialsProperties();
    }

    @Bean(DEFAULT_CREDENTIAL_BEAN_NAME)
    @Lazy
    @ConditionalOnMissingBean(name = DEFAULT_CREDENTIAL_BEAN_NAME)
    public Client alibabaCloudDefaultCredential(@Qualifier(GLOBAL_PROPERTIES_BEAN_NAME) AlibabaCloudCredentialsProperties properties) {
        return AlibabaCloudCredentialsFactory.fromProperties(properties);
    }
}
