package com.wind.integration.alibaba;

import com.aliyun.credentials.http.CompatibleUrlConnClient;
import com.aliyun.credentials.http.FormatType;
import com.aliyun.credentials.http.HttpRequest;
import com.aliyun.credentials.http.HttpResponse;
import com.aliyun.credentials.models.Config;
import com.aliyun.credentials.utils.AuthUtils;
import com.aliyun.tea.Tea;
import com.aliyun.tea.TeaRequest;
import com.aliyun.tea.TeaResponse;
import com.aliyun.credentials.utils.AuthConstant;
import com.wind.common.exception.BaseException;
import com.wind.integration.kms.WindKmsClientCredentialsDecryptor;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

class AlibabaCloudKmsCredentialConfigurationTests {

    private static final Path CREDENTIAL_FILE = Path.of("/etc/secrets/alibaba_cloud_credential");

    @TempDir
    Path directory;

    @ParameterizedTest
    @ValueSource(strings = {AuthConstant.ACCESS_KEY, AuthConstant.STS, AuthConstant.RAM_ROLE_ARN, AuthConstant.OIDC_ROLE_ARN, AuthConstant.ECS_RAM_ROLE, AuthConstant.CREDENTIALS_URI})
    void testConfiguredCredentialSourcesReachKmsRequests(String type) throws Exception {
        Path tokenFile = directory.resolve("oidc-token");
        Files.writeString(tokenFile, "test-oidc");
        Config config = credentialConfig(type, tokenFile);
        Map<String, Object> original = config.toMap();
        List<TeaRequest> requests = new ArrayList<>();
        try (MockedConstruction<CompatibleUrlConnClient> ignored = Mockito.mockConstruction(CompatibleUrlConnClient.class,
                (http, context) -> Mockito.when(http.syncInvoke(Mockito.any(HttpRequest.class)))
                        .thenAnswer(invocation -> credentialResponse(invocation.getArgument(0))));
             MockedStatic<Tea> transport = interceptKms(requests)) {
            AlibabaCloudKmsCryptoClient client = AlibabaCloudKmsCryptoClient.withCredentialConfig(config,
                    new com.aliyun.teaopenapi.models.Config().setEndpoint("kms.example.invalid"));

            Assertions.assertEquals("ciphertext", client.encrypt("key-id", "plaintext"));
            AlibabaCloudKmsCryptoClient selected = AlibabaCloudKmsCryptoClient.defaults(config,
                    new com.aliyun.teaopenapi.models.Config().setEndpoint("kms.example.invalid"));
            Assertions.assertEquals("ciphertext", selected.encrypt("key-id", "plaintext"));
        }

        Assertions.assertEquals(original, config.toMap());
        Assertions.assertEquals(2, requests.size());
        String accessKey = AuthConstant.ACCESS_KEY.equals(type) ? "source-id" : "STS.result";
        for (TeaRequest request : requests) {
            Assertions.assertTrue(request.headers.get("Authorization").contains(accessKey));
            if (AuthConstant.ACCESS_KEY.equals(type)) {
                Assertions.assertNull(request.headers.get("x-acs-security-token"));
            } else {
                Assertions.assertEquals("result-token", request.headers.get("x-acs-security-token"));
            }
        }
    }

