package com.wind.integration.message.email.alibabacloud;

import com.aliyun.credentials.Client;
import com.aliyun.credentials.models.CredentialModel;
import com.aliyun.credentials.provider.AlibabaCloudCredentialsProvider;
import com.aliyun.credentials.utils.AuthConstant;
import com.aliyun.tea.Tea;
import com.aliyun.tea.TeaException;
import com.aliyun.tea.TeaRequest;
import com.aliyun.tea.TeaResponse;
import com.aliyun.teaopenapi.models.Config;
import com.wind.common.exception.BaseException;
import com.wind.integration.alibaba.credentials.AlibabaCloudCredentialsFactory;
import com.wind.integration.alibaba.credentials.AlibabaCloudProductProperties;
import com.wind.integration.core.message.definition.SimpleEmailMessage;
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
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

class AlibabaCloudEmailCredentialsTests {

    @ParameterizedTest
    @ValueSource(strings = {AuthConstant.ACCESS_KEY, AuthConstant.STS})
    void testPublicProductPropertiesSelectIdentityAndResolveAccountForEachSend(String type) {
        AlibabaCloudProductProperties properties = new AlibabaCloudProductProperties();
        properties.setEndpoint("dm.example.invalid");
        properties.setAccessKey("product-id");
        properties.setSecretKey("product-secret");
        if (AuthConstant.STS.equals(type)) {
            properties.setSecurityToken("product-token");
        }
        AtomicReference<String> account = new AtomicReference<>();
        AlibabaCloudEmailMessageSender sender = AlibabaCloudEmailMessageSender.fromProperties(properties, () -> {
            Assertions.assertNotNull(account.get(), "构造时不应读取发件账号");
            return account.get();
        });
        List<TeaRequest> requests = new ArrayList<>();
        try (MockedStatic<Tea> ignored = interceptHttp(requests, new ArrayList<>())) {
            account.set("first@example.test");
            sender.sendMessage(message());
            account.set("second@example.test");
            sender.sendMessage(message());
        }
        Assertions.assertEquals(2, requests.size());
        String token = AuthConstant.STS.equals(type) ? "product-token" : null;
        for (TeaRequest request : requests) {
            assertSignedWith(request, "product-id", token);
            Assertions.assertEquals("dm.example.invalid", request.headers.get("host"));
        }
        Assertions.assertEquals("first@example.test", requests.getFirst().query.get("AccountName"));
        Assertions.assertEquals("second@example.test", requests.getLast().query.get("AccountName"));
        Assertions.assertNull(properties.getType());
        Assertions.assertEquals("product-id", properties.getAccessKey());
        Assertions.assertFalse(properties.toString().contains("product-id"));
        Assertions.assertFalse(properties.toString().contains("product-secret"));
        Assertions.assertFalse(properties.toString().contains("product-token"));
    }

    @Test
    void testPublicProductPropertiesRequireExplicitCompleteIdentity() {
        AlibabaCloudProductProperties properties = new AlibabaCloudProductProperties();
        properties.setEndpoint("dm.example.invalid");

        Assertions.assertThrows(BaseException.class, () -> AlibabaCloudEmailMessageSender.fromProperties(null, () -> "sender@example.test"));
        Assertions.assertThrows(BaseException.class, () -> AlibabaCloudEmailMessageSender.fromProperties(properties, () -> "sender@example.test"));
        properties.setAccessKey("partial-id");
        Assertions.assertThrows(BaseException.class, () -> AlibabaCloudEmailMessageSender.fromProperties(properties, () -> "sender@example.test"));
        properties.setSecretKey("complete-secret");
        Assertions.assertThrows(BaseException.class, () -> AlibabaCloudEmailMessageSender.fromProperties(properties, null));
    }

