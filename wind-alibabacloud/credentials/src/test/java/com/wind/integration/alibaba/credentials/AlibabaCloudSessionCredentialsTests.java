package com.wind.integration.alibaba.credentials;

import com.aliyun.credentials.Client;
import com.aliyun.credentials.exception.CredentialException;
import com.aliyun.credentials.http.CompatibleUrlConnClient;
import com.aliyun.credentials.http.FormatType;
import com.aliyun.credentials.http.HttpRequest;
import com.aliyun.credentials.http.HttpResponse;
import com.aliyun.credentials.http.MethodType;
import com.aliyun.credentials.models.Config;
import com.aliyun.credentials.models.CredentialModel;
import com.aliyun.credentials.utils.AuthConstant;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * 替换元数据和凭据服务的 HTTP 边界，验证官方提供者的实际取证行为。
 */
class AlibabaCloudSessionCredentialsTests {

    @Test
    void testCredentialsUriShortcutUsesRequestedService() throws Exception {
        List<HttpRequest> requests = new ArrayList<>();
        try (MockedConstruction<CompatibleUrlConnClient> ignored = Mockito.mockConstruction(CompatibleUrlConnClient.class,
                (http, context) -> Mockito.when(http.syncInvoke(Mockito.any(HttpRequest.class))).thenAnswer(invocation -> {
                    requests.add(invocation.getArgument(0));
                    return sessionResponse(1, 7200);
                }))) {
            Client client = AlibabaCloudCredentialsFactory.credentialsUri("http://127.0.0.1:1/shortcut-credentials");
            Assertions.assertTrue(requests.isEmpty());

            assertCredentials(client.getCredential(), 1);

            Assertions.assertEquals(1, requests.size());
            Assertions.assertEquals("http://127.0.0.1:1/shortcut-credentials", requests.getFirst().getSysUrl());
            Assertions.assertEquals(MethodType.GET, requests.getFirst().getSysMethod());
        }
    }

