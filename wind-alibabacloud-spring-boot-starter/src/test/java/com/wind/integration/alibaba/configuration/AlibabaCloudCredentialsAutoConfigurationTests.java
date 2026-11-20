package com.wind.integration.alibaba.configuration;

import com.aliyun.credentials.Client;
import com.aliyun.credentials.http.CompatibleUrlConnClient;
import com.aliyun.credentials.http.FormatType;
import com.aliyun.credentials.http.HttpRequest;
import com.aliyun.credentials.http.HttpResponse;
import com.aliyun.credentials.models.CredentialModel;
import com.aliyun.credentials.provider.AlibabaCloudCredentialsProvider;
import com.aliyun.credentials.utils.AuthConstant;
import com.aliyun.credentials.utils.AuthUtils;
import com.aliyun.tea.Tea;
import com.aliyun.tea.TeaRequest;
import com.aliyun.tea.TeaResponse;
import com.wind.integration.alibaba.credentials.AlibabaCloudCredentialsFactory;
import com.wind.integration.alibaba.credentials.AlibabaCloudCredentialsProperties;
import com.wind.integration.alibaba.credentials.AlibabaCloudProductProperties;
import com.wind.integration.message.email.SimpleEmailMessage;
import com.wind.integration.message.email.alibabacloud.AlibabaCloudEmailMessageSender;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

class AlibabaCloudCredentialsAutoConfigurationTests {

    @Test
    void testAbsentOrUnusedGlobalCredentialsDoNotPreventContextStartup() {
        runner().run(context -> {
            Assertions.assertNull(context.getStartupFailure());
            Assertions.assertFalse(context.getBean(AlibabaCloudCredentialsAutoConfiguration.GLOBAL_PROPERTIES_BEAN_NAME,
                    AlibabaCloudCredentialsProperties.class).hasCredentials());
            Assertions.assertFalse(context.containsBean(AlibabaCloudEmailAutoConfiguration.EMAIL_SENDER_BEAN_NAME));
        });
        runner().withPropertyValues("wind.alibaba.cloud.credentials.type=invalid")
                .run(context -> Assertions.assertNull(context.getStartupFailure()));
    }

    @Test
    void testStarterDoesNotRequireMailDependency() {
        runner().withClassLoader(new FilteredClassLoader("com.wind.integration.message.email"))
                .withPropertyValues("wind.alibaba.cloud.credentials.access-key=test-id", "wind.alibaba.cloud.credentials.secret-key=test-secret")
                .run(context -> {
                    Assertions.assertNull(context.getStartupFailure());
                    Assertions.assertFalse(context.containsBean("windAlibabaCloudEmailProperties"));
                    Client credential = context.getBean(AlibabaCloudCredentialsAutoConfiguration.DEFAULT_CREDENTIAL_BEAN_NAME, Client.class);
                    Assertions.assertEquals("test-id", credential.getCredential().getAccessKeyId());
                });
    }

    @Test
    void testUnrelatedCredentialBeanDoesNotSuppressApplicationDefault() {
        Client independent = AlibabaCloudCredentialsFactory.accessKey("independent-id", "independent-secret");
        runner().withBean("otherCredential", Client.class, () -> independent)
                .withPropertyValues("wind.alibaba.cloud.credentials.access-key=global-id", "wind.alibaba.cloud.credentials.secret-key=global-secret")
                .run(context -> {
                    Assertions.assertNull(context.getStartupFailure());
                    Client global = context.getBean(AlibabaCloudCredentialsAutoConfiguration.DEFAULT_CREDENTIAL_BEAN_NAME, Client.class);
                    Assertions.assertEquals("global-id", global.getCredential().getAccessKeyId());
                    Assertions.assertSame(global, context.getBean(AlibabaCloudCredentialsAutoConfiguration.DEFAULT_CREDENTIAL_BEAN_NAME));
                    Assertions.assertNotSame(independent, global);
                });
    }

