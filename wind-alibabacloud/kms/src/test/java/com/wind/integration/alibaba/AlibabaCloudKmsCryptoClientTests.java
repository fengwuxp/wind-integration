package com.wind.integration.alibaba;

import com.aliyun.credentials.Client;
import com.aliyun.credentials.models.CredentialModel;
import com.aliyun.credentials.provider.AlibabaCloudCredentialsProvider;
import com.aliyun.tea.Tea;
import com.aliyun.tea.TeaRequest;
import com.aliyun.tea.TeaResponse;
import com.aliyun.teaopenapi.models.Config;
import com.aliyun.credentials.utils.AuthConstant;
import com.wind.common.exception.BaseException;
import com.wind.integration.alibaba.credentials.AlibabaCloudCredentialsFactory;
import com.wind.integration.kms.WindKmsException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * @author wuxp
 * @date 2025-02-18 11:36
 **/
class AlibabaCloudKmsCryptoClientTests {

    @Test
    void testBuildKmsClientWithCredentialProvider() {
        com.aliyun.credentials.models.Config credentialConfig = new com.aliyun.credentials.models.Config()
                .setType(AuthConstant.OIDC_ROLE_ARN)
                .setRoleArn("acs:ram::123456789:role/test-role")
                .setOidcProviderArn("acs:ram::123456789:oidc-provider/test-provider")
                .setOidcTokenFilePath("/tmp/test-oidc-token")
                .setRoleSessionName("test-session");
        Config config = new Config().setCredential(AlibabaCloudCredentialsFactory.create(credentialConfig))
                .setEndpoint("kms.us-east-1.aliyuncs.com");

        Assertions.assertDoesNotThrow(() -> AlibabaCloudKmsCryptoClient.buildKmsClient(config));
    }

    @Test
    void testSharedCredentialCreationDoesNotResolveCredentialsOrChangeConfig() {
        Client credential = credentialClient(() -> {
            throw new AssertionError("创建 KMS 客户端时不应获取凭据");
        });
        Config config = new Config().setRegionId("us-east-1").setConnectTimeout(1234).setReadTimeout(5678);
        Map<String, Object> original = config.toMap();

        Assertions.assertNotNull(AlibabaCloudKmsCryptoClient.withCredential(credential, config));

        Assertions.assertEquals(original, config.toMap());
    }

