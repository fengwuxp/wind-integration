package com.wind.integration.oss.configuration;

import com.aliyun.credentials.models.Config;
import com.aliyun.credentials.exception.CredentialException;
import com.aliyun.credentials.http.CompatibleUrlConnClient;
import com.aliyun.credentials.http.FormatType;
import com.aliyun.credentials.http.HttpRequest;
import com.aliyun.credentials.http.HttpResponse;
import com.aliyun.credentials.models.CredentialModel;
import com.aliyun.credentials.provider.AlibabaCloudCredentialsProvider;
import com.aliyun.oss.ClientConfiguration;
import com.aliyun.oss.OSSClient;
import com.aliyun.oss.common.auth.CredentialsProvider;
import com.aliyun.credentials.utils.AuthConstant;
import com.wind.integration.alibaba.credentials.AlibabaCloudCredentialsFactory;
import com.wind.integration.alibaba.credentials.AlibabaCloudProductProperties;
import com.wind.integration.oss.WindOssClient;
import com.wind.integration.oss.BucketOperationsTemplate;
import com.wind.integration.oss.alibaba.AlibabaCloudOssClient;
import org.apache.http.Header;
import org.apache.http.HttpVersion;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicStatusLine;
import org.apache.http.protocol.HttpContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.ArrayList;
import java.util.List;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicReference;

class WindOssAutoConfigurationTests {

    @TempDir
    Path directory;

    @Test
    void testProductAccessKeyBindingAndSignature() throws Exception {
        List<HttpUriRequest> requests = new ArrayList<>();
        try (MockedStatic<HttpClients> ignored = interceptHttp(requests)) {
            contextRunner().withPropertyValues("wind.alibaba.cloud.oss.access-key=product-id", "wind.alibaba.cloud.oss.secret-key=product-secret")
                    .run(context -> {
                        Assertions.assertNull(context.getStartupFailure());
                        Assertions.assertSame(context.getBean(WindOssClient.class), context.getBean("alibabaCloudOssClient"));
                        context.getBean(WindOssClient.class).deleteFile("test-bucket", "test-object");
                    });
        }
        Assertions.assertTrue(requests.getFirst().getFirstHeader("Authorization").getValue().startsWith("OSS product-id:"));
        Assertions.assertNull(requests.getFirst().getFirstHeader("x-oss-security-token"));
    }

    @Test
    void testApplicationProductBeanOverridesByNameWithoutBlockingOtherProductConfiguration() throws Exception {
        AlibabaCloudProductProperties product = new AlibabaCloudProductProperties();
        product.setAccessKey("custom-oss-id");
        product.setSecretKey("custom-oss-secret");
        product.setEndpoint("https://oss-cn-shanghai.aliyuncs.com");
        AlibabaCloudProductProperties mail = new AlibabaCloudProductProperties();
        mail.setType("unused-mail-type");
        List<HttpUriRequest> requests = new ArrayList<>();
        try (MockedStatic<HttpClients> ignored = interceptHttp(requests)) {
            contextRunner().withBean("windAlibabaCloudOssProperties", AlibabaCloudProductProperties.class, () -> product)
                    .withBean("windAlibabaCloudEmailProperties", AlibabaCloudProductProperties.class, () -> mail)
                    .withPropertyValues("wind.alibaba.cloud.credentials.type=unused-global-type")
                    .run(context -> {
                        Assertions.assertNull(context.getStartupFailure());
                        Assertions.assertSame(product, context.getBean("windAlibabaCloudOssProperties"));
                        context.getBean(WindOssClient.class).deleteFile("test-bucket", "test-object");
                    });
            contextRunner().withBean("windAlibabaCloudEmailProperties", AlibabaCloudProductProperties.class, () -> mail)
                    .withPropertyValues("wind.alibaba.cloud.credentials.type=unused-global-type",
                            "wind.alibaba.cloud.oss.access-key=bound-oss-id", "wind.alibaba.cloud.oss.secret-key=bound-oss-secret")
                    .run(context -> {
                        Assertions.assertNull(context.getStartupFailure());
                        Assertions.assertNotSame(mail, context.getBean("windAlibabaCloudOssProperties"));
                        context.getBean(WindOssClient.class).deleteFile("test-bucket", "test-object");
                    });
        }
        Assertions.assertEquals(2, requests.size());
        assertIdentity(requests.getFirst(), "custom-oss-id", null);
        Assertions.assertEquals("test-bucket.oss-cn-shanghai.aliyuncs.com", requests.getFirst().getURI().getHost());
        assertIdentity(requests.getLast(), "bound-oss-id", null);
        Assertions.assertEquals("test-bucket.oss-cn-hangzhou.aliyuncs.com", requests.getLast().getURI().getHost());
    }

