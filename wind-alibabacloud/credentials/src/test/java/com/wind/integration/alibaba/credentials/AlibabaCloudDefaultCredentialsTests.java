package com.wind.integration.alibaba.credentials;

import com.aliyun.credentials.Client;
import com.aliyun.credentials.exception.CredentialException;
import com.aliyun.credentials.http.CompatibleUrlConnClient;
import com.aliyun.credentials.http.FormatType;
import com.aliyun.credentials.http.HttpRequest;
import com.aliyun.credentials.http.HttpResponse;
import com.aliyun.credentials.models.CredentialModel;
import com.aliyun.credentials.models.Config;
import com.aliyun.credentials.utils.AuthConstant;
import com.aliyun.credentials.utils.AuthUtils;
import com.wind.common.exception.BaseException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

class AlibabaCloudDefaultCredentialsTests {

    @TempDir
    Path directory;

    private static Stream<Arguments> environmentCases() {
        return Stream.of(
                Arguments.of("none", null, null, null, "none", "未配置"),
                Arguments.of("ak", "environment-id", "environment-secret", null, "none", ""),
                Arguments.of("sts", "environment-id", "environment-secret", "environment-token", "none", ""),
                Arguments.of("ak-only", "environment-id", null, null, "none", "ALIBABA_CLOUD_ACCESS_KEY_SECRET"),
                Arguments.of("sk-only", null, "environment-secret", null, "none", "ALIBABA_CLOUD_ACCESS_KEY_ID"),
                Arguments.of("token-only", null, null, "environment-token", "none", "ALIBABA_CLOUD_ACCESS_KEY_ID"),
                Arguments.of("empty-ak", "", "environment-secret", null, "none", "ALIBABA_CLOUD_ACCESS_KEY_ID"),
                Arguments.of("blank-sk", "environment-id", " ", null, "none", "ALIBABA_CLOUD_ACCESS_KEY_SECRET"),
                Arguments.of("empty-token", "environment-id", "environment-secret", "", "none", "ALIBABA_CLOUD_SECURITY_TOKEN"),
                Arguments.of("blank-token", "environment-id", "environment-secret", "\t", "none", "ALIBABA_CLOUD_SECURITY_TOKEN"),
                Arguments.of("oidc", null, null, null, "complete", ""),
                Arguments.of("oidc-properties", null, null, null, "complete", ""),
                Arguments.of("oidc-properties-default", null, null, null, "no-path", ""),
                Arguments.of("oidc-shortcut", null, null, null, "complete", ""),
                Arguments.of("oidc-explicit-path", null, null, null, "complete", ""),
                Arguments.of("oidc-sdk-override", null, null, null, "complete", ""),
                Arguments.of("oidc-missing-path", null, null, null, "complete", ""),
                Arguments.of("oidc-empty-path", null, null, null, "complete", "ALIBABA_CLOUD_OIDC_TOKEN_FILE"),
                Arguments.of("oidc-blank-path", null, null, null, "complete", "ALIBABA_CLOUD_OIDC_TOKEN_FILE"),
                Arguments.of("oidc-without-token-path", null, null, null, "no-path", ""),
                Arguments.of("token-path-only", "environment-id", "environment-secret", null, "path-only", ""),
                Arguments.of("oidc-before-sts", "environment-id", "environment-secret", "environment-token", "complete", ""),
                Arguments.of("oidc-before-invalid-ak", "environment-id", null, "", "complete", ""),
                Arguments.of("partial-oidc-with-ak", "environment-id", "environment-secret", null, "partial", "ALIBABA_CLOUD_OIDC_PROVIDER_ARN"),
                Arguments.of("blank-oidc-with-ak", "environment-id", "environment-secret", null, "blank", "ALIBABA_CLOUD_ROLE_ARN"),
                Arguments.of("oidc-exchange-failure", "environment-id", "environment-secret", "environment-token", "complete", ""));
    }