    @Test
    void testMailEndpointOnlyInheritsGlobalStsAndBindsProductSeparately() {
        List<TeaRequest> requests = new ArrayList<>();
        try (MockedStatic<Tea> ignored = interceptMail(requests)) {
            runner().withUserConfiguration(AccountConfiguration.class)
                    .withPropertyValues("wind.alibaba.cloud.credentials.type=sts", "wind.alibaba.cloud.credentials.access-key=STS.global",
                            "wind.alibaba.cloud.credentials.secret-key=global-secret", "wind.alibaba.cloud.credentials.security-token=global-token",
                            "wind.alibaba.cloud.dm.endpoint=dm.example.invalid")
                    .run(context -> {
                        Assertions.assertNull(context.getStartupFailure());
                        AlibabaCloudProductProperties product = context.getBean("windAlibabaCloudEmailProperties", AlibabaCloudProductProperties.class);
                        Assertions.assertFalse(product.hasCredentials());
                        Assertions.assertEquals("dm.example.invalid", product.getEndpoint());
                        context.getBean(AlibabaCloudEmailMessageSender.class).sendMessage(message());
                    });
        }
        Assertions.assertEquals(1, requests.size());
        Assertions.assertTrue(requests.getFirst().headers.get("Authorization").contains("STS.global"));
        Assertions.assertEquals("global-token", requests.getFirst().headers.get("x-acs-security-token"));
        Assertions.assertEquals("sender@example.test", requests.getFirst().query.get("AccountName"));
    }

    @Test
    void testMailIdentityOverridesInvalidGlobalConfiguration() {
        List<TeaRequest> requests = new ArrayList<>();
        try (MockedStatic<Tea> ignored = interceptMail(requests)) {
            runner().withUserConfiguration(AccountConfiguration.class)
                    .withPropertyValues("wind.alibaba.cloud.credentials.type=invalid", "wind.alibaba.cloud.dm.access-key=local-id",
                            "wind.alibaba.cloud.dm.secret-key=local-secret", "wind.alibaba.cloud.dm.endpoint=dm.example.invalid")
                    .run(context -> {
                        Assertions.assertNull(context.getStartupFailure());
                        context.getBean(AlibabaCloudEmailMessageSender.class).sendMessage(message());
                    });
        }
        Assertions.assertTrue(requests.getFirst().headers.get("Authorization").contains("local-id"));
        Assertions.assertNull(requests.getFirst().headers.get("x-acs-security-token"));
    }

    @Test
    void testMailPropertiesBindCommonProductConfiguration() {
        runner().withPropertyValues("wind.alibaba.cloud.dm.endpoint=dm.example.invalid",
                        "wind.alibaba.cloud.dm.access-key=mail-id", "wind.alibaba.cloud.dm.secret-key=mail-secret")
                .run(context -> {
                    Assertions.assertNull(context.getStartupFailure());
                    AlibabaCloudProductProperties product = context.getBean("windAlibabaCloudEmailProperties", AlibabaCloudProductProperties.class);
                    Assertions.assertEquals("mail-id", product.getAccessKey());
                    Assertions.assertEquals("mail-secret", product.getSecretKey());
                    Assertions.assertEquals("dm.example.invalid", product.getEndpoint());
                });
    }

    @Test
    void testApplicationSuppliedProductBeanProvidesIdentityAndEndpoint() {
        AlibabaCloudProductProperties product = new AlibabaCloudProductProperties();
        product.setAccessKey("custom-bean-id");
        product.setSecretKey("custom-secret");
        product.setEndpoint("dm.custom.invalid");
        List<TeaRequest> requests = new ArrayList<>();
        try (MockedStatic<Tea> ignored = interceptMail(requests)) {
            runner().withUserConfiguration(AccountConfiguration.class)
                    .withBean("windAlibabaCloudEmailProperties", AlibabaCloudProductProperties.class, () -> product)
                    .withPropertyValues("wind.alibaba.cloud.credentials.type=invalid")
                    .run(context -> {
                        Assertions.assertNull(context.getStartupFailure());
                        Assertions.assertSame(product, context.getBean("windAlibabaCloudEmailProperties"));
                        context.getBean(AlibabaCloudEmailMessageSender.class).sendMessage(message());
                    });
        }
        Assertions.assertTrue(requests.getFirst().headers.get("Authorization").contains("custom-bean-id"));
        Assertions.assertEquals("dm.custom.invalid", requests.getFirst().headers.get("host"));
    }