    @Test
    void testSingleConfigurationOverloadUsesExistingEndpoint() throws Exception {
        String originalEndpoint = System.getProperty("ALIBABA_CLOUD_K_ENDPOINT");
        String originalInstance = System.getProperty("ALIBABA_CLOUD_K_ID");
        List<TeaRequest> requests = new ArrayList<>();
        try (MockedStatic<Tea> ignored = interceptKms(requests)) {
            System.setProperty("ALIBABA_CLOUD_K_ID", "");
            System.setProperty("ALIBABA_CLOUD_K_ENDPOINT", "configured.example.invalid");
            AlibabaCloudKmsCryptoClient client = AlibabaCloudKmsCryptoClient.withCredentialConfig(credentialConfig(AuthConstant.ACCESS_KEY, directory.resolve("missing")));

            Assertions.assertEquals("ciphertext", client.encrypt("key-id", "plaintext"));
            Assertions.assertEquals("ciphertext", AlibabaCloudKmsCryptoClient.defaults(credentialConfig(AuthConstant.ACCESS_KEY, directory.resolve("missing")))
                    .encrypt("key-id", "plaintext"));
            Assertions.assertEquals("configured.example.invalid", requests.getFirst().headers.get("host"));
            Assertions.assertEquals("configured.example.invalid", requests.getLast().headers.get("host"));
        } finally {
            restoreProperty("ALIBABA_CLOUD_K_ENDPOINT", originalEndpoint);
            restoreProperty("ALIBABA_CLOUD_K_ID", originalInstance);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void testLegacyOidcKeepsEffectiveOptionsAndIgnoresUnrelatedFields(boolean useSdkDefaults) throws Exception {
        Path tokenFile = directory.resolve("legacy-oidc-token");
        Files.writeString(tokenFile, "legacy-oidc-token");
        Config config = credentialConfig(AuthConstant.OIDC_ROLE_ARN, tokenFile).setType("default")
                .setRoleSessionName("legacy-session").setPolicy("{\"Version\":\"1\",\"Statement\":[]}")
                .setAccessKeyId("ignored-ak").setAccessKeySecret("ignored-secret").setSecurityToken("ignored-token")
                .setExternalId("ignored-external-id").setRoleName("ignored-ecs-role").setCredentialsUri("ignored-uri")
                .setBearerToken("ignored-bearer").setProxy("ignored-proxy").setHost("ignored-host").setEnableIMDSv2(true)
                .setMetadataTokenDuration(60);
        if (useSdkDefaults) {
            config.setRoleSessionExpiration(null);
        } else {
            config.setRoleSessionExpiration(1800).setConnectTimeout(1234).setTimeout(5678);
        }
        Map<String, Object> original = config.toMap();
        String endpoint = System.getProperty("ALIBABA_CLOUD_K_ENDPOINT");
        List<HttpRequest> stsRequests = new ArrayList<>();
        List<TeaRequest> kmsRequests = new ArrayList<>();
        try (MockedConstruction<CompatibleUrlConnClient> ignored = Mockito.mockConstruction(CompatibleUrlConnClient.class,
                (http, context) -> Mockito.when(http.syncInvoke(Mockito.any(HttpRequest.class))).thenAnswer(invocation -> {
                    HttpRequest request = invocation.getArgument(0);
                    stsRequests.add(request);
                    return credentialResponse(request);
                })); MockedStatic<Tea> transport = interceptKms(kmsRequests)) {
            System.setProperty("ALIBABA_CLOUD_K_ENDPOINT", "kms.example.invalid");
            Assertions.assertEquals("ciphertext", AlibabaCloudKmsCryptoClient.withOIDCRoleArn(config).encrypt("key-id", "plaintext"));
            Assertions.assertEquals(original, config.toMap());
            Assertions.assertEquals(1, stsRequests.size());
            HttpRequest request = stsRequests.getFirst();
            String body = URLDecoder.decode(request.getHttpContentString(), StandardCharsets.UTF_8);
            Assertions.assertEquals("AssumeRoleWithOIDC", request.getUrlParameters().get("Action"));
            Assertions.assertTrue(request.getSysUrl().startsWith("https://sts.example.invalid"));
            Assertions.assertTrue(body.contains("RoleArn=acs:ram::123456789:role/test-role"));
            Assertions.assertTrue(body.contains("OIDCProviderArn=acs:ram::123456789:oidc-provider/test"));
            Assertions.assertTrue(body.contains("OIDCToken=legacy-oidc-token"));
            Assertions.assertTrue(body.contains("RoleSessionName=legacy-session"));
            Assertions.assertTrue(body.contains("Policy={\"Version\":\"1\",\"Statement\":[]}"));
            Assertions.assertEquals("result-token", kmsRequests.getFirst().headers.get("x-acs-security-token"));
            if (useSdkDefaults) {
                Assertions.assertTrue(body.contains("DurationSeconds=3600"));
                Assertions.assertEquals(5000, request.getSysConnectTimeout());
                Assertions.assertEquals(10000, request.getSysReadTimeout());
            } else {
                Assertions.assertTrue(body.contains("DurationSeconds=1800"));
                Assertions.assertEquals(1234, request.getSysConnectTimeout());
                Assertions.assertEquals(5678, request.getSysReadTimeout());
            }
            config.setType(AuthConstant.OIDC_ROLE_ARN);
            Assertions.assertThrows(BaseException.class, () -> AlibabaCloudKmsCryptoClient.defaults(config));
        } finally {
            restoreProperty("ALIBABA_CLOUD_K_ENDPOINT", endpoint);
        }
    }

    @Test
    void testExplicitConfigurationDoesNotBecomeAutomaticSelection() {
        try (MockedStatic<Files> files = interceptCredentialFile()) {
            files.when(() -> Files.readString(CREDENTIAL_FILE)).thenThrow(new AssertionError("错误显式配置不得回退文件身份"));
            Assertions.assertThrows(BaseException.class, () -> AlibabaCloudKmsCryptoClient.withCredentialConfig(null));
            Assertions.assertThrows(BaseException.class, () -> AlibabaCloudKmsCryptoClient.withCredentialConfig(null, new com.aliyun.teaopenapi.models.Config()));
            Assertions.assertThrows(BaseException.class, () -> AlibabaCloudKmsCryptoClient.defaults(new Config()));
            Assertions.assertThrows(BaseException.class, () -> AlibabaCloudKmsCryptoClient.defaults(new Config().setType("")));
            Assertions.assertThrows(BaseException.class, () -> AlibabaCloudKmsCryptoClient.defaults(null, null));
        }
    }

    @Test
    void testCredentialFileUsesOriginalDecryptorSpiAndSignsWithAk() throws Exception {
        String endpoint = System.getProperty("ALIBABA_CLOUD_K_ENDPOINT");
        List<TeaRequest> requests = new ArrayList<>();
        try (MockedStatic<Files> files = interceptCredentialFile(); MockedStatic<Tea> ignored = interceptKms(requests)) {
            System.setProperty("ALIBABA_CLOUD_K_ENDPOINT", "kms.example.invalid");
            AlibabaCloudKmsCryptoClient kms = AlibabaCloudKmsCryptoClient.withCredentialFile();

            Assertions.assertEquals("ciphertext", kms.encrypt("key-id", "plaintext"));
            Assertions.assertTrue(requests.getFirst().headers.get("Authorization").contains("file-id"));
            Assertions.assertNull(requests.getFirst().headers.get("x-acs-security-token"));
        } finally {
            restoreProperty("ALIBABA_CLOUD_K_ENDPOINT", endpoint);
        }
    }

    @Test
    void testMissingDecryptorSpiFailsBeforeReadingCredentialFile() {
        Thread thread = Thread.currentThread();
        ClassLoader original = thread.getContextClassLoader();
        ClassLoader withoutDecryptor = new ClassLoader(original) {
            @Override
            public Enumeration<URL> getResources(String name) throws IOException {
                if (name.equals("META-INF/services/" + WindKmsClientCredentialsDecryptor.class.getName())) {
                    return Collections.emptyEnumeration();
                }
                return super.getResources(name);
            }
        };
        try (MockedStatic<Files> files = interceptCredentialFile()) {
            files.when(() -> Files.readString(CREDENTIAL_FILE)).thenThrow(new AssertionError("缺少解密器时不得读取凭据文件"));
            thread.setContextClassLoader(withoutDecryptor);

            IllegalStateException failure = Assertions.assertThrows(IllegalStateException.class, AlibabaCloudKmsCryptoClient::withCredentialFile);

            Assertions.assertTrue(failure.getMessage().contains(WindKmsClientCredentialsDecryptor.class.getName()));
        } finally {
            thread.setContextClassLoader(original);
        }
    }

    private static Stream<Arguments> defaultSourceCases() {
        String role = "acs:ram::123456789:role/test-role";
        String provider = "acs:ram::123456789:oidc-provider/test";
        return Stream.of(
                Arguments.of("file", null, null, null, ""),
                Arguments.of("environment-ak", null, null, null, ""),
                Arguments.of("environment-sts", null, null, null, ""),
                Arguments.of("role-only", role, null, null, "ALIBABA_CLOUD_OIDC_PROVIDER_ARN"),
                Arguments.of("provider-only", null, provider, null, "ALIBABA_CLOUD_ROLE_ARN"),
                Arguments.of("token-only", null, null, "token", ""),
                Arguments.of("oidc-without-token-variable", role, provider, null, ""),
                Arguments.of("missing-provider", role, null, "token", "ALIBABA_CLOUD_OIDC_PROVIDER_ARN"),
                Arguments.of("missing-role", null, provider, "token", "ALIBABA_CLOUD_ROLE_ARN"),
                Arguments.of("blank-role", " ", provider, "token", "ALIBABA_CLOUD_ROLE_ARN"),
                Arguments.of("blank-provider", role, "", "token", "ALIBABA_CLOUD_OIDC_PROVIDER_ARN"),
                Arguments.of("oidc-blank-token-variable", role, provider, "\t", "ALIBABA_CLOUD_OIDC_TOKEN_FILE"),
                Arguments.of("oidc-empty-token-variable", role, provider, "", "ALIBABA_CLOUD_OIDC_TOKEN_FILE"),
                Arguments.of("empty-all", "", "", "", "ALIBABA_CLOUD_ROLE_ARN"),
                Arguments.of("oidc", role, provider, "token", ""),
                Arguments.of("oidc-sdk-override", role, provider, "token", ""),
                Arguments.of("explicit-over-oidc", role, provider, "token", ""),
                Arguments.of("explicit-over-partial", role, null, null, ""),
                Arguments.of("explicit-over-blank", "", "", "", ""));
    }

    @ParameterizedTest
    @MethodSource("defaultSourceCases")
    void testDefaultSourceSelectionInIsolatedEnvironment(String mode, String role, String provider, String token, String invalidVariable) throws Exception {
        String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        ManagementFactory.getRuntimeMXBean().getInputArguments().stream()
                .filter(argument -> argument.startsWith("-javaagent:") && argument.contains("mockito-core"))
                .forEach(command::add);
        command.addAll(List.of("-cp", classpath, DefaultSourceProbe.class.getName(), mode, invalidVariable));
        ProcessBuilder builder = new ProcessBuilder(command);
        Map<String, String> environment = builder.environment();
        environment.keySet().removeIf(key -> key.startsWith("ALIBABA_CLOUD_"));
        environment.put("ALIBABA_CLOUD_K_ENDPOINT", "kms.example.invalid");
        if (role != null) {
            environment.put("ALIBABA_CLOUD_ROLE_ARN", role);
        }
        if (provider != null) {
            environment.put("ALIBABA_CLOUD_OIDC_PROVIDER_ARN", provider);
        }
        if (token != null) {
            String tokenValue = token;
            if ("token".equals(token)) {
                tokenValue = directory.resolve("environment-oidc-token").toString();
            }
            environment.put("ALIBABA_CLOUD_OIDC_TOKEN_FILE", tokenValue);
        }
        environment.put("ALIBABA_CLOUD_ACCESS_KEY_ID", "environment-ak");
        environment.put("ALIBABA_CLOUD_ACCESS_KEY_SECRET", "environment-secret");
        if ("environment-sts".equals(mode)) {
            environment.put("ALIBABA_CLOUD_SECURITY_TOKEN", "environment-token");
        }
        Path output = directory.resolve("probe-" + mode + ".log");
        Process process = builder.redirectErrorStream(true).redirectOutput(output.toFile()).start();
        try {
            Assertions.assertTrue(process.waitFor(30, TimeUnit.SECONDS), "默认来源分支验证超时");
            Assertions.assertEquals(0, process.exitValue(), Files.readString(output));
        } finally {
            process.destroyForcibly();
        }
    }

    public static class DefaultSourceProbe {

        public static void main(String[] args) throws Exception {
            String mode = args[0];
            String invalidVariable = args[1];
            try (MockedStatic<Files> files = interceptCredentialFile(); MockedStatic<AuthUtils> tokenReader = interceptOidcToken()) {
                if (!invalidVariable.isEmpty() || mode.startsWith("oidc") || mode.startsWith("explicit-")) {
                    files.when(() -> Files.readString(CREDENTIAL_FILE)).thenThrow(new AssertionError("已配置策略不得回退凭据文件"));
                }
                if (!invalidVariable.isEmpty()) {
                    BaseException failure = Assertions.assertThrows(BaseException.class, AlibabaCloudKmsCryptoClient::defaults);
                    Assertions.assertTrue(failure.getMessage().contains(invalidVariable));
                    Assertions.assertThrows(BaseException.class, () -> AlibabaCloudKmsCryptoClient.defaults(null));
                } else if (mode.startsWith("oidc")) {
                    Assertions.assertNotNull(AlibabaCloudKmsCryptoClient.defaults());
                    Assertions.assertNotNull(AlibabaCloudKmsCryptoClient.defaults(null));
                    Assertions.assertNotNull(AlibabaCloudKmsCryptoClient.withOIDCRoleArn());
                    if ("oidc-sdk-override".equals(mode)) {
                        AuthUtils.setEnvironmentRoleArn("acs:ram::123456789:role/other-role");
                        AuthUtils.setEnvironmentOIDCProviderArn("acs:ram::123456789:oidc-provider/other-provider");
                        AuthUtils.setEnvironmentOIDCTokenFilePath("/missing-override-token");
                    }
                    List<TeaRequest> kmsRequests = new ArrayList<>();
                    List<HttpRequest> stsRequests = new ArrayList<>();
                    try (MockedConstruction<CompatibleUrlConnClient> http = Mockito.mockConstruction(CompatibleUrlConnClient.class,
                            (client, context) -> Mockito.when(client.syncInvoke(Mockito.any(HttpRequest.class))).thenAnswer(invocation -> {
                                HttpRequest request = invocation.getArgument(0);
                                stsRequests.add(request);
                                return oidcResponse(request, 200);
                            })); MockedStatic<Tea> ignored = interceptKms(kmsRequests)) {
                        Assertions.assertEquals("ciphertext", AlibabaCloudKmsCryptoClient.defaults().encrypt("key-id", "plaintext"));
                        String body = URLDecoder.decode(stsRequests.getFirst().getHttpContentString(), StandardCharsets.UTF_8);
                        Assertions.assertTrue(body.contains("RoleArn=acs:ram::123456789:role/test-role"));
                        Assertions.assertTrue(body.contains("OIDCProviderArn=acs:ram::123456789:oidc-provider/test"));
                        Assertions.assertTrue(body.contains("OIDCToken=route-oidc-token"));
                        Assertions.assertEquals("route-token", kmsRequests.getFirst().headers.get("x-acs-security-token"));
                        Assertions.assertEquals("ciphertext", AlibabaCloudKmsCryptoClient.withOIDCRoleArn().encrypt("key-id", "plaintext"));
                        String explicitBody = URLDecoder.decode(stsRequests.getLast().getHttpContentString(), StandardCharsets.UTF_8);
                        Assertions.assertTrue(explicitBody.contains("OIDCToken=route-oidc-token"));
                    }
                    try (MockedConstruction<CompatibleUrlConnClient> http = Mockito.mockConstruction(CompatibleUrlConnClient.class,
                            (client, context) -> Mockito.when(client.syncInvoke(Mockito.any(HttpRequest.class)))
                                    .thenAnswer(invocation -> oidcResponse(invocation.getArgument(0), 403)))) {
                        Assertions.assertThrows(com.wind.integration.kms.WindKmsException.class,
                                () -> AlibabaCloudKmsCryptoClient.defaults().encrypt("key-id", "plaintext"));
                    }
                    files.when(() -> Files.readString(CREDENTIAL_FILE)).thenReturn(" encrypted-test ");
                    List<TeaRequest> requests = new ArrayList<>();
                    try (MockedStatic<Tea> ignored = interceptKms(requests)) {
                        Assertions.assertEquals("ciphertext", AlibabaCloudKmsCryptoClient.withCredentialFile().encrypt("key-id", "plaintext"));
                        Assertions.assertTrue(requests.getFirst().headers.get("Authorization").contains("file-id"));
                    }
                } else if (mode.startsWith("explicit-")) {
                    Config explicit = new Config().setType(AuthConstant.STS).setAccessKeyId("STS.explicit")
                            .setAccessKeySecret("explicit-secret").setSecurityToken("explicit-token");
                    List<TeaRequest> requests = new ArrayList<>();
                    try (MockedStatic<Tea> ignored = interceptKms(requests)) {
                        Assertions.assertEquals("ciphertext", AlibabaCloudKmsCryptoClient.defaults(explicit).encrypt("key-id", "plaintext"));
                        Assertions.assertTrue(requests.getFirst().headers.get("Authorization").contains("STS.explicit"));
                        Assertions.assertEquals("explicit-token", requests.getFirst().headers.get("x-acs-security-token"));
                    }
                } else {
                    List<TeaRequest> requests = new ArrayList<>();
                    try (MockedStatic<Tea> ignored = interceptKms(requests)) {
                        Assertions.assertEquals("ciphertext", AlibabaCloudKmsCryptoClient.defaults().encrypt("key-id", "plaintext"));
                        Assertions.assertTrue(requests.getFirst().headers.get("Authorization").contains("file-id"));
                        Assertions.assertEquals("ciphertext", AlibabaCloudKmsCryptoClient.defaults(null,
                                new com.aliyun.teaopenapi.models.Config().setEndpoint("custom.example.invalid")).encrypt("key-id", "plaintext"));
                        Assertions.assertTrue(requests.getLast().headers.get("Authorization").contains("file-id"));
                        Assertions.assertEquals("custom.example.invalid", requests.getLast().headers.get("host"));
                    }
                }
            }
        }

        private static HttpResponse oidcResponse(HttpRequest request, int status) {
            String body = """
                    {"Credentials":{"AccessKeyId":"STS.route","AccessKeySecret":"route-secret",
                    "SecurityToken":"route-token","Expiration":"%s"}}
                    """.formatted(Instant.now().plusSeconds(7200).truncatedTo(ChronoUnit.SECONDS));
            HttpResponse response = new HttpResponse(request.getSysUrl());
            response.setResponseCode(status);
            response.setHttpContent(body.getBytes(StandardCharsets.UTF_8), "UTF-8", FormatType.JSON);
            return response;
        }

        /**
         * SDK 通过 FileInputStream 读 Token，在读取边界验证环境路径或缺省路径，避免访问真实挂载文件。
         */
        private static MockedStatic<AuthUtils> interceptOidcToken() {
            String environmentPath = System.getenv("ALIBABA_CLOUD_OIDC_TOKEN_FILE");
            String expectedPath = environmentPath == null ? CREDENTIAL_FILE.toString() : environmentPath;
            return Mockito.mockStatic(AuthUtils.class, invocation -> {
                if ("getOIDCToken".equals(invocation.getMethod().getName())) {
                    Assertions.assertEquals(expectedPath, invocation.getArgument(0));
                    return "route-oidc-token";
                }
                return invocation.callRealMethod();
            });
        }
    }

    /**
     * 仅用于测试 KMS 对既有解密 SPI 的真实发现与调用。
     */
    public static class TestCredentialsDecryptor implements WindKmsClientCredentialsDecryptor {

        @Override
        public String decrypt(String encryptedText) {
            Assertions.assertEquals("encrypted-test", encryptedText);
            return "file-id\n\nfile-secret\nignored-extra-line";
        }
    }

    private static MockedStatic<Files> interceptCredentialFile() {
        return Mockito.mockStatic(Files.class, invocation -> {
            if (invocation.getArguments().length > 0 && CREDENTIAL_FILE.equals(invocation.getArgument(0))) {
                if ("readString".equals(invocation.getMethod().getName())) {
                    return " encrypted-test ";
                }
            }
            return invocation.callRealMethod();
        });
    }

    private Config credentialConfig(String type, Path tokenFile) {
        Config config = new Config().setType(type);
        switch (type) {
            case AuthConstant.ACCESS_KEY, AuthConstant.RAM_ROLE_ARN -> config.setAccessKeyId("source-id").setAccessKeySecret("source-secret");
            case AuthConstant.STS -> config.setAccessKeyId("STS.result").setAccessKeySecret("result-secret").setSecurityToken("result-token");
            case AuthConstant.ECS_RAM_ROLE -> config.setRoleName("test-role").setDisableIMDSv1(true);
            case AuthConstant.CREDENTIALS_URI -> config.setCredentialsUri("https://credentials.example.invalid");
            default -> config.setOidcProviderArn("acs:ram::123456789:oidc-provider/test").setOidcTokenFilePath(tokenFile.toString());
        }
        if (AuthConstant.OIDC_ROLE_ARN.equals(type) || AuthConstant.RAM_ROLE_ARN.equals(type)) {
            config.setRoleArn("acs:ram::123456789:role/test-role").setSTSEndpoint("sts.example.invalid");
        }
        return config;
    }

    private HttpResponse credentialResponse(HttpRequest request) {
        String json;
        if (request.getSysUrl().contains("/latest/api/token")) {
            json = "metadata-token";
        } else {
            String credentials = """
                    {"Code":"Success","AccessKeyId":"STS.result","AccessKeySecret":"result-secret",
                     "SecurityToken":"result-token","Expiration":"%s"}
                    """.formatted(Instant.now().plusSeconds(7200).truncatedTo(ChronoUnit.SECONDS));
            json = request.getSysUrl().contains("sts.example.invalid") ? "{\"Credentials\":" + credentials + "}" : credentials;
        }
        HttpResponse response = new HttpResponse(request.getSysUrl());
        response.setResponseCode(200);
        response.setHttpContent(json.getBytes(StandardCharsets.UTF_8), "UTF-8", FormatType.JSON);
        return response;
    }

    private static MockedStatic<Tea> interceptKms(List<TeaRequest> requests) {
        return Mockito.mockStatic(Tea.class, invocation -> {
            if (!"doAction".equals(invocation.getMethod().getName())) {
                return invocation.callRealMethod();
            }
            requests.add(invocation.getArgument(0));
            TeaResponse response = new TeaResponse();
            response.statusCode = 200;
            response.headers.put("content-type", "application/json");
            response.body = new ByteArrayInputStream("{\"RequestId\":\"test-request\",\"CiphertextBlob\":\"ciphertext\"}".getBytes(StandardCharsets.UTF_8));
            return response;
        });
    }

    private static void restoreProperty(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }
}
