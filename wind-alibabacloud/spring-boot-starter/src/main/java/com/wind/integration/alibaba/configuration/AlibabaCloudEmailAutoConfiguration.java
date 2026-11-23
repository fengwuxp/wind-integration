package com.wind.integration.alibaba.configuration;

import com.aliyun.credentials.Client;
import com.aliyun.teaopenapi.models.Config;
import com.wind.common.exception.AssertUtils;
import com.wind.integration.alibaba.credentials.AlibabaCloudCredentialsFactory;
import com.wind.integration.alibaba.credentials.AlibabaCloudProductProperties;
import com.wind.integration.message.email.alibabacloud.AlibabaCloudEmailAccountSupplier;
import com.wind.integration.message.email.alibabacloud.AlibabaCloudEmailMessageSender;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 邮件产品配置只在邮件模块可用时绑定，应用提供明确的发件账号 Supplier 后才装配发送器。
 */
@AutoConfiguration(after = AlibabaCloudCredentialsAutoConfiguration.class)
@ConditionalOnClass(AlibabaCloudEmailMessageSender.class)
public class AlibabaCloudEmailAutoConfiguration {

    /**
     * 邮件能力的阿里云实现配置，由邮件装配维护其端点和独立身份。
     */
    public static final String CONFIG_PREFIX = "wind.message.email.alibaba";

    /**
     * 邮件产品配置的 Bean 名称，用于区分相同公共类型的不同产品配置。
     */
    public static final String EMAIL_PROPERTIES_BEAN_NAME = "windAlibabaCloudEmailProperties";

    public static final String EMAIL_SENDER_BEAN_NAME = "alibabaCloudEmailMessageSender";

    /**
     * @return 邮件产品配置，新用法通过 Bean 名称限定公共类型
     */
    @Bean(EMAIL_PROPERTIES_BEAN_NAME)
    @ConditionalOnMissingBean(name = EMAIL_PROPERTIES_BEAN_NAME)
    @ConfigurationProperties(prefix = CONFIG_PREFIX)
    public AlibabaCloudProductProperties windAlibabaCloudEmailProperties() {
        return new AlibabaCloudProductProperties();
    }

    /**
     * 使用产品独立身份或应用全局身份，端点由产品提供。
     *
     * @param properties 邮件产品配置
     * @param defaultCredential 产品没有身份配置时使用的共享凭据
     * @param accountSupplier 发件账号
     * @return 邮件发送器
     */
    @Bean(EMAIL_SENDER_BEAN_NAME)
    @ConditionalOnBean(AlibabaCloudEmailAccountSupplier.class)
    @ConditionalOnMissingBean(value = AlibabaCloudEmailMessageSender.class, name = EMAIL_SENDER_BEAN_NAME)
    public AlibabaCloudEmailMessageSender alibabaCloudEmailMessageSender(
            @Qualifier(EMAIL_PROPERTIES_BEAN_NAME) AlibabaCloudProductProperties properties,
            @Qualifier(AlibabaCloudCredentialsAutoConfiguration.DEFAULT_CREDENTIAL_BEAN_NAME) ObjectProvider<Client> defaultCredential,
            AlibabaCloudEmailAccountSupplier accountSupplier) {
        String endpoint = properties.getEndpoint();
        AssertUtils.hasText(endpoint, "邮件服务 endpoint 不能为空白");
        Client credential;
        if (properties.hasCredentials()) {
            credential = AlibabaCloudCredentialsFactory.fromProperties(properties);
        } else {
            credential = defaultCredential.getObject();
        }
        return AlibabaCloudEmailMessageSender.withCredential(credential, new Config().setEndpoint(endpoint), accountSupplier);
    }
}
