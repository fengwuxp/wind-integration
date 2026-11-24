package com.wind.integration.oss.alibaba;

import com.aliyun.credentials.Client;
import com.aliyun.credentials.models.Config;
import com.aliyun.credentials.models.CredentialModel;
import com.aliyun.credentials.provider.AlibabaCloudCredentialsProvider;
import com.aliyun.oss.ClientConfiguration;
import com.aliyun.oss.OSSClient;
import com.aliyun.oss.common.comm.SignVersion;
import com.aliyun.credentials.utils.AuthConstant;
import com.wind.common.exception.BaseException;
import com.wind.integration.alibaba.credentials.AlibabaCloudCredentialsFactory;
import com.wind.integration.oss.model.WindOssFile;
import org.apache.http.HttpHost;
import org.apache.http.HttpVersion;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.protocol.HttpContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Stream;

class AlibabaCloudOssClientTests {

    @Test
    void testBucketEndpointUtilitiesKeepPublicAndInternalAddressRules() {
        Assertions.assertEquals("https://example.oss-cn-hangzhou.aliyuncs.com",
                AlibabaCloudOssClient.getBucketEndpoint("example", "oss-cn-hangzhou.aliyuncs.com"));
        Assertions.assertEquals("https://example.oss-cn-hangzhou.aliyuncs.com",
                AlibabaCloudOssClient.getBucketEndpoint("example", "oss-cn-hangzhou-internal.aliyuncs.com"));
        Assertions.assertEquals("https://example.oss-cn-hangzhou-internal.aliyuncs.com",
                AlibabaCloudOssClient.getBucketInternalEndpoint("example", "oss-cn-hangzhou.aliyuncs.com"));
        Assertions.assertEquals("https://example.oss-cn-hangzhou-internal.aliyuncs.com",
                AlibabaCloudOssClient.getBucketInternalEndpoint("example", "oss-cn-hangzhou-internal.aliyuncs.com"));
    }

    @ParameterizedTest
    @MethodSource("objectKeyUrlCases")
    void testPublicObjectUrlEncodesRawKeyAndPreservesSlashes(String objectKey, String encodedKey) {
        Client credential = AlibabaCloudCredentialsFactory.accessKey("test-id", "test-secret");
        try (AlibabaCloudOssClient client = AlibabaCloudOssClient.withCredential(credential,
                "http://oss-cn-hangzhou-internal.aliyuncs.com", new ClientConfiguration())) {
            String url = client.getFileUrl("test-bucket", objectKey);

            Assertions.assertEquals("https://test-bucket.oss-cn-hangzhou.aliyuncs.com/" + encodedKey, url);
            URI parsed = URI.create(url);
            Assertions.assertEquals("/" + objectKey, parsed.getPath());
            Assertions.assertNull(parsed.getQuery());
            Assertions.assertNull(parsed.getFragment());
        }
    }

    @ParameterizedTest
    @MethodSource("objectKeyUrlCases")
    void testInternalObjectUrlEncodesRawKeyAndPreservesSlashes(String objectKey, String encodedKey) {
        Client credential = AlibabaCloudCredentialsFactory.accessKey("test-id", "test-secret");
        try (AlibabaCloudOssClient client = AlibabaCloudOssClient.withCredential(credential,
                "http://oss-cn-hangzhou.aliyuncs.com", new ClientConfiguration())) {
            String url = client.getFileInternalUrl("test-bucket", objectKey);

            Assertions.assertEquals("https://test-bucket.oss-cn-hangzhou-internal.aliyuncs.com/" + encodedKey, url);
            URI parsed = URI.create(url);
            Assertions.assertEquals("/" + objectKey, parsed.getPath());
            Assertions.assertNull(parsed.getQuery());
            Assertions.assertNull(parsed.getFragment());
        }
    }

    @Test
    void testObjectUrlsStillRejectNullKeys() {
        Client credential = AlibabaCloudCredentialsFactory.accessKey("test-id", "test-secret");
        try (AlibabaCloudOssClient client = AlibabaCloudOssClient.withCredential(credential,
                "https://oss-cn-hangzhou.aliyuncs.com", new ClientConfiguration())) {
            Assertions.assertThrows(NullPointerException.class, () -> client.getFileUrl("test-bucket", null));
            Assertions.assertThrows(NullPointerException.class, () -> client.getFileInternalUrl("test-bucket", null));
        }
    }

