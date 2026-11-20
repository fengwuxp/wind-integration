package com.wind.integration.alibaba.credentials;

import com.aliyun.credentials.Client;
import com.aliyun.credentials.http.CompatibleUrlConnClient;
import com.aliyun.credentials.http.FormatType;
import com.aliyun.credentials.http.HttpRequest;
import com.aliyun.credentials.http.HttpResponse;
import com.aliyun.credentials.http.MethodType;
import com.aliyun.credentials.models.CredentialModel;
import com.aliyun.credentials.utils.AuthConstant;
import com.aliyun.credentials.utils.AuthUtils;
import com.wind.common.exception.BaseException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

class AlibabaCloudCredentialsPropertiesTests {

    private static final String SESSION_POLICY = "{\"Version\":\"1\",\"Statement\":[]}";

    @ParameterizedTest
    @ValueSource(strings = {"inferred-ak", "explicit-ak", "inferred-sts", "explicit-sts"})
    void testStaticCredentialsUseRequestedOrInferredTypeWithoutChangingInput(String mode) {
        AlibabaCloudCredentialsProperties properties = accessKeyProperties();
        if (mode.endsWith("sts")) {
            properties.setSecurityToken("test-token");
        }
        if ("explicit-ak".equals(mode)) {
            properties.setType(AuthConstant.ACCESS_KEY);
        } else if ("explicit-sts".equals(mode)) {
            properties.setType(AuthConstant.STS);
        }
        String originalType = properties.getType();

        Client client = AlibabaCloudCredentialsFactory.fromProperties(properties);
        properties.setAccessKey("changed-id");
        CredentialModel credential = client.getCredential();

        Assertions.assertEquals("test-id", credential.getAccessKeyId());
        Assertions.assertEquals("test-secret", credential.getAccessKeySecret());
        Assertions.assertEquals(properties.getSecurityToken(), credential.getSecurityToken());
        Assertions.assertEquals(originalType, properties.getType());
        Assertions.assertNull(properties.getRoleSessionExpiration());
    }

    @ParameterizedTest
    @ValueSource(strings = {"none", "role-without-type", "blank-type", "unknown-type", "partial-ak", "token-without-ak", "blank-token", "mixed-oidc"})
    void testIncompleteOrConflictingConfigurationDoesNotSelectAnotherIdentity(String mode) {
        AlibabaCloudCredentialsProperties properties = new AlibabaCloudCredentialsProperties();
        switch (mode) {
            case "role-without-type" -> properties.setRoleArn("acs:ram::123456789:role/test-role");
            case "blank-type" -> properties.setType(" ");
            case "unknown-type" -> properties.setType("unknown");
            case "partial-ak" -> properties.setAccessKey("test-id");
            case "token-without-ak" -> properties.setSecurityToken("test-token");
            case "blank-token" -> {
                properties.setAccessKey("test-id");
                properties.setSecretKey("test-secret");
                properties.setSecurityToken("");
            }
            case "mixed-oidc" -> {
                properties.setType(AuthConstant.OIDC_ROLE_ARN);
                properties.setAccessKey("unrelated-id");
            }
            default -> { }
        }

        Assertions.assertThrows(BaseException.class, () -> AlibabaCloudCredentialsFactory.fromProperties(properties));
    }

    @Test
    void testRejectNullProperties() {
        Assertions.assertThrows(BaseException.class, () -> AlibabaCloudCredentialsFactory.fromProperties(null));
    }

    @Test
    void testEndpointAloneDoesNotOverrideIdentityButExplicitBlankAndOptionsDo() {
        AlibabaCloudProductProperties properties = new AlibabaCloudProductProperties();
        properties.setEndpoint("oss-cn-hangzhou.aliyuncs.com");
        Assertions.assertFalse(properties.hasCredentials());
        properties.setAccessKey("");
        Assertions.assertTrue(properties.hasCredentials());
        properties.setAccessKey(null);
        properties.setDisableIMDSv1(false);
        Assertions.assertTrue(properties.hasCredentials());
        properties.setDisableIMDSv1(null);
        properties.setConnectTimeout(0);
        Assertions.assertTrue(properties.hasCredentials());
        properties.setConnectTimeout(null);
        properties.setType("");
        Assertions.assertTrue(properties.hasCredentials());
    }