    @Test
    void testCustomStsClientUsesFactoryAndBypassesProductCredentials() throws Exception {
        var credential = AlibabaCloudCredentialsFactory.create(new Config().setType(AuthConstant.STS)
                .setAccessKeyId("STS.custom").setAccessKeySecret("custom-secret").setSecurityToken("custom-token"));
        List<HttpUriRequest> requests = new ArrayList<>();
        try (MockedStatic<HttpClients> ignored = interceptHttp(requests)) {
            AlibabaCloudOssClient custom = AlibabaCloudOssClient.withCredential(credential,
                    "https://oss-cn-hangzhou.aliyuncs.com", new ClientConfiguration());
            contextRunner().withBean(WindOssClient.class, () -> custom)
                    .withPropertyValues("wind.alibaba.cloud.oss.access-key=unused-partial")
                    .run(context -> {
                        Assertions.assertNull(context.getStartupFailure());
                        Assertions.assertSame(custom, context.getBean(WindOssClient.class));
                        context.getBean(WindOssClient.class).deleteFile("test-bucket", "test-object");
                    });
        }
        Assertions.assertEquals(1, requests.size());
        Assertions.assertTrue(requests.getFirst().getFirstHeader("Authorization").getValue().startsWith("OSS STS.custom:"));
        Assertions.assertEquals("custom-token", requests.getFirst().getFirstHeader("x-oss-security-token").getValue());
    }