    @Test
    void testDefaultConnectionConfigurationsAreIndependent() {
        ClientConfiguration first = AlibabaCloudOssClient.getDefaultClientConfiguration();
        first.setMaxConnections(1);

        Assertions.assertEquals(256, AlibabaCloudOssClient.getDefaultClientConfiguration().getMaxConnections());
    }

    @Test
    void testRequestsUseCurrentCompleteCredentials() throws Exception {
        AtomicInteger resolutions = new AtomicInteger();
        Client credential = credentialClient(() -> {
            String suffix = resolutions.getAndIncrement() == 0 ? "first" : "second";
            return CredentialModel.builder().type(AuthConstant.STS).accessKeyId("STS." + suffix)
                    .accessKeySecret("secret-" + suffix).securityToken("token-" + suffix).build();
        });
        List<HttpUriRequest> requests = new ArrayList<>();
        CloseableHttpClient http = Mockito.mock(CloseableHttpClient.class);
        Mockito.when(http.execute(Mockito.any(HttpUriRequest.class), Mockito.any(HttpContext.class))).thenAnswer(invocation -> {
            HttpUriRequest request = invocation.getArgument(0);
            requests.add(request);
            return new OssResponse(request.getMethod());
        });
        HttpClientBuilder builder = Mockito.spy(HttpClientBuilder.create());
        Mockito.doReturn(http).when(builder).build();
        try (MockedStatic<HttpClients> clients = Mockito.mockStatic(HttpClients.class)) {
            clients.when(HttpClients::custom).thenReturn(builder);
            ClientConfiguration configuration = new ClientConfiguration();
            configuration.setMaxErrorRetry(0);
            try (AlibabaCloudOssClient oss = AlibabaCloudOssClient.withCredential(credential, "https://oss-cn-hangzhou.aliyuncs.com", configuration)) {
                Assertions.assertEquals(0, resolutions.get());
                WindOssFile file = oss.uploadFile("test-bucket", "object.txt", new ByteArrayInputStream("payload".getBytes(StandardCharsets.UTF_8)), Map.of("purpose", "test"));
                Assertions.assertEquals("object.txt", file.getKey());
                Assertions.assertEquals(7L, file.getSize());
                Assertions.assertEquals("test-etag", file.getETag());
                Assertions.assertEquals("test", file.getMetadata().get("purpose"));
                Assertions.assertEquals("https://test-bucket.oss-cn-hangzhou.aliyuncs.com/object.txt", file.getUrl());
                try (InputStream input = oss.downloadFile("test-bucket", "object.txt")) {
                    Assertions.assertEquals("payload", new String(input.readAllBytes(), StandardCharsets.UTF_8));
                }
                oss.deleteFile("test-bucket", "object.txt");
            }
        }
        Assertions.assertEquals(List.of("PUT", "HEAD", "GET", "DELETE"), requests.stream().map(HttpUriRequest::getMethod).toList());
        Assertions.assertEquals(List.of("token-first", "token-second", "token-second", "token-second"),
                requests.stream().map(request -> request.getFirstHeader("x-oss-security-token").getValue()).toList());
        Assertions.assertTrue(requests.getFirst().getFirstHeader("Authorization").getValue().startsWith("OSS STS.first:"));
        Assertions.assertTrue(requests.getLast().getFirstHeader("Authorization").getValue().startsWith("OSS STS.second:"));
    }