    @Test
    void testProductEqualityIncludesIdentityWhileLoggingExcludesSensitiveFields() {
        AlibabaCloudProductProperties first = productProperties("first-secret");
        AlibabaCloudProductProperties same = productProperties("first-secret");
        AlibabaCloudProductProperties other = productProperties("second-secret");

        Assertions.assertEquals(first, same);
        Assertions.assertEquals(first.hashCode(), same.hashCode());
        Assertions.assertNotEquals(first, other);
        for (AlibabaCloudCredentialsProperties properties : List.of(first, accessKeyProperties())) {
            String text = properties.toString();
            Assertions.assertFalse(text.contains("test-id"));
            Assertions.assertFalse(text.contains("test-secret"));
            Assertions.assertFalse(text.contains("first-secret"));
            Assertions.assertFalse(text.contains("sensitive-token"));
            Assertions.assertFalse(text.contains("sensitive-policy"));
            Assertions.assertFalse(text.contains("sensitive-external-id"));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {AuthConstant.RAM_ROLE_ARN, AuthConstant.OIDC_ROLE_ARN, AuthConstant.ECS_RAM_ROLE, AuthConstant.CREDENTIALS_URI})
    void testSessionConfigurationReachesOfficialProviderRequests(String type) throws Exception {
        AlibabaCloudCredentialsProperties properties = sessionProperties(type);
        List<HttpRequest> requests = new ArrayList<>();
        try (MockedStatic<AuthUtils> auth = Mockito.mockStatic(AuthUtils.class, invocation -> {
            if ("getOIDCToken".equals(invocation.getMethod().getName())) {
                Assertions.assertEquals("/explicit/token", invocation.getArgument(0));
                return "oidc-token";
            }
            return invocation.callRealMethod();
        }); MockedConstruction<CompatibleUrlConnClient> ignored = Mockito.mockConstruction(CompatibleUrlConnClient.class,
                (http, context) -> Mockito.when(http.syncInvoke(Mockito.any(HttpRequest.class))).thenAnswer(invocation -> {
                    HttpRequest request = invocation.getArgument(0);
                    requests.add(request);
                    return sessionResponse(request);
                }))) {
            Client client = AlibabaCloudCredentialsFactory.fromProperties(properties);
            Assertions.assertTrue(requests.isEmpty());
            CredentialModel credential = client.getCredential();
            Assertions.assertEquals("STS.result", credential.getAccessKeyId());
            Assertions.assertEquals("result-secret", credential.getAccessKeySecret());
            Assertions.assertEquals("result-token", credential.getSecurityToken());
        }
        HttpRequest request = requests.getLast();
        Assertions.assertEquals(1234, request.getSysConnectTimeout());
        Assertions.assertEquals(5678, request.getSysReadTimeout());
        if (AuthConstant.RAM_ROLE_ARN.equals(type)) {
            Assertions.assertTrue(request.getSysUrl().startsWith("https://sts.example.invalid"));
            Assertions.assertEquals("AssumeRole", request.getUrlParameters().get("Action"));
            Assertions.assertEquals("source-id", request.getUrlParameters().get("AccessKeyId"));
            Assertions.assertEquals("source-token", request.getUrlParameters().get("SecurityToken"));
            Assertions.assertEquals("test-session", request.getUrlParameters().get("RoleSessionName"));
            Assertions.assertEquals("1800", request.getUrlParameters().get("DurationSeconds"));
            Assertions.assertEquals(SESSION_POLICY, request.getUrlParameters().get("Policy"));
            Assertions.assertEquals("external-id", request.getUrlParameters().get("ExternalId"));
        } else if (AuthConstant.OIDC_ROLE_ARN.equals(type)) {
            String body = URLDecoder.decode(request.getHttpContentString(), StandardCharsets.UTF_8);
            Assertions.assertTrue(request.getSysUrl().startsWith("https://sts.example.invalid"));
            Assertions.assertTrue(body.contains("RoleArn=acs:ram::123456789:role/test-role"));
            Assertions.assertTrue(body.contains("OIDCProviderArn=acs:ram::123456789:oidc-provider/test"));
            Assertions.assertTrue(body.contains("RoleSessionName=test-session"));
            Assertions.assertTrue(body.contains("DurationSeconds=1800"));
            Assertions.assertTrue(body.contains("Policy=" + SESSION_POLICY));
        } else if (AuthConstant.ECS_RAM_ROLE.equals(type)) {
            Assertions.assertEquals(MethodType.PUT, requests.getFirst().getSysMethod());
            Assertions.assertTrue(request.getSysUrl().endsWith("/test-role"));
            Assertions.assertEquals("metadata-token", request.getHeaderValue("X-aliyun-ecs-metadata-token"));
        } else {
            Assertions.assertEquals("http://127.0.0.1:1/credentials", request.getSysUrl());
            Assertions.assertEquals(MethodType.GET, request.getSysMethod());
        }
    }

    @Test
    void testOidcExplicitPathPreservesProperties() throws Exception {
        AlibabaCloudCredentialsProperties properties = sessionProperties(AuthConstant.OIDC_ROLE_ARN);
        String inputPath = properties.getOidcTokenFilePath();
        List<String> paths = new ArrayList<>();
        try (MockedStatic<AuthUtils> auth = Mockito.mockStatic(AuthUtils.class, invocation -> {
            if ("getOIDCToken".equals(invocation.getMethod().getName())) {
                paths.add(invocation.getArgument(0));
                return "oidc-token";
            }
            return invocation.callRealMethod();
        }); MockedConstruction<CompatibleUrlConnClient> ignored = Mockito.mockConstruction(CompatibleUrlConnClient.class,
                (http, context) -> Mockito.when(http.syncInvoke(Mockito.any(HttpRequest.class)))
                        .thenAnswer(invocation -> sessionResponse(invocation.getArgument(0))))) {
            Assertions.assertEquals("STS.result", AlibabaCloudCredentialsFactory.fromProperties(properties).getCredential().getAccessKeyId());
        }
        Assertions.assertEquals(List.of("/explicit/token"), paths);
        Assertions.assertEquals(inputPath, properties.getOidcTokenFilePath());
    }

    private static AlibabaCloudCredentialsProperties accessKeyProperties() {
        AlibabaCloudCredentialsProperties properties = new AlibabaCloudCredentialsProperties();
        properties.setAccessKey("test-id");
        properties.setSecretKey("test-secret");
        return properties;
    }

    private static AlibabaCloudProductProperties productProperties(String secret) {
        AlibabaCloudProductProperties properties = new AlibabaCloudProductProperties();
        properties.setEndpoint("product.example.invalid");
        properties.setType(AuthConstant.STS);
        properties.setAccessKey("test-id");
        properties.setSecretKey(secret);
        properties.setSecurityToken("sensitive-token");
        properties.setPolicy("sensitive-policy");
        properties.setExternalId("sensitive-external-id");
        return properties;
    }

    private static AlibabaCloudCredentialsProperties sessionProperties(String type) {
        AlibabaCloudCredentialsProperties properties = new AlibabaCloudCredentialsProperties();
        properties.setType(type);
        properties.setConnectTimeout(1234);
        properties.setReadTimeout(5678);
        if (AuthConstant.RAM_ROLE_ARN.equals(type) || AuthConstant.OIDC_ROLE_ARN.equals(type)) {
            properties.setRoleArn("acs:ram::123456789:role/test-role");
            properties.setRoleSessionName("test-session");
            properties.setRoleSessionExpiration(1800);
            properties.setPolicy(SESSION_POLICY);
            properties.setStsEndpoint("sts.example.invalid");
        }
        switch (type) {
            case AuthConstant.RAM_ROLE_ARN -> {
                properties.setAccessKey("source-id");
                properties.setSecretKey("source-secret");
                properties.setSecurityToken("source-token");
                properties.setExternalId("external-id");
            }
            case AuthConstant.OIDC_ROLE_ARN -> {
                properties.setOidcProviderArn("acs:ram::123456789:oidc-provider/test");
                properties.setOidcTokenFilePath("/explicit/token");
            }
            case AuthConstant.ECS_RAM_ROLE -> {
                properties.setRoleName("test-role");
                properties.setDisableIMDSv1(true);
            }
            default -> properties.setCredentialsUri("http://127.0.0.1:1/credentials");
        }
        return properties;
    }

    private static HttpResponse sessionResponse(HttpRequest request) {
        String content;
        if (request.getSysMethod() == MethodType.PUT) {
            content = "metadata-token";
        } else {
            String credentials = """
                    {"Code":"Success","AccessKeyId":"STS.result","AccessKeySecret":"result-secret",
                     "SecurityToken":"result-token","Expiration":"%s"}
                    """.formatted(Instant.now().plusSeconds(7200).truncatedTo(ChronoUnit.SECONDS));
            content = request.getSysUrl().contains("sts.example.invalid") ? "{\"Credentials\":" + credentials + "}" : credentials;
        }
        HttpResponse response = new HttpResponse(request.getSysUrl());
        response.setResponseCode(200);
        response.setHttpContent(content.getBytes(StandardCharsets.UTF_8), "UTF-8", FormatType.JSON);
        return response;
    }
}
