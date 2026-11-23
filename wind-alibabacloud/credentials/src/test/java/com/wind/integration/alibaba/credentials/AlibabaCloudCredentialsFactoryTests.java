package com.wind.integration.alibaba.credentials;

import com.aliyun.credentials.Client;
import com.aliyun.credentials.exception.CredentialException;
import com.aliyun.credentials.http.CompatibleUrlConnClient;
import com.aliyun.credentials.http.FormatType;
import com.aliyun.credentials.http.HttpRequest;
import com.aliyun.credentials.http.HttpResponse;
import com.aliyun.credentials.models.Config;
import com.aliyun.credentials.models.CredentialModel;
import com.aliyun.credentials.utils.AuthUtils;
import com.aliyun.credentials.utils.AuthConstant;
import com.wind.common.exception.BaseException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

class AlibabaCloudCredentialsFactoryTests {

    private static final String SESSION_POLICY = "{\"Version\":\"1\",\"Statement\":[{\"Effect\":\"Allow\",\"Action\":[\"kms:Encrypt\"],\"Resource\":\"*\"}]}";

    @TempDir
    Path temporaryDirectory;

    @Test
    void testAccessKeyCredentialsAndInputUnchanged() {
        Config config = accessKeyConfig().setRoleSessionExpiration(null);
        Map<String, Object> original = config.toMap();

        Client client = AlibabaCloudCredentialsFactory.create(config);

        Assertions.assertEquals(original, config.toMap());
        config.setAccessKeyId("other-id").setAccessKeySecret("other-secret");
        assertCredentials(client.getCredential(), "test-id", "test-secret", null);
    }

    @Test
    void testCredentialsAreIsolatedBetweenClients() {
        Client first = AlibabaCloudCredentialsFactory.create(accessKeyConfig());
        Client second = AlibabaCloudCredentialsFactory.create(accessKeyConfig().setAccessKeyId("second-id").setAccessKeySecret("second-secret"));

        assertCredentials(first.getCredential(), "test-id", "test-secret", null);
        assertCredentials(second.getCredential(), "second-id", "second-secret", null);
    }

    @Test
    void testAccessKeyShortcutUsesSharedValidation() {
        assertCredentials(AlibabaCloudCredentialsFactory.accessKey("test-id", "test-secret").getCredential(), "test-id", "test-secret", null);
        Assertions.assertThrows(BaseException.class, () -> AlibabaCloudCredentialsFactory.accessKey(" ", "test-secret"));
    }

    @ParameterizedTest
    @ValueSource(strings = {AuthConstant.RAM_ROLE_ARN, AuthConstant.OIDC_ROLE_ARN})
    void testRoleShortcutsUseRequestedIdentity(String type) throws Exception {
        Path tokenFile = temporaryDirectory.resolve("shortcut-token");
        Files.writeString(tokenFile, "shortcut-oidc-token");
        String roleArn = "acs:ram::123456789:role/shortcut-role";
        String providerArn = "acs:ram::123456789:oidc-provider/shortcut-provider";
        List<HttpRequest> requests = new ArrayList<>();
        try (MockedConstruction<CompatibleUrlConnClient> ignored = Mockito.mockConstruction(CompatibleUrlConnClient.class,
                (http, context) -> Mockito.when(http.syncInvoke(Mockito.any(HttpRequest.class))).thenAnswer(invocation -> {
                    requests.add(invocation.getArgument(0));
                    return stsResponse(1, Instant.now().plusSeconds(7200));
                }))) {
            Client client;
            if (AuthConstant.RAM_ROLE_ARN.equals(type)) {
                client = AlibabaCloudCredentialsFactory.ramRoleArn("source-id", "source-secret", roleArn);
            } else {
                client = AlibabaCloudCredentialsFactory.oidcRoleArn(roleArn, providerArn, tokenFile.toString());
            }
            Assertions.assertTrue(requests.isEmpty());
            assertCredentials(client.getCredential(), "STS.test-1", "secret-1", "token-1");
            Assertions.assertEquals(1, requests.size());
            HttpRequest request = requests.getFirst();
            if (AuthConstant.RAM_ROLE_ARN.equals(type)) {
                Assertions.assertEquals("AssumeRole", request.getUrlParameters().get("Action"));
                Assertions.assertEquals(roleArn, request.getUrlParameters().get("RoleArn"));
                Assertions.assertEquals("source-id", request.getUrlParameters().get("AccessKeyId"));
            } else {
                Assertions.assertEquals("AssumeRoleWithOIDC", request.getUrlParameters().get("Action"));
                String body = URLDecoder.decode(request.getHttpContentString(), StandardCharsets.UTF_8);
                Assertions.assertTrue(body.contains("RoleArn=" + roleArn));
                Assertions.assertTrue(body.contains("OIDCProviderArn=" + providerArn));
                Assertions.assertTrue(body.contains("OIDCToken=shortcut-oidc-token"));
            }
        }
    }