    @Test
    void testExplicitAkUsesPublicFactoryAndKeepsUrlSemantics() throws Exception {
        List<HttpUriRequest> requests = new ArrayList<>();
        CloseableHttpClient http = Mockito.mock(CloseableHttpClient.class);
        Mockito.when(http.execute(Mockito.any(HttpUriRequest.class), Mockito.any(HttpContext.class))).thenAnswer(invocation -> {
            HttpUriRequest request = invocation.getArgument(0);
            requests.add(request);
            return new OssResponse(request.getMethod());
        });
        HttpClientBuilder builder = Mockito.spy(HttpClientBuilder.create());
        Mockito.doReturn(http).when(builder).build();
        try (MockedStatic<HttpClients> clients = Mockito.mockStatic(HttpClients.class)) {
            clients.when(HttpClients::custom).thenReturn(builder);
            Config credential = new Config().setType(AuthConstant.ACCESS_KEY).setAccessKeyId("test-id").setAccessKeySecret("test-secret");
            Map<String, Object> original = credential.toMap();
            try (AlibabaCloudOssClient oss = AlibabaCloudOssClient.defaults(credential, "https://oss-cn-hangzhou-internal.aliyuncs.com", new ClientConfiguration())) {
                oss.deleteFile("test-bucket", "object.txt");
                Assertions.assertEquals("https://test-bucket.oss-cn-hangzhou.aliyuncs.com/object.txt", oss.getFileUrl("test-bucket", "object.txt"));
                Assertions.assertEquals("https://test-bucket.oss-cn-hangzhou-internal.aliyuncs.com/object.txt", oss.getFileInternalUrl("test-bucket", "object.txt"));
            }
            Assertions.assertEquals(original, credential.toMap());
        }
        Assertions.assertEquals(1, requests.size());
        Assertions.assertNull(requests.getFirst().getFirstHeader("x-oss-security-token"));
        Assertions.assertTrue(requests.getFirst().getFirstHeader("Authorization").getValue().startsWith("OSS test-id:"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testV4UsesExplicitRegionAndStsCredentials(boolean useConfig) throws Exception {
        Config credential = new Config().setType(AuthConstant.STS).setAccessKeyId("STS.test")
                .setAccessKeySecret("test-secret").setSecurityToken("test-token");
        ClientConfiguration configuration = new ClientConfiguration();
        configuration.setSignatureVersion(SignVersion.V4);
        List<HttpUriRequest> requests = new ArrayList<>();
        try (MockedStatic<HttpClients> ignored = interceptRequests(requests)) {
            AlibabaCloudOssClient client;
            if (useConfig) {
                client = AlibabaCloudOssClient.defaults(credential, "https://oss-cn-hangzhou.aliyuncs.com", "cn-hangzhou", configuration);
            } else {
                client = AlibabaCloudOssClient.withCredential(AlibabaCloudCredentialsFactory.create(credential),
                        "https://oss-cn-hangzhou.aliyuncs.com", "cn-hangzhou", configuration);
            }
            try (client) {
                client.deleteFile("test-bucket", "object.txt");
            }
        }
        Assertions.assertEquals(1, requests.size());
        String authorization = requests.getFirst().getFirstHeader("Authorization").getValue();
        Assertions.assertTrue(authorization.startsWith("OSS4-HMAC-SHA256 Credential=STS.test/"));
        Assertions.assertTrue(authorization.contains("/cn-hangzhou/oss/aliyun_v4_request"));
        Assertions.assertEquals("test-token", requests.getFirst().getFirstHeader("x-oss-security-token").getValue());
    }

    @Test
    void testV4PreservesRegionExtractedFromCloudBoxEndpoint() throws Exception {
        ClientConfiguration configuration = new ClientConfiguration();
        configuration.setSignatureVersion(SignVersion.V4);
        List<HttpUriRequest> requests = new ArrayList<>();
        try (MockedStatic<HttpClients> ignored = interceptRequests(requests);
             AlibabaCloudOssClient client = AlibabaCloudOssClient.withCredential(AlibabaCloudCredentialsFactory.accessKey("test-id", "test-secret"),
                     "https://cb-example.cn-hangzhou.oss-cloudbox.aliyuncs.com", configuration)) {
            client.deleteFile("test-bucket", "object.txt");
        }
        Assertions.assertEquals(1, requests.size());
        Assertions.assertTrue(requests.getFirst().getFirstHeader("Authorization").getValue()
                .contains("/cb-example/oss-cloudbox/aliyun_v4_request"));
    }

    @Test
    void testV4WithoutRegionFailsDuringCreationAndReleasesConnectionPool() {
        HttpClientBuilder builder = Mockito.spy(HttpClientBuilder.create());
        Mockito.doReturn(Mockito.mock(CloseableHttpClient.class)).when(builder).build();
        ClientConfiguration configuration = new ClientConfiguration();
        configuration.setSignatureVersion(SignVersion.V4);
        Client credential = AlibabaCloudCredentialsFactory.accessKey("test-id", "test-secret");
        try (MockedStatic<HttpClients> clients = Mockito.mockStatic(HttpClients.class)) {
            clients.when(HttpClients::custom).thenReturn(builder);
            BaseException failure = Assertions.assertThrows(BaseException.class,
                    () -> AlibabaCloudOssClient.withCredential(credential, "https://oss-cn-hangzhou.aliyuncs.com", configuration).close());
            Assertions.assertTrue(failure.getMessage().contains("region"));
            ArgumentCaptor<HttpClientConnectionManager> manager = ArgumentCaptor.forClass(HttpClientConnectionManager.class);
            Mockito.verify(builder).setConnectionManager(manager.capture());
            Assertions.assertThrows(IllegalStateException.class, () -> manager.getValue().requestConnection(new HttpRoute(new HttpHost("example.invalid")), null));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " "})
    void testRejectBlankExplicitRegion(String region) {
        Client credential = AlibabaCloudCredentialsFactory.accessKey("test-id", "test-secret");
        BaseException failure = Assertions.assertThrows(BaseException.class,
                () -> AlibabaCloudOssClient.withCredential(credential, "https://oss-cn-hangzhou.aliyuncs.com", region, new ClientConfiguration()).close());
        Assertions.assertTrue(failure.getMessage().contains("region"));
    }

    @Test
    void testCreatedClientReleasesConnectionPoolWithoutClosingCredential() throws Exception {
        HttpClientBuilder builder = Mockito.spy(HttpClientBuilder.create());
        Mockito.doReturn(Mockito.mock(CloseableHttpClient.class)).when(builder).build();
        AlibabaCloudCredentialsProvider provider = Mockito.mock(AlibabaCloudCredentialsProvider.class);
        try (MockedStatic<HttpClients> clients = Mockito.mockStatic(HttpClients.class)) {
            clients.when(HttpClients::custom).thenReturn(builder);
            AlibabaCloudOssClient oss = AlibabaCloudOssClient.withCredential(new Client(provider), "https://oss-cn-hangzhou.aliyuncs.com", new ClientConfiguration());
            ArgumentCaptor<HttpClientConnectionManager> manager = ArgumentCaptor.forClass(HttpClientConnectionManager.class);
            Mockito.verify(builder).setConnectionManager(manager.capture());

            oss.close();
            oss.close();

            Assertions.assertThrows(IllegalStateException.class, () -> manager.getValue().requestConnection(new HttpRoute(new HttpHost("example.invalid")), null));
            Mockito.verifyNoInteractions(provider);
        }
    }

    @Test
    void testCloseShutsDownNativeClient() {
        OSSClient nativeClient = Mockito.mock(OSSClient.class);

        AlibabaCloudOssClient wrapper = new AlibabaCloudOssClient(nativeClient);
        wrapper.close();

        Mockito.verify(nativeClient).shutdown();
    }

    @Test
    void testDefaultsEndpointUsesRecommendedCredentialAndExistingConnectionLimit() {
        Client credential = AlibabaCloudCredentialsFactory.accessKey("test-id", "test-secret");
        try (MockedStatic<AlibabaCloudCredentialsFactory> factory = Mockito.mockStatic(AlibabaCloudCredentialsFactory.class);
             MockedStatic<HttpClients> clients = Mockito.mockStatic(HttpClients.class)) {
            factory.when(AlibabaCloudCredentialsFactory::defaults).thenReturn(credential);
            HttpClientBuilder builder = Mockito.spy(HttpClientBuilder.create());
            Mockito.doReturn(Mockito.mock(CloseableHttpClient.class)).when(builder).build();
            clients.when(HttpClients::custom).thenReturn(builder);
            try (AlibabaCloudOssClient oss = AlibabaCloudOssClient.defaults("https://oss-cn-hangzhou.aliyuncs.com")) {
                Assertions.assertEquals("https://test-bucket.oss-cn-hangzhou.aliyuncs.com/a", oss.getFileUrl("test-bucket", "a"));
                ArgumentCaptor<HttpClientConnectionManager> manager = ArgumentCaptor.forClass(HttpClientConnectionManager.class);
                Mockito.verify(builder).setConnectionManager(manager.capture());
                Assertions.assertEquals(256, ((org.apache.http.impl.conn.PoolingHttpClientConnectionManager) manager.getValue()).getMaxTotal());
            }
        }
    }

    @Test
    void testRejectInvalidCreationArguments() {
        Client credential = AlibabaCloudCredentialsFactory.accessKey("test-id", "test-secret");
        Assertions.assertThrows(BaseException.class, () -> AlibabaCloudOssClient.withCredential(null, "https://oss-cn-hangzhou.aliyuncs.com", new ClientConfiguration()));
        Assertions.assertThrows(BaseException.class, () -> AlibabaCloudOssClient.withCredential(credential, " ", new ClientConfiguration()));
        Assertions.assertThrows(BaseException.class, () -> AlibabaCloudOssClient.withCredential(credential, "https://oss-cn-hangzhou.aliyuncs.com", null));
        Assertions.assertThrows(BaseException.class, () -> AlibabaCloudOssClient.defaults(new Config().setType("invalid"), "https://oss-cn-hangzhou.aliyuncs.com", new ClientConfiguration()));
    }

    private static Stream<Arguments> objectKeyUrlCases() {
        return Stream.of(
                Arguments.of("object.txt", "object.txt"),
                Arguments.of("report #? %.txt", "report%20%23%3F%20%25.txt"),
                Arguments.of("a+b&c=d.txt", "a%2Bb%26c%3Dd.txt"),
                Arguments.of("%2F/%20/%E4%B8%AD", "%252F/%2520/%25E4%25B8%25AD"),
                Arguments.of("目录/报告📄.txt", "%E7%9B%AE%E5%BD%95/%E6%8A%A5%E5%91%8A%F0%9F%93%84.txt"),
                Arguments.of("/leading//space name///", "/leading//space%20name///"),
                Arguments.of("//", "//"));
    }

    private Client credentialClient(Supplier<CredentialModel> supplier) {
        return new Client(new AlibabaCloudCredentialsProvider() {
            @Override
            public CredentialModel getCredentials() {
                return supplier.get();
            }

            @Override
            public String getProviderName() {
                return "test";
            }

            @Override
            public void close() {
                throw new AssertionError("OSS 不拥有共享凭据");
            }
        });
    }

    private MockedStatic<HttpClients> interceptRequests(List<HttpUriRequest> requests) throws Exception {
        CloseableHttpClient http = Mockito.mock(CloseableHttpClient.class);
        Mockito.when(http.execute(Mockito.any(HttpUriRequest.class), Mockito.any(HttpContext.class))).thenAnswer(invocation -> {
            HttpUriRequest request = invocation.getArgument(0);
            requests.add(request);
            return new OssResponse(request.getMethod());
        });
        MockedStatic<HttpClients> clients = Mockito.mockStatic(HttpClients.class);
        clients.when(HttpClients::custom).thenAnswer(invocation -> {
            HttpClientBuilder builder = Mockito.spy(HttpClientBuilder.create());
            Mockito.doReturn(http).when(builder).build();
            return builder;
        });
        return clients;
    }

    private static class OssResponse extends BasicHttpResponse implements CloseableHttpResponse {

        OssResponse(String method) {
            super(HttpVersion.HTTP_1_1, "DELETE".equals(method) ? 204 : 200, "OK");
            setHeader("x-oss-request-id", "test-request");
            setHeader("ETag", "\"test-etag\"");
            setHeader("Content-Type", "text/plain");
            setHeader("Content-Length", "7");
            setHeader("x-oss-meta-purpose", "test");
            setHeader("Last-Modified", "Wed, 18 Nov 2026 01:00:00 GMT");
            if ("GET".equals(method)) {
                setEntity(new ByteArrayEntity("payload".getBytes(StandardCharsets.UTF_8)));
            }
        }

        @Override
        public void close() {
        }
    }
}
