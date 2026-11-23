package com.wind.integration.message.dingtalk;

import com.wind.common.exception.BaseException;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 钉钉消息类型
 * 参见：https://open.dingtalk.com/document/robots/custom-robot-access
 *
 * @author wuxp
 * @date 2025-12-11 16:41
 **/
@AllArgsConstructor
@Getter
public enum DingTalkMessageType {
    /**
     * 文本消息
     */
    TEXT("text"),

    /**
     * 链接消息
     */
    LINK("link"),

    /**
     * markdown消息
     */
    MARKDOWN("markdown"),

    ACTIONCARD("actionCard"),

    FEEDCARD("feedCard");

    private final String type;

    public static DingTalkMessageType fromMsgType(String msgType) {
        for (DingTalkMessageType value : DingTalkMessageType.values()) {
            if (value.getType().equals(msgType)) {
                return value;
            }
        }
        throw BaseException.common("不支持的消息类型");
    }
}