    @Test
    void testMissingCredentialsFailWithoutCustomClient() {
        contextRunner().run(context -> Assertions.assertNotNull(context.getStartupFailure()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"access-key=", "secret-key=", "access-key=partial-id", "secret-key=partial-secret"})
    void testRejectIncompleteProductCredentials(String property) {
        contextRunner().withPropertyValues("wind.alibaba.cloud.oss." + property)
                .run(context -> Assertions.assertNotNull(context.getStartupFailure()));
    }

    @Test
    void testCustomClientBypassesProductCredentialValidation() {
        WindOssClient custom = Mockito.mock(WindOssClient.class);
        try (MockedConstruction<OSSClient> sdk = Mockito.mockConstruction(OSSClient.class)) {
            contextRunner().withBean(WindOssClient.class, () -> custom)
                    .withPropertyValues("wind.alibaba.cloud.oss.access-key=partial")
                    .run(context -> {
                        Assertions.assertNull(context.getStartupFailure());
                        Assertions.assertSame(custom, context.getBean(WindOssClient.class));
                        Assertions.assertTrue(sdk.constructed().isEmpty());
                    });
        }
    }

    @Test
    void testContextClosesSdkCreatedByAutoConfiguration() {
        try (MockedConstruction<OSSClient> sdk = Mockito.mockConstruction(OSSClient.class)) {
            contextRunner().withPropertyValues("wind.alibaba.cloud.oss.access-key=product-id", "wind.alibaba.cloud.oss.secret-key=product-secret")
                    .run(context -> Assertions.assertNull(context.getStartupFailure()));

            Assertions.assertEquals(1, sdk.constructed().size());
            Mockito.verify(sdk.constructed().getFirst()).shutdown();
        }
    }

    @Test
    void testContextClosesCustomClientCreatedByFactory() {
        try (MockedConstruction<OSSClient> sdk = Mockito.mockConstruction(OSSClient.class)) {
            AlibabaCloudOssClient custom = AlibabaCloudOssClient.withCredential(AlibabaCloudCredentialsFactory.accessKey("test-id", "test-secret"),
                    "https://oss-cn-hangzhou.aliyuncs.com", new ClientConfiguration());
            contextRunner().withBean(WindOssClient.class, () -> custom)
                    .run(context -> Assertions.assertNull(context.getStartupFailure()));

            Assertions.assertEquals(1, sdk.constructed().size());
            Mockito.verify(sdk.constructed().getFirst()).shutdown();
        }
    }

    @Test
    void testNewProductStsConfigurationUsesNewEndpoint() throws Exception {
        List<HttpUriRequest> requests = new ArrayList<>();
        try (MockedStatic<HttpClients> ignored = interceptHttp(requests)) {
            contextRunner().withPropertyValues("wind.alibaba.cloud.oss.type=sts",
                            "wind.alibaba.cloud.oss.access-key=STS.product", "wind.alibaba.cloud.oss.secret-key=product-secret",
                            "wind.alibaba.cloud.oss.security-token=product-token", "wind.alibaba.cloud.oss.endpoint=https://oss-cn-shanghai.aliyuncs.com")
                    .run(context -> {
                        Assertions.assertNull(context.getStartupFailure());
                        context.getBean(WindOssClient.class).deleteFile("test-bucket", "test-object");
                    });
        }
        assertIdentity(requests.getFirst(), "STS.product", "product-token");
        Assertions.assertEquals("test-bucket.oss-cn-shanghai.aliyuncs.com", requests.getFirst().getURI().getHost());
    }

    @Test
    void testProductEndpointOnlyUsesGlobalCredentials() throws Exception {
        List<HttpUriRequest> requests = new ArrayList<>();
        try (MockedStatic<HttpClients> ignored = interceptHttp(requests)) {
            contextRunner().withPropertyValues("wind.alibaba.cloud.credentials.type=sts",
                            "wind.alibaba.cloud.credentials.access-key=STS.global", "wind.alibaba.cloud.credentials.secret-key=global-secret",
                            "wind.alibaba.cloud.credentials.security-token=global-token", "wind.alibaba.cloud.oss.endpoint=https://oss-cn-shanghai.aliyuncs.com")
                    .run(context -> {
                        Assertions.assertNull(context.getStartupFailure());
                        context.getBean(WindOssClient.class).deleteFile("test-bucket", "test-object");
                    });
        }
        assertIdentity(requests.getFirst(), "STS.global", "global-token");
        Assertions.assertEquals("test-bucket.oss-cn-shanghai.aliyuncs.com", requests.getFirst().getURI().getHost());
    }

    @Test
    void testProductIdentityDoesNotResolveInvalidGlobal() throws Exception {
        List<HttpUriRequest> requests = new ArrayList<>();
        try (MockedStatic<HttpClients> ignored = interceptHttp(requests)) {
            contextRunner().withPropertyValues("wind.alibaba.cloud.credentials.type=invalid-global",
                            "wind.alibaba.cloud.oss.access-key=product-id", "wind.alibaba.cloud.oss.secret-key=product-secret")
                    .run(context -> {
                        Assertions.assertNull(context.getStartupFailure());
                        context.getBean(WindOssClient.class).deleteFile("test-bucket", "test-object");
                    });
        }
        assertIdentity(requests.getFirst(), "product-id", null);
    }

    @ParameterizedTest
    @ValueSource(strings = {"access-key=partial", "security-token=orphan", "type=sts"})
    void testPartialProductIdentityDoesNotMergeWithGlobal(String property) {
        contextRunner().withPropertyValues("wind.alibaba.cloud.oss." + property,
                        "wind.alibaba.cloud.credentials.access-key=global-id", "wind.alibaba.cloud.credentials.secret-key=global-secret")
                .run(context -> Assertions.assertNotNull(context.getStartupFailure()));
    }

    @Test
    void testGlobalOidcIsLazyUntilTheFirstOssRequest() throws Exception {
        Path token = directory.resolve("missing-oidc-token");
        List<HttpUriRequest> requests = new ArrayList<>();
        try (MockedStatic<HttpClients> ignored = interceptHttp(requests)) {
            contextRunner().withPropertyValues("wind.alibaba.cloud.credentials.type=oidc_role_arn",
                            "wind.alibaba.cloud.credentials.role-arn=acs:ram::123456789:role/test-role",
                            "wind.alibaba.cloud.credentials.oidc-provider-arn=acs:ram::123456789:oidc-provider/test",
                            "wind.alibaba.cloud.credentials.oidc-token-file-path=" + token)
                    .run(context -> {
                        Assertions.assertNull(context.getStartupFailure());
                        CredentialException failure = Assertions.assertThrows(CredentialException.class,
                                () -> context.getBean(WindOssClient.class).deleteFile("test-bucket", "test-object"));
                        Assertions.assertTrue(failure.getMessage().contains(token.toString()));
                    });
        }
        Assertions.assertTrue(requests.isEmpty());
    }

    @Test
    void testGlobalIdentityCanBeReconfiguredFromAccessKeyToAckOidc() throws Exception {
        Path token = directory.resolve("projected-token");
        Files.writeString(token, "test-ack-oidc-token");
        List<HttpUriRequest> requests = new ArrayList<>();
        List<HttpRequest> exchanges = new ArrayList<>();
        try (MockedStatic<HttpClients> ignored = interceptHttp(requests);
             MockedConstruction<CompatibleUrlConnClient> sts = Mockito.mockConstruction(CompatibleUrlConnClient.class,
                     (client, context) -> Mockito.when(client.syncInvoke(Mockito.any(HttpRequest.class))).thenAnswer(invocation -> {
                         HttpRequest request = invocation.getArgument(0);
                         exchanges.add(request);
                         HttpResponse response = new HttpResponse(request.getSysUrl());
                         response.setResponseCode(200);
                         response.setHttpContent("""
                                 {"Credentials":{"AccessKeyId":"STS.ack","AccessKeySecret":"ack-secret",
                                  "SecurityToken":"ack-token","Expiration":"%s"}}
                                 """.formatted(Instant.now().plusSeconds(7200).truncatedTo(ChronoUnit.SECONDS))
                                 .getBytes(StandardCharsets.UTF_8), "UTF-8", FormatType.JSON);
                         return response;
                     }))) {
            contextRunner().withPropertyValues("wind.alibaba.cloud.credentials.access-key=initial-id",
                            "wind.alibaba.cloud.credentials.secret-key=initial-secret")
                    .run(context -> {
                        Assertions.assertNull(context.getStartupFailure());
                        context.getBean(WindOssClient.class).deleteFile("test-bucket", "test-object");
                    });
            contextRunner().withPropertyValues("wind.alibaba.cloud.credentials.type=oidc_role_arn",
                            "wind.alibaba.cloud.credentials.role-arn=acs:ram::123456789:role/test-role",
                            "wind.alibaba.cloud.credentials.oidc-provider-arn=acs:ram::123456789:oidc-provider/test",
                            "wind.alibaba.cloud.credentials.oidc-token-file-path=" + token,
                            "wind.alibaba.cloud.credentials.sts-endpoint=sts.example.invalid")
                    .run(context -> {
                        Assertions.assertNull(context.getStartupFailure());
                        Assertions.assertTrue(exchanges.isEmpty());
                        context.getBean(WindOssClient.class).deleteFile("test-bucket", "test-object");
                    });
        }
        Assertions.assertEquals(2, requests.size());
        assertIdentity(requests.getFirst(), "initial-id", null);
        assertIdentity(requests.getLast(), "STS.ack", "ack-token");
        Assertions.assertEquals(1, exchanges.size());
        Assertions.assertTrue(exchanges.getFirst().getHttpContentString().contains("OIDCToken=test-ack-oidc-token"));
    }

    @Test
    void testRemovedLegacyPrefixDoesNotProvideIdentity() {
        contextRunner().withPropertyValues("wind.oss.alibaba.access-key=removed-id", "wind.oss.alibaba.secret-key=removed-secret")
                .run(context -> Assertions.assertNotNull(context.getStartupFailure()));
    }

    @Test
    void testEndpointIsRequiredFromProductConfiguration() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class, WindOssAutoConfiguration.class))
                .withPropertyValues("wind.oss.default-bucket-name=", "wind.oss.alibaba.endpoint=https://oss-cn-hangzhou.aliyuncs.com",
                        "wind.alibaba.cloud.credentials.access-key=global-id", "wind.alibaba.cloud.credentials.secret-key=global-secret")
                .run(context -> Assertions.assertNotNull(context.getStartupFailure()));
    }

    @Test
    void testCustomClientDoesNotResolveInvalidGlobalCredentials() {
        WindOssClient custom = Mockito.mock(WindOssClient.class);
        contextRunner().withBean(WindOssClient.class, () -> custom)
                .withPropertyValues("wind.alibaba.cloud.credentials.type=invalid-global")
                .run(context -> {
                    Assertions.assertNull(context.getStartupFailure());
                    Assertions.assertSame(custom, context.getBean(WindOssClient.class));
                });
    }

    @Test
    void testGlobalCredentialBeanIsBorrowedWithoutSnapshotting() {
        AtomicReference<CredentialModel> current = new AtomicReference<>(CredentialModel.builder().type("sts")
                .accessKeyId("STS.first").accessKeySecret("first-secret").securityToken("first-token").build());
        com.aliyun.credentials.Client shared = new com.aliyun.credentials.Client(new AlibabaCloudCredentialsProvider() {
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
            }
        });
        List<CredentialsProvider> providers = new ArrayList<>();
        try (MockedConstruction<OSSClient> ignored = Mockito.mockConstruction(OSSClient.class,
                (sdk, context) -> providers.add((CredentialsProvider) context.arguments().get(1)))) {
            contextRunner().withBean("alibabaCloudDefaultCredential", com.aliyun.credentials.Client.class, () -> shared)
                    .run(context -> {
                        Assertions.assertNull(context.getStartupFailure());
                        Assertions.assertSame(shared, context.getBean("alibabaCloudDefaultCredential"));
                        Assertions.assertEquals("first-token", providers.getFirst().getCredentials().getSecurityToken());
                        current.set(CredentialModel.builder().type("sts").accessKeyId("STS.second")
                                .accessKeySecret("second-secret").securityToken("second-token").build());
                        Assertions.assertEquals("second-token", providers.getFirst().getCredentials().getSecurityToken());
                    });
        }
    }

    @Test
    void testProductConfigurationDoesNotRequireDefaultBucket() {
        newPrefixRunner().run(context -> {
            Assertions.assertNull(context.getStartupFailure());
            Assertions.assertNotNull(context.getBean(WindOssClient.class));
            Assertions.assertTrue(context.getBeansOfType(BucketOperationsTemplate.class).isEmpty());
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void testBlankDefaultBucketDoesNotCreateTemplate(String bucket) {
        newPrefixRunner().withPropertyValues("wind.oss.default-bucket-name=" + bucket).run(context -> {
            Assertions.assertNull(context.getStartupFailure());
            Assertions.assertTrue(context.getBeansOfType(BucketOperationsTemplate.class).isEmpty());
        });
    }

    @Test
    void testDefaultBucketCreatesWorkingTemplate() throws Exception {
        List<HttpUriRequest> requests = new ArrayList<>();
        try (MockedStatic<HttpClients> ignored = interceptHttp(requests)) {
            newPrefixRunner().withPropertyValues("wind.oss.default-bucket-name=default-bucket").run(context -> {
                Assertions.assertNull(context.getStartupFailure());
                context.getBean(BucketOperationsTemplate.class).deleteFile("test-object");
            });
        }
        Assertions.assertEquals("default-bucket.oss-cn-hangzhou.aliyuncs.com", requests.getFirst().getURI().getHost());
    }

    @ParameterizedTest
    @ValueSource(strings = {"com.aliyun.oss", "com.wind.integration.oss.alibaba"})
    void testExcludedBackendMakesAutoConfigurationBackOff(String excludedPackage) {
        new ApplicationContextRunner()
                .withClassLoader(new FilteredClassLoader(excludedPackage))
                .withConfiguration(AutoConfigurations.of(PropertyPlaceholderAutoConfiguration.class,
                        ConfigurationPropertiesAutoConfiguration.class, WindOssAutoConfiguration.class))
                .run(context -> {
                    Assertions.assertNull(context.getStartupFailure());
                    Assertions.assertFalse(context.containsBean("alibabaCloudOssClient"));
                    Assertions.assertFalse(context.containsBean("alibabaCloudOssProperties"));
                    Assertions.assertFalse(context.containsBean("windAlibabaCloudOssProperties"));
                });
    }

    private static ApplicationContextRunner newPrefixRunner() {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(PropertyPlaceholderAutoConfiguration.class,
                        ConfigurationPropertiesAutoConfiguration.class, WindOssAutoConfiguration.class))
                .withPropertyValues("wind.alibaba.cloud.oss.endpoint=https://oss-cn-hangzhou.aliyuncs.com",
                        "wind.alibaba.cloud.oss.access-key=product-id", "wind.alibaba.cloud.oss.secret-key=product-secret");
    }

    private static void assertIdentity(HttpUriRequest request, String accessKeyId, String token) {
        Assertions.assertTrue(request.getFirstHeader("Authorization").getValue().startsWith("OSS " + accessKeyId + ":"));
        if (token == null) {
            Assertions.assertNull(request.getFirstHeader("x-oss-security-token"));
        } else {
            Assertions.assertEquals(token, request.getFirstHeader("x-oss-security-token").getValue());
        }
    }

    private static ApplicationContextRunner contextRunner() {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class, WindOssAutoConfiguration.class))
                .withPropertyValues("wind.oss.default-bucket-name=", "wind.alibaba.cloud.oss.endpoint=https://oss-cn-hangzhou.aliyuncs.com");
    }

    private static MockedStatic<HttpClients> interceptHttp(List<HttpUriRequest> requests) throws Exception {
        CloseableHttpClient http = Mockito.mock(CloseableHttpClient.class);
        Mockito.when(http.execute(Mockito.any(HttpUriRequest.class), Mockito.any(HttpContext.class))).thenAnswer(invocation -> {
            requests.add(invocation.getArgument(0));
            CloseableHttpResponse response = Mockito.mock(CloseableHttpResponse.class);
            Mockito.when(response.getStatusLine()).thenReturn(new BasicStatusLine(HttpVersion.HTTP_1_1, 204, "No Content"));
            Mockito.when(response.getAllHeaders()).thenReturn(new Header[]{new BasicHeader("x-oss-request-id", "test-request")});
            return response;
        });
        MockedStatic<HttpClients> factory = Mockito.mockStatic(HttpClients.class);
        factory.when(HttpClients::custom).thenAnswer(invocation -> {
            HttpClientBuilder builder = Mockito.spy(HttpClientBuilder.create());
            Mockito.doReturn(http).when(builder).build();
            return builder;
        });
        return factory;
    }
}