    @Test
    void testSharedCredentialsStayDynamicAndConnectionConfigUnchanged() {
        AtomicReference<CredentialModel> current = new AtomicReference<>();
        Client credential = dynamicClient(() -> {
            Assertions.assertNotNull(current.get(), "创建邮件发送器时不能提前取证");
            return current.get();
        });
        Config connection = connection().setConnectTimeout(1234).setReadTimeout(5678);
        Map<String, Object> original = connection.toMap();
        AlibabaCloudEmailMessageSender sender = AlibabaCloudEmailMessageSender.withCredential(credential, connection, () -> "sender@example.test");
        List<TeaRequest> requests = new ArrayList<>();
        List<Map<String, Object>> runtimes = new ArrayList<>();
        try (MockedStatic<Tea> ignored = interceptHttp(requests, runtimes)) {
            current.set(temporaryCredential("first"));
            sender.sendMessage(message());
            current.set(temporaryCredential("second"));
            sender.sendMessage(message());
        }

        Assertions.assertEquals(original, connection.toMap());
        Assertions.assertEquals(2, requests.size());
        assertSignedWith(requests.getFirst(), "STS.first", "token-first");
        assertSignedWith(requests.getLast(), "STS.second", "token-second");
        Assertions.assertEquals("dm.example.invalid", requests.getFirst().headers.get("host"));
        Assertions.assertEquals(1234, runtimes.getFirst().get("connectTimeout"));
        Assertions.assertEquals(5678, runtimes.getFirst().get("readTimeout"));
        Assertions.assertEquals("SingleSendMail", requests.getFirst().headers.get("x-acs-action"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"accessKeyId", "accessKeySecret", "securityToken", "bearerToken", "credential"})
    void testRejectIdentityInConnectionConfig(String field) {
        Config config = connection();
        switch (field) {
            case "accessKeyId" -> config.setAccessKeyId("");
            case "accessKeySecret" -> config.setAccessKeySecret("other-secret");
            case "securityToken" -> config.setSecurityToken("other-token");
            case "bearerToken" -> config.setBearerToken("other-bearer");
            default -> config.setCredential(accessKey("other"));
        }
        Assertions.assertThrows(BaseException.class,
                () -> AlibabaCloudEmailMessageSender.withCredential(accessKey("mail"), config, () -> "sender@example.test"));
    }

    @Test
    void testExplicitCredentialAndSdkClientSignWithTheirOwnIdentity() throws Exception {
        List<TeaRequest> requests = new ArrayList<>();
        try (MockedStatic<Tea> ignored = interceptHttp(requests, new ArrayList<>())) {
            AlibabaCloudEmailMessageSender.withCredential(accessKey("shared"), connection(), () -> "sender@example.test").sendMessage(message());
            com.aliyun.dm20151123.Client sdk = new com.aliyun.dm20151123.Client(connection().setAccessKeyId("sdk-id").setAccessKeySecret("sdk-secret"));
            new AlibabaCloudEmailMessageSender(sdk, () -> "sender@example.test").sendMessage(message());
        }
        assertSignedWith(requests.getFirst(), "shared", null);
        assertSignedWith(requests.getLast(), "sdk-id", null);
    }

    @Test
    void testPublicPropertiesSupportExplicitSts() {
        AlibabaCloudProductProperties properties = properties();
        properties.setType(AuthConstant.STS);
        properties.setAccessKey("STS.properties");
        properties.setSecretKey("properties-secret");
        properties.setSecurityToken("properties-token");
        List<TeaRequest> requests = new ArrayList<>();
        try (MockedStatic<Tea> ignored = interceptHttp(requests, new ArrayList<>())) {
            AlibabaCloudEmailMessageSender.fromProperties(properties, () -> "sender@example.test").sendMessage(message());
        }
        assertSignedWith(requests.getFirst(), "STS.properties", "properties-token");
        AlibabaCloudProductProperties different = properties();
        different.setAccessKey("other-id");
        Assertions.assertNotEquals(properties, different);
        Assertions.assertFalse(properties.toString().contains("properties-secret"));
        Assertions.assertFalse(properties.toString().contains("properties-token"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing-id", "missing-secret", "blank-id", "blank-secret"})
    void testProductCredentialsMustBeComplete(String missing) {
        AlibabaCloudProductProperties properties = properties();
        properties.setAccessKey("product-id");
        properties.setSecretKey("product-secret");
        switch (missing) {
            case "missing-id" -> properties.setAccessKey(null);
            case "missing-secret" -> properties.setSecretKey(null);
            case "blank-id" -> properties.setAccessKey(" ");
            default -> properties.setSecretKey(" ");
        }
        Assertions.assertThrows(BaseException.class, () -> AlibabaCloudEmailMessageSender.fromProperties(properties, () -> "sender@example.test"));
    }

    @Test
    void testTextAndHtmlFormatsAreDistinguished() {
        SimpleEmailMessage text = message();
        SimpleEmailMessage html = SimpleEmailMessage.html("标题", "<p>正文</p>", Set.of("recipient@example.test"));
        Assertions.assertTrue(text.isText());
        Assertions.assertFalse(text.isHtml());
        Assertions.assertTrue(html.isHtml());
        Assertions.assertFalse(html.isText());
        SimpleEmailMessage unknown = new SimpleEmailMessage("标题", "正文", "unknown", Set.of("recipient@example.test"));
        Assertions.assertFalse(unknown.isText());
        Assertions.assertFalse(unknown.isHtml());
    }

    @Test
    void testSendingPreservesProtocolAndResolvesAccountForEveryMessage() {
        AtomicReference<String> account = new AtomicReference<>("first@example.test");
        AlibabaCloudEmailMessageSender sender = AlibabaCloudEmailMessageSender.withCredential(accessKey("mail"), connection(), account::get);
        List<TeaRequest> requests = new ArrayList<>();
        try (MockedStatic<Tea> ignored = interceptHttp(requests, new ArrayList<>())) {
            sender.sendMessage(message());
            account.set("second@example.test");
            sender.sendMessage(SimpleEmailMessage.html("HTML标题", "<p>正文</p>", Set.of("html@example.test")));
        }
        Assertions.assertEquals(2, requests.size());
        TeaRequest text = requests.getFirst();
        TeaRequest html = requests.getLast();
        Assertions.assertEquals("first@example.test", text.query.get("AccountName"));
        Assertions.assertEquals("second@example.test", html.query.get("AccountName"));
        Assertions.assertEquals("测试标题", text.query.get("Subject"));
        Assertions.assertEquals("测试内容", text.query.get("TextBody"));
        Assertions.assertNull(text.query.get("HtmlBody"));
        Assertions.assertEquals("<p>正文</p>", html.query.get("HtmlBody"));
        Assertions.assertNull(html.query.get("TextBody"));
        Assertions.assertEquals("recipient@example.test", text.query.get("ToAddress"));
        Assertions.assertEquals("html@example.test", html.query.get("ToAddress"));
        for (TeaRequest request : requests) {
            Assertions.assertEquals("1", request.query.get("AddressType"));
            Assertions.assertEquals("true", request.query.get("ReplyToAddress"));
            Assertions.assertEquals("POST", request.method);
            Assertions.assertEquals("SingleSendMail", request.headers.get("x-acs-action"));
        }
    }

    @Test
    void testSendingFailureRetainsSdkErrorAndRequestId() {
        AlibabaCloudEmailMessageSender sender = AlibabaCloudEmailMessageSender.withCredential(accessKey("mail"), connection(), () -> "sender@example.test");
        try (MockedStatic<Tea> ignored = Mockito.mockStatic(Tea.class, invocation -> {
            if (!"doAction".equals(invocation.getMethod().getName())) {
                return invocation.callRealMethod();
            }
            TeaResponse response = new TeaResponse();
            response.statusCode = 403;
            response.headers.put("content-type", "application/json");
            response.body = new ByteArrayInputStream("""
                    {"Code":"InvalidAccessKeyId","Message":"test denied","RequestId":"request-test"}
                    """.getBytes(StandardCharsets.UTF_8));
            return response;
        })) {
            BaseException failure = Assertions.assertThrows(BaseException.class, () -> sender.sendMessage(message()));
            Assertions.assertEquals("邮件发送失败", failure.getMessage());
            TeaException cause = Assertions.assertInstanceOf(TeaException.class, failure.getCause());
            Assertions.assertEquals("InvalidAccessKeyId", cause.getCode());
            Assertions.assertEquals("request-test", cause.getData().get("RequestId"));
            Assertions.assertEquals(403, cause.getStatusCode());
        }
    }

    private static AlibabaCloudProductProperties properties() {
        AlibabaCloudProductProperties result = new AlibabaCloudProductProperties();
        result.setEndpoint("dm.example.invalid");
        return result;
    }

    private static Config connection() {
        return new Config().setEndpoint("dm.example.invalid");
    }

    private static Client accessKey(String id) {
        return AlibabaCloudCredentialsFactory.accessKey(id, "test-secret");
    }

    private static CredentialModel temporaryCredential(String suffix) {
        return CredentialModel.builder().type(AuthConstant.STS).accessKeyId("STS." + suffix)
                .accessKeySecret("secret-" + suffix).securityToken("token-" + suffix).build();
    }

    private static Client dynamicClient(Supplier<CredentialModel> supplier) {
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

    private static SimpleEmailMessage message() {
        return SimpleEmailMessage.text("测试标题", "测试内容", Set.of("recipient@example.test"));
    }

    private static void assertSignedWith(TeaRequest request, String accessKeyId, String token) {
        Assertions.assertTrue(request.headers.get("Authorization").contains(accessKeyId));
        Assertions.assertEquals(token, request.headers.get("x-acs-security-token"));
    }

    private static MockedStatic<Tea> interceptHttp(List<TeaRequest> requests, List<Map<String, Object>> runtimes) {
        return Mockito.mockStatic(Tea.class, invocation -> {
            if (!"doAction".equals(invocation.getMethod().getName())) {
                return invocation.callRealMethod();
            }
            requests.add(invocation.getArgument(0));
            runtimes.add(new HashMap<>(invocation.getArgument(1)));
            TeaResponse response = new TeaResponse();
            response.statusCode = 200;
            response.headers.put("content-type", "application/json");
            response.body = new ByteArrayInputStream("{\"RequestId\":\"test-request\",\"EnvId\":\"test-envelope\"}".getBytes(StandardCharsets.UTF_8));
            return response;
        });
    }

}
