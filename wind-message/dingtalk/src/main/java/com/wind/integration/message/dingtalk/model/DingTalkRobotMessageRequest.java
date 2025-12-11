package com.wind.integration.message.dingtalk.model;

import com.alibaba.fastjson2.JSON;
import com.wind.signature.algorithm.HmacSHA256Signer;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import org.jspecify.annotations.NonNull;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;


/**
 * 钉钉消息发送请求
 *
 * @author wuxp
 * @date 2025-12-11 16:48
 **/
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DingTalkRobotMessageRequest {

    /**
     * 钉钉机器人的 access_token
     */
    private String accessToken;

    /**
     * 钉钉机器人的secret_key
     */
    private String secretKey;

    /**
     * 时间戳
     */
    private Long timestamp;

    /**
     * 钉钉机器人的数据
     */
    private String data;

    public static DingTalkRobotMessageRequest of(@NonNull Object data, @NonNull String accessKey, @NonNull String secretKey) {
        DingTalkRobotMessageRequest result = new DingTalkRobotMessageRequest();
        result.setAccessToken(accessKey);
        result.setSecretKey(secretKey);
        if (data instanceof String text) {
            result.setData(text);
        } else {
            result.setData(JSON.toJSONString(data));
        }
        result.setTimestamp(System.currentTimeMillis());
        return result;
    }

    @SneakyThrows
    public String getSign() {
        String stringToSign = timestamp + "\n" + secretKey;
        Mac mac = Mac.getInstance(HmacSHA256Signer.ALGORITHM_NAME);
        mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), HmacSHA256Signer.ALGORITHM_NAME));
        byte[] signData = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
        return URLEncoder.encode(new String(Base64.getEncoder().encode(signData)), StandardCharsets.UTF_8);
    }

    @NonNull
    public String getQueryString() {
        return "access_token=" + accessToken + "&timestamp=" + timestamp + "&sign=" + getSign();
    }
}
