package com.wind.integration.message.dingtalk;

import com.alibaba.fastjson2.annotation.JSONField;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wind.common.exception.AssertUtils;
import com.wind.integration.core.message.MessageDefinition;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 钉钉机器人消息
 *
 * @author wuxp
 * @date 2025-12-11 16:39
 **/
@Data
public class DingTalkRobotMessage implements MessageDefinition<DingTalkRobotMessage.DingDingRobotMessageData> {

    private String title;

    private String accessToken;

    private String secret;

    private DingDingRobotMessageData messageBody;


    @Override
    public String title() {
        return title;
    }

    @Override
    public DingDingRobotMessageData message() {
        return messageBody;
    }

    @Override
    public String format() {
        AssertUtils.notNull(messageBody, "messageBody不能为空");
        return messageBody.getMsgtype().getType();
    }

    @Override
    public Set<String> receivers() {
        return Set.of(accessToken);
    }

    public static DingTalkRobotMessage text(String accessToken, String secret, String title, String message, List<String> userIds, List<String> mobiles, boolean atAll) {
        DingTalkRobotMessage robotMessage = initDingDingMessage(accessToken, secret, title);
        DingDingMessageText text = new DingDingMessageText();
        text.setContent(message);
        DingDingRobotMessageData messageData = new DingDingRobotMessageData();
        messageData.setMsgtype(DingTalkMessageType.TEXT);
        messageData.setMessageContent(Map.of(DingTalkMessageType.TEXT.getType(), text));
        DingDingRobotMessageAt messageAt = new DingDingRobotMessageAt(mobiles, userIds, atAll);
        messageData.setAt(messageAt);
        robotMessage.setMessageBody(messageData);
        return robotMessage;
    }

    public static DingTalkRobotMessage markdown(String accessToken, String secret, String title, String markDown, Collection<String> userIds, Collection<String> mobiles,
                                                boolean atAll) {
        DingTalkRobotMessage robotMessage = initDingDingMessage(accessToken, secret, title);
        DingDingRobotMessageMarkdown markdown = new DingDingRobotMessageMarkdown();
        markdown.setText(markDown);
        markdown.setTitle(title);
        DingDingRobotMessageData messageData = new DingDingRobotMessageData();
        messageData.setMsgtype(DingTalkMessageType.MARKDOWN);
        messageData.setMessageContent(Map.of(DingTalkMessageType.MARKDOWN.getType(), markdown));
        DingDingRobotMessageAt messageAt = new DingDingRobotMessageAt(mobiles, userIds, atAll);
        messageData.setAt(messageAt);
        robotMessage.setMessageBody(messageData);
        return robotMessage;
    }

    private static DingTalkRobotMessage initDingDingMessage(String accessToken, String secret, String title) {
        DingTalkRobotMessage robotMessage = new DingTalkRobotMessage();
        robotMessage.setAccessToken(accessToken);
        robotMessage.setSecret(secret);
        robotMessage.setTitle(title);
        return robotMessage;
    }

    @Data
    @FieldNameConstants
    public static class DingDingRobotMessageData {

        private DingTalkMessageType msgtype;

        private DingDingRobotMessageAt at;

        private Map<String, Object> messageContent;

        public Map<String, Object> getSendBodyInfo() {
            return Map.of(Fields.msgtype, msgtype.getType(), Fields.at, at, msgtype.getType(), messageContent.get(msgtype.getType()));
        }
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class DingDingRobotMessageAt {

        private Collection<String> atMobiles;

        private Collection<String> atUserIds;

        @JSONField(name = "isAtAll")
        @JsonProperty("isAtAll")
        private Boolean atAll;
    }

    @Data
    public static class DingDingMessageText {

        private String content;
    }

    @Data
    public static class DingDingRobotMessageLink {
        private String text;

        private String title;

        private String picUrl;

        private String messageUrl;

    }

    @Data
    public static class DingDingRobotMessageMarkdown {

        private String title;

        private String text;


    }

    @Data
    public static class DingDingRobotMessageActionCard {
        private String title;

        private String text;

        private String btnOrientation;

        private String singleTitle;

        private String singleURL;

        private List<DingDingRobotMessageActionCardBtn> btns;
    }

    @Data
    public static class DingDingRobotMessageActionCardBtn {

        private String title;

        private String actionURL;
    }

    @Data
    public static class DingDingRobotMessageFeedCard {

        private List<DingDingRobotMessageLink> links;
    }
}