    @Test
    void testEcsUsesImdsV2AndRefreshesOnDemand() throws Exception {
        List<HttpRequest> requests = new ArrayList<>();
        try (MockedConstruction<CompatibleUrlConnClient> ignored = Mockito.mockConstruction(CompatibleUrlConnClient.class,
                (http, context) -> Mockito.when(http.syncInvoke(Mockito.any(HttpRequest.class))).thenAnswer(invocation -> {
                    HttpRequest request = invocation.getArgument(0);
                    requests.add(request);
                    if (request.getSysMethod() == MethodType.PUT) {
                        return response(200, "metadata-token");
                    }
                    int sequence = requests.size() / 2;
                    return sessionResponse(sequence, sequence == 1 ? 120 : 7200);
                }))) {
            Config config = new Config().setType(AuthConstant.ECS_RAM_ROLE).setRoleName("test-role")
                    .setDisableIMDSv1(true).setConnectTimeout(1234).setTimeout(5678).setMetadataTokenDuration(60);
            Client client = AlibabaCloudCredentialsFactory.create(config);
            Assertions.assertTrue(requests.isEmpty());

            assertCredentials(client.getCredential(), 1);
            assertCredentials(client.getCredential(), 2);
            assertCredentials(client.getCredential(), 2);

            Assertions.assertEquals(4, requests.size());
            Assertions.assertEquals("21600", requests.getFirst().getHeaderValue("X-aliyun-ecs-metadata-token-ttl-seconds"));
            Assertions.assertEquals("metadata-token", requests.get(1).getHeaderValue("X-aliyun-ecs-metadata-token"));
            Assertions.assertTrue(requests.get(1).getSysUrl().endsWith("/ram/security-credentials/test-role"));
            Assertions.assertEquals(1234, requests.get(1).getSysConnectTimeout());
            Assertions.assertEquals(5678, requests.get(1).getSysReadTimeout());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testEcsHonorsImdsV1FallbackPolicy(boolean disableImdsV1) throws Exception {
        List<HttpRequest> requests = new ArrayList<>();
        try (MockedConstruction<CompatibleUrlConnClient> ignored = Mockito.mockConstruction(CompatibleUrlConnClient.class,
                (http, context) -> Mockito.when(http.syncInvoke(Mockito.any(HttpRequest.class))).thenAnswer(invocation -> {
                    HttpRequest request = invocation.getArgument(0);
                    requests.add(request);
                    if (request.getSysMethod() == MethodType.PUT) {
                        return response(403, "metadata token denied");
                    }
                    return sessionResponse(1, 7200);
                }))) {
            Client client = AlibabaCloudCredentialsFactory.create(new Config().setType(AuthConstant.ECS_RAM_ROLE)
                    .setRoleName("test-role").setDisableIMDSv1(disableImdsV1));

            if (disableImdsV1) {
                Assertions.assertThrows(CredentialException.class, client::getCredential);
                Assertions.assertEquals(1, requests.size());
            } else {
                assertCredentials(client.getCredential(), 1);
                Assertions.assertEquals(2, requests.size());
                Assertions.assertNull(requests.getLast().getHeaderValue("X-aliyun-ecs-metadata-token"));
            }
        }
    }

    @Test
    void testCredentialsUriRefreshAndCache() throws Exception {
        List<HttpRequest> requests = new ArrayList<>();
        try (MockedConstruction<CompatibleUrlConnClient> ignored = Mockito.mockConstruction(CompatibleUrlConnClient.class,
                (http, context) -> Mockito.when(http.syncInvoke(Mockito.any(HttpRequest.class))).thenAnswer(invocation -> {
                    requests.add(invocation.getArgument(0));
                    return sessionResponse(requests.size(), requests.size() == 1 ? 120 : 7200);
                }))) {
            Client client = AlibabaCloudCredentialsFactory.create(new Config().setType(AuthConstant.CREDENTIALS_URI)
                    .setCredentialsUri("http://127.0.0.1:1/credentials").setConnectTimeout(1234).setTimeout(5678));
            Assertions.assertTrue(requests.isEmpty());

            assertCredentials(client.getCredential(), 1);
            assertCredentials(client.getCredential(), 2);
            assertCredentials(client.getCredential(), 2);

            Assertions.assertEquals(2, requests.size());
            Assertions.assertEquals(MethodType.GET, requests.getFirst().getSysMethod());
            Assertions.assertEquals("http://127.0.0.1:1/credentials", requests.getFirst().getSysUrl());
            Assertions.assertEquals(1234, requests.getFirst().getSysConnectTimeout());
            Assertions.assertEquals(5678, requests.getFirst().getSysReadTimeout());
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {200, 403})
    void testCredentialsUriRejectsInvalidResponse(int status) throws Exception {
        try (MockedConstruction<CompatibleUrlConnClient> ignored = Mockito.mockConstruction(CompatibleUrlConnClient.class,
                (http, context) -> Mockito.when(http.syncInvoke(Mockito.any(HttpRequest.class))).thenReturn(response(status, "{\"Code\":\"Success\"}")))) {
            Client client = AlibabaCloudCredentialsFactory.create(new Config().setType(AuthConstant.CREDENTIALS_URI)
                    .setCredentialsUri("http://127.0.0.1:1/credentials"));

            Assertions.assertThrows(CredentialException.class, client::getCredential);
        }
    }

    private static void assertCredentials(CredentialModel credential, int sequence) {
        Assertions.assertEquals("STS.test-" + sequence, credential.getAccessKeyId());
        Assertions.assertEquals("secret-" + sequence, credential.getAccessKeySecret());
        Assertions.assertEquals("token-" + sequence, credential.getSecurityToken());
    }

    private static HttpResponse sessionResponse(int sequence, long lifetimeSeconds) {
        String expiration = Instant.now().plusSeconds(lifetimeSeconds).truncatedTo(ChronoUnit.SECONDS).toString();
        String body = """
                {"Code":"Success","AccessKeyId":"STS.test-%d","AccessKeySecret":"secret-%d",
                "SecurityToken":"token-%d","Expiration":"%s"}
                """.formatted(sequence, sequence, sequence, expiration);
        return response(200, body);
    }

    private static HttpResponse response(int status, String body) {
        HttpResponse response = new HttpResponse("http://credentials.example.invalid");
        response.setResponseCode(status);
        response.setHttpContent(body.getBytes(StandardCharsets.UTF_8), "UTF-8", FormatType.JSON);
        return response;
    }
}