    @Test
    void testGlobalAccessKeySignsMailWithoutProductIdentity() {
        List<TeaRequest> requests = new ArrayList<>();
        try (MockedStatic<Tea> ignored = interceptMail(requests)) {
            runner().withUserConfiguration(AccountConfiguration.class)
                    .withPropertyValues("wind.alibaba.cloud.credentials.type=access_key",
                            "wind.alibaba.cloud.credentials.access-key=global-id", "wind.alibaba.cloud.credentials.secret-key=global-secret",
                            "wind.alibaba.cloud.dm.endpoint=dm.example.invalid")
                    .run(context -> {
                        Assertions.assertNull(context.getStartupFailure());
                        context.getBean(AlibabaCloudEmailMessageSender.class).sendMessage(message());
                    });
        }
        Assertions.assertTrue(requests.getFirst().headers.get("Authorization").contains("global-id"));
        Assertions.assertNull(requests.getFirst().headers.get("x-acs-security-token"));
    }

    @Test
    void testGlobalOidcBindsExplicitTokenPathAndExchangesStsOnFirstSend() {
        List<TeaRequest> requests = new ArrayList<>();
        List<HttpRequest> exchanges = new ArrayList<>();
        try (MockedStatic<Tea> mail = interceptMail(requests);
             MockedStatic<AuthUtils> auth = Mockito.mockStatic(AuthUtils.class, invocation -> {
                 if ("getOIDCToken".equals(invocation.getMethod().getName())) {
                     Assertions.assertEquals("/test/ack-projected-token", invocation.getArgument(0));
                     return "projected-token";
                 }
                 return invocation.callRealMethod();
             });
             MockedConstruction<CompatibleUrlConnClient> sts = Mockito.mockConstruction(CompatibleUrlConnClient.class,
                     (http, context) -> Mockito.when(http.syncInvoke(Mockito.any(HttpRequest.class))).thenAnswer(invocation -> {
                         HttpRequest request = invocation.getArgument(0);
                         exchanges.add(request);
                         HttpResponse response = new HttpResponse(request.getSysUrl());
                         response.setResponseCode(200);
                         response.setHttpContent("""
                                 {"Credentials":{"AccessKeyId":"STS.workload","AccessKeySecret":"workload-secret",
                                 "SecurityToken":"workload-token","Expiration":"%s"}}
                                 """.formatted(Instant.now().plusSeconds(7200).truncatedTo(ChronoUnit.SECONDS))
                                 .getBytes(StandardCharsets.UTF_8), "UTF-8", FormatType.JSON);
                         return response;
                     }))) {
            runner().withUserConfiguration(AccountConfiguration.class)
                    .withPropertyValues("wind.alibaba.cloud.credentials.type=oidc_role_arn",
                            "wind.alibaba.cloud.credentials.role-arn=acs:ram::123456789:role/test-role",
                            "wind.alibaba.cloud.credentials.oidc-provider-arn=acs:ram::123456789:oidc-provider/test-provider",
                            "wind.alibaba.cloud.credentials.oidc-token-file-path=${test.oidc-file}",
                            "test.oidc-file=/test/ack-projected-token", "wind.alibaba.cloud.dm.endpoint=dm.example.invalid")
                    .run(context -> {
                        Assertions.assertNull(context.getStartupFailure());
                        Assertions.assertTrue(exchanges.isEmpty());
                        AlibabaCloudEmailMessageSender sender = context.getBean(AlibabaCloudEmailMessageSender.class);
                        sender.sendMessage(message());
                        sender.sendMessage(message());
                    });
        }
        Assertions.assertEquals(1, exchanges.size());
        Assertions.assertEquals("AssumeRoleWithOIDC", exchanges.getFirst().getUrlParameters().get("Action"));
        Assertions.assertEquals(2, requests.size());
        for (TeaRequest request : requests) {
            Assertions.assertTrue(request.headers.get("Authorization").contains("STS.workload"));
            Assertions.assertEquals("workload-token", request.headers.get("x-acs-security-token"));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"access-key=partial", "security-token=", "type=unknown"})
    void testIncompleteMailIdentityCannotMergeWithGlobal(String property) {
        runner().withUserConfiguration(AccountConfiguration.class)
                .withPropertyValues("wind.alibaba.cloud.credentials.access-key=global-id", "wind.alibaba.cloud.credentials.secret-key=global-secret",
                        "wind.alibaba.cloud.dm.endpoint=dm.example.invalid", "wind.alibaba.cloud.dm." + property)
                .run(context -> Assertions.assertNotNull(context.getStartupFailure()));
    }