    @ParameterizedTest
    @MethodSource("environmentCases")
    void testRecommendedEnvironmentStrategy(String name, String accessKey, String secret, String token, String oidc, String expectedError) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        ManagementFactory.getRuntimeMXBean().getInputArguments().stream()
                .filter(argument -> argument.startsWith("-javaagent:") && argument.contains("mockito-core"))
                .forEach(command::add);
        command.addAll(List.of("-Dalibabacloud.accessKeyId=system-id", "-Dalibabacloud.accessKeySecret=system-secret",
                "-cp", System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")),
                EnvironmentProbe.class.getName(), name, expectedError, directory.resolve("explicit-token-file").toString()));
        ProcessBuilder builder = new ProcessBuilder(command);
        Map<String, String> environment = builder.environment();
        environment.keySet().removeIf(key -> key.startsWith("ALIBABA_CLOUD_"));
        environment.put("ALIBABA_CLOUD_ECS_METADATA_DISABLED", "true");
        if (accessKey != null) {
            environment.put("ALIBABA_CLOUD_ACCESS_KEY_ID", accessKey);
        }
        if (secret != null) {
            environment.put("ALIBABA_CLOUD_ACCESS_KEY_SECRET", secret);
        }
        if (token != null) {
            environment.put("ALIBABA_CLOUD_SECURITY_TOKEN", token);
        }
        if (!"none".equals(oidc) && !"path-only".equals(oidc)) {
            environment.put("ALIBABA_CLOUD_ROLE_ARN", "acs:ram::123456789:role/test-role");
        }
        if ("complete".equals(oidc) || "blank".equals(oidc) || "no-path".equals(oidc)) {
            environment.put("ALIBABA_CLOUD_OIDC_PROVIDER_ARN", "acs:ram::123456789:oidc-provider/test-provider");
        }
        if ("complete".equals(oidc) || "path-only".equals(oidc)) {
            Path tokenFile = directory.resolve("ack-token-file");
            if (!"oidc-missing-path".equals(name)) {
                Files.writeString(tokenFile, "test-oidc-token");
            }
            environment.put("ALIBABA_CLOUD_OIDC_TOKEN_FILE", tokenFile.toString());
        }
        if ("oidc-empty-path".equals(name)) {
            environment.put("ALIBABA_CLOUD_OIDC_TOKEN_FILE", "");
        }
        if ("oidc-blank-path".equals(name)) {
            environment.put("ALIBABA_CLOUD_OIDC_TOKEN_FILE", "\t");
        }
        if ("oidc-explicit-path".equals(name)) {
            Files.writeString(directory.resolve("explicit-token-file"), "explicit-oidc-token");
        }
        if ("blank".equals(oidc)) {
            environment.put("ALIBABA_CLOUD_ROLE_ARN", " ");
        }
        Path output = directory.resolve(name + ".log");
        Process process = builder.redirectErrorStream(true).redirectOutput(output.toFile()).start();
        try {
            Assertions.assertTrue(process.waitFor(30, TimeUnit.SECONDS), "推荐凭据策略检查超时");
            Assertions.assertEquals(0, process.exitValue(), Files.readString(output));
        } finally {
            process.destroyForcibly();
        }
    }

    public static class EnvironmentProbe {

        public static void main(String[] args) throws Exception {
            String name = args[0];
            String expectedError = args[1];
            String environmentPath = System.getenv("ALIBABA_CLOUD_OIDC_TOKEN_FILE");
            String expectedPath = environmentPath == null ? "/etc/secrets/alibaba_cloud_credential" : environmentPath;
            if ("oidc-explicit-path".equals(name)) {
                expectedPath = args[2];
            }
            String selectedPath = expectedPath;
            if ("oidc-sdk-override".equals(name)) {
                AuthUtils.setEnvironmentOIDCTokenFilePath("/ignored-sdk-token-file");
            }
            List<HttpRequest> requests = new ArrayList<>();
            try (MockedStatic<AuthUtils> auth = Mockito.mockStatic(AuthUtils.class, invocation -> {
                if ("getOIDCToken".equals(invocation.getMethod().getName())) {
                    Assertions.assertEquals(selectedPath, invocation.getArgument(0));
                    if (environmentPath == null) {
                        return "test-oidc-token";
                    }
                }
                return invocation.callRealMethod();
            }); MockedConstruction<CompatibleUrlConnClient> ignored = Mockito.mockConstruction(CompatibleUrlConnClient.class,
                    (http, context) -> Mockito.when(http.syncInvoke(Mockito.any(HttpRequest.class))).thenAnswer(invocation -> {
                        HttpRequest request = invocation.getArgument(0);
                        requests.add(request);
                        HttpResponse response = new HttpResponse(request.getSysUrl());
                        response.setResponseCode("oidc-exchange-failure".equals(name) ? 403 : 200);
                        String body = """
                                {"Credentials":{"AccessKeyId":"STS.oidc","AccessKeySecret":"oidc-secret",
                                "SecurityToken":"oidc-token","Expiration":"%s"}}
                                """.formatted(Instant.now().plusSeconds(7200).truncatedTo(ChronoUnit.SECONDS));
                        response.setHttpContent(body.getBytes(StandardCharsets.UTF_8), "UTF-8", FormatType.JSON);
                        return response;
                    }))) {
                if (!expectedError.isEmpty()) {
                    BaseException failure = Assertions.assertThrows(BaseException.class, AlibabaCloudCredentialsFactory::defaults);
                    Assertions.assertTrue(failure.getMessage().contains(expectedError));
                    Assertions.assertTrue(requests.isEmpty());
                    return;
                }
                if (name.startsWith("oidc")) {
                    Assertions.assertTrue(AlibabaCloudCredentialsFactory.findOidcRoleArnFromEnvironment().isPresent());
                } else {
                    Assertions.assertTrue(AlibabaCloudCredentialsFactory.findOidcRoleArnFromEnvironment().isEmpty());
                }
                Client client;
                if (name.startsWith("oidc-properties")) {
                    AlibabaCloudCredentialsProperties properties = new AlibabaCloudCredentialsProperties();
                    properties.setType(AuthConstant.OIDC_ROLE_ARN);
                    client = AlibabaCloudCredentialsFactory.fromProperties(properties);
                    Assertions.assertNull(properties.getOidcTokenFilePath());
                } else if ("oidc-shortcut".equals(name)) {
                    client = AlibabaCloudCredentialsFactory.oidcRoleArn();
                } else if ("oidc-explicit-path".equals(name)) {
                    Config config = new Config().setType(AuthConstant.OIDC_ROLE_ARN).setOidcTokenFilePath(args[2]);
                    Map<String, Object> original = config.toMap();
                    client = AlibabaCloudCredentialsFactory.create(config);
                    Assertions.assertEquals(original, config.toMap());
                } else {
                    client = AlibabaCloudCredentialsFactory.defaults();
                }
                Assertions.assertTrue(requests.isEmpty());
                if ("oidc-missing-path".equals(name)) {
                    Assertions.assertThrows(CredentialException.class, client::getCredential);
                    Assertions.assertTrue(requests.isEmpty());
                    return;
                }
                if ("oidc-exchange-failure".equals(name)) {
                    Assertions.assertThrows(CredentialException.class, client::getCredential);
                    Assertions.assertEquals(1, requests.size());
                    return;
                }
                CredentialModel credential = client.getCredential();
                if (name.startsWith("oidc")) {
                    Assertions.assertEquals("STS.oidc", credential.getAccessKeyId());
                    Assertions.assertEquals("oidc-secret", credential.getAccessKeySecret());
                    Assertions.assertEquals("oidc-token", credential.getSecurityToken());
                    Assertions.assertEquals(1, requests.size());
                    Assertions.assertEquals("AssumeRoleWithOIDC", requests.getFirst().getUrlParameters().get("Action"));
                    String expectedToken = "oidc-explicit-path".equals(name) ? "explicit-oidc-token" : "test-oidc-token";
                    Assertions.assertTrue(requests.getFirst().getHttpContentString().contains("OIDCToken=" + expectedToken));
                } else {
                    Assertions.assertEquals("environment-id", credential.getAccessKeyId());
                    Assertions.assertEquals("environment-secret", credential.getAccessKeySecret());
                    if ("sts".equals(name)) {
                        Assertions.assertEquals("environment-token", credential.getSecurityToken());
                    } else {
                        Assertions.assertNull(credential.getSecurityToken());
                    }
                    Assertions.assertTrue(requests.isEmpty());
                }
            }
        }
    }
}
