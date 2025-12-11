package com.wind.integration.message.dingtalk;

import com.wind.common.WindConstants;
import com.wind.common.exception.AssertUtils;
import com.wind.integration.core.message.MessageSender;
import com.wind.integration.message.dingtalk.model.DingTalkRobotMessageRequest;
import com.wind.integration.message.dingtalk.model.DingTalkRobotMessageResponse;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * 钉钉机器人消息发送
 * <a href="https://open.dingtalk.com/document/robots/custom-robot-access">自定义机器人接入</a>
 *
 * @author wuxp
 * @date 2025-12-11 16:40
 **/
public record DingTalkRobotMessageSender(RestClient restClient) implements MessageSender<DingTalkRobotMessage> {

    /**
     * 钉钉机器人消息发送地址
     */
    private static final String BASE_URL = "https://oapi.dingtalk.com/robot/send";

    @Override
    public void sendMessage(DingTalkRobotMessage message) {
        AssertUtils.hasText(message.getAccessToken(), "argument accessToken must not empty");
        AssertUtils.hasText(message.getSecret(), "argument secret must not empty");
        AssertUtils.hasText(message.title(), "argument title must not empty");
        AssertUtils.notNull(message.getMessageBody(), "argument message body is not null");
        DingTalkRobotMessageRequest request = DingTalkRobotMessageRequest.of(message.getMessageBody().getSendBodyInfo(), message.getAccessToken(), message.getSecret());
        DingTalkRobotMessageResponse response = restClient.post()
                .uri(BASE_URL.concat(WindConstants.QUESTION_MARK).concat(request.getQueryString()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(request.getData())
                .retrieve()
                .body(DingTalkRobotMessageResponse.class);
        AssertUtils.notNull(response, "robot dingtalk response must not null");
        AssertUtils.isTrue(response.isSuccess(), response.getErrmsg());
    }

    @Override
    public boolean supports(Class<? extends DingTalkRobotMessage> messageType) {
        return DingTalkRobotMessage.class.isAssignableFrom(messageType);
    }
}