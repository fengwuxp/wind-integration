package com.wind.integration.message.email.alibabacloud;

import com.aliyun.credentials.http.CompatibleUrlConnClient;
import com.aliyun.credentials.http.FormatType;
import com.aliyun.credentials.http.HttpRequest;
import com.aliyun.credentials.http.HttpResponse;
import com.aliyun.credentials.utils.AuthConstant;
import com.aliyun.credentials.utils.AuthUtils;
import com.aliyun.tea.Tea;
import com.aliyun.tea.TeaRequest;
import com.aliyun.tea.TeaResponse;
import com.aliyun.teaopenapi.models.Config;
import com.wind.common.exception.BaseException;
import com.wind.integration.alibaba.credentials.AlibabaCloudProductProperties;
import com.wind.integration.core.message.definition.SimpleEmailMessage;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.lang.management.ManagementFactory;
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
import java.util.concurrent.atomic.AtomicInteger;

class AlibabaCloudEmailDefaultsTests {

    @TempDir
    Path directory;

    @ParameterizedTest
    @ValueSource(strings = {"ak", "sts", "oidc", "none", "partial", "explicit", "invalid", "product-ak", "mixed"})
    void testCredentialSelectionInIsolatedEnvironment(String mode) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        ManagementFactory.getRuntimeMXBean().getInputArguments().stream()
                .filter(value -> value.startsWith("-javaagent:") && value.contains("mockito-core"))
                .forEach(command::add);
        command.addAll(List.of("-cp", System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")),
                EnvironmentProbe.class.getName(), mode));
        ProcessBuilder builder = new ProcessBuilder(command);
        Map<String, String> environment = builder.environment();
        environment.keySet().removeIf(key -> key.startsWith("ALIBABA_CLOUD_"));
        if (!"none".equals(mode)) {
            environment.put("ALIBABA_CLOUD_ACCESS_KEY_ID", "environment-id");
            environment.put("ALIBABA_CLOUD_ACCESS_KEY_SECRET", "environment-secret");
        }
        if ("sts".equals(mode)) {
            environment.put("ALIBABA_CLOUD_SECURITY_TOKEN", "environment-token");
        }
        if (Set.of("oidc", "partial", "explicit", "product-ak").contains(mode)) {
            environment.put("ALIBABA_CLOUD_ROLE_ARN", "acs:ram::123456789:role/test-role");
        }
        if ("oidc".equals(mode)) {
            environment.put("ALIBABA_CLOUD_OIDC_PROVIDER_ARN", "acs:ram::123456789:oidc-provider/test-provider");
        }
        Path output = directory.resolve(mode + ".log");
        Process process = builder.redirectErrorStream(true).redirectOutput(output.toFile()).start();
        try {
            Assertions.assertTrue(process.waitFor(30, TimeUnit.SECONDS), "邮件凭据入口验证超时");
            Assertions.assertEquals(0, process.exitValue(), Files.readString(output));
        } finally {
            process.destroyForcibly();
        }
    }

    public static class EnvironmentProbe {

        public static void main(String[] args) throws Exception {
            String mode = args[0];
            AtomicInteger accountReads = new AtomicInteger();
            List<TeaRequest> requests = new ArrayList<>();
            List<HttpRequest> exchanges = new ArrayList<>();
            try (MockedStatic<Tea> tea = interceptMail(requests);
                 MockedStatic<AuthUtils> auth = Mockito.mockStatic(AuthUtils.class, invocation -> {
                     if ("getOIDCToken".equals(invocation.getMethod().getName())) {
                         Assertions.assertEquals("/etc/secrets/alibaba_cloud_credential", invocation.getArgument(0));
                         return "test-oidc-token";
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
                                     {"Credentials":{"AccessKeyId":"STS.oidc","AccessKeySecret":"oidc-secret",
                                     "SecurityToken":"oidc-token","Expiration":"%s"}}
                                     """.formatted(Instant.now().plusSeconds(7200).truncatedTo(ChronoUnit.SECONDS))
                                     .getBytes(StandardCharsets.UTF_8), "UTF-8", FormatType.JSON);
                             return response;
                         }))) {
                Config connection = new Config().setEndpoint("dm.example.invalid").setConnectTimeout(1234);
                Map<String, Object> originalConnection = connection.toMap();
                if ("none".equals(mode) || "partial".equals(mode)) {
                    Assertions.assertThrows(BaseException.class, () -> AlibabaCloudEmailMessageSender.defaults("dm.example.invalid", () -> "sender@example.test"));
                    Assertions.assertThrows(BaseException.class, () -> AlibabaCloudEmailMessageSender.defaults(null, connection, () -> "sender@example.test"));
                    Assertions.assertTrue(requests.isEmpty());
                    Assertions.assertTrue(exchanges.isEmpty());
                    return;
                }
                com.aliyun.credentials.models.Config credential = null;
                if ("explicit".equals(mode)) {
                    credential = new com.aliyun.credentials.models.Config().setType(AuthConstant.STS)
                            .setAccessKeyId("STS.explicit").setAccessKeySecret("explicit-secret").setSecurityToken("explicit-token");
                } else if ("invalid".equals(mode)) {
                    Assertions.assertThrows(BaseException.class, () -> AlibabaCloudEmailMessageSender.defaults(
                            new com.aliyun.credentials.models.Config().setType("invalid"), connection, () -> "sender@example.test"));
                    Assertions.assertTrue(requests.isEmpty());
                    return;
                } else if ("mixed".equals(mode)) {
                    Assertions.assertThrows(BaseException.class, () -> AlibabaCloudEmailMessageSender.defaults(null,
                            connection.setAccessKeyId("other-id"), () -> "sender@example.test"));
                    Assertions.assertTrue(requests.isEmpty());
                    return;
                } else if ("product-ak".equals(mode)) {
                    AlibabaCloudProductProperties properties = new AlibabaCloudProductProperties();
                    properties.setAccessKey("product-id");
                    properties.setSecretKey("product-secret");
                    properties.setEndpoint("dm.example.invalid");
                    AlibabaCloudEmailMessageSender.fromProperties(properties, () -> "sender@example.test").sendMessage(message());
                    Assertions.assertTrue(requests.getFirst().headers.get("Authorization").contains("product-id"));
                    Assertions.assertTrue(exchanges.isEmpty());
                    return;
                }
                AlibabaCloudEmailMessageSender full = AlibabaCloudEmailMessageSender.defaults(credential, connection, () -> {
                    accountReads.incrementAndGet();
                    return "sender@example.test";
                });
                Assertions.assertEquals(0, accountReads.get());
                Assertions.assertTrue(exchanges.isEmpty());
                full.sendMessage(message());
                Assertions.assertEquals(1, accountReads.get());
                Assertions.assertEquals(originalConnection, connection.toMap());
                if (!"explicit".equals(mode)) {
                    AlibabaCloudEmailMessageSender.defaults("dm.example.invalid", () -> "sender@example.test").sendMessage(message());
                }
                String expectedId = switch (mode) {
                    case "oidc" -> "STS.oidc";
                    case "explicit" -> "STS.explicit";
                    default -> "environment-id";
                };
                String expectedToken = switch (mode) {
                    case "oidc" -> "oidc-token";
                    case "sts" -> "environment-token";
                    case "explicit" -> "explicit-token";
                    default -> null;
                };
                Assertions.assertEquals("explicit".equals(mode) ? 1 : 2, requests.size());
                for (TeaRequest request : requests) {
                    Assertions.assertTrue(request.headers.get("Authorization").contains(expectedId));
                    Assertions.assertEquals(expectedToken, request.headers.get("x-acs-security-token"));
                }
            }
        }

        private static SimpleEmailMessage message() {
            return SimpleEmailMessage.text("测试标题", "正文", Set.of("recipient@example.test"));
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
}
