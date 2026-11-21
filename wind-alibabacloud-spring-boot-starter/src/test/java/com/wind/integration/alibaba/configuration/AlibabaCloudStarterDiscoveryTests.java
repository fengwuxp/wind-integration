package com.wind.integration.alibaba.configuration;

import com.aliyun.credentials.Client;
import com.wind.integration.alibaba.credentials.AlibabaCloudProductProperties;
import com.wind.integration.message.email.alibabacloud.AlibabaCloudEmailAccountSupplier;
import com.wind.integration.message.email.alibabacloud.AlibabaCloudEmailMessageSender;
import com.wind.integration.oss.WindOssClient;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 使用实际自动发现入口验证按需装配，不在测试中直接导入产品自动配置。
 */
class AlibabaCloudStarterDiscoveryTests {

    @Test
    void testCredentialsOnlyDoesNotRequireProductDependencies() {
        runner().withClassLoader(new FilteredClassLoader("com.wind.integration.oss", "com.aliyun.oss",
                        "com.wind.integration.message.email", "com.aliyun.dm20151123"))
                .run(context -> {
                    Assertions.assertNull(context.getStartupFailure());
                    Assertions.assertEquals("shared-id", context.getBean(AlibabaCloudCredentialsAutoConfiguration.DEFAULT_CREDENTIAL_BEAN_NAME,
                            Client.class).getCredential().getAccessKeyId());
                    Assertions.assertFalse(context.containsBean(AlibabaCloudOssAutoConfiguration.OSS_CLIENT_BEAN_NAME));
                    Assertions.assertFalse(context.containsBean(AlibabaCloudEmailAutoConfiguration.EMAIL_SENDER_BEAN_NAME));
                });
    }

    @Test
    void testOssOnlyDoesNotRequireMailDependency() {
        runner().withClassLoader(new FilteredClassLoader("com.wind.integration.message.email", "com.aliyun.dm20151123"))
                .withPropertyValues("wind.oss.alibaba.endpoint=https://oss-cn-hangzhou.aliyuncs.com")
                .run(context -> {
                    Assertions.assertNull(context.getStartupFailure());
                    Assertions.assertNotNull(context.getBean(WindOssClient.class));
                    Assertions.assertFalse(context.containsBean(AlibabaCloudEmailAutoConfiguration.EMAIL_SENDER_BEAN_NAME));
                });
    }

    @Test
    void testMailOnlyDoesNotRequireOssDependencyOrEndpoint() {
        runner().withClassLoader(new FilteredClassLoader("com.wind.integration.oss", "com.aliyun.oss"))
                .withUserConfiguration(MailAccountConfiguration.class)
                .withPropertyValues("wind.message.email.alibaba.endpoint=dm.example.invalid")
                .run(context -> {
                    Assertions.assertNull(context.getStartupFailure());
                    Assertions.assertNotNull(context.getBean(AlibabaCloudEmailMessageSender.class));
                    Assertions.assertFalse(context.containsBean(AlibabaCloudOssAutoConfiguration.OSS_CLIENT_BEAN_NAME));
                });
    }

    @Test
    void testBothProductsUseGlobalIdentityAndSeparateEndpoints() {
        runner().withUserConfiguration(MailAccountConfiguration.class)
                .withPropertyValues("wind.oss.alibaba.endpoint=https://oss-cn-hangzhou.aliyuncs.com",
                        "wind.message.email.alibaba.endpoint=dm.example.invalid")
                .run(context -> {
                    Assertions.assertNull(context.getStartupFailure());
                    Assertions.assertNotNull(context.getBean(WindOssClient.class));
                    Assertions.assertNotNull(context.getBean(AlibabaCloudEmailMessageSender.class));
                    AlibabaCloudProductProperties oss = context.getBean(AlibabaCloudOssAutoConfiguration.OSS_PROPERTIES_BEAN_NAME,
                            AlibabaCloudProductProperties.class);
                    AlibabaCloudProductProperties mail = context.getBean(AlibabaCloudEmailAutoConfiguration.EMAIL_PROPERTIES_BEAN_NAME,
                            AlibabaCloudProductProperties.class);
                    Assertions.assertFalse(oss.hasCredentials());
                    Assertions.assertFalse(mail.hasCredentials());
                    Assertions.assertEquals("https://oss-cn-hangzhou.aliyuncs.com", oss.getEndpoint());
                    Assertions.assertEquals("dm.example.invalid", mail.getEndpoint());
                    Assertions.assertEquals(1, context.getBeansOfType(Client.class).size());
                    Assertions.assertEquals("shared-id", context.getBean(Client.class).getCredential().getAccessKeyId());
                });
    }

    private static ApplicationContextRunner runner() {
        return new ApplicationContextRunner().withUserConfiguration(Application.class)
                .withPropertyValues("wind.alibaba.credentials.type=access_key",
                        "wind.alibaba.credentials.access-key=shared-id", "wind.alibaba.credentials.secret-key=shared-secret");
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class Application {
    }

    @Configuration(proxyBeanMethods = false)
    static class MailAccountConfiguration {

        @Bean
        AlibabaCloudEmailAccountSupplier mailAccount() {
            return () -> "sender@example.test";
        }
    }
}