    @Test
    void testExplicitDefaultBeanWorksWithoutGlobalPropertiesAndRemainsDynamic() {
        AtomicReference<CredentialModel> current = new AtomicReference<>(temporary("first"));
        Client credential = new Client(new AlibabaCloudCredentialsProvider() {
            @Override
            public CredentialModel getCredentials() {
                return current.get();
            }

            @Override
            public String getProviderName() {
                return "test";
            }

            @Override
            public void close() {
                throw new AssertionError("产品装配不负责关闭传入凭据");
            }
        });
        List<TeaRequest> requests = new ArrayList<>();
        try (MockedStatic<Tea> ignored = interceptMail(requests)) {
            runner().withUserConfiguration(AccountConfiguration.class)
                    .withBean(AlibabaCloudCredentialsAutoConfiguration.DEFAULT_CREDENTIAL_BEAN_NAME, Client.class, () -> credential)
                    .withPropertyValues("wind.alibaba.cloud.dm.endpoint=dm.example.invalid")
                    .run(context -> {
                        Assertions.assertNull(context.getStartupFailure());
                        AlibabaCloudEmailMessageSender sender = context.getBean(AlibabaCloudEmailMessageSender.class);
                        sender.sendMessage(message());
                        current.set(temporary("second"));
                        sender.sendMessage(message());
                    });
        }
        Assertions.assertEquals("token-first", requests.getFirst().headers.get("x-acs-security-token"));
        Assertions.assertEquals("token-second", requests.getLast().headers.get("x-acs-security-token"));
    }

    @Test
    void testCustomMailSenderBypassesUnusedCredentials() {
        AlibabaCloudEmailMessageSender custom = Mockito.mock(AlibabaCloudEmailMessageSender.class);
        runner().withUserConfiguration(AccountConfiguration.class)
                .withBean(AlibabaCloudEmailMessageSender.class, () -> custom)
                .withPropertyValues("wind.alibaba.cloud.credentials.type=invalid", "wind.alibaba.cloud.dm.access-key=partial")
                .run(context -> {
                    Assertions.assertNull(context.getStartupFailure());
                    Assertions.assertSame(custom, context.getBean(AlibabaCloudEmailMessageSender.class));
                });
    }

    private static ApplicationContextRunner runner() {
        return new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class,
                AlibabaCloudCredentialsAutoConfiguration.class, AlibabaCloudEmailAutoConfiguration.class));
    }

    @Configuration(proxyBeanMethods = false)
    static class AccountConfiguration {

        @Bean(AlibabaCloudEmailAutoConfiguration.ACCOUNT_SUPPLIER_BEAN_NAME)
        Supplier<String> accountSupplier() {
            return () -> "sender@example.test";
        }
    }

    private static CredentialModel temporary(String suffix) {
        return CredentialModel.builder().type(AuthConstant.STS).accessKeyId("STS." + suffix)
                .accessKeySecret("secret-" + suffix).securityToken("token-" + suffix).build();
    }

    private static SimpleEmailMessage message() {
        return SimpleEmailMessage.text("标题", "正文", Set.of("recipient@example.test"));
    }

    private static MockedStatic<Tea> interceptMail(List<TeaRequest> requests) {
        return Mockito.mockStatic(Tea.class, invocation -> {
            if (!"doAction".equals(invocation.getMethod().getName())) {
                return invocation.callRealMethod();
            }
            requests.add(invocation.getArgument(0));
            TeaResponse response = new TeaResponse();
            response.statusCode = 200;
            response.headers.put("content-type", "application/json");
            response.body = new ByteArrayInputStream("{\"RequestId\":\"test-request\",\"EnvId\":\"test-envelope\"}".getBytes(StandardCharsets.UTF_8));
            return response;
        });
    }
}