    @Test
    void testRejectNullConfiguration() {
        Assertions.assertThrows(BaseException.class, () -> AlibabaCloudCredentialsFactory.create(null));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "default", "unknown", "bearer"})
    void testRejectImplicitOrUnsupportedType(String type) {
        Assertions.assertThrows(BaseException.class, () -> AlibabaCloudCredentialsFactory.create(accessKeyConfig().setType(type)));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    void testRejectIncompleteAccessKey(String value) {
        Assertions.assertThrows(BaseException.class, () -> AlibabaCloudCredentialsFactory.create(accessKeyConfig().setAccessKeyId(value)));
        Assertions.assertThrows(BaseException.class, () -> AlibabaCloudCredentialsFactory.create(accessKeyConfig().setAccessKeySecret(value)));
    }

    @Test
    void testRejectTokenInAccessKeyMode() {
        Assertions.assertThrows(BaseException.class, () -> AlibabaCloudCredentialsFactory.create(accessKeyConfig().setSecurityToken("ignored-token")));
    }

    @Test
    void testAcceptExplicitStaticStsWithoutPromisingRefresh() {
        Client client = AlibabaCloudCredentialsFactory.create(accessKeyConfig().setType(AuthConstant.STS).setSecurityToken("test-token"));

        assertCredentials(client.getCredential(), "test-id", "test-secret", "test-token");
    }

    @Test
    void testRejectStsWithoutToken() {
        Assertions.assertThrows(BaseException.class, () -> AlibabaCloudCredentialsFactory.create(accessKeyConfig().setType(AuthConstant.STS)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "unexpected"})
    void testRejectStaticCredentialsInOidcMode(String value) {
        Assertions.assertThrows(BaseException.class, () -> AlibabaCloudCredentialsFactory.create(oidcConfig().setAccessKeyId(value)));
        Assertions.assertThrows(BaseException.class, () -> AlibabaCloudCredentialsFactory.create(oidcConfig().setAccessKeySecret(value)));
        Assertions.assertThrows(BaseException.class, () -> AlibabaCloudCredentialsFactory.create(oidcConfig().setSecurityToken(value)));
        Assertions.assertThrows(BaseException.class, () -> AlibabaCloudCredentialsFactory.create(oidcConfig().setBearerToken(value)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " "})
    void testRejectExplicitBlankOidcConfiguration(String value) {
        Assertions.assertThrows(BaseException.class, () -> AlibabaCloudCredentialsFactory.create(oidcConfig().setRoleArn(value)));
        Assertions.assertThrows(BaseException.class, () -> AlibabaCloudCredentialsFactory.create(oidcConfig().setOidcProviderArn(value)));
        Assertions.assertThrows(BaseException.class, () -> AlibabaCloudCredentialsFactory.create(oidcConfig().setOidcTokenFilePath(value)));
    }

    @Test
    void testRejectConflictingIdentitySources() {
        Assertions.assertThrows(BaseException.class, () -> AlibabaCloudCredentialsFactory.create(accessKeyConfig().setRoleArn("ignored-role")));
        Assertions.assertThrows(BaseException.class, () -> AlibabaCloudCredentialsFactory.create(oidcConfig().setCredentialsUri("http://127.0.0.1:1")));
        Assertions.assertThrows(BaseException.class, () -> AlibabaCloudCredentialsFactory.create(oidcConfig().setRoleName("ignored-instance-role")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"proxy", "host", "enableIMDSv2"})
    void testRejectOptionsIgnoredByCurrentSdk(String field) {
        Config config = accessKeyConfig();
        switch (field) {
            case "proxy" -> config.setProxy("http://127.0.0.1:1");
            case "host" -> config.setHost("sts.example.invalid");
            case "enableIMDSv2" -> config.setEnableIMDSv2(true);
            default -> throw new AssertionError(field);
        }

        BaseException failure = Assertions.assertThrows(BaseException.class, () -> AlibabaCloudCredentialsFactory.create(config));

        Assertions.assertTrue(failure.getMessage().contains(field));
    }

    @Test
    void testSdkDefaultOptionsDoNotRejectValidConfiguration() {
        assertCredentials(AlibabaCloudCredentialsFactory.create(accessKeyConfig()).getCredential(), "test-id", "test-secret", null);
        assertCredentials(AlibabaCloudCredentialsFactory.create(accessKeyConfig().setMetadataTokenDuration(null)).getCredential(), "test-id", "test-secret", null);
    }

    @ParameterizedTest
    @ValueSource(strings = {AuthConstant.ACCESS_KEY, AuthConstant.STS, AuthConstant.ECS_RAM_ROLE, AuthConstant.CREDENTIALS_URI})
    void testRejectIgnoredAuthorizationOptions(String type) {
        Config config = switch (type) {
            case AuthConstant.ACCESS_KEY -> accessKeyConfig();
            case AuthConstant.STS -> accessKeyConfig().setType(type).setSecurityToken("source-token");
            case AuthConstant.ECS_RAM_ROLE -> new Config().setType(type).setRoleName("test-role");
            default -> new Config().setType(type).setCredentialsUri("http://127.0.0.1:1/credentials");
        };
        config.setPolicy(SESSION_POLICY);
        BaseException policyFailure = Assertions.assertThrows(BaseException.class, () -> AlibabaCloudCredentialsFactory.create(config));
        Assertions.assertTrue(policyFailure.getMessage().contains("policy"));

        config.setPolicy(null).setExternalId("test-external-id");
        BaseException externalIdFailure = Assertions.assertThrows(BaseException.class, () -> AlibabaCloudCredentialsFactory.create(config));
        Assertions.assertTrue(externalIdFailure.getMessage().contains("externalId"));
    }

    @Test
    void testRejectExternalIdInOidcMode() {
        BaseException failure = Assertions.assertThrows(BaseException.class,
                () -> AlibabaCloudCredentialsFactory.create(oidcConfig().setExternalId("test-external-id")));

        Assertions.assertTrue(failure.getMessage().contains("externalId"));
    }

    @Test
    void testAssumeRoleReceivesAuthorizationOptions() throws Exception {
        List<HttpRequest> requests = new ArrayList<>();
        try (MockedConstruction<CompatibleUrlConnClient> ignored = Mockito.mockConstruction(CompatibleUrlConnClient.class,
                (http, context) -> Mockito.when(http.syncInvoke(Mockito.any(HttpRequest.class))).thenAnswer(invocation -> {
                    requests.add(invocation.getArgument(0));
                    return stsResponse(1, Instant.now().plusSeconds(7200));
                }))) {
            Config config = accessKeyConfig().setType(AuthConstant.RAM_ROLE_ARN)
                    .setRoleArn("acs:ram::123456789:role/test-role")
                    .setPolicy(SESSION_POLICY).setExternalId("test-external-id");

            CredentialModel credential = AlibabaCloudCredentialsFactory.create(config).getCredential();

            assertCredentials(credential, "STS.test-1", "secret-1", "token-1");
            Assertions.assertEquals(1, requests.size());
            Assertions.assertEquals("AssumeRole", requests.getFirst().getUrlParameters().get("Action"));
            Assertions.assertEquals(SESSION_POLICY, requests.getFirst().getUrlParameters().get("Policy"));
            Assertions.assertEquals("test-external-id", requests.getFirst().getUrlParameters().get("ExternalId"));
        }
    }

    @Test
    void testOidcCreationDoesNotReadTokenOrCallSts() {
        Config config = oidcConfig();
        Map<String, Object> original = config.toMap();

        Client client = AlibabaCloudCredentialsFactory.create(config);

        Assertions.assertNotNull(client);
        Assertions.assertEquals(original, config.toMap());
        Assertions.assertFalse(Files.exists(Path.of(config.getOidcTokenFilePath())));
    }

    @Test
    void testOidcExplicitPathOverridesSdkPathWithoutMutatingConfig() throws Exception {
        String expectedPath = temporaryDirectory.resolve("explicit-token").toString();
        Config config = oidcConfig().setOidcTokenFilePath(expectedPath);
        Map<String, Object> original = config.toMap();
        List<String> readPaths = new ArrayList<>();
        try (MockedStatic<AuthUtils> auth = Mockito.mockStatic(AuthUtils.class, invocation -> {
            return switch (invocation.getMethod().getName()) {
                case "getEnvironmentRoleArn" -> "acs:ram::123456789:role/test-role";
                case "getEnvironmentOIDCProviderArn" -> "acs:ram::123456789:oidc-provider/test-provider";
                case "getEnvironmentOIDCTokenFilePath" -> "/ignored-sdk-token-file";
                case "getOIDCToken" -> {
                    readPaths.add(invocation.getArgument(0));
                    yield "path-test-token";
                }
                default -> invocation.callRealMethod();
            };
        }); MockedConstruction<CompatibleUrlConnClient> http = Mockito.mockConstruction(CompatibleUrlConnClient.class,
                (client, context) -> Mockito.when(client.syncInvoke(Mockito.any(HttpRequest.class)))
                        .thenAnswer(invocation -> stsResponse(1, Instant.now().plusSeconds(7200))))) {
            Client client = AlibabaCloudCredentialsFactory.create(config);
            Assertions.assertTrue(readPaths.isEmpty());
            assertCredentials(client.getCredential(), "STS.test-1", "secret-1", "token-1");
        }
        Assertions.assertEquals(List.of(expectedPath), readPaths);
        Assertions.assertEquals(original, config.toMap());
    }

    @Test
    void testOtherSessionProvidersAreCreatedWithoutHttpRequests() {
        try (MockedConstruction<CompatibleUrlConnClient> http = Mockito.mockConstruction(CompatibleUrlConnClient.class)) {
            Client assumedRole = AlibabaCloudCredentialsFactory.create(accessKeyConfig().setType(AuthConstant.RAM_ROLE_ARN)
                    .setRoleArn("acs:ram::123456789:role/test-role"));
            Client instanceRole = AlibabaCloudCredentialsFactory.create(new Config().setType(AuthConstant.ECS_RAM_ROLE).setRoleName("test-role"));
            Client uri = AlibabaCloudCredentialsFactory.create(new Config().setType(AuthConstant.CREDENTIALS_URI).setCredentialsUri("http://127.0.0.1:1/credentials"));

            Assertions.assertNotNull(assumedRole);
            Assertions.assertNotNull(instanceRole);
            Assertions.assertNotNull(uri);
            Assertions.assertTrue(http.constructed().isEmpty());
        }
    }

    /**
     * 只替换 STS 的 HTTP 通信，保留官方凭据获取、缓存和刷新逻辑。
     */
    @Test
    void testOidcRefreshReadsUpdatedTokenAndReturnsWholeCredentials() throws Exception {
        Path tokenFile = temporaryDirectory.resolve("oidc-token");
        Files.writeString(tokenFile, "oidc-first");
        List<HttpRequest> requests = new ArrayList<>();
        try (MockedConstruction<CompatibleUrlConnClient> ignored = Mockito.mockConstruction(CompatibleUrlConnClient.class,
                (http, context) -> Mockito.when(http.syncInvoke(Mockito.any(HttpRequest.class))).thenAnswer(invocation -> {
                    HttpRequest request = invocation.getArgument(0);
                    requests.add(request);
                    int sequence = requests.size();
                    Instant expiration = Instant.now().plusSeconds(sequence == 1 ? 120 : 7200);
                    return stsResponse(sequence, expiration);
                }))) {
            Config config = oidcConfig().setOidcTokenFilePath(tokenFile.toString()).setConnectTimeout(1234).setTimeout(5678).setPolicy(SESSION_POLICY);
            Client client = AlibabaCloudCredentialsFactory.create(config);
            Assertions.assertTrue(requests.isEmpty());

            CredentialModel first = client.getCredential();
            Files.writeString(tokenFile, "oidc-second");
            CredentialModel second = client.getCredential();
            CredentialModel cached = client.getCredential();

            assertCredentials(first, "STS.test-1", "secret-1", "token-1");
            assertCredentials(second, "STS.test-2", "secret-2", "token-2");
            assertCredentials(cached, "STS.test-2", "secret-2", "token-2");
            Assertions.assertEquals(2, requests.size());
            Assertions.assertTrue(URLDecoder.decode(requests.getFirst().getHttpContentString(), StandardCharsets.UTF_8).contains("OIDCToken=oidc-first"));
            Assertions.assertTrue(URLDecoder.decode(requests.getLast().getHttpContentString(), StandardCharsets.UTF_8).contains("OIDCToken=oidc-second"));
            Assertions.assertTrue(URLDecoder.decode(requests.getFirst().getHttpContentString(), StandardCharsets.UTF_8).contains("Policy=" + SESSION_POLICY));
            Assertions.assertTrue(requests.getFirst().getSysUrl().startsWith("https://sts.example.invalid"));
            Assertions.assertEquals(1234, requests.getFirst().getSysConnectTimeout());
            Assertions.assertEquals(5678, requests.getFirst().getSysReadTimeout());
        }
    }

    @Test
    void testOidcRefreshFailureIsReported() throws Exception {
        Path tokenFile = temporaryDirectory.resolve("oidc-token");
        Files.writeString(tokenFile, "oidc-token");
        List<HttpRequest> requests = new ArrayList<>();
        try (MockedConstruction<CompatibleUrlConnClient> ignored = Mockito.mockConstruction(CompatibleUrlConnClient.class,
                (http, context) -> Mockito.when(http.syncInvoke(Mockito.any(HttpRequest.class))).thenAnswer(invocation -> {
                    requests.add(invocation.getArgument(0));
                    if (requests.size() == 1) {
                        return stsResponse(1, Instant.now().plusSeconds(120));
                    }
                    HttpResponse response = new HttpResponse("https://sts.example.invalid");
                    response.setResponseCode(403);
                    response.setHttpContent("{}".getBytes(StandardCharsets.UTF_8), "UTF-8", FormatType.JSON);
                    return response;
                }))) {
            Client client = AlibabaCloudCredentialsFactory.create(oidcConfig().setOidcTokenFilePath(tokenFile.toString()));
            assertCredentials(client.getCredential(), "STS.test-1", "secret-1", "token-1");

            CredentialException failure = Assertions.assertThrows(CredentialException.class, client::getCredential);

            Assertions.assertTrue(failure.getMessage().contains("403"));
            Assertions.assertEquals(2, requests.size());
        }
    }

    @Test
    void testOidcEnvironmentEntry() throws Exception {
        Assertions.assertEquals(0, runEnvironmentProbe("environment"));
    }

    @Test
    void testMissingOidcEnvironmentDoesNotFallBackToAccessKey() throws Exception {
        Assertions.assertEquals(3, runEnvironmentProbe("missing"));
    }

    @Test
    void testSdkEnvironmentOverridesAreUsed() throws Exception {
        Assertions.assertEquals(0, runEnvironmentProbe("override"));
    }

    @Test
    void testExplicitEcsCreatesNoBackgroundThreads() throws Exception {
        Assertions.assertEquals(0, runEnvironmentProbe("ecs-lifecycle"));
    }

    private int runEnvironmentProbe(String mode) throws Exception {
        String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        ProcessBuilder builder = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", classpath, EnvironmentProbe.class.getName(), mode, temporaryDirectory.resolve("missing-token").toString());
        Map<String, String> environment = builder.environment();
        environment.keySet().removeIf(key -> key.startsWith("ALIBABA_CLOUD_"));
        environment.put("ALIBABA_CLOUD_ACCESS_KEY_ID", "environment-ak");
        environment.put("ALIBABA_CLOUD_ACCESS_KEY_SECRET", "environment-secret");
        if ("environment".equals(mode)) {
            environment.put("ALIBABA_CLOUD_ROLE_ARN", "acs:ram::123456789:role/test-role");
            environment.put("ALIBABA_CLOUD_OIDC_PROVIDER_ARN", "acs:ram::123456789:oidc-provider/test-provider");
            environment.put("ALIBABA_CLOUD_OIDC_TOKEN_FILE", temporaryDirectory.resolve("missing-token").toString());
        }
        Process process = builder.redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        try {
            Assertions.assertTrue(process.waitFor(15, TimeUnit.SECONDS), "环境变量入口检查超时");
            return process.exitValue();
        } finally {
            process.destroyForcibly();
        }
    }

    public static class EnvironmentProbe {

        public static void main(String[] args) {
            try {
                if ("override".equals(args[0])) {
                    AuthUtils.setEnvironmentRoleArn("acs:ram::123456789:role/test-role");
                    AuthUtils.setEnvironmentOIDCProviderArn("acs:ram::123456789:oidc-provider/test-provider");
                    AuthUtils.setEnvironmentOIDCTokenFilePath(args[1]);
                    AuthUtils.setEnvironmentCredentialsURI("http://127.0.0.1:1/credentials");
                    AlibabaCloudCredentialsFactory.create(new Config().setType(AuthConstant.CREDENTIALS_URI));
                    AlibabaCloudCredentialsFactory.create(new Config().setType(AuthConstant.RAM_ROLE_ARN)
                            .setAccessKeyId("source-id").setAccessKeySecret("source-secret"));
                }
                if ("ecs-lifecycle".equals(args[0])) {
                    AlibabaCloudCredentialsFactory.create(new Config().setType(AuthConstant.ACCESS_KEY)
                            .setAccessKeyId("test-id").setAccessKeySecret("test-secret"));
                    Set<Long> originalThreads = Thread.getAllStackTraces().keySet().stream().map(Thread::threadId).collect(Collectors.toSet());
                    for (int i = 0; i < 3; i++) {
                        AlibabaCloudCredentialsFactory.ecsRamRole("test-role");
                        AlibabaCloudCredentialsFactory.ecsRamRole();
                    }
                    boolean createdThread = Thread.getAllStackTraces().keySet().stream().anyMatch(thread -> !originalThreads.contains(thread.threadId()));
                    if (createdThread) {
                        System.exit(4);
                    }
                    return;
                }
                AlibabaCloudCredentialsFactory.oidcRoleArn();
            } catch (BaseException exception) {
                System.exit(2);
            } catch (IllegalArgumentException | CredentialException exception) {
                System.exit(3);
            }
        }
    }

    private Config accessKeyConfig() {
        return new Config().setType(AuthConstant.ACCESS_KEY).setAccessKeyId("test-id").setAccessKeySecret("test-secret");
    }

    private Config oidcConfig() {
        return new Config().setType(AuthConstant.OIDC_ROLE_ARN)
                .setRoleArn("acs:ram::123456789:role/test-role")
                .setOidcProviderArn("acs:ram::123456789:oidc-provider/test-provider")
                .setOidcTokenFilePath(temporaryDirectory.resolve("missing-token").toString())
                .setSTSEndpoint("sts.example.invalid");
    }

    private static void assertCredentials(CredentialModel credentials, String accessKeyId, String secret, String token) {
        Assertions.assertEquals(accessKeyId, credentials.getAccessKeyId());
        Assertions.assertEquals(secret, credentials.getAccessKeySecret());
        Assertions.assertEquals(token, credentials.getSecurityToken());
    }

    private HttpResponse stsResponse(int sequence, Instant expiration) {
        String json = """
                {"Credentials":{"AccessKeyId":"STS.test-%d","AccessKeySecret":"secret-%d",
                "SecurityToken":"token-%d","Expiration":"%s"}}
                """.formatted(sequence, sequence, sequence, expiration.truncatedTo(ChronoUnit.SECONDS).toString());
        HttpResponse response = new HttpResponse("https://sts.example.invalid");
        response.setResponseCode(200);
        response.setHttpContent(json.getBytes(StandardCharsets.UTF_8), "UTF-8", FormatType.JSON);
        return response;
    }
}