    @Test
    void testRejectMissingSharedCredentialOrConnectionConfig() {
        Assertions.assertThrows(BaseException.class, () -> AlibabaCloudKmsCryptoClient.withCredential(null, new Config()));
        Assertions.assertThrows(BaseException.class, () -> AlibabaCloudKmsCryptoClient.withCredential(staticCredential(), null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"accessKeyId", "accessKeySecret", "securityToken", "bearerToken", "credential"})
    void testRejectIdentityInsideConnectionConfig(String field) {
        Config config = new Config().setEndpoint("kms.example.invalid");
        switch (field) {
            case "accessKeyId" -> config.setAccessKeyId("");
            case "accessKeySecret" -> config.setAccessKeySecret("ignored-secret");
            case "securityToken" -> config.setSecurityToken("ignored-token");
            case "bearerToken" -> config.setBearerToken("ignored-bearer");
            default -> config.setCredential(staticCredential());
        }

        Assertions.assertThrows(BaseException.class, () -> AlibabaCloudKmsCryptoClient.withCredential(staticCredential(), config));
    }

    @Test
    void testKmsOperationsUseCurrentWholeCredentialsAndConnectionOptions() throws Exception {
        AtomicReference<CredentialModel> current = new AtomicReference<>(temporaryCredential("first"));
        Client credential = credentialClient(current::get);
        Config connection = new Config().setEndpoint("kms.example.invalid").setConnectTimeout(1234).setReadTimeout(5678);
        Map<String, Object> original = connection.toMap();
        AlibabaCloudKmsCryptoClient kms = AlibabaCloudKmsCryptoClient.withCredential(credential, connection);
        List<TeaRequest> requests = new ArrayList<>();
        List<Map<String, Object>> runtimeOptions = new ArrayList<>();
        try (MockedStatic<Tea> ignored = interceptKmsTransport(requests, runtimeOptions)) {
            Assertions.assertEquals("ciphertext", kms.encrypt("key-id", "plaintext"));
            current.set(temporaryCredential("second"));
            Assertions.assertEquals("plaintext", kms.decrypt("ciphertext"));
            Assertions.assertEquals("secret-value", kms.getCredentials("secret-name", "version-id").getContent());
        }

        Assertions.assertEquals(original, connection.toMap());
        Assertions.assertEquals(3, requests.size());
        Assertions.assertEquals(List.of("Encrypt", "Decrypt", "GetSecretValue"), requests.stream().map(request -> request.headers.get("x-acs-action")).toList());
        Assertions.assertEquals(List.of("token-first", "token-second", "token-second"), requests.stream().map(request -> request.headers.get("x-acs-security-token")).toList());
        Assertions.assertTrue(requests.getFirst().headers.get("Authorization").contains("STS.first"));
        Assertions.assertTrue(requests.getLast().headers.get("Authorization").contains("STS.second"));
        for (int index = 0; index < requests.size(); index++) {
            Assertions.assertEquals("kms.example.invalid", requests.get(index).headers.get("host"));
            Assertions.assertEquals(1234, runtimeOptions.get(index).get("connectTimeout"));
            Assertions.assertEquals(5678, runtimeOptions.get(index).get("readTimeout"));
        }
    }

    @Test
    void testEndpointSelectionKeepsExplicitAndSystemPropertyPriority() throws Exception {
        String originalInstance = System.getProperty("ALIBABA_CLOUD_K_ID");
        String originalEndpoint = System.getProperty("ALIBABA_CLOUD_K_ENDPOINT");
        List<TeaRequest> requests = new ArrayList<>();
        try (MockedStatic<Tea> ignored = interceptKmsTransport(requests, new ArrayList<>())) {
            System.setProperty("ALIBABA_CLOUD_K_ID", "test-instance");
            System.setProperty("ALIBABA_CLOUD_K_ENDPOINT", "fallback.example.invalid");
            Client credential = staticCredential();
            Config explicit = new Config().setEndpoint("explicit.example.invalid");
            Assertions.assertEquals("ciphertext", AlibabaCloudKmsCryptoClient.withCredential(credential, explicit).encrypt("key-id", "plaintext"));
            Config inherited = new Config();
            Assertions.assertEquals("ciphertext", AlibabaCloudKmsCryptoClient.withCredential(credential, inherited).encrypt("key-id", "plaintext"));
            System.setProperty("ALIBABA_CLOUD_K_ID", "");
            Assertions.assertEquals("ciphertext", AlibabaCloudKmsCryptoClient.withCredential(credential, inherited).encrypt("key-id", "plaintext"));

            Assertions.assertEquals(List.of("explicit.example.invalid", "test-instance.cryptoservice.kms.aliyuncs.com", "fallback.example.invalid"),
                    requests.stream().map(request -> request.headers.get("host")).toList());
            Assertions.assertNull(inherited.getEndpoint());
            Assertions.assertEquals("explicit.example.invalid", explicit.getEndpoint());
        } finally {
            restoreProperty("ALIBABA_CLOUD_K_ID", originalInstance);
            restoreProperty("ALIBABA_CLOUD_K_ENDPOINT", originalEndpoint);
        }
    }

    @Test
    void testCredentialFailureUsesExistingKmsException() {
        IllegalStateException failure = new IllegalStateException("test credential failure");
        Client credential = credentialClient(() -> {
            throw failure;
        });
        AlibabaCloudKmsCryptoClient kms = AlibabaCloudKmsCryptoClient.withCredential(credential, new Config().setEndpoint("kms.example.invalid"));

        WindKmsException actual = Assertions.assertThrows(WindKmsException.class, () -> kms.encrypt("key-id", "plaintext"));

        Assertions.assertSame(failure, actual.getCause());
    }

    private void restoreProperty(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }

    private Client staticCredential() {
        return AlibabaCloudCredentialsFactory.create(new com.aliyun.credentials.models.Config()
                .setType(AuthConstant.STS).setAccessKeyId("STS.first").setAccessKeySecret("secret-first").setSecurityToken("token-first"));
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
            }
        });
    }

    private CredentialModel temporaryCredential(String suffix) {
        return CredentialModel.builder().type(AuthConstant.STS).accessKeyId("STS." + suffix)
                .accessKeySecret("secret-" + suffix).securityToken("token-" + suffix).build();
    }

    private MockedStatic<Tea> interceptKmsTransport(List<TeaRequest> requests, List<Map<String, Object>> runtimeOptions) {
        return Mockito.mockStatic(Tea.class, invocation -> {
            if (!"doAction".equals(invocation.getMethod().getName())) {
                return invocation.callRealMethod();
            }
            requests.add(invocation.getArgument(0));
            runtimeOptions.add(new HashMap<>(invocation.getArgument(1)));
            TeaResponse response = new TeaResponse();
            response.statusCode = 200;
            response.headers.put("content-type", "application/json");
            response.body = new ByteArrayInputStream("""
                    {"RequestId":"test-request","CiphertextBlob":"ciphertext","Plaintext":"plaintext",
                     "SecretData":"secret-value","VersionId":"version-id"}
                    """.getBytes(StandardCharsets.UTF_8));
            return response;
        });
    }
}
