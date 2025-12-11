package com.wind.integration.message.email;

import com.wind.integration.core.message.MessageDefinition;
import com.wind.integration.core.message.WindSendMessageConstants;

import java.util.Set;


/**
 * 简单的邮件消息
 *
 * @author wuxp
 * @date 2025-12-11 16:48
 */
public record SimpleEmailMessage(String title, String message, String format, Set<String> receivers) implements MessageDefinition<String> {

    public boolean isText() {
        return WindSendMessageConstants.TEXT_FORMAT.equals(format);
    }

    public boolean isHtml() {
        return WindSendMessageConstants.TEXT_FORMAT.equals(format);
    }

    public static SimpleEmailMessage text(String title, String message, Set<String> receivers) {
        return new SimpleEmailMessage(title, message, WindSendMessageConstants.TEXT_FORMAT, receivers);
    }

    public static SimpleEmailMessage html(String title, String message, Set<String> receivers) {
        return new SimpleEmailMessage(title, message, WindSendMessageConstants.HTML_FORMAT, receivers);
    }
}
