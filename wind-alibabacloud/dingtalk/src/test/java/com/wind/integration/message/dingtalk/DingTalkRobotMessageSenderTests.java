package com.wind.integration.message.dingtalk;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * 验证真实 RestClient 发送链保留钉钉签名的单次 URL 编码。
 */
class DingTalkRobotMessageSenderTests {

    @Test
    void testSendMessagePreservesSingleEncodedSignature() throws Exception {
        String accessToken = "test-access-token";
        String secret = "test-signing-secret";
        List<MockClientHttpRequest> requests = new ArrayList<>();
        RestClient restClient = RestClient.builder().requestFactory((uri, method) -> {
            MockClientHttpRequest request = new MockClientHttpRequest(method, uri);
            MockClientHttpResponse response = new MockClientHttpResponse(
                    "{\"errcode\":0,\"errmsg\":\"ok\"}".getBytes(StandardCharsets.UTF_8), HttpStatus.OK);
            response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
            request.setResponse(response);
            requests.add(request);
            return request;
        }).build();
        DingTalkRobotMessage message = DingTalkRobotMessage.text(
                accessToken, secret, "title", "content", List.of(), List.of(), false);

        new DingTalkRobotMessageSender(restClient).sendMessage(message);

        MockClientHttpRequest request = requests.getFirst();
        Assertions.assertEquals(HttpMethod.POST, request.getMethod());
        Assertions.assertEquals("oapi.dingtalk.com", request.getURI().getHost());
        Assertions.assertEquals("/robot/send", request.getURI().getPath());
        MultiValueMap<String, String> query = UriComponentsBuilder.fromUri(request.getURI()).build(true).getQueryParams();
        Assertions.assertEquals(accessToken, query.getFirst("access_token"));
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String stringToSign = query.getFirst("timestamp") + "\n" + secret;
        String expectedSignature = Base64.getEncoder().encodeToString(mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8)));
        Assertions.assertEquals(expectedSignature, URLDecoder.decode(query.getFirst("sign"), StandardCharsets.UTF_8));
    }
}
