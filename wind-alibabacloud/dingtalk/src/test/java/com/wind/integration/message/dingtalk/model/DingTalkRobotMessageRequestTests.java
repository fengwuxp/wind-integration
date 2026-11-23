package com.wind.integration.message.dingtalk.model;

import com.wind.integration.message.dingtalk.DingTalkRobotMessage;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 钉钉机器人请求 JSON 合同测试。
 *
 * @author wuxp
 * @date 2026-08-04
 */
class DingTalkRobotMessageRequestTests {

    @Test
    void testSerializeTextMessageWithDingTalkAtAllField() {
        DingTalkRobotMessage message = DingTalkRobotMessage.text(
                "access-token", "secret", "title", "content", List.of("user-1"), List.of("13800000000"), true);

        String data = DingTalkRobotMessageRequest.of(
                        message.message().getSendBodyInfo(), message.getAccessToken(), message.getSecret())
                .getData();

        Assertions.assertTrue(data.contains("\"msgtype\":\"text\""));
        Assertions.assertTrue(data.contains("\"content\":\"content\""));
        Assertions.assertTrue(data.contains("\"isAtAll\":true"));
        Assertions.assertFalse(data.contains("\"atAll\":"));
    }
}
